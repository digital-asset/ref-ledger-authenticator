// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger.user

import com.daml.ledger.api.v1.command_service.SubmitAndWaitRequest
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.Contract
import com.projectdabl.authenticationservice.exc.InternalException
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3.{ServiceAccountCredentialHash, User, UserFetchServiceAccountCredentialHash}
import com.projectdabl.authenticationservice.service.ledger.{CommandBuilder, TransactionResponseTransformer}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

trait CredentialHashResolver extends CommandBuilder with TransactionResponseTransformer with LazyLogging {

  import User._

  val ledgerClient: LedgerClient

  def resolveServiceAccountCredentialById(userC: Contract[User], credentialId: String)(implicit ec: ExecutionContext):
  Future[Contract[ServiceAccountCredentialHash]] =
    ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
      SubmitAndWaitRequest(
        Some(
          buildCommands(
            userC
              .value
              .user,
            userC
              .contractId
              .exerciseUserFetchServiceAccountCredentialHash(
                userC.value.user,
                UserFetchServiceAccountCredentialHash(credentialId)
              )
              .command
          )
        )
      )
    ).map { res =>
      extractFirstExercisedTemplateContract(res)(ServiceAccountCredentialHash.`the TemplateCompanion`) match {
        case Some(contract) => contract
        case None => throw new InternalException("failed to extract service account credential hash")
      }
    }
}

