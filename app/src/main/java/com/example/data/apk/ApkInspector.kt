package com.example.data.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.model.ApkMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object ApkInspector {

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    fun isValidPackageName(packageName: String): Boolean =
        PACKAGE_NAME_REGEX.matches(packageName.trim())

    suspend fun inspectApkUri(
        context: Context,
        uri: Uri,
        maxSizeMb: Long
    ): Result<ApkMetadata> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            var displayName = "upload.apk"
            var reportedSize = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
                }
            }

            if (!displayName.lowercase().endsWith(".apk")) {
                return@withContext Result.failure(IllegalArgumentException("Selected file must have a .apk extension"))
            }

            val maxBytes = maxSizeMb.coerceAtLeast(10L) * 1024L * 1024L
            if (reportedSize > maxBytes) {
                return@withContext Result.failure(IllegalArgumentException("APK exceeds maximum allowed upload size of ${maxSizeMb} MB"))
            }

            val cacheDir = File(context.cacheDir, "apks").apply { mkdirs() }
            val tempFile = File(cacheDir, "inspect_${System.currentTimeMillis()}.apk")

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > maxBytes) {
                            tempFile.delete()
                            return@withContext Result.failure(
                                IllegalArgumentException("APK exceeds maximum allowed upload size of ${maxSizeMb} MB")
                            )
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected APK file"))

            if (totalBytes < 128) {
                tempFile.delete()
                return@withContext Result.failure(IllegalArgumentException("File is too small to be a valid Android APK"))
            }

            // Verify ZIP magic bytes (PK\x03\x04) & AndroidManifest.xml inside archive
            val magic = ByteArray(4)
            FileInputStream(tempFile).use { it.read(magic) }
            val isZipMagic = magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
            if (!isZipMagic) {
                tempFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Invalid APK format: file is not a valid ZIP archive"))
            }

            var hasManifest = false
            ZipFile(tempFile).use { zip ->
                hasManifest = zip.getEntry("AndroidManifest.xml") != null
            }
            if (!hasManifest) {
                tempFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Invalid APK: missing AndroidManifest.xml"))
            }

            val sha256Checksum = digest.digest().joinToString("") { "%02x".format(it) }

            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES or PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
            }

            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tempFile.absolutePath, flags)
            } ?: run {
                tempFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Unable to parse APK manifest or package metadata"))
            }

            val appInfo = pkgInfo.applicationInfo
            appInfo?.sourceDir = tempFile.absolutePath
            appInfo?.publicSourceDir = tempFile.absolutePath

            val appLabel = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkgInfo.packageName
            val versionName = pkgInfo.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                (appInfo?.minSdkVersion ?: 24).toLong()
            } else 24L
            val targetSdk = (appInfo?.targetSdkVersion ?: 36).toLong()

            val signingCertSha256 = extractSigningCertSha256(pkgInfo)
            if (signingCertSha256.isBlank()) {
                tempFile.delete()
                return@withContext Result.failure(IllegalArgumentException("APK is unsigned: a valid signing certificate is required"))
            }

            val permissions = pkgInfo.requestedPermissions?.toList().orEmpty().take(50)

            Result.success(
                ApkMetadata(
                    fileName = displayName,
                    packageName = pkgInfo.packageName,
                    appName = appLabel,
                    versionName = versionName,
                    versionCode = versionCode.coerceAtLeast(1L),
                    minSdkVersion = minSdk.coerceAtLeast(1L),
                    targetSdkVersion = targetSdk.coerceAtLeast(1L),
                    fileSizeBytes = totalBytes,
                    sha256Checksum = sha256Checksum,
                    signingCertSha256 = signingCertSha256,
                    permissions = permissions,
                    cachedFilePath = tempFile.absolutePath
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractSigningCertSha256(pkgInfo: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return ""
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
        val firstCert = signatures?.firstOrNull()?.toByteArray() ?: return ""
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(firstCert).joinToString("") { "%02x".format(it) }
    }

    suspend fun computeFileSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
