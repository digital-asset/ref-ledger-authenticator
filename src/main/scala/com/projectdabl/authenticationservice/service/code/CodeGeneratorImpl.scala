// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.code

import java.security.SecureRandom

object CodeGeneratorImpl {
  val random: SecureRandom = SecureRandom.getInstance("SHA1PRNG")

  val codeChars: Seq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')

  def apply(): CodeGeneratorImpl = new CodeGeneratorImpl(codeChars, random)
}

class CodeGeneratorImpl(codeChars: Seq[Char], random: SecureRandom) extends CodeGenerator {
  @scala.annotation.tailrec
  private[this] def genCode(remaining: Int, sb: StringBuilder): String =
    if (remaining <= 0) {
      sb.toString()
    } else {
      genCode(remaining - 1, sb.append(codeChars(random.nextInt(codeChars.length))))
    }

  override def genCode(chars: Int): String = genCode(chars, new StringBuilder())
}

