package com.himalayasoni.ledger.ui.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.himalayasoni.ledger.R
import com.himalayasoni.ledger.ui.quickadd.QuickAdd
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/*
 * Screen scan: the side button (if set to "Scan screen"), the "Scan screen" app shortcut or RegiStar's back tap
 * reads the payment that's on screen right now, without taking a screenshot yourself.
 *
 * Android only lets an app see another app's screen in three ways: as the phone's assistant, with
 * accessibility access (always watching), or through screen capture ("MediaProjection"), where Android itself
 * asks "Start recording or casting with Ledger?" every single time. We use the last: nothing happens without
 * that tap, and the capture lasts under a second.
 *
 * The steps:
 * 1. [ScreenScanActivity], an invisible window, asks Android for one capture.
 * 2. On "Start", it starts [ScreenCaptureService]. Android requires a foreground service of type
 *    mediaProjection to be running before it hands over the capture; it shows a notification while it runs.
 * 3. The service mirrors the screen into an ImageReader, waits for the consent box to fade, keeps the newest
 *    frame, stops the capture and saves the frame as a PNG in Ledger's cache.
 * 4. The activity (still in front, which is what lets it open another screen) opens the quick add pop-up
 *    with that file; the pop-up reads it exactly like a shared screenshot (ScreenshotReader) and deletes it.
 *
 * Payment apps that block screenshots on a screen (FLAG_SECURE) come out black here too; the pop-up then
 * says it found no amount. Success screens usually allow it, since people share them.
 */

/** Hands the saved frame from the service back to the activity (same process). */
internal object ScreenGrab {
    @Volatile private var pending: CompletableDeferred<File?>? = null
    fun expect(): CompletableDeferred<File?> = CompletableDeferred<File?>().also { pending = it }
    fun deliver(file: File?) { pending?.complete(file); pending = null }
}

class ScreenScanActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode != RESULT_OK || data == null) { finishQuietly(); return@registerForActivityResult }
        lifecycleScope.launch {
            val grab = ScreenGrab.expect()
            val service = ScreenCaptureService.intent(this@ScreenScanActivity, r.resultCode, data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            val file = withTimeoutOrNull(8_000) { grab.await() }
            startActivity(QuickAdd.screenIntent(this@ScreenScanActivity, file))
            finishQuietly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // turned while asking: the request is still on its way
        val mpm = getSystemService(MediaProjectionManager::class.java)
        // Android 14+: ask for the whole screen straight away (no "one app or entire screen?" step)
        val ask = if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
        consent.launch(ask)
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

class ScreenCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val note = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTE_ID, note)
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java) else intent?.getParcelableExtra(EXTRA_DATA)
        scope.launch {
            val file = data?.let { runCatching { captureOnce(code, it) }.getOrNull() }
            ScreenGrab.deliver(file)
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /** Mirror the screen for a moment, keep the newest frame, save it as a PNG. */
    private suspend fun captureOnce(code: Int, data: Intent): File {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val projection = mpm.getMediaProjection(code, data) ?: error("no capture")
        val main = Handler(Looper.getMainLooper())
        projection.registerCallback(object : MediaProjection.Callback() {}, main) // required before mirroring (Android 14)
        val metrics = resources.displayMetrics
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
            val b = getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
            b.width() to b.height()
        } else metrics.widthPixels to metrics.heightPixels
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        var newest: Image? = null
        reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.let { img -> newest?.close(); newest = img } }, main)
        val display = projection.createVirtualDisplay(
            "ledger-scan", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, main,
        )
        try {
            delay(700) // the consent box fading out; the newest frame after that is the screen as you left it
            var waited = 0
            while (newest == null && waited < 2_000) { delay(100); waited += 100 }
            val img = newest ?: error("no frame")
            val bitmap = img.toBitmap(w, h)
            return withContext(Dispatchers.IO) {
                File(cacheDir, "scan-${System.currentTimeMillis()}.png").also { f ->
                    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        } finally {
            newest?.close()
            display.release(); reader.close(); projection.stop()
        }
    }

    /** RGBA rows can be padded wider than the screen; draw the padded width, then crop. */
    private fun Image.toBitmap(w: Int, h: Int): Bitmap {
        val plane = planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * w
        val padded = Bitmap.createBitmap(w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        return if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, w, h).also { padded.recycle() }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen scan", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows for the second Ledger reads your screen, after you tap Start"
            },
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_stat).setContentTitle("Reading the screen…").setOngoing(true).build()
    }

    companion object {
        private const val CHANNEL = "scan"
        private const val NOTE_ID = 4207
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        fun intent(context: Context, code: Int, data: Intent) = Intent(context, ScreenCaptureService::class.java)
            .putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data)
    }
}
