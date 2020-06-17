// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.key

import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}

object RSAKeyPair {
  def apply(privateKey: RSAPrivateKey, publicKey: RSAPublicKey): RSAKeyPair = new RSAKeyPair(privateKey, publicKey)
}

final case class RSAKeyPair(privateKey: RSAPrivateKey, publicKey: RSAPublicKey)


