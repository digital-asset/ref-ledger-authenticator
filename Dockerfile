FROM openjdk:11-jre-slim

COPY target/pack /srv/authentication-service

COPY .daml/dist/authentication-service-3.0.0.dar /srv/authentication-service/authentication-service.dar

ENV DABL_AUTHENTICATION_SERVICE_PORT 8089

ENV DABL_AUTHENTICATION_SERVICE_ADDRESS 0.0.0.0

WORKDIR /srv/authentication-service

ENTRYPOINT ["sh", "./bin/authentication-service"]
