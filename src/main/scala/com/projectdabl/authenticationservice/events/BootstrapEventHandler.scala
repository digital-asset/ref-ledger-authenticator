// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.events

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import com.daml.ledger.api.refinements.ApiTypes
import com.daml.ledger.api.refinements.ApiTypes.ApplicationId
import com.daml.ledger.api.v1.command_service.{SubmitAndWaitForTransactionResponse, SubmitAndWaitRequest}
import com.daml.ledger.api.v1.event.CreatedEvent
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.{Contract, Primitive}
import com.projectdabl.authenticationservice.config.LedgerConfig
import com.projectdabl.authenticationservice.exc.InternalException
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3.Operator
import com.projectdabl.authenticationservice.service.ledger.{CommandBuilder, PartyFilterer, TransactionResponseTransformer}
import com.typesafe.scalalogging.LazyLogging
import scalaz.@@

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object BootstrapEventHandler {
  def apply(ledgerClient: LedgerClient, ledgerConfig: LedgerConfig)
           (implicit ec: ExecutionContext, am: Materializer): BootstrapEventHandler =
    new BootstrapEventHandler(ledgerClient, ledgerConfig)(ec, am)
}

class BootstrapEventHandler(override val ledgerClient: LedgerClient,
                            ledgerConfig: LedgerConfig)(implicit ec: ExecutionContext, am: Materializer)
  extends CommandBuilder with TransactionResponseTransformer with PartyFilterer with LazyLogging {

  override val applicationId: ApplicationId = ApplicationId("authentication-service")

  private val serviceParty: String @@ ApiTypes.PartyTag = Primitive.Party(ledgerConfig.serviceParty)

  private[this] val operatorEventOptF: Future[Option[CreatedEvent]] =
    ledgerClient
      .activeContractSetClient
      .getActiveContracts(partyFilterTemplate(serviceParty, Operator.id))
      .filter(_.activeContracts.nonEmpty)
      .map(_.activeContracts.head)
      .toMat(Sink.headOption)(Keep.right)
      .run()

  private[this] val createOperatorF: Future[SubmitAndWaitForTransactionResponse] =
    ledgerClient
      .commandServiceClient
      .submitAndWaitForTransaction(
        SubmitAndWaitRequest(
          Some(
            buildCommands(
              serviceParty,
              Operator(serviceParty)
                .create
                .command
            )
          )
        )
      )

  private[this] val operatorCidOptF: Future[Option[String]] = operatorEventOptF.flatMap {
    case Some(ev) => Future.successful(Some(ev.contractId))
    case None => createOperatorF.map(extractFirstCreatedContractId)
  }

  private[this] val operatorContractIdF: Future[String] = operatorCidOptF.flatMap {
    case Some(value) => Future.successful(value)
    case None => createOperatorF.map { ev =>
      extractFirstCreatedContractId(ev) match {
        case Some(contractId) => contractId
        case None => throw new InternalException("no cid could be extracted from matched operator contract")
      }
    }
  }

  def operatorContractF(): Future[Contract[Operator]] =
    operatorContractIdF.map(cid => contractInstance(Primitive.ContractId(cid), Operator(serviceParty)))

  operatorContractF onComplete {
    case Success(operatorContract) =>
      logger.info("operator contract is in place: {}", operatorContract)

    case Failure(exc) =>
      logger.error("failed to create or ensure initial operator contract", exc)
      throw exc
  }
}

