package com.zianblk.ziangts.server

/** Runs only after successful payment and delivery. Never retries either operation. */
internal object PurchaseFinalization {
    fun complete(creditSeller: () -> Unit, appendHistory: () -> Unit,
                 quarantine: (String, Exception) -> Unit) {
        try {
            creditSeller()
        } catch (error: Exception) {
            quarantine("buy_credit", error)
            return
        }
        try {
            appendHistory()
        } catch (error: Exception) {
            quarantine("buy_history", error)
        }
    }
}
