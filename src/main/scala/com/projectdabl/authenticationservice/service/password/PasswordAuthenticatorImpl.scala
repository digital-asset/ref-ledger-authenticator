// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.password

import akka.http.scaladsl.server.directives.Credentials
import akka.stream.Materializer
import com.daml.ledger.client.binding.Primitive
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3.ServiceAccountCredentialHash
import com.projectdabl.authenticationservice.service.ledger.{AdminLedgerService, ClockReader}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

object PasswordAuthenticatorImpl {
  def apply(adminLedgerService: AdminLedgerService)
           (implicit ec: ExecutionContext, am: Materializer): PasswordAuthenticatorImpl =
    new PasswordAuthenticatorImpl(adminLedgerService)
}

class PasswordAuthenticatorImpl(adminLedgerService: AdminLedgerService)
                               (implicit ec: ExecutionContext, am: Materializer)
  extends PasswordAuthenticator
    with KeyExpander
    with ClockReader
    with LazyLogging {

  def passwordAuthenticateAsync(credentials: Credentials): Future[Option[ServiceAccountCredentialHash]] =
    credentials match {
      case provided@Credentials.Provided(id) =>
        adminLedgerService
          .operatorFetchServiceAccountCredentialHashById(id)
          .run
          .map { hashOpt: Option[ServiceAccountCredentialHash] =>
            hashOpt
              .filter { hash =>
                val authTime = readClock()

                hash.validFrom.isBefore(authTime) &&
                  authTime.isBefore(hash.validTo) &&
                  provided.verify(
                    hash.credentialHash,
                    generateHasher(hash.credentialSalt)
                  )
              }
          }

      case _ => Future.successful(None)
    }

  private def generateHasher(salt: String): String => String = { cred: String => expandKey(cred, salt) }
}

