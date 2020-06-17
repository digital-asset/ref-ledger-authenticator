// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.daml.ledger.api.refinements.ApiTypes.{ApplicationId, CommandId, WorkflowId}
import com.daml.ledger.api.v1.commands.{Command, Commands}
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.binding.Primitive
import com.google.protobuf.timestamp.Timestamp
import scalaz.syntax.tag._

import scala.concurrent.duration.Duration

trait CommandBuilder {
  val ledgerClient: LedgerClient

  val applicationId: ApplicationId

  private[this] def genUniqueId(): String = UUID.randomUUID().toString

  private[this] def fromInstant(instant: Instant): Timestamp =
    new Timestamp()
      .withSeconds(instant.getEpochSecond)
      .withNanos(instant.getNano)

  private[this] val tenSeconds: Duration = Duration(10, TimeUnit.SECONDS)

  def buildCommands[T](party: Primitive.Party,
                       command: Command,
                       commandId: CommandId = CommandId(genUniqueId()),
                       workflowId: WorkflowId = WorkflowId(genUniqueId())): Commands =
    Commands(
      ledgerId = ledgerClient.ledgerId.unwrap,
      workflowId = workflowId.unwrap,
      applicationId = ApplicationId.unwrap(applicationId),
      commandId = commandId.unwrap,
      party = Primitive.Party.unwrap(party),
      commands = Seq(command)
    )
}

