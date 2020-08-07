// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.projectdabl.authenticationservice.api.ServiceAccountProtocol.jsonFormat1
import spray.json.{DefaultJsonProtocol, JsObject}

case class UserIdentity(userId: String)

object UserIdentityProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uip = jsonFormat1(UserIdentity)
}

// ledger access token
final case class LedgerPartyIdentity(ledgerId: String,
                                     owner: String,
                                     party: String,
                                     rights: List[String],
                                     partyName: String) {
  def tokenPayload(applicationId: String): JsObject = {
    import spray.json.DefaultJsonProtocol._
    import spray.json._

    JsObject(
      "https://daml.com/ledger-api" -> JsObject(
        "ledgerId" -> JsString(ledgerId),
        "applicationId" -> JsString(applicationId),
        "actAs" -> List(party).toJson,
      ),
      "owner" -> JsString(owner),
      "partyName" -> JsString(partyName),
      "rights" -> rights.toJson
    )
  }
}

case class ServiceAccountCredentialResponse(
  credId: String,
  nonce: Option[String] = None,
  cred: Option[String] = None,
  validFrom: Option[String] = None,
  validTo: Option[String] = None)

case class ServiceAccountResponse(
  serviceAccount: String,
  nonce: String,
  creds: List[ServiceAccountCredentialResponse]
)

case class ServiceAccountListResponse(serviceAccounts: List[ServiceAccountResponse])

object LedgerPartyIdentityProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val lpip = jsonFormat5(LedgerPartyIdentity)
}

object ServiceAccountProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val sacp = jsonFormat5(ServiceAccountCredentialResponse)
  implicit val sap = jsonFormat3(ServiceAccountResponse)
  implicit val sasp = jsonFormat1(ServiceAccountListResponse)
}

case class JwtTokenResponse(token: String)

object JwtTokenResponseProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val jtp = jsonFormat1(JwtTokenResponse)
}

