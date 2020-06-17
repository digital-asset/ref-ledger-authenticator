// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger

import java.util.UUID

import com.daml.ledger.client.binding.Primitive

trait IdGenerator {
  private def genLowercaseUUID(): String = UUID.randomUUID().toString.toLowerCase

  def genAuthenticationUser(): Primitive.Party = Primitive.Party(s"sa-user-${genLowercaseUUID()}")

  def genServiceAccount(): Primitive.Party = Primitive.Party(s"sa-${genLowercaseUUID()}")

  def genCredentialId(): String = s"cred-${genLowercaseUUID()}"
}

