// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

case class ServiceAccountRequest(nonce: String)

object ServiceAccountRequestProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val sarp = jsonFormat1(ServiceAccountRequest)
}

