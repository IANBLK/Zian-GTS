package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.port.*
import java.util.UUID

class RecordingTradeJournal : TradeJournalPort {
    data class Event(val operationId: UUID, val value: String)
    val events = mutableListOf<Event>()
    val quarantined = mutableSetOf<UUID>()

    override fun begin(operation: TradeOperation, subjectId: UUID): JournalTicket {
        val ticket = JournalTicket(UUID.randomUUID(), operation)
        events += Event(ticket.operationId, "begin:$operation:$subjectId")
        return ticket
    }
    override fun stage(ticket: JournalTicket, stage: TradeStage) {
        events += Event(ticket.operationId, "stage:$stage")
    }
    override fun complete(ticket: JournalTicket) {
        events += Event(ticket.operationId, "complete")
    }
    override fun quarantine(ticket: JournalTicket, reason: String) {
        quarantined += ticket.operationId
        events += Event(ticket.operationId, "quarantine:$reason")
    }
}
