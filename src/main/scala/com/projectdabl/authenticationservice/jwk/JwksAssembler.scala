// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.jwk

import java.security.interfaces.RSAPublicKey

import org.jose4j.jwk.{JsonWebKeySet, RsaJsonWebKey}

object JwksAssembler {
  def apply(): JwksAssembler = new JwksAssembler()
}

class JwksAssembler {
  def assembleJwk(publicKey: RSAPublicKey, kid: String): RsaJsonWebKey = {
    val jwk = new RsaJsonWebKey(publicKey)
    jwk.setKeyId(kid)
    jwk.setAlgorithm("RS256")
    jwk.setUse("sig")
    jwk
  }

  def assemble(jwk: RsaJsonWebKey*): JsonWebKeySet = new JsonWebKeySet(jwk: _*)

  def assembleJwks(publicKey: RSAPublicKey, kid: String): JsonWebKeySet =
    assemble(assembleJwk(publicKey, kid))
}

