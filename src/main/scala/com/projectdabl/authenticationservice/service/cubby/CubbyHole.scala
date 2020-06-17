// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.cubby

import com.daml.ledger.client.binding.Primitive

trait CubbyHole {
  def put(credCoord: CredentialCoordinate, cred: String)

  def remove(credCoord: CredentialCoordinate): Option[String]
}

sealed case class CredentialCoordinate(owner: Primitive.Party, credentialId: String)

