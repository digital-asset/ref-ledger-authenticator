// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.events

import java.time.temporal.ChronoUnit

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import com.daml.ledger.api.refinements.ApiTypes.ApplicationId
import com.daml.ledger.api.v1.command_service.SubmitAndWaitRequest
import com.daml.ledger.api.v1.ledger_offset.LedgerOffset
import com.daml.ledger.api.v1.transaction.Transaction
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.{Contract, Primitive}
import com.projectdabl.authenticationservice.config.ServiceAccountConfig
import com.projectdabl.authenticationservice.exc.InternalException
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3._
import com.projectdabl.authenticationservice.service.code.CodeGenerator
import com.projectdabl.authenticationservice.service.cubby.{CredentialCoordinate, CubbyHole}
import com.projectdabl.authenticationservice.service.ledger._
import com.projectdabl.authenticationservice.service.password.KeyExpander
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

object LedgerEventHandler {
  def apply(ledgerClient: LedgerClient,
            operatorC: Contract[Operator],
            codeGenerator: CodeGenerator,
            cubbyHole: CubbyHole,
            serviceAccountConfig: ServiceAccountConfig)
           (implicit ec: ExecutionContext, am: Materializer): LedgerEventHandler =
    new LedgerEventHandler(ledgerClient, operatorC, codeGenerator, cubbyHole, serviceAccountConfig)(ec, am)
}

class LedgerEventHandler(val ledgerClient: LedgerClient,
                         val operatorC: Contract[Operator],
                         val codeGenerator: CodeGenerator,
                         val cubbyHole: CubbyHole,
                         val serviceAccountConfig: ServiceAccountConfig)
                        (implicit ec: ExecutionContext, am: Materializer)
  extends CommandBuilder
    with TransactionResponseTransformer
    with IdGenerator
    with PartyFilterer
    with ClockReader
    with KeyExpander
    with LazyLogging {

  override val applicationId: ApplicationId = ApplicationId("authentication-service")

  private val txnCreationFlow = Flow[Transaction]
    .mapConcat(txn => txn.events.toList)
    .filter(ev => ev.event.isCreated)
    .map(createEv => createEv.getCreated)

  private def startLedgerOffsetF(): Future[LedgerOffset] =
    ledgerClient
      .transactionClient
      .getLedgerEnd()
      .map(_.offset)
      .map {
        case Some(startOffset) =>
          logger.info("starting event handlers from offset {}", startOffset)
          startOffset
        case _ => throw new InternalException("failed to find start offset")
      }

  private def buildServiceAccountRequestHandler(offset: LedgerOffset): Future[Done] =
    ledgerClient.transactionClient.getTransactions(
      start = offset,
      end = None,
      transactionFilter = partyFilterTemplate(operatorC.value.operator, ServiceAccountRequest.id)
    )
      .via(txnCreationFlow)
      .mapConcat(createdEv =>
        ServiceAccountRequest
          .fromNamedArguments(createdEv.getCreateArguments)
          .map(sar => contractInstance(Primitive.ContractId(createdEv.contractId), sar))
          .map(List(_))
          .getOrElse(List())
      )
      .mapAsync(parallelism = 1) { requestC: Contract[ServiceAccountRequest] =>
        import ServiceAccountRequest._

        ledgerClient.commandServiceClient.submitAndWaitForTransaction(
          SubmitAndWaitRequest(
            Some(
              buildCommands(
                operatorC.value.operator,
                requestC
                  .contractId
                  .exerciseServiceAccountRequestAccept(
                    operatorC.value.operator,
                    ServiceAccountRequestAccept(
                      genServiceAccount()
                    )
                  )
                  .command
              )
            )
          )
        ).recover {
          case ex => logger.warn("failed to accept service account request", ex)
        }
      }.runWith(Sink.ignore)

  private def buildServiceAccountCredentialRequestHandler(offset: LedgerOffset): Future[Done] =
    ledgerClient.transactionClient.getTransactions(
      start = offset,
      end = None,
      transactionFilter = partyFilterTemplate(operatorC.value.operator, ServiceAccountCredentialRequest.id)
    )
      .via(txnCreationFlow)
      .mapConcat { createdEv =>
        ServiceAccountCredentialRequest
          .fromNamedArguments(createdEv.getCreateArguments)
          .map { sacr =>
            contractInstance(
              Primitive.ContractId(createdEv.contractId),
              sacr
            )
          }
          .map(List(_))
          .getOrElse(List())
      }
      .mapAsync(parallelism = 1) { saCredRequestC: Contract[ServiceAccountCredentialRequest] =>
        import ServiceAccountCredentialRequest._

        val credential = codeGenerator.genCode(serviceAccountConfig.credLength)
        val credentialId = genCredentialId()
        val salt = codeGenerator.genCode(serviceAccountConfig.saltLength)
        val hash = expandKey(credential, salt)
        val issueTime = readClock()
        val expiryTime = readClock(issueTime.plus(serviceAccountConfig.validityDuration.getSeconds, ChronoUnit.SECONDS))

        cubbyHole.put(CredentialCoordinate(saCredRequestC.value.owner, credentialId), credential)

        ledgerClient.commandServiceClient.submitAndWaitForTransaction(
          SubmitAndWaitRequest(
            Some(
              buildCommands(
                operatorC.value.operator,
                saCredRequestC
                  .contractId
                  .exerciseServiceAccountCredentialGrant(
                    operatorC.value.operator,
                    ServiceAccountCredentialGrant(
                      credentialId = credentialId,
                      credentialSalt = salt,
                      validFrom = issueTime,
                      validTo = expiryTime,
                      credentialHash = hash
                    )
                  )
                  .command
              )
            )
          )
        ).recover {
          case ex =>
            logger.warn("failed to process service account credential request", ex)
        }
      }.runWith(Sink.ignore)

  def runHandler(): Future[Done] = for {
    offset <- startLedgerOffsetF()
    res <- Future.firstCompletedOf(
      Seq(
        buildServiceAccountRequestHandler(offset),
        buildServiceAccountCredentialRequestHandler(offset)
      )
    )
  } yield res
}

