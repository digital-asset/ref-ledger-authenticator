// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger

import java.time.Instant

import com.daml.ledger.client.binding.Primitive
import com.projectdabl.authenticationservice.exc.InternalException

trait ClockReader {
  def readClock(instant: Instant = Instant.now()): Primitive.Timestamp = Primitive.Timestamp.discardNanos(instant) match {
    case Some(value) => value
    case None => throw new InternalException("failed to discard nanos")
  }
}

