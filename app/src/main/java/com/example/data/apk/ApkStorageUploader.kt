package com.example.data.apk

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApkStorageUploader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun resolveStorageBucket(): String? =
        FirebaseApp.getInstance().options.storageBucket?.takeIf { it.isNotBlank() }

    suspend fun uploadApkFile(
        file: File,
        objectPath: String,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bucket = resolveStorageBucket()
                ?: return@withContext Result.failure(
                    IllegalStateException("Firebase Storage bucket is not configured in google-services.json")
                )
            val user = Firebase.auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("User must be signed in before uploading"))
            val idToken = user.getIdToken(false).await().token
                ?: return@withContext Result.failure(IllegalStateException("Unable to obtain Firebase Auth token"))

            val encodedPath = URLEncoder.encode(objectPath, "UTF-8")
            val uploadUrl = "https://firebasestorage.googleapis.com/v0/b/$bucket/o?uploadType=media&name=$encodedPath"
            val totalBytes = file.length()

            val body = object : RequestBody() {
                override fun contentType() = "application/vnd.android.package-archive".toMediaType()
                override fun contentLength() = totalBytes
                override fun writeTo(sink: BufferedSink) {
                    FileInputStream(file).use { input ->
                        val buffer = ByteArray(16 * 1024)
                        var uploaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                            uploaded += read
                            onProgress(uploaded, totalBytes)
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Firebase $idToken")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val hint = if (response.code == 404 || response.code == 403) {
                        "Firebase Storage bucket ($bucket) is not enabled or lacks rules in Firebase Console (HTTP ${response.code}). Enable Storage in Firebase Console or supply a verified HTTPS APK URL."
                    } else {
                        "Upload failed (HTTP ${response.code}): ${respStr.take(200)}"
                    }
                    return@withContext Result.failure(IllegalStateException(hint))
                }
                val json = JSONObject(respStr)
                val token = json.optString("downloadTokens")
                val encodedName = URLEncoder.encode(json.optString("name", objectPath), "UTF-8")
                val downloadUrl = if (token.isNotBlank()) {
                    "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedName?alt=media&token=$token"
                } else {
                    "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedName?alt=media"
                }
                Result.success(downloadUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadImageUri(
        uri: Uri,
        objectPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bucket = resolveStorageBucket()
                ?: return@withContext Result.failure(IllegalStateException("Storage bucket not configured"))
            val user = Firebase.auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("Sign in required"))
            val idToken = user.getIdToken(false).await().token
                ?: return@withContext Result.failure(IllegalStateException("Missing auth token"))

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Unable to read image"))
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val encodedPath = URLEncoder.encode(objectPath, "UTF-8")
            val uploadUrl = "https://firebasestorage.googleapis.com/v0/b/$bucket/o?uploadType=media&name=$encodedPath"

            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Firebase $idToken")
                .post(RequestBody.create(mimeType.toMediaType(), bytes))
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Image upload failed (HTTP ${response.code}). Enable Firebase Storage in Console or enter an HTTPS image URL.")
                    )
                }
                val json = JSONObject(respStr)
                val token = json.optString("downloadTokens")
                val encodedName = URLEncoder.encode(json.optString("name", objectPath), "UTF-8")
                val url = if (token.isNotBlank()) {
                    "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedName?alt=media&token=$token"
                } else {
                    "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedName?alt=media"
                }
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
