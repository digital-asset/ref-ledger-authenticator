// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger.user

import com.daml.ledger.api.v1.command_service.SubmitAndWaitRequest
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.{Contract, Primitive}
import com.projectdabl.authenticationservice.exc.InternalException
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3.{ServiceAccount, User, UserFetchServiceAccount}
import com.projectdabl.authenticationservice.service.ledger.{CommandBuilder, TransactionResponseTransformer}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

trait ServiceAccountResolver extends CommandBuilder with TransactionResponseTransformer with LazyLogging {
  import User._

  val ledgerClient: LedgerClient

  def resolveServiceAccountById(userC: Contract[User], saId: String)(implicit ec: ExecutionContext):
  Future[Contract[ServiceAccount]] = {
    ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
      SubmitAndWaitRequest(
        Some(
          buildCommands(
            userC
              .value
              .user,
            userC
              .contractId
              .exerciseUserFetchServiceAccount(userC.value.user, UserFetchServiceAccount(Primitive.Party(saId)))
              .command
          )
        )
      )
    ).map { res =>
      extractFirstExercisedTemplateContract(res)(ServiceAccount.`the TemplateCompanion`) match {
        case Some(value) => value
        case None => throw new InternalException("failed to extract service account")
      }
    }
  }
}

