package com.himalayasoni.ledger.core.model

/**
 * The six kinds of entry Ledger knows.
 *
 * @property key the value stored in the database (shared with the web app, so never rename it)
 * @property sign -1 when money leaves your pocket, +1 when it comes in
 * @property balanceEffect how the entry moves a person's balance: +1 means they owe you more,
 *   -1 means they owe you less (or you owe them more). 0 for entries that aren't loans.
 */
enum class EntryType(
    val key: String,
    val label: String,
    val sign: Int,
    val isDebt: Boolean,
    val balanceEffect: Int,
) {
    SPENT("spent", "Spent", -1, false, 0),
    RECEIVED("received", "Received", +1, false, 0),
    LENT("lent", "Lent", -1, true, +1),
    BORROWED("borrowed", "Borrowed", +1, true, -1),
    GOT_BACK("gotback", "Got back", +1, true, -1),
    PAID_BACK("paidback", "Paid back", -1, true, +1),

    /** Paying a credit card bill: money leaves your bank, but it isn't new spending (the card spends were logged already). */
    CARD_BILL("cardbill", "Card bill", -1, false, 0);

    /** Spent or received: the types that have a category and count in spending/income totals. */
    val hasCategory: Boolean get() = this == SPENT || this == RECEIVED

    companion object {
        fun fromKey(key: String): EntryType? = entries.firstOrNull { it.key == key }
    }
}
