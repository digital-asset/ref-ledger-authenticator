// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.key

import java.security.KeyPairGenerator
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}

import com.projectdabl.authenticationservice.exc.InternalException
import com.typesafe.scalalogging.LazyLogging

object RSAKeyPairGenerator {
  val cipher = "RSA"
  val keySize = 1024
  val provider = "SunRsaSign"

  def apply(): RSAKeyPairGenerator = {
    val generator = KeyPairGenerator.getInstance(cipher, provider)
    generator.initialize(keySize)
    RSAKeyPairGenerator(generator)
  }
}

final case class RSAKeyPairGenerator(keyPairGenerator: KeyPairGenerator) extends LazyLogging {
  def generate(): RSAKeyPair = {
    logger.info(
      "generating key pair for algorithm {} with provider {}",
      keyPairGenerator.getAlgorithm, keyPairGenerator.getProvider)

    val generatedKeyPair = keyPairGenerator.generateKeyPair()

    val keyPairOpt = for {
      privateKey <- generatedKeyPair.getPrivate match {
        case rsaPrivateKey: RSAPrivateKey => Some(rsaPrivateKey)
        case _ =>
          logger.error("RSA key pair generator generated a non-RSA private key")
          None
      }
      publicKey <- generatedKeyPair.getPublic match {
        case rsaPublicKey: RSAPublicKey => Some(rsaPublicKey)
        case _ =>
          logger.error("RSA key pair generator generated a non-RSA public key")
          None
      }
    } yield RSAKeyPair(privateKey, publicKey)

    keyPairOpt match {
      case None => throw new InternalException("failed to generate an RSA key pair")
      case Some(key) => key
    }
  }
}

