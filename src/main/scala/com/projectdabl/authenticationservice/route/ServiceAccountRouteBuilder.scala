// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.route

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZoneId}

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.HttpMethods.{DELETE, GET, OPTIONS, POST}
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import com.daml.ledger.client.binding.Primitive
import com.projectdabl.authenticationservice.api._
import com.projectdabl.authenticationservice.config.ServiceConfig
import com.projectdabl.authenticationservice.service.cubby.{CredentialCoordinate, CubbyHole}
import com.projectdabl.authenticationservice.service.jwt.{JwtAuthenticator, JwtMinter}
import com.projectdabl.authenticationservice.service.ledger.AdminLedgerService
import com.projectdabl.authenticationservice.service.password.PasswordAuthenticator
import org.jose4j.jwk.JsonWebKeySet
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

object ServiceAccountRouteBuilder {
  def apply(adminLedgerService: AdminLedgerService,
            jwks: JsonWebKeySet,
            jwtAuthenticator: JwtAuthenticator,
            passwordAuthenticatorService: PasswordAuthenticator,
            jwtMinter: JwtMinter,
            cubbyHole: CubbyHole,
            serviceConfig: ServiceConfig)
           (implicit ec: ExecutionContext, system: ActorSystem, clock: Clock): ServiceAccountRouteBuilder =
    new ServiceAccountRouteBuilder(
      adminLedgerService,
      jwks,
      jwtAuthenticator,
      passwordAuthenticatorService,
      jwtMinter,
      cubbyHole,
      serviceConfig)(ec, system, clock)
}

class ServiceAccountRouteBuilder(adminLedgerService: AdminLedgerService,
                                 jwks: JsonWebKeySet,
                                 jwtAuthenticator: JwtAuthenticator,
                                 pwAuthService: PasswordAuthenticator,
                                 jwtMinter: JwtMinter,
                                 cubbyHole: CubbyHole,
                                 serviceConfig: ServiceConfig)
                                (implicit ec: ExecutionContext, system: ActorSystem, clock: Clock)
  extends Directives
    with SprayJsonSupport {

  val ALPHANUMERIC_ID_DASH_REGEX: Regex =
    """[A-Za-z0-9\-]+""".r

  import ServiceAccountRequestProtocol._
  import com.projectdabl.authenticationservice.api.ServiceAccountProtocol._

  val corsHeaders = List(
    `Access-Control-Allow-Origin`(serviceConfig.allowedConsoleOrigin),
    `Access-Control-Allow-Credentials`(true),
    `Access-Control-Allow-Headers`("Authorization", "Content-Type", "pragma"),
    `Access-Control-Allow-Methods`(OPTIONS, POST, GET, DELETE)
  )

  val consoleCorsResponse: Route = options {
    respondWithHeaders(corsHeaders) {
      complete(
        HttpResponse(StatusCodes.OK)
      )
    }
  }

  val route: Route = pathPrefix("sa") {
    extractLog { log =>
      concat(
        pathPrefix("secure") {
          respondWithHeaders(corsHeaders) {
            authenticateOAuth2("", jwtAuthenticator.oauth2Authenticator) { userId =>
              concat(
                path("authorize") {
                  post {
                    log.info("in POST handler for authorize call with user id: {}", userId)

                    complete(
                      adminLedgerService.authorizeUser(userId).run.map {
                        case Some(value) =>
                          log.info("completed user authorization with: {}", value)
                          HttpResponse(status = StatusCodes.OK)
                        case None =>
                          log.warning("rejected user authorization")
                          HttpResponse(status = StatusCodes.BadRequest)
                      }
                    )
                  }
                },
                path("me") {
                  complete(
                    adminLedgerService.retrieveUser(userId).run.map {
                      case Some(user) =>
                        val userUUID: String = user.user.toString
                        import spray.json.DefaultJsonProtocol._
                        import spray.json._

                        HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(
                            `application/json`,
                            JsObject(
                              "user" -> JsString(userUUID)
                            ).toJson.prettyPrint
                          )
                        )

                      case None =>
                        HttpResponse(status = StatusCodes.NotFound)
                    }
                  )
                },
                path("request" / ALPHANUMERIC_ID_DASH_REGEX) { ledgerId =>
                  post {
                    entity(as[ServiceAccountRequest]) { serviceAccountRequest =>
                      log.info("in POST handler for request call with user id: {} and request {}",
                        userId, serviceAccountRequest)

                      complete(
                        adminLedgerService
                          .createServiceAccountRequest(userId, ledgerId, serviceAccountRequest.nonce)
                          .run
                          .map {
                            case Some(value) =>
                              log.info("completed service account request with: {}", value)
                              HttpResponse(status = StatusCodes.OK)
                            case None =>
                              HttpResponse(status = StatusCodes.BadRequest)
                          }
                      )
                    }
                  }
                },
                pathEndOrSingleSlash {
                  get {
                    log.info("in GET handler for sa listing call: {}", userId)

                    complete(
                      adminLedgerService
                        .listServiceAccounts(userId)
                        .run
                        .map {
                          case Some(value) =>
                            ServiceAccountListResponse(
                              value
                                .map(_.value)
                                .map { sa =>


                                  ServiceAccountResponse(
                                    serviceAccount = sa.serviceAccount.toString,
                                    nonce = sa.nonce,
                                    creds = sa.credentialIds
                                      .map(_.toString)
                                      .map(credId => ServiceAccountCredentialResponse(credId))
                                      .toList,
                                  )
                                }
                                .toList
                            )
                          case None =>
                            ServiceAccountListResponse(List())
                        }
                    )
                  }
                },
                path(ALPHANUMERIC_ID_DASH_REGEX / "credRequest") { saId =>
                  pathEndOrSingleSlash {
                    post {
                      log.info(
                        "in POST handler for sa cred request call with userId {} and saID {}",
                        userId, saId)

                      complete(
                        adminLedgerService.createServiceAccountCredentialRequest(userId, saId).run.map {
                          case Some(value) =>
                            log.info("created service account credential request with: {}", value)

                            HttpResponse(status = StatusCodes.OK)
                          case None =>
                            HttpResponse(status = StatusCodes.BadRequest)
                        }
                      )
                    }
                  }
                },
                path("cred" / ALPHANUMERIC_ID_DASH_REGEX) { credentialId =>
                  pathEndOrSingleSlash {
                    concat(
                      get {
                        log.info("in GET handler for sa credential read with userId {} and credId {}",
                          userId,
                          credentialId)

                        def formattedUTC(timestamp: Primitive.Timestamp) =
                          timestamp
                            .atZone(ZoneId.of("UTC"))
                            .format(DateTimeFormatter.ISO_INSTANT)

                        complete(
                          adminLedgerService
                            .fetchServiceAccountCredentialHashById(userId, credentialId)
                            .run
                            .recover {
                              case ex =>
                                log.warning("failed to fetch credential for user {} with id {}",
                                  userId, credentialId, ex)
                                None
                            }
                            .map {
                              case Some(value) =>
                                HttpResponse(
                                  entity = HttpEntity(
                                    `application/json`,
                                    string = ServiceAccountCredentialResponse(
                                      credId = value.credentialId,
                                      nonce = Some(value.nonce.toString),
                                      cred = cubbyHole.remove(CredentialCoordinate(value.owner, value.credentialId)),
                                      validFrom = Some(formattedUTC(value.validFrom)),
                                      validTo = Some(formattedUTC(value.validTo))
                                    ).toJson.prettyPrint
                                  )
                                )
                              case None =>
                                HttpResponse(status = StatusCodes.NotFound)
                            }
                        )
                      },
                      delete {
                        log.info("in DELETE handler for sa credential with userId {}", userId)

                        complete(
                          adminLedgerService.deleteServiceAccountCredentialHashById(userId, credentialId).run.map {
                            case Some(value) =>
                              log.info("completed service account deletion with {}", value)
                              HttpResponse(status = StatusCodes.OK)
                            case None =>
                              HttpResponse(status = StatusCodes.BadRequest)
                          }
                        )
                      }
                    )
                  }
                }
              )
            }
          }
        },
        pathPrefix("secure") {
          concat(
            path("authorize") {
              consoleCorsResponse
            },
            path("me") {
              consoleCorsResponse
            },
            path("request" / ALPHANUMERIC_ID_DASH_REGEX) { _ =>
              consoleCorsResponse
            },
            pathEndOrSingleSlash {
              consoleCorsResponse
            },
            path(ALPHANUMERIC_ID_DASH_REGEX / "credRequest") { _ =>
              consoleCorsResponse
            },
            path("cred" / ALPHANUMERIC_ID_DASH_REGEX) { _ =>
              consoleCorsResponse
            }
          )
        },
        path("login") {
          authenticateBasicAsync("", pwAuthService.passwordAuthenticateAsync) { saCredHash =>
            pathEndOrSingleSlash {
              post {
                log.info("in POST handler for sa login: {}", saCredHash)

                complete(
                  ServiceAccountTokenResponse(token =
                    jwtMinter.mintJwt(
                      ServiceAccountIdentity(
                        party = saCredHash.serviceAccount.toString,
                        rights = Seq("read", "write:create", "write:exercise").toList,
                        ledgerId = saCredHash.ledgerId
                      )
                    )
                  )
                )
              }
            }
          }
        },
        path("jwks") {
          get {
            complete(HttpEntity(contentType = `application/json`, string = jwks.toJson))
          }
        }
      )
    }
  }
}

