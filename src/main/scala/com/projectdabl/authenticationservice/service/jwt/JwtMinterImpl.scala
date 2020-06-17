// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.jwt


import java.time.Clock

import com.projectdabl.authenticationservice.api.ServiceAccountIdentity
import com.projectdabl.authenticationservice.key.RSAKeyPair
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtHeader, JwtSprayJson}
import spray.json._

import scala.concurrent.duration.Duration

object JwtMinterImpl {
  def apply(rsaKeyPair: RSAKeyPair, keyId: String, issuer: String, validityDuration: Duration): JwtMinterImpl =
    new JwtMinterImpl(rsaKeyPair, keyId, issuer, validityDuration)
}

class JwtMinterImpl(rsaKeyPair: RSAKeyPair,
                    keyId: String,
                    issuer: String,
                    validityDuration: Duration) extends JwtMinter {

  import com.projectdabl.authenticationservice.api.ServiceAccountIdentityProtocol._

  override def mintJwt(serviceAccountIdentity: ServiceAccountIdentity)(implicit clock: Clock): String =
    JwtSprayJson.encode(
      JwtHeader(JwtAlgorithm.RS256)
        .withKeyId(keyId),
      JwtClaim()
        .about(serviceAccountIdentity.party)
        .withContent(serviceAccountIdentity.toJson.compactPrint)
        .by(issuer)
        .expiresIn(validityDuration.toSeconds),
      rsaKeyPair.privateKey
    )
}

