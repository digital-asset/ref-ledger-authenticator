// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.password

import akka.http.scaladsl.server.directives.Credentials
import com.daml.ledger.client.binding.Primitive
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3.ServiceAccountCredentialHash

import scala.concurrent.Future

trait PasswordAuthenticator {
  def passwordAuthenticateAsync(credentials: Credentials): Future[Option[ServiceAccountCredentialHash]]
}

