// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger

import com.daml.ledger.api.v1.command_service.SubmitAndWaitForTransactionTreeResponse
import com.daml.ledger.client.binding.{Contract, Template}
import com.projectdabl.authenticationservice.api.UserIdentity
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3._
import scalaz.OptionT

import scala.concurrent.Future

trait AdminLedgerService {
  def authorizeUser(identity: UserIdentity):
  OptionT[Future, String]

  def retrieveUser(identity: UserIdentity):
  OptionT[Future, User with Template[User]]

  def listServiceAccounts(identity: UserIdentity):
  OptionT[Future, Seq[Contract[ServiceAccount]]]

  def createServiceAccountRequest(identity: UserIdentity, ledgerId: String, nonce: String):
  OptionT[Future, ServiceAccountRequest]

  def createServiceAccountCredentialRequest(identity: UserIdentity, saId: String):
  OptionT[Future, ServiceAccountCredentialRequest]

  def operatorFetchServiceAccountCredentialHashById(credentialId: String):
  OptionT[Future, ServiceAccountCredentialHash]

  def fetchServiceAccountCredentialHashById(identity: UserIdentity, credentialId: String):
  OptionT[Future, ServiceAccountCredentialHash]

  def deleteServiceAccountCredentialHashById(identity: UserIdentity, credentialId: String):
  OptionT[Future, SubmitAndWaitForTransactionTreeResponse]
}

