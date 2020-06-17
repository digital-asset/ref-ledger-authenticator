// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.projectdabl.authenticationservice.service.ledger

import com.daml.ledger.api.refinements.ApiTypes.TemplateId
import com.daml.ledger.api.v1.transaction_filter.{Filters, InclusiveFilters, TransactionFilter}
import com.daml.ledger.client.binding.Primitive
import scalaz.syntax.tag._

trait PartyFilterer {
  def partyFilterTemplate(party: Primitive.Party, id: TemplateId): TransactionFilter =
    TransactionFilter(
      Map(
        party.unwrap -> Filters(
          Some(
            InclusiveFilters(
              Seq(
                id.unwrap
              )
            )
          )
        )
      )
    )
}

