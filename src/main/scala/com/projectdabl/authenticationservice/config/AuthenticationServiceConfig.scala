// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.config

import java.net.URL
import java.time.Duration

import com.typesafe.config.Config

import scala.util.Try

object AuthenticationServiceConfig {
  def apply(config: Config): AuthenticationServiceConfig =
    AuthenticationServiceConfig(
      testMode = config.getBoolean("testMode"),
      serviceConfig = ServiceConfig(
        address = config.getString("service.address"),
        port = config.getInt("service.port"),
        allowedConsoleOrigin = config.getString("service.allowedConsoleOrigin")
      ),
      ledgerConfig = LedgerConfig(
        ledgerUrl = config.getString("ledger.url"),
        preloadPath = Try {
          config.getString("ledger.preloadPath")
        }.toOption,
        serviceParty = config.getString("ledger.serviceParty")
      ),
      jwtConfig = JwtConfig(
        issuer = config.getString("jwt.issuer"),
        validityDuration = config.getDuration("jwt.validityDuration")
      ),
      jwksConfig = JwksConfig(
        new URL(config.getString("jwks.endpoint")), // note: throws
        connTimeout = config.getDuration("jwks.connTimeout"),
        readTimeout = config.getDuration("jwks.readTimeout")
      ),
      serviceAccountConfig = ServiceAccountConfig(
        config.getDuration("serviceAccount.validityDuration"),
        config.getInt("serviceAccount.credLength"),
        config.getInt("serviceAccount.saltLength")
      )
    )
}

final case class AuthenticationServiceConfig(
  testMode: Boolean,
  serviceConfig: ServiceConfig,
  ledgerConfig: LedgerConfig,
  jwtConfig: JwtConfig,
  jwksConfig: JwksConfig,
  serviceAccountConfig: ServiceAccountConfig)

final case class ServiceConfig(address: String, port: Int, allowedConsoleOrigin: String)

final case class LedgerConfig(ledgerUrl: String, preloadPath: Option[String], serviceParty: String)

final case class JwtConfig(issuer: String, validityDuration: Duration)

final case class JwksConfig(endpoint: URL, connTimeout: Duration, readTimeout: Duration)

final case class ServiceAccountConfig(validityDuration: Duration, credLength: Int, saltLength: Int)

