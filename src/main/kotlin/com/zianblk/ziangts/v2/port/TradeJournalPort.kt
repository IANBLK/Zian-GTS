package com.zianblk.ziangts.v2.port

import java.util.UUID

enum class TradeOperation { PUBLISH, PURCHASE, WITHDRAW, CLAIM }

enum class TradeStage {
    PREPARED,
    BEFORE_PAYMENT,
    PROCEEDS_RESERVED,
    PAYMENT_APPLIED,
    BEFORE_POKEMON_REMOVE,
    POKEMON_REMOVED,
    BEFORE_POKEMON_DELIVERY,
    POKEMON_DELIVERED,
    PROCEEDS_CREDITED,
    HISTORY_APPENDED,
    RUNTIME_COMPLETE
}

data class JournalTicket(val operationId: UUID, val operation: TradeOperation)

interface TradeJournalPort {
    fun begin(operation: TradeOperation, subjectId: UUID): JournalTicket
    fun stage(ticket: JournalTicket, stage: TradeStage)
    fun complete(ticket: JournalTicket)
    /** Close a journal ticket only when the operation is known to have produced no external side effect. */
    fun abort(ticket: JournalTicket, reason: String) = complete(ticket)
    fun quarantine(ticket: JournalTicket, reason: String)
}
