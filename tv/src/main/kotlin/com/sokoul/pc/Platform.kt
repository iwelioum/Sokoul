package com.sokoul.pc

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.sokoul.tv.TvApp
import java.io.File

/** What differs between the Windows app and the TV app. */
object Platform {
    fun localExtractionBridge(http: okhttp3.OkHttpClient): com.sokoul.extract.HttpBridge? = com.sokoul.desktop.player.CurlHttpBridge(com.sokoul.tv.TvApp.context, com.sokoul.extract.OkHttpBridge(http))

    const val IS_TV = true
    @Volatile private var playerBackHandler: (() -> Unit)? = null
    private var lastPlayerBackAt = 0L

    /** Set by the activity: a phone turns sideways and hides its system bars while a video plays. */
    @Volatile var playerFullscreenHook: ((Boolean) -> Unit)? = null
    fun playerFullscreen(on: Boolean) { playerFullscreenHook?.invoke(on) }

    /** MainActivity already keeps the screen on while a video plays (FLAG_KEEP_SCREEN_ON). */
    @Suppress("UNUSED_PARAMETER") fun keepAwake(on: Boolean) = Unit

    fun setPlayerBackHandler(handler: (() -> Unit)?) { playerBackHandler = handler }
    fun hasPlayerBackHandler(): Boolean = playerBackHandler != null
    @Synchronized fun dispatchPlayerBack(): Boolean {
        val handler = playerBackHandler ?: return false
        val now = System.currentTimeMillis()
        if (now - lastPlayerBackAt < 250) return true
        lastPlayerBackAt = now
        handler()
        return true
    }

    fun recordingStorages(): List<com.sokoul.desktop.data.RecordingStorage> {
        val internal = File(TvApp.context.filesDir, "recordings").apply { mkdirs() }
        val result = mutableListOf(com.sokoul.desktop.data.RecordingStorage(internal.absolutePath, com.sokoul.pc.data.L("Internal storage", "Stockage interne").text(), internal))
        TvApp.context.getExternalFilesDirs(null).filterNotNull().forEach { base ->
            val removable = runCatching { android.os.Environment.isExternalStorageRemovable(base) }.getOrDefault(false)
            if (removable) {
                val directory = File(base, "recordings")
                result += com.sokoul.desktop.data.RecordingStorage(directory.absolutePath, com.sokoul.pc.data.L("USB storage", "Stockage USB").text(), directory, true)
            }
        }
        return result
    }

    fun pickRecordingDirectory(): File? = null
    fun recordingKeepAlive(needed: Boolean) {
        val intent = Intent(TvApp.context, com.sokoul.tv.RecordingService::class.java)
        if (needed) TvApp.context.startForegroundService(intent) else TvApp.context.stopService(intent)
    }
    fun recordingScheduleWake(atMillis: Long?) = com.sokoul.tv.RecordingWakeReceiver.schedule(TvApp.context, atMillis)
    fun recordingSchedulingExact(): Boolean {
        val alarms = TvApp.context.getSystemService(android.app.AlarmManager::class.java)
        return android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    }
    fun requestRecordingExactAlarms() {
        if (android.os.Build.VERSION.SDK_INT >= 31) TvApp.context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${TvApp.context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** One step of a playback start, with its time since the press (StartClock): `adb logcat -s SokoulStart`. */
    fun startDiagnostic(message: String) {
        android.util.Log.i("SokoulStart", message)
    }

    fun guideDiagnostic(message: String) {
        android.util.Log.w("SokoulEPG", message)
    }

    suspend fun resolveTrailer(keys: List<String>) = com.sokoul.tv.TvTrailers.best(keys)

    /** What the TV really shows and decodes, asked of the system. */
    fun deviceCaps(): com.sokoul.pc.playback.sources.DeviceCaps {
        val display = TvApp.context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        val types = display?.hdrCapabilities?.supportedHdrTypes?.toSet().orEmpty()
        val height = display?.supportedModes?.maxOfOrNull { maxOf(it.physicalHeight, it.physicalWidth * 9 / 16) } ?: 1080
        val infos = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        val decoders = infos.flatMap { it.supportedTypes.toList() }.toSet()
        // A hardware decoder that takes 3840 x 2160 in HEVC (what 4K releases use). The software one only fails slowly.
        val decodes4k = infos.any { info ->
            val hardware = if (android.os.Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else !info.name.startsWith("c2.android.") && !info.name.startsWith("OMX.google.")
            hardware && info.supportedTypes.any { it.equals("video/hevc", true) } &&
                runCatching { info.getCapabilitiesForType("video/hevc").videoCapabilities?.isSizeSupported(3840, 2160) == true }.getOrDefault(false)
        }
        return com.sokoul.pc.playback.sources.DeviceCaps(
            hdr10 = android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 in types,
            dolbyVision = android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types && "video/dolby-vision" in decoders,
            maxHeight = height,
            decodes4k = decodes4k,
        )
    }
    /**
     * A share of the app's heap: 180 MB of images made the TV app weigh 409 MB and collect its garbage while scrolling.
     * On a device with little memory (the Chromecast HD: 1.4 GB, swapping 445 MB, 2026-11-22) a fixed 12 MB, whatever
     * the large heap allows: what the cache does not keep is decoded again from the disk cache in a few milliseconds.
     */
    val IMAGE_CACHE_BYTES: Long = run {
        val byHeap = (Runtime.getRuntime().maxMemory() * 0.12).toLong()
        val am = TvApp.context.getSystemService(android.app.ActivityManager::class.java)
        val info = android.app.ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        if (am?.isLowRamDevice == true || info.totalMem in 1 until SMALL_DEVICE_BYTES) minOf(byHeap, SMALL_IMAGE_CACHE) else byHeap
    }
    private const val SMALL_DEVICE_BYTES = 2_500L * 1024 * 1024
    private const val SMALL_IMAGE_CACHE = 12L * 1024 * 1024

    /** Never larger than the screen: a page of a manga or an original logo would otherwise take tens of megabytes. */
    fun decodeImage(bytes: ByteArray, maxWidth: Int = 0): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        if (maxWidth > 0) {
            // The size it is shown at: a poster in a card of 300 px is not decoded at 780 or 1920.
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        } else {
            while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_SIDE && bounds.outHeight / (sample * 2) >= MAX_IMAGE_SIDE / 2) sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            // A JPEG has no transparency: 16 bits halve its memory with no visible loss on a TV. Logos (PNG, WebP) keep it.
            if (bounds.outMimeType == "image/jpeg") inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        // Sampling only halves: a w500 poster shown 173 px wide stayed 500 px. Scaled to the width asked, it uploads and
        // samples a third of the pixels while rows scroll in (2026-11-24).
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val fitted = if (maxWidth in 1 until decoded.width) {
            android.graphics.Bitmap.createScaledBitmap(decoded, maxWidth, maxOf(1, decoded.height * maxWidth / decoded.width), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else decoded
        // Sent to the graphics chip now, off the interface thread: done at its first draw, a 1280-wide backdrop cost that
        // frame 63 ms on the Google TV Streamer when a title page opened (2026-11-24).
        return fitted.also { it.prepareToDraw() }.asImageBitmap()
    }

    private const val MAX_IMAGE_SIDE = 1920

    /** A web page opens in what the TV has for it: YouTube's app for a trailer. */
    fun openUrl(url: String) {
        runCatching {
            TvApp.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun isBackButton(e: androidx.compose.ui.input.pointer.PointerEvent) = false

    fun pickComicFile(): File? = null

    /** Set by the activity: opens the system's speech recogniser and gives back what was said. */
    @Volatile var voiceHook: (((String) -> Unit) -> Boolean)? = null
    val hasVoice: Boolean get() = voiceHook != null
    fun listen(onText: (String) -> Unit): Boolean = voiceHook?.invoke(onText) ?: false
}
