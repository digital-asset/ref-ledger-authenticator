// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger.user

import com.daml.ledger.api.v1.command_service.SubmitAndWaitRequest
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.Contract
import com.projectdabl.authenticationservice.api.UserIdentity
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3.{Operator, User}
import com.projectdabl.authenticationservice.service.ledger.{CommandBuilder, TransactionResponseTransformer}
import com.typesafe.scalalogging.LazyLogging
import scalaz.OptionT
import scalaz.OptionT.optionT
import com.daml.ledger.client.binding.Primitive

import scala.concurrent.{ExecutionContext, Future}

trait UserResolver extends CommandBuilder with TransactionResponseTransformer with LazyLogging {
  import Operator._

  val ledgerClient: LedgerClient

  val operatorC: Contract[Operator]

  def resolveUserContractById(userId: UserIdentity)
                             (implicit ec: ExecutionContext): OptionT[Future, Contract[User]] =
    optionT(
      ledgerClient.commandServiceClient.submitAndWaitForTransactionTree(
        SubmitAndWaitRequest(
          Some(
            buildCommands(
              operatorC.value.operator,
              operatorC
                .contractId
                .exerciseOperatorFetchUser(operatorC.value.operator, Primitive.Party(userId.userId))
                .command
            )
          )
        )
      )
        .map(res => extractFirstExercisedTemplateContract(res)(User.`the TemplateCompanion`))
        .recover {
          case ex =>
            logger.warn("failed to fetch user by contract id", ex)
            None
        }
    )
}

