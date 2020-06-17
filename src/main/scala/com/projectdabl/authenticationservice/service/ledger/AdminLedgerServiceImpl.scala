// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import com.daml.ledger.api.refinements.ApiTypes.ApplicationId
import com.daml.ledger.api.v1.command_service.{SubmitAndWaitForTransactionTreeResponse, SubmitAndWaitRequest}
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.{Contract, Primitive, Template}
import com.projectdabl.authenticationservice.api.UserIdentity
import com.projectdabl.authenticationservice.exc.InternalException
import com.projectdabl.authenticationservice.model.DA.Internal.Template.Archive
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3.{ServiceAccount, _}
import com.projectdabl.authenticationservice.service.ledger.user.{CredentialHashResolver, ServiceAccountResolver, UserResolver}
import com.typesafe.scalalogging.LazyLogging
import scalaz.OptionT
import scalaz.OptionT.optionT
import scalaz.std.scalaFuture._

import scala.concurrent.{ExecutionContext, Future}

object AdminLedgerServiceImpl {
  def apply(ledgerClient: LedgerClient, operatorC: Contract[Operator])
           (implicit ec: ExecutionContext, am: Materializer): AdminLedgerServiceImpl =
    new AdminLedgerServiceImpl(ledgerClient, operatorC)(ec, am)
}

class AdminLedgerServiceImpl(val ledgerClient: LedgerClient,
                             val operatorC: Contract[Operator])
                            (implicit ec: ExecutionContext, am: Materializer)
  extends AdminLedgerService
    with UserResolver
    with ServiceAccountResolver
    with CredentialHashResolver
    with TransactionResponseTransformer
    with IdGenerator
    with PartyFilterer
    with LazyLogging {

  override val applicationId: ApplicationId = ApplicationId("authentication-service")

  override def authorizeUser(identity: UserIdentity): OptionT[Future, String] = {
    import Operator._

    optionT(
      ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
        SubmitAndWaitRequest(
          Some(
            buildCommands(
              operatorC
                .value
                .operator,
              operatorC
                .contractId
                .exerciseOperatorUpsertPartyAssociation(
                  operatorC.value.operator,
                  OperatorUpsertPartyAssociation(
                    identity.userId,
                    genAuthenticationUser()
                  )
                )
                .command
            )
          )
        )
      )
        .map(res => extractFirstExerciseResultValue(res))
        .map(valOpt => valOpt.map(_.getContractId))
        .recover {
          case ex =>
            logger.warn("failed to upsert user association", ex)
            None
        }
    )
  }

  def retrieveUser(identity: UserIdentity): OptionT[Future, User with Template[User]] = {
    import Operator._

    optionT(
      ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
        SubmitAndWaitRequest(
          Some(
            buildCommands(
              operatorC
                .value
                .operator,
              operatorC
                .contractId
                .exerciseOperatorFetchUser(
                  operatorC.value.operator,
                  OperatorFetchUser(
                    identity.userId
                  )
                ).command
            )
          )
        )
      ).map { res =>
        extractFirstExercisedTemplateContract(res)(User.`the TemplateCompanion`).map(_.value)
      }.recover {
        case ex =>
          logger.warn("failed to fetch user with id {}", identity, ex)
          None
      }
    )
  }

  override def listServiceAccounts(identity: UserIdentity): OptionT[Future, Seq[Contract[ServiceAccount]]] =
    resolveUserContractById(identity).flatMapF { userC =>
      ledgerClient.activeContractSetClient.getActiveContracts(
        partyFilterTemplate(
          userC.value.user,
          ServiceAccount.id
        )
      ).toMat(Sink.seq)(Keep.right).run().map { responses =>
        for {
          res <- responses
          created <- res.activeContracts
          serviceAccount <- ServiceAccount.fromNamedArguments(created.getCreateArguments)
        } yield contractInstance(Primitive.ContractId(created.contractId), serviceAccount)
      }
    }

  override def createServiceAccountRequest(identity: UserIdentity, ledgerId: String, nonce: String):
  OptionT[Future, ServiceAccountRequest] =
    resolveUserContractById(identity).flatMapF { userC =>
      import User._

      ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
        SubmitAndWaitRequest(
          Some(
            buildCommands(
              userC
                .value
                .user,
              userC
                .contractId
                .exerciseUserRequestServiceAccount(
                  userC.value.user,
                  UserRequestServiceAccount(ledgerId, nonce)
                )
                .command
            )
          )
        )
      )
    }.map { res =>
      extractFirstCreatedTemplateContract(res)(ServiceAccountRequest.`the TemplateCompanion`) match {
        case Some(value) => value
        case None => throw new InternalException("failed to extract")
      }
    }

  override def createServiceAccountCredentialRequest(identity: UserIdentity, saId: String):
  OptionT[Future, ServiceAccountCredentialRequest] =
    resolveUserContractById(identity).flatMapF { userC =>
      import ServiceAccount._

      resolveServiceAccountById(userC, saId).flatMap { serviceAccountC: Contract[ServiceAccount] =>
        ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
          SubmitAndWaitRequest(
            Some(
              buildCommands(
                userC
                  .value
                  .user,
                serviceAccountC
                  .contractId
                  .exerciseServiceAccountRequestCredential(
                    userC.value.user,
                    ServiceAccountRequestCredential()
                  )
                  .command
              )
            )
          )
        )
      }
    }.map { res =>
      extractFirstCreatedTemplateContract(res)(ServiceAccountCredentialRequest.`the TemplateCompanion`) match {
        case Some(value) => value
        case None => throw new InternalException("failed to extract service account credential request")
      }
    }

  override def operatorFetchServiceAccountCredentialHashById(credentialId: String):
  OptionT[Future, ServiceAccountCredentialHash] = {
    import Operator._

    optionT(
      ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
        SubmitAndWaitRequest(
          Some(
            buildCommands(
              operatorC
                .value
                .operator,
              operatorC
                .contractId
                .exerciseOperatorFetchServiceAccountCredentialHash(
                  operatorC.value.operator,
                  OperatorFetchServiceAccountCredentialHash(credentialId)
                )
                .command
            )
          )
        )
      ).map { res =>
        extractFirstExercisedTemplateContract(res)(ServiceAccountCredentialHash.`the TemplateCompanion`)
          .map(_.value)
      }.recover {
        case ex =>
          logger.warn("failed to fetch credential with id {}", credentialId, ex)
          None
      }
    )
  }

  override def fetchServiceAccountCredentialHashById(identity: UserIdentity, credentialId: String):
  OptionT[Future, ServiceAccountCredentialHash] =
    resolveUserContractById(identity).flatMapF { userC =>
      resolveServiceAccountCredentialById(userC, credentialId)
        .map(_.value)
    }

  override def deleteServiceAccountCredentialHashById(identity: UserIdentity, credentialId: String):
  OptionT[Future, SubmitAndWaitForTransactionTreeResponse] =
    resolveUserContractById(identity).flatMapF { userC =>
      import ServiceAccountCredentialHash._

      resolveServiceAccountCredentialById(userC, credentialId).flatMap { serviceAccountCredHashC =>
        ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
          SubmitAndWaitRequest(
            Some(
              buildCommands(
                userC
                  .value
                  .user,
                serviceAccountCredHashC
                  .contractId
                  .exerciseArchive(userC.value.user, Archive())
                  .command
              )
            )
          )
        )
      }
    }
}

