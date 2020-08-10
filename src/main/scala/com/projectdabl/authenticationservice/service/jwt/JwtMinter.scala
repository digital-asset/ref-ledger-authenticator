// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.jwt

import java.time.Clock

import com.projectdabl.authenticationservice.api.LedgerPartyIdentity

trait JwtMinter {
  def mintSaJwt(serviceAccountIdentity: LedgerPartyIdentity)(implicit clock: Clock): String
}

