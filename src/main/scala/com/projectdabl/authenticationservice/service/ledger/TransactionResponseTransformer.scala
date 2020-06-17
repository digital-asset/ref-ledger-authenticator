// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger

import com.daml.ledger.api.v1.active_contracts_service.GetActiveContractsResponse
import com.daml.ledger.api.v1.command_service.{SubmitAndWaitForTransactionResponse, SubmitAndWaitForTransactionTreeResponse}
import com.daml.ledger.api.v1.value.{Record, Value}
import com.daml.ledger.client.binding.{Contract, Primitive, Template, TemplateCompanion}

trait TransactionResponseTransformer {
  def extractFirstCreatedContractId(txnResponse: SubmitAndWaitForTransactionResponse): Option[String] =
    for {
      txn <- txnResponse.transaction
      ev <- txn.events.find(_.event.isCreated)
    } yield ev.getCreated.contractId

  def extractFirstCreationArguments(txnResponse: SubmitAndWaitForTransactionResponse): Option[Record] =
    for {
      txn <- txnResponse.transaction
      ev <- txn.events.find(_.event.isCreated)
      ca <- ev.getCreated.createArguments
    } yield ca

  def extractAllEventBatchRecords(createdEvents: Seq[GetActiveContractsResponse]): Seq[(String, Record)] =
    for {
      activeContractsResponse <- createdEvents
      createdEvent <- activeContractsResponse.activeContracts
      cid = createdEvent.contractId
      record <- createdEvent.createArguments
    } yield (cid, record)

  def extractFirstExerciseResultValue(txnTreeResponse: SubmitAndWaitForTransactionTreeResponse): Option[Value] =
    for {
      txn <- txnTreeResponse.transaction
      exercisedEv <- txn.eventsById.values.find(_.kind.isExercised)
    } yield exercisedEv.getExercised.getExerciseResult

  def extractCidRecordPair(value: Value): Option[(String, Record)] =
    value.getRecord.fields.map(_.getValue).toList match {
      case cidValue :: recordValue :: _ => Some((cidValue.getContractId, recordValue.getRecord))
      case _ => None
    }

  def contractInstance[T](cid: Primitive.ContractId[T],
                          value: T with Template[T]): Contract[T] =
    Contract[T](
      contractId = cid,
      value = value,
      agreementText = None,
      signatories = Seq(),
      observers = Seq(),
      key = None
    )

  def extractFirstCreatedTemplateContract[T](txnTreesRes: SubmitAndWaitForTransactionTreeResponse)
                                            (implicit companion: TemplateCompanion[T]): Option[T] =
    for {
      txn <- txnTreesRes.transaction
      createdEv <- txn.eventsById.values.find(_.kind.isCreated)
      contract <- companion.fromNamedArguments(createdEv.getCreated.getCreateArguments)
    } yield contract

  def extractFirstExercisedTemplateContract[T](txnTreeRes: SubmitAndWaitForTransactionTreeResponse)
                                              (implicit companion: TemplateCompanion[T]):
  Option[Contract[T]] =
    extractFirstExerciseResultValue(txnTreeRes)
    .flatMap(value => extractTemplateContract(value)(companion))

  def extractTemplateContract[T](value: Value)
                                (implicit companion: TemplateCompanion[T]):
  Option[Contract[T]] =
    extractCidRecordPair(value)
      .flatMap {
        case (cid: String, rec: Record) =>
          companion
            .fromNamedArguments(rec)
            .map { value =>
              contractInstance(Primitive.ContractId(cid), value.asInstanceOf[T with Template[T]])
            }
      }
}

