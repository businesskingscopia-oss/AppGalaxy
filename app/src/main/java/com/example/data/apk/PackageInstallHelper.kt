package com.example.data.apk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.InstalledAppState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

data class InstalledPackageInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String
)

object PackageInstallHelper {

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(IllegalStateException("Downloaded APK file is missing"))
            }
            if (!canRequestPackageInstalls(context)) {
                openUnknownSourcesSettings(context)
                return Result.failure(
                    IllegalStateException("Please allow AppGalaxy to install unknown apps, then tap Install again.")
                )
            }
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getInstalledPackageInfo(context: Context, packageName: String): InstalledPackageInfo? {
        if (packageName.isBlank()) return null
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            InstalledPackageInfo(
                packageName = packageName,
                versionCode = vCode,
                versionName = info.versionName ?: "1.0"
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun resolveAppInstallState(
        context: Context,
        packageName: String,
        latestVersionCode: Long
    ): InstalledAppState {
        val installed = getInstalledPackageInfo(context, packageName) ?: return InstalledAppState.NOT_INSTALLED
        return if (installed.versionCode < latestVersionCode) {
            InstalledAppState.UPDATE_AVAILABLE
        } else {
            InstalledAppState.INSTALLED_LATEST
        }
    }

    fun launchInstalledApp(context: Context, packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    fun observePackageChanges(context: Context): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        awaitClose {
            try {
                context.applicationContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }
}
