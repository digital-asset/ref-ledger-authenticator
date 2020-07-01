package com.projectdabl.authenticationservice.service.jwt

import akka.http.scaladsl.server.directives.Credentials
import com.projectdabl.authenticationservice.api.UserIdentity

object SerialJwtAuthenticator {
  def apply(jwtAuthenticators: Seq[JwtAuthenticator]): SerialJwtAuthenticator =
    new SerialJwtAuthenticator(jwtAuthenticators)
}

class SerialJwtAuthenticator(jwtAuthenticators: Seq[JwtAuthenticator]) extends JwtAuthenticator {
  override def oauth2Authenticator(creds: Credentials): Option[UserIdentity] =
    oauth2Authenticator(creds, jwtAuthenticators)

  private def oauth2Authenticator(creds: Credentials,
                                  jwtAuthenticators: Seq[JwtAuthenticator]): Option[UserIdentity] =
    if (jwtAuthenticators.isEmpty) {
      None
    } else {
      jwtAuthenticators.head.oauth2Authenticator(creds).orElse(oauth2Authenticator(creds, jwtAuthenticators.tail))
    }
}
