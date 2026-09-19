package com.zianblk.ziangts.history

import com.zianblk.ziangts.data.TransactionRecord
import java.util.UUID

interface TransactionHistory {
    fun append(record: TransactionRecord)
    fun findByTransactionId(id: UUID): TransactionRecord?
    fun findByPlayer(playerId: UUID): List<TransactionRecord>
    fun all(): List<TransactionRecord>

    /**
     * Destructive deletion must only be exposed through a permission-checked
     * administrative command with explicit confirmation.
     */
    fun delete(id: UUID): Boolean
}

