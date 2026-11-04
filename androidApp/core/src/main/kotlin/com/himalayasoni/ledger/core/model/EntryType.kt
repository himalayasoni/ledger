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
    PAID_BACK("paidback", "Paid back", -1, true, +1);

    companion object {
        fun fromKey(key: String): EntryType? = entries.firstOrNull { it.key == key }
    }
}
