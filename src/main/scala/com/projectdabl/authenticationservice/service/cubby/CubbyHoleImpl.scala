// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.cubby

import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

object CubbyHoleImpl {
  def apply(): CubbyHoleImpl = new CubbyHoleImpl()
}

/**
 * A simple in-memory credential cubby hole.
 * This should be replaced with a distributed in-memory variant.
 */
class CubbyHoleImpl extends CubbyHole with LazyLogging {
  private val inMemCredStore = mutable.Map[CredentialCoordinate, String]()

  override def put(credCoord: CredentialCoordinate, cred: String): Unit = {
    logger.info("stored credential at coordinate: {}", credCoord)

    inMemCredStore.put(credCoord, cred)
  }

  override def remove(credCoord: CredentialCoordinate): Option[String] = {
    logger.info("removing credential at coordinate: {}", credCoord)

    inMemCredStore.remove(credCoord)
  }
}

