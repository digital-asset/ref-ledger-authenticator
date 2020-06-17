// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.password

import java.nio.charset.StandardCharsets
import java.util.Base64

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

trait KeyExpander {
  val base64Encoder: Base64.Encoder = Base64.getEncoder

  val keyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")

  // TODO: validate these empirically
  val derivedKeyLengthChars: Int = 64

  val derivedKeyLengthBits: Int = 8 * derivedKeyLengthChars // this can remain fixed

  val iterationCount = 128

  def expandKey(password: String, salt: String): String =
    base64Encoder.encodeToString(
      keyFactory.generateSecret(
        new PBEKeySpec(
          password.toCharArray,
          salt.getBytes(StandardCharsets.UTF_8),
          iterationCount,
          derivedKeyLengthBits
        )
      ).getEncoded
    )
}

