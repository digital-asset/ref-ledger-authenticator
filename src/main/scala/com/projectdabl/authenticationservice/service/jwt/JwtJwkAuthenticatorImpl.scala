// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.jwt

import akka.http.scaladsl.server.directives.Credentials
import com.auth0.jwk.{GuavaCachedJwkProvider, JwkProvider, UrlJwkProvider}
import com.projectdabl.authenticationservice.api.UserIdentity
import com.projectdabl.authenticationservice.config.JwksConfig
import pdi.jwt.{JwtAlgorithm, JwtOptions, JwtSprayJson}

import scala.util.Try

object JwtJwkAuthenticatorImpl {
  def apply(jwksConfig: JwksConfig): JwtJwkAuthenticatorImpl =
    new JwtJwkAuthenticatorImpl(
      new GuavaCachedJwkProvider(
        new UrlJwkProvider(
          jwksConfig.endpoint,
          jwksConfig.connTimeout.toMillis.toInt,
          jwksConfig.readTimeout.toMillis.toInt
        )
      )
    )
}

class JwtJwkAuthenticatorImpl(jwkProvider: JwkProvider) extends JwtAuthenticator {
  override def oauth2Authenticator(creds: Credentials): Option[UserIdentity] =
    creds match {
      case Credentials.Missing => None
      case Credentials.Provided(bearerToken) => authenticateToken(bearerToken)
    }

  private def authenticateToken(tokenValue: String): Option[UserIdentity] =
    for {
      (header, _, _) <- JwtSprayJson.decodeAll(tokenValue, JwtOptions(signature = false)).toOption
      jwtKeyId <- header.keyId
      jwkToDecodeWith <- Try(jwkProvider.get(jwtKeyId)).toOption
      algToUse <- JwtAlgorithm.optionFromString(jwkToDecodeWith.getAlgorithm)
      if algToUse == JwtAlgorithm.RS256
      validatedClaim <- JwtSprayJson.decode(tokenValue, jwkToDecodeWith.getPublicKey, Seq(JwtAlgorithm.RS256)).toOption
      sub <- validatedClaim.subject
    } yield UserIdentity(sub)
}

