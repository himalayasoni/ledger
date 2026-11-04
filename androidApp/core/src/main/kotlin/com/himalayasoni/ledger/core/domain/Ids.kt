package com.himalayasoni.ledger.core.domain

import kotlin.random.Random

/** Same shape as the web app's ids (time in base 36 + random), so ids never clash across apps. */
fun newEntryId(now: Long = System.currentTimeMillis(), random: Random = Random.Default): String =
    now.toString(36) + (1..6).map { "0123456789abcdefghijklmnopqrstuvwxyz"[random.nextInt(36)] }.joinToString("")
