package com.himalayasoni.ledger.ui.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.himalayasoni.ledger.core.scan.OcrLine
import com.himalayasoni.ledger.core.scan.PaymentScreen
import com.himalayasoni.ledger.core.scan.ScannedPayment
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Turns a shared screenshot into a [ScannedPayment].
 *
 * Two steps, kept apart on purpose:
 * 1. ML Kit text recognition (on the phone, through Play services; the image never leaves it) gives lines
 *    of text with their positions. We keep each line's height, since the amount is the biggest text.
 * 2. core's [PaymentScreen] rules turn those lines into amount, who, date and UPI reference. That part is
 *    plain Kotlin, so it's unit-tested without a phone (PaymentScreenTest).
 */
object ScreenshotReader {
    sealed interface Result {
        data class Read(val payment: ScannedPayment) : Result
        /** Text was found but no amount: probably not a payment screen. */
        data object NotAPayment : Result
        /** The image couldn't be opened, or text recognition isn't ready (first run with no internet). */
        data object Unreadable : Result
    }

    suspend fun read(context: Context, uri: Uri, today: LocalDate = LocalDate.now()): Result {
        val lines = runCatching {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                recognizer.process(image).await().textBlocks.flatMap { it.lines }
                    .sortedBy { it.boundingBox?.top ?: 0 }
                    .map { OcrLine(it.text, it.boundingBox?.height() ?: 0) }
            } finally { recognizer.close() }
        }.getOrNull() ?: return Result.Unreadable
        return PaymentScreen.read(lines, today)?.let { Result.Read(it) } ?: Result.NotAPayment
    }
}
