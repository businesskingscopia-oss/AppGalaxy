package com.example.data.apk

import android.content.Context
import android.os.Environment
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ActiveDownloadState(
    val appId: String,
    val versionId: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val expectedSha256: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long = 0L,
    val localFilePath: String? = null,
    val errorMessage: String? = null
)

class ApkDownloadManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloads = MutableStateFlow<Map<String, ActiveDownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, ActiveDownloadState>> = _downloads.asStateFlow()

    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private fun downloadDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "apks")
        return File(base, "apks").apply { mkdirs() }
    }

    private fun partFileFor(appId: String, versionCode: Long): File =
        File(downloadDirectory(), "${appId}_v${versionCode}.apk.part")

    private fun finalFileFor(appId: String, versionCode: Long): File =
        File(downloadDirectory(), "${appId}_v${versionCode}.apk")

    fun startOrResumeDownload(
        appId: String,
        versionId: String,
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Long,
        apkUrl: String,
        expectedSha256: String,
        knownTotalBytes: Long,
        onStatePersisted: (ActiveDownloadState) -> Unit = {}
    ) {
        if (apkUrl.isBlank() || (!apkUrl.startsWith("https://") && !apkUrl.startsWith("http://"))) {
            val failed = ActiveDownloadState(
                appId = appId,
                versionId = versionId,
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                apkUrl = apkUrl,
                expectedSha256 = expectedSha256,
                status = DownloadStatus.FAILED,
                bytesDownloaded = 0L,
                totalBytes = knownTotalBytes,
                errorMessage = "Invalid or missing APK download URL"
            )
            _downloads.update { it + (appId to failed) }
            onStatePersisted(failed)
            return
        }

        // Cancel existing job if restarting
        activeCalls[appId]?.cancel()
        activeJobs[appId]?.cancel()

        val finalFile = finalFileFor(appId, versionCode)
        val partFile = partFileFor(appId, versionCode)

        val job = scope.launch {
            // Check if already downloaded and verified
            if (finalFile.exists() && finalFile.length() > 0L) {
                val actualHash = ApkInspector.computeFileSha256(finalFile)
                if (expectedSha256.isBlank() || actualHash.equals(expectedSha256, ignoreCase = true)) {
                    val completed = ActiveDownloadState(
                        appId = appId,
                        versionId = versionId,
                        appName = appName,
                        packageName = packageName,
                        versionName = versionName,
                        versionCode = versionCode,
                        apkUrl = apkUrl,
                        expectedSha256 = expectedSha256,
                        status = DownloadStatus.COMPLETED,
                        bytesDownloaded = finalFile.length(),
                        totalBytes = finalFile.length(),
                        localFilePath = finalFile.absolutePath
                    )
                    _downloads.update { it + (appId to completed) }
                    onStatePersisted(completed)
                    return@launch
                } else {
                    finalFile.delete()
                }
            }

            var existingBytes = if (partFile.exists()) partFile.length() else 0L
            val initialState = ActiveDownloadState(
                appId = appId,
                versionId = versionId,
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                apkUrl = apkUrl,
                expectedSha256 = expectedSha256,
                status = DownloadStatus.WAITING,
                bytesDownloaded = existingBytes,
                totalBytes = knownTotalBytes
            )
            _downloads.update { it + (appId to initialState) }
            onStatePersisted(initialState)

            try {
                val reqBuilder = Request.Builder().url(apkUrl)
                if (existingBytes > 0L) {
                    reqBuilder.addHeader("Range", "bytes=$existingBytes-")
                }
                val call = client.newCall(reqBuilder.build())
                activeCalls[appId] = call

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Server returned HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty download response")
                    val isPartial = response.code == 206
                    if (!isPartial && existingBytes > 0L) {
                        partFile.delete()
                        existingBytes = 0L
                    }
                    val contentLength = body.contentLength()
                    val totalBytes = when {
                        isPartial && contentLength > 0L -> existingBytes + contentLength
                        contentLength > 0L -> contentLength
                        else -> knownTotalBytes
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, isPartial).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var downloaded = existingBytes
                            var lastSpeedCheckAt = System.currentTimeMillis()
                            var bytesSinceCheck = 0L
                            var currentSpeed = 0L

                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                bytesSinceCheck += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastSpeedCheckAt
                                if (elapsed >= 400L) {
                                    currentSpeed = (bytesSinceCheck * 1000L) / elapsed.coerceAtLeast(1L)
                                    lastSpeedCheckAt = now
                                    bytesSinceCheck = 0L
                                    val downloadingState = ActiveDownloadState(
                                        appId = appId,
                                        versionId = versionId,
                                        appName = appName,
                                        packageName = packageName,
                                        versionName = versionName,
                                        versionCode = versionCode,
                                        apkUrl = apkUrl,
                                        expectedSha256 = expectedSha256,
                                        status = DownloadStatus.DOWNLOADING,
                                        bytesDownloaded = downloaded,
                                        totalBytes = totalBytes.coerceAtLeast(downloaded),
                                        bytesPerSecond = currentSpeed
                                    )
                                    _downloads.update { it + (appId to downloadingState) }
                                }
                            }
                            output.flush()
                        }
                    }

                    // Integrity verification
                    val actualHash = ApkInspector.computeFileSha256(partFile)
                    if (expectedSha256.isNotBlank() && !actualHash.equals(expectedSha256, ignoreCase = true)) {
                        partFile.delete()
                        throw SecurityException("APK SHA-256 verification failed. Expected $expectedSha256, got $actualHash")
                    }

                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }

                    val completedState = ActiveDownloadState(
                        appId = appId,
                        versionId = versionId,
                        appName = appName,
                        packageName = packageName,
                        versionName = versionName,
                        versionCode = versionCode,
                        apkUrl = apkUrl,
                        expectedSha256 = expectedSha256,
                        status = DownloadStatus.COMPLETED,
                        bytesDownloaded = finalFile.length(),
                        totalBytes = finalFile.length(),
                        localFilePath = finalFile.absolutePath
                    )
                    _downloads.update { it + (appId to completedState) }
                    onStatePersisted(completedState)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentStatus = _downloads.value[appId]?.status
                if (currentStatus == DownloadStatus.PAUSED || currentStatus == DownloadStatus.CANCELLED) {
                    return@launch
                }
                val failed = ActiveDownloadState(
                    appId = appId,
                    versionId = versionId,
                    appName = appName,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    apkUrl = apkUrl,
                    expectedSha256 = expectedSha256,
                    status = DownloadStatus.FAILED,
                    bytesDownloaded = if (partFile.exists()) partFile.length() else 0L,
                    totalBytes = knownTotalBytes,
                    errorMessage = e.localizedMessage ?: "Download failed"
                )
                _downloads.update { it + (appId to failed) }
                onStatePersisted(failed)
            } finally {
                activeCalls.remove(appId)
                activeJobs.remove(appId)
            }
        }
        activeJobs[appId] = job
    }

    fun pauseDownload(appId: String, onStatePersisted: (ActiveDownloadState) -> Unit = {}) {
        val current = _downloads.value[appId] ?: return
        val paused = current.copy(status = DownloadStatus.PAUSED, bytesPerSecond = 0L)
        _downloads.update { it + (appId to paused) }
        activeCalls[appId]?.cancel()
        activeJobs[appId]?.cancel()
        onStatePersisted(paused)
    }

    fun cancelDownload(appId: String, versionCode: Long, onStatePersisted: (ActiveDownloadState) -> Unit = {}) {
        val current = _downloads.value[appId]
        if (current != null) {
            val cancelled = current.copy(status = DownloadStatus.CANCELLED, bytesPerSecond = 0L)
            _downloads.update { it + (appId to cancelled) }
            onStatePersisted(cancelled)
        }
        activeCalls[appId]?.cancel()
        activeJobs[appId]?.cancel()
        partFileFor(appId, versionCode).delete()
        finalFileFor(appId, versionCode).delete()
    }

    fun getDownloadedApkFile(appId: String, versionCode: Long): File? {
        val f = finalFileFor(appId, versionCode)
        return if (f.exists() && f.length() > 0L) f else null
    }
}
