 [![CircleCI](https://circleci.com/gh/digital-asset/ref-ledger-authenticator.svg?style=svg&circle-token=99ffcb3092d49a5b66dea330bfe4dc32635a1343)](https://circleci.com/gh/digital-asset/ref-ledger-authenticator)
 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/digital-asset/ref-ledger-authenticator/blob/master/LICENSE)

# Reference Ledger Authenticator

This component consumes DAML Ledger JWTs and provides an API to create ledger identities.
Other components can be configured to trust the identities that this component produces, by configuring them to accept the JWKS that this service vends.

## Security Notice

This service has endpoints that accept credentials using Basic Auth, meaning the credentials get transmitted in the clear. For this reason, in a production environment, it is critical to only run this service over TLS with appropriate certificate provisions, to mitigate the risk of credential theft.

## Building

``make``

The project uses a makefile to order its build steps, and Scala codegen, which is included as unmanaged source in the build. For this reason it's required to build with make in order to ensure that generated source is available when the sbt build starts.

## Docs

* https://docs.daml.com/getting-started/installation.html
* https://docs.daml.com/app-dev/bindings-scala/index.html

## Dependencies

* JDK >= 11
* Daml SDK >= 1.2.0

## Related

* https://tools.ietf.org/html/rfc7517
