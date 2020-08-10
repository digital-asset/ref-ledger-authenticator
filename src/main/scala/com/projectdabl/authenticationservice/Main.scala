// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice

import java.io.{FileInputStream, InputStream}
import java.time.{Clock, ZoneId}
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import com.daml.grpc.adapter.{AkkaExecutionSequencerPool, ExecutionSequencerFactory}
import com.daml.ledger.api.refinements.ApiTypes.ApplicationId
import com.daml.ledger.api.v1.admin.package_management_service.PackageManagementServiceGrpc.PackageManagementServiceStub
import com.daml.ledger.api.v1.admin.package_management_service.{UploadDarFileRequest, UploadDarFileResponse}
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.LedgerClientBinding
import com.daml.ledger.client.configuration.{CommandClientConfiguration, LedgerClientConfiguration, LedgerIdRequirement}
import com.google.protobuf.ByteString
import com.projectdabl.authenticationservice.config.AuthenticationServiceConfig
import com.projectdabl.authenticationservice.events.{BootstrapEventHandler, LedgerEventHandler}
import com.projectdabl.authenticationservice.jwk.JwksAssembler
import com.projectdabl.authenticationservice.key.RSAKeyPairGenerator
import com.projectdabl.authenticationservice.route.ServiceAccountRouteBuilder
import com.projectdabl.authenticationservice.service.code.CodeGeneratorImpl
import com.projectdabl.authenticationservice.service.cubby.CubbyHoleImpl
import com.projectdabl.authenticationservice.service.jwt.{JwtJwkAuthenticatorImpl, JwtMinterImpl, SerialJwtAuthenticator, StaticJwtAuthenticator}
import com.projectdabl.authenticationservice.service.ledger.AdminLedgerServiceImpl
import com.projectdabl.authenticationservice.service.password.PasswordAuthenticatorImpl
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.grpc.ManagedChannel

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main extends LazyLogging {
  private val config: Config = ConfigFactory.load()

  private val authServiceConfig: Config = config.getConfig("authentication-service")

  private val serviceConfig: AuthenticationServiceConfig = AuthenticationServiceConfig(authServiceConfig)

  implicit private val as: ActorSystem = ActorSystem("authentication-service-system")

  implicit private val ec: ExecutionContext = as.dispatcher

  implicit private val clock: Clock = Clock.system(ZoneId.systemDefault())

  implicit private val esf: ExecutionSequencerFactory = new AkkaExecutionSequencerPool("clientPool")(as)

  private val applicationId = ApplicationId("AuthenticationService")

  private val ledgerUri: Uri = Uri(serviceConfig.ledgerConfig.ledgerUrl)

  private val managedChannel: ManagedChannel = LedgerClientBinding.createChannel(
    ledgerUri.authority.host.address,
    ledgerUri.authority.port,
    None)

  private val clientConfig = LedgerClientConfiguration(
    applicationId = ApplicationId.unwrap(applicationId),
    ledgerIdRequirement = LedgerIdRequirement("", enabled = false),
    commandClient = CommandClientConfiguration.default,
    sslContext = None,
    token = None)

  private val packageManagementServiceStub = new PackageManagementServiceStub(managedChannel)

  private def buildLedgerClient(): Future[LedgerClient] =
    LedgerClient.singleHost(
      ledgerUri.authority.host.address,
      ledgerUri.authority.port,
      clientConfig)(ec, esf)

  private def preloadPathAsStream(path: Option[String]): InputStream = {
    path match {
      case None => getClass.getClassLoader.getResourceAsStream("daml.dar")
      case Some(p) => new FileInputStream(p)
    }
  }

  private def uploadDar(packageManagementService: PackageManagementServiceStub,
                        preloadPath: Option[String]): Future[UploadDarFileResponse] = {
    val is = preloadPathAsStream(preloadPath)
    try {
      val bs = ByteString.readFrom(is)
      val uploadDarFileRequest = new UploadDarFileRequest(bs)
      packageManagementService.uploadDarFile(uploadDarFileRequest)
    } finally {
      is.close()
    }
  }

  def main(args: Array[String]): Unit = {
    val ledgerClientConnectF = for {
      _ <- uploadDar(packageManagementServiceStub, serviceConfig.ledgerConfig.preloadPath)
      ledgerClient <- buildLedgerClient()
    } yield ledgerClient

    val operatorContractF = for {
      ledgerClient <- ledgerClientConnectF
      beh = BootstrapEventHandler(ledgerClient, serviceConfig.ledgerConfig)
      operatorC <- beh.operatorContractF()
    } yield operatorC

    val cubbyHole = CubbyHoleImpl()

    val bindF = for {
      ledgerClient <- ledgerClientConnectF
      operatorC <- operatorContractF
      adminLedgerService = AdminLedgerServiceImpl(ledgerClient, operatorC)
      rsaKeyPair = RSAKeyPairGenerator().generate()
      keyId = s"a-s-${UUID.randomUUID()}"
      jwks = JwksAssembler().assembleJwks(rsaKeyPair.publicKey, keyId)
      staticA = StaticJwtAuthenticator(rsaKeyPair.publicKey)
      jwtJwkA = JwtJwkAuthenticatorImpl(serviceConfig.jwksConfig)
      binding <- Http().bindAndHandle(
        ServiceAccountRouteBuilder(
          adminLedgerService,
          jwks,
          SerialJwtAuthenticator(Seq(staticA, jwtJwkA)),
          PasswordAuthenticatorImpl(adminLedgerService),
          JwtMinterImpl(
            rsaKeyPair,
            keyId,
            serviceConfig.jwtConfig.issuer,
            serviceConfig.serviceAccountConfig.applicationId,
            Duration(serviceConfig.jwtConfig.validityDuration.getSeconds, SECONDS)
          ),
          cubbyHole,
          serviceConfig
        ).route,
        interface = serviceConfig.serviceConfig.address,
        port = serviceConfig.serviceConfig.port)
    } yield binding

    val eventHandlerF = for {
      operatorC <- operatorContractF
      ledgerClient <- ledgerClientConnectF
      codeGenerator = CodeGeneratorImpl()
      leh = LedgerEventHandler(ledgerClient, operatorC, codeGenerator, cubbyHole, serviceConfig.serviceAccountConfig)
      done <- leh.runHandler()
    } yield done

    bindF onComplete {
      case Success(value) =>
        logger.info("started up successfully: {}", value)
      case Failure(throwable) =>
        crash(throwable)
    }

    eventHandlerF onComplete {
      case Success(_) =>
        logger.info("event handler terminated normally!")
      case Failure(throwable) =>
        crash(throwable)
    }
  }

  private def crash(throwable: Throwable): Unit = {
    logger.error("authentication service crashed", throwable)
    Await.result(as.terminate(), Duration(3, SECONDS))
    System.exit(99)
  }
}

