package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.auth.AuthManager
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.firebase.unwrapFirestoreError
import com.example.data.model.AdminSettings
import com.example.data.model.AppNotification
import com.example.data.model.AppVersion
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import com.example.data.model.FavoriteItem
import com.example.data.model.Review
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MarketplaceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before performing this action.")
    }

    // --- Public Catalog Streams ---

    fun observePublishedApps(): Flow<List<ApplicationListing>> =
        db.collection("applications")
            .whereEqualTo("status", "published")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ApplicationListing::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "applications")
                throw root
            }

    fun observeAppById(appId: String): Flow<ApplicationListing?> =
        db.collection("applications")
            .document(appId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        ApplicationListing::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.GET, "applications/$appId")
                throw root
            }

    fun observeCategories(): Flow<List<Category>> =
        db.collection("categories")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Category::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.active }.sortedBy { it.sortOrder }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "categories")
                throw root
            }

    fun observePublishedVersionsForApp(appId: String): Flow<List<AppVersion>> =
        db.collection("app_versions")
            .whereEqualTo("appId", appId)
            .whereEqualTo("status", "published")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AppVersion::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.versionCode }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "app_versions")
                throw root
            }

    fun observeApprovedReviewsForApp(appId: String): Flow<List<Review>> =
        db.collection("reviews")
            .whereEqualTo("appId", appId)
            .whereEqualTo("status", "approved")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Review::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "reviews")
                throw root
            }

    fun observeAdminSettings(): Flow<AdminSettings> =
        db.collection("admin_settings")
            .document("marketplace_config")
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    AdminSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: AdminSettings()
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.GET, "admin_settings/marketplace_config")
                emit(AdminSettings())
            }

    // --- User-Scoped Streams (Authenticated Only) ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.GET, "users/$uid")
                    throw root
                }
        )
    }

    fun observeIsAdmin(userId: String): Flow<Boolean> = flow {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid != userId) {
            emit(false)
            return@flow
        }
        val isBootstrapAdmin = currentUser.isEmailVerified &&
            currentUser.email.equals(AuthManager.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)
        emitAll(
            db.collection("admins").document(userId)
                .snapshots()
                .map { snap -> snap.exists() || isBootstrapAdmin }
                .catch { _ ->
                    emit(isBootstrapAdmin)
                }
        )
    }

    fun observeDownloads(userId: String): Flow<List<DownloadRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("users").document(uid).collection("downloads")
                .snapshots()
                .map { snap ->
                    snap.toObjects(DownloadRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.LIST, "users/$uid/downloads")
                    throw root
                }
        )
    }

    fun observeFavorites(userId: String): Flow<List<FavoriteItem>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("users").document(uid).collection("favorites")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FavoriteItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.LIST, "users/$uid/favorites")
                    throw root
                }
        )
    }

    fun observeNotifications(userId: String): Flow<List<AppNotification>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.LIST, "users/$uid/notifications")
                    throw root
                }
        )
    }

    fun observeUserReviews(userId: String): Flow<List<Review>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("reviews")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Review::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.LIST, "reviews")
                    throw root
                }
        )
    }

    // --- Mutations ---

    suspend fun syncUserProfileOnLogin(user: FirebaseUser): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val snap = docRef.get().await()
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "AppGalaxy User"
            val email = user.email?.takeIf { it.isNotBlank() } ?: "user@appgalaxy.local"
            val photoUrl = user.photoUrl?.toString()

            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to user.uid,
                    "displayName" to displayName.take(120),
                    "email" to email.take(200),
                    "photoUrl" to photoUrl?.take(1000),
                    "role" to "user",
                    "status" to "active",
                    "notificationsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to displayName.take(120),
                    "email" to email.take(200),
                    "photoUrl" to photoUrl?.take(1000),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "notificationsEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(app: ApplicationListing, listType: String, currentlySaved: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Please sign in with Google to save apps"))
        val path = "users/$uid/favorites/${app.id}"
        return try {
            val docRef = db.collection("users").document(uid).collection("favorites").document(app.id)
            if (currentlySaved) {
                docRef.delete().await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "appId" to app.id,
                    "listType" to listType,
                    "appName" to app.appName.take(120),
                    "developerName" to app.developerName.take(120),
                    "iconUrl" to app.iconUrl?.take(1000),
                    "averageRating" to app.averageRating,
                    "createdAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertDownloadRecord(
        app: ApplicationListing,
        status: DownloadStatus,
        bytesDownloaded: Long,
        totalBytes: Long,
        localFilePath: String? = null,
        errorMessage: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit) // Non-fatal if browsing unauthenticated
        val downloadId = "${app.id}_v${app.latestVersionCode}"
        val path = "users/$uid/downloads/$downloadId"
        return try {
            val docRef = db.collection("users").document(uid).collection("downloads").document(downloadId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "appId" to app.id,
                    "versionId" to (app.latestVersionId ?: "v_${app.latestVersionCode}"),
                    "appName" to app.appName.take(120),
                    "packageName" to app.packageName.take(200),
                    "versionName" to app.latestVersionName.take(60),
                    "versionCode" to app.latestVersionCode,
                    "status" to status.value,
                    "bytesDownloaded" to bytesDownloaded.coerceAtLeast(0L),
                    "totalBytes" to totalBytes.coerceAtLeast(0L),
                    "apkUrl" to (app.apkUrl ?: "").take(2000),
                    "sha256" to app.apkSha256.take(128),
                    "localFilePath" to localFilePath?.take(1000),
                    "errorMessage" to errorMessage?.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "versionId" to (app.latestVersionId ?: "v_${app.latestVersionCode}"),
                    "versionName" to app.latestVersionName.take(60),
                    "versionCode" to app.latestVersionCode,
                    "status" to status.value,
                    "bytesDownloaded" to bytesDownloaded.coerceAtLeast(0L),
                    "totalBytes" to totalBytes.coerceAtLeast(0L),
                    "apkUrl" to (app.apkUrl ?: "").take(2000),
                    "sha256" to app.apkSha256.take(128),
                    "localFilePath" to localFilePath?.take(1000),
                    "errorMessage" to errorMessage?.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementAppDownloadCount(appId: String): Result<Unit> {
        if (auth.currentUser == null) return Result.success(Unit)
        val path = "applications/$appId"
        return try {
            db.collection("applications").document(appId).update(
                mapOf(
                    "downloadCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitOrUpdateReview(
        app: ApplicationListing,
        rating: Long,
        comment: String
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Please sign in with Google to submit a review"))
        val cleanComment = comment.trim()
        if (cleanComment.isEmpty()) return Result.failure(IllegalArgumentException("Please write a review comment"))
        if (rating !in 1L..5L) return Result.failure(IllegalArgumentException("Rating must be between 1 and 5 stars"))

        val reviewId = "${app.id}_${user.uid}"
        val reviewRef = db.collection("reviews").document(reviewId)
        val appRef = db.collection("applications").document(app.id)

        return try {
            val existingReviews = db.collection("reviews")
                .whereEqualTo("appId", app.id)
                .whereEqualTo("status", "approved")
                .get()
                .await()
                .toObjects(Review::class.java)

            val otherRatings = existingReviews.filter { it.userId != user.uid }.map { it.rating }
            val allRatings = otherRatings + rating
            val newTotal = allRatings.size.toLong()
            val newAvg = if (allRatings.isEmpty()) 0.0 else allRatings.sum().toDouble() / allRatings.size

            val existingDoc = reviewRef.get().await()
            val batch = db.batch()
            if (!existingDoc.exists()) {
                val payload = mapOf(
                    "appId" to app.id,
                    "userId" to user.uid,
                    "userName" to (user.displayName?.takeIf { it.isNotBlank() } ?: "AppGalaxy User").take(120),
                    "userPhotoUrl" to user.photoUrl?.toString()?.take(1000),
                    "rating" to rating,
                    "comment" to cleanComment.take(2000),
                    "helpfulCount" to 0L,
                    "status" to "approved",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                batch.set(reviewRef, payload)
            } else {
                val updatePayload = mapOf(
                    "userName" to (user.displayName?.takeIf { it.isNotBlank() } ?: "AppGalaxy User").take(120),
                    "userPhotoUrl" to user.photoUrl?.toString()?.take(1000),
                    "rating" to rating,
                    "comment" to cleanComment.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                batch.update(reviewRef, updatePayload)
            }

            batch.update(
                appRef,
                mapOf(
                    "averageRating" to newAvg,
                    "totalRatings" to newTotal,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "reviews/$reviewId")
            Result.failure(e)
        }
    }

    suspend fun deleteOwnReview(appId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val reviewId = "${appId}_$uid"
        return try {
            db.collection("reviews").document(reviewId).delete().await()
            val remaining = db.collection("reviews")
                .whereEqualTo("appId", appId)
                .whereEqualTo("status", "approved")
                .get()
                .await()
                .toObjects(Review::class.java)
            val newTotal = remaining.size.toLong()
            val newAvg = if (remaining.isEmpty()) 0.0 else remaining.sumOf { it.rating }.toDouble() / remaining.size
            db.collection("applications").document(appId).update(
                mapOf(
                    "averageRating" to newAvg,
                    "totalRatings" to newTotal,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "reviews/$reviewId")
            Result.failure(e)
        }
    }

    suspend fun markReviewHelpful(reviewId: String): Result<Unit> {
        if (auth.currentUser == null) return Result.failure(IllegalStateException("Sign in with Google to mark reviews helpful"))
        return try {
            db.collection("reviews").document(reviewId).update(
                mapOf(
                    "helpfulCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reviews/$reviewId")
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in with Google to submit a report"))
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return try {
            db.collection("reports").document(reportId).set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId.take(256),
                    "reason" to reason.trim().take(160),
                    "details" to details.trim().take(2000),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports/$reportId")
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        return try {
            db.collection("users").document(uid)
                .collection("notifications").document(notificationId)
                .update("read", true)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications/$notificationId")
            Result.failure(e)
        }
    }

    suspend fun createUserNotification(
        targetUserId: String,
        title: String,
        body: String,
        type: String,
        targetAppId: String? = null
    ): Result<Unit> {
        val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return try {
            val payload = mapOf(
                "userId" to targetUserId,
                "title" to title.take(160),
                "body" to body.take(1000),
                "type" to type,
                "targetAppId" to targetAppId?.take(128),
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            db.collection("users").document(targetUserId)
                .collection("notifications").document(notifId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$targetUserId/notifications/$notifId")
            Result.failure(e)
        }
    }
}
