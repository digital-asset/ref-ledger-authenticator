package com.projectdabl.authenticationservice.service.jwt

import java.security.interfaces.RSAPublicKey

import akka.http.scaladsl.server.directives.Credentials
import com.projectdabl.authenticationservice.api.UserIdentity
import pdi.jwt.{JwtAlgorithm, JwtSprayJson}

object StaticJwtAuthenticator {
  def apply(rsaPublicKey: RSAPublicKey): StaticJwtAuthenticator = new StaticJwtAuthenticator(rsaPublicKey)
}

class StaticJwtAuthenticator(rsaPublicKey: RSAPublicKey) extends JwtAuthenticator {
  override def oauth2Authenticator(creds: Credentials): Option[UserIdentity] =
    creds match {
      case Credentials.Missing => None
      case Credentials.Provided(bearerToken) => authenticateToken(bearerToken)
    }

  private def authenticateToken(bearerToken: String): Option[UserIdentity] =
    for {
      validatedClaim <- JwtSprayJson.decode(bearerToken, rsaPublicKey, Seq(JwtAlgorithm.RS256)).toOption
      sub <- validatedClaim.subject
    } yield UserIdentity(sub)
}
