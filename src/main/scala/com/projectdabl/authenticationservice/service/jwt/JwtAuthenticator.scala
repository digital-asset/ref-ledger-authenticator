// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.jwt

import akka.http.scaladsl.server.directives.Credentials
import com.projectdabl.authenticationservice.api.UserIdentity

trait JwtAuthenticator {
  def oauth2Authenticator(creds: Credentials): Option[UserIdentity]
}

