// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.jwt

import java.time.Clock

import com.projectdabl.authenticationservice.api.ServiceAccountIdentity

trait JwtMinter {
  def mintJwt(serviceAccountIdentity: ServiceAccountIdentity)(implicit clock: Clock): String
}

