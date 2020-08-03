// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.route

import java.time.{Clock, ZoneId}

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.daml.ledger.client.binding.Primitive
import com.projectdabl.authenticationservice.api.ServiceAccountRequestProtocol._
import com.projectdabl.authenticationservice.api.{LedgerPartyIdentity, UserIdentity, ServiceAccountRequest => APIServiceAccountRequest}
import com.projectdabl.authenticationservice.config.AuthenticationServiceConfig
import com.projectdabl.authenticationservice.model.DABL.AuthenticationService.V3._
import com.projectdabl.authenticationservice.service.cubby.CubbyHole
import com.projectdabl.authenticationservice.service.jwt.{JwtAuthenticator, JwtMinter}
import com.projectdabl.authenticationservice.service.ledger.AdminLedgerService
import com.projectdabl.authenticationservice.service.password.PasswordAuthenticator
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.jose4j.jwk.JsonWebKeySet
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import scalaz.OptionT
import scalaz.OptionT.optionT
import spray.json._

import scala.concurrent.Future

class ServiceAccountRouteBuilderSpec
  extends WordSpec with Matchers with ScalatestRouteTest with MockFactory with LazyLogging {

  implicit private val clock: Clock = Clock.system(ZoneId.systemDefault())

  val config: Config = ConfigFactory.load()

  val authenticationServiceConfig: AuthenticationServiceConfig = AuthenticationServiceConfig(
    config.getConfig("authentication-service")
  )

  val mockAdminLedgerService: AdminLedgerService =
    mock[AdminLedgerService]("admin-ledger-service")

  val emptyJsonWebKeySet: JsonWebKeySet = new JsonWebKeySet("""{"keys": []}"""")

  val testUser: String = "test-user"

  val testUserIdentity: UserIdentity = UserIdentity(testUser)

  val testUserAuthResult: OptionT[Future, String] = optionT(Future.successful(Option(testUser)))

  val testLedgerId: String = "123"

  val testNonce: String = "only1ce"

  val testCredId = "cred-id-573"

  val testServiceAccountRequestResult: OptionT[Future, ServiceAccountRequest] =
    optionT(
      Future.successful(
        Option(
          ServiceAccountRequest(
            Primitive.Party("operator"),
            Primitive.Party("user"),
            "userId",
            testLedgerId,
            testNonce
          )
        )
      )
    )

  val testServiceAccountCredentialRequestResult: OptionT[Future, ServiceAccountCredentialRequest] =
    optionT(
      Future.successful(
        Option(
          ServiceAccountCredentialRequest(
            Primitive.Party("operator"),
            Primitive.Party("owner"),
            "ownerId",
            Primitive.Party("serviceAccount"),
            testLedgerId,
            testNonce
          )
        )
      )
    )

  class TestJwtAuthenticator extends JwtAuthenticator {
    override def oauth2Authenticator(creds: Credentials): Option[UserIdentity] = {
      logger.info("received authentication request with credentials: {}", creds)
      Some(testUserIdentity)
    }
  }

  val mockJwtAuthenticator: JwtAuthenticator =
    new TestJwtAuthenticator

  val mockPasswordAuthenticatorService: PasswordAuthenticator =
    mock[PasswordAuthenticator]("password-authenticator")

  val mockJwtMinter: JwtMinter =
    mock[JwtMinter]("jwt-minter")

  val mockCubbyHole: CubbyHole =
    mock[CubbyHole]("cubby-hole")

  val sut: ServiceAccountRouteBuilder = ServiceAccountRouteBuilder(
    mockAdminLedgerService,
    emptyJsonWebKeySet,
    mockJwtAuthenticator,
    mockPasswordAuthenticatorService,
    mockJwtMinter,
    mockCubbyHole,
    authenticationServiceConfig)

  val routeUnderTest: Route = sut.route

  "the service account route" should {
    "authorize the test user when receiving an authorize post" in {
      (mockAdminLedgerService.authorizeUser _)
        .expects(testUserIdentity)
        .returns(testUserAuthResult)

      (mockJwtMinter.mintSaJwt(_: LedgerPartyIdentity)(_: Clock))
        .expects(*, clock)
        .returns("{}")

      Post("/sa/secure/authorize") ~> routeUnderTest ~> check {
        response.status shouldBe (StatusCodes.OK)
      }
    }

    "send a create SAR when receiving a ledger creation request" in {
      (mockAdminLedgerService.createServiceAccountRequest _)
        .expects(testUserIdentity, testLedgerId, testNonce)
        .returns(testServiceAccountRequestResult)

      Post(s"/sa/secure/request/$testLedgerId",
        HttpEntity(
          contentType = `application/json`,
          string = APIServiceAccountRequest(testNonce)
            .toJson
            .compactPrint
        )
      ) ~> routeUnderTest ~> check {
        response.status shouldBe (StatusCodes.OK)
      }
    }

    "send a create SAC request when posted with an SA ID" in {
      (mockAdminLedgerService.createServiceAccountCredentialRequest _)
        .expects(testUserIdentity, testCredId)
        .returns(testServiceAccountCredentialRequestResult)

      Post(s"/sa/secure/$testCredId/credRequest") ~> routeUnderTest ~> check {
        response.status shouldBe StatusCodes.OK
      }
    }

    "render a JWKS" in {
      Get("/sa/jwks") ~> routeUnderTest ~> check {
        logger.info("jwks response: {}", response)
        response.status shouldBe StatusCodes.OK
      }
    }
  }
}

