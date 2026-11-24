package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.apk.ApkInspector
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.firebase.unwrapFirestoreError
import com.example.data.model.AppVersion
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.data.model.DeveloperProfile
import com.example.data.model.Report
import com.example.data.model.Review
import com.example.data.model.STANDARD_MARKETPLACE_CATEGORIES
import com.example.data.model.UploadSession
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
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

class DeveloperAdminRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    // --- Developer Streams ---

    fun observeDeveloperProfile(developerId: String): Flow<DeveloperProfile?> = flow {
        val uid = requireUserId()
        check(uid == developerId)
        emitAll(
            db.collection("developers").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(DeveloperProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.GET, "developers/$uid")
                    throw root
                }
        )
    }

    fun observeDeveloperApps(developerId: String): Flow<List<ApplicationListing>> = flow {
        val uid = requireUserId()
        check(uid == developerId)
        emitAll(
            db.collection("applications")
                .whereEqualTo("developerId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ApplicationListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.LIST, "applications")
                    throw root
                }
        )
    }

    fun observeDeveloperVersions(developerId: String): Flow<List<AppVersion>> = flow {
        val uid = requireUserId()
        check(uid == developerId)
        emitAll(
            db.collection("app_versions")
                .whereEqualTo("developerId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppVersion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.versionCode }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.LIST, "app_versions")
                    throw root
                }
        )
    }

    fun observeDeveloperUploadSessions(developerId: String): Flow<List<UploadSession>> = flow {
        val uid = requireUserId()
        check(uid == developerId)
        emitAll(
            db.collection("upload_sessions")
                .whereEqualTo("developerId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UploadSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) handleFirestoreError(root, OperationType.LIST, "upload_sessions")
                    throw root
                }
        )
    }

    // --- Developer Mutations ---

    suspend fun registerOrUpdateDeveloper(
        developerName: String,
        bio: String,
        contactEmail: String,
        websiteUrl: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val cleanName = developerName.trim()
        val cleanEmail = contactEmail.trim()
        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Developer name must be at least 2 characters"))
        if (!cleanEmail.contains("@")) return Result.failure(IllegalArgumentException("Enter a valid contact email"))

        val path = "developers/$uid"
        return try {
            val docRef = db.collection("developers").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "developerId" to uid,
                    "developerName" to cleanName.take(120),
                    "bio" to bio.trim().take(1000).ifBlank { null },
                    "contactEmail" to cleanEmail.take(200),
                    "websiteUrl" to websiteUrl?.trim()?.take(500)?.ifBlank { null },
                    "status" to "pending",
                    "totalDownloads" to 0L,
                    "publishedAppsCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "developerName" to cleanName.take(120),
                    "bio" to bio.trim().take(1000),
                    "contactEmail" to cleanEmail.take(200),
                    "websiteUrl" to (websiteUrl?.trim()?.take(500) ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
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

    suspend fun createUploadSession(
        appId: String?,
        fileName: String,
        packageName: String,
        versionName: String,
        versionCode: Long,
        totalBytes: Long,
        sha256: String,
        signingCertSha256: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val sessionId = "upl_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return try {
            val payload = mapOf(
                "developerId" to uid,
                "appId" to appId,
                "fileName" to fileName.take(256),
                "packageName" to packageName.take(200),
                "versionName" to versionName.take(60),
                "versionCode" to versionCode,
                "totalBytes" to totalBytes,
                "uploadedBytes" to 0L,
                "sha256" to sha256.take(128),
                "signingCertSha256" to signingCertSha256.take(128),
                "status" to "initiated",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            db.collection("upload_sessions").document(sessionId).set(payload).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "upload_sessions/$sessionId")
            Result.failure(e)
        }
    }

    suspend fun updateUploadSession(
        sessionId: String,
        uploadedBytes: Long,
        status: String,
        appId: String? = null,
        errorMessage: String? = null
    ): Result<Unit> {
        return try {
            val payload = mapOf(
                "appId" to appId,
                "uploadedBytes" to uploadedBytes,
                "status" to status,
                "errorMessage" to errorMessage?.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            db.collection("upload_sessions").document(sessionId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "upload_sessions/$sessionId")
            Result.failure(e)
        }
    }

    suspend fun submitNewApplication(
        developerName: String,
        appName: String,
        packageName: String,
        category: Category,
        shortDescription: String,
        fullDescription: String,
        iconUrl: String?,
        screenshotUrls: List<String>,
        versionName: String,
        versionCode: Long,
        apkUrl: String,
        apkSizeBytes: Long,
        apkSha256: String,
        signingCertSha256: String,
        whatsNew: String,
        permissions: List<String>,
        minSdkVersion: Long,
        targetSdkVersion: Long,
        privacyPolicyUrl: String,
        contactEmail: String,
        submitAsDraft: Boolean = false
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val cleanPkg = packageName.trim()
        if (!ApkInspector.isValidPackageName(cleanPkg)) {
            return Result.failure(IllegalArgumentException("Invalid Android package name (e.g. com.company.app)"))
        }
        if (appName.trim().length < 2) return Result.failure(IllegalArgumentException("App name is too short"))
        if (shortDescription.trim().length < 4) return Result.failure(IllegalArgumentException("Short description must be at least 4 characters"))
        if (fullDescription.trim().length < 10) return Result.failure(IllegalArgumentException("Full description must be at least 10 characters"))
        if (apkSha256.isBlank() || signingCertSha256.isBlank()) {
            return Result.failure(IllegalArgumentException("Valid APK checksum and signing certificate are required"))
        }

        return try {
            // Duplicate package detection across published apps and developer's own apps
            val publishedDup = db.collection("applications")
                .whereEqualTo("status", "published")
                .whereEqualTo("packageName", cleanPkg)
                .get()
                .await()
            val ownDup = db.collection("applications")
                .whereEqualTo("developerId", uid)
                .whereEqualTo("packageName", cleanPkg)
                .get()
                .await()
            if (!publishedDup.isEmpty || !ownDup.isEmpty) {
                return Result.failure(IllegalStateException("An application with package '$cleanPkg' already exists. Upload a new version update instead."))
            }

            val appId = "app_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val versionId = "ver_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val initialStatus = if (submitAsDraft) "draft" else "pending_review"

            val batch = db.batch()
            val appPayload = mapOf(
                "developerId" to uid,
                "developerName" to developerName.trim().take(120),
                "appName" to appName.trim().take(120),
                "packageName" to cleanPkg.take(200),
                "categoryId" to category.id,
                "categoryName" to category.name.take(80),
                "gameApp" to category.gameCategory,
                "shortDescription" to shortDescription.trim().take(240),
                "fullDescription" to fullDescription.trim().take(6000),
                "iconUrl" to iconUrl?.trim()?.take(2000)?.ifBlank { null },
                "screenshotUrls" to screenshotUrls.map { it.trim() }.filter { it.isNotBlank() }.take(8),
                "latestVersionId" to versionId,
                "latestVersionName" to versionName.trim().take(60),
                "latestVersionCode" to versionCode.coerceAtLeast(1L),
                "apkUrl" to apkUrl.trim().take(2000).ifBlank { null },
                "apkSizeBytes" to apkSizeBytes.coerceAtLeast(0L),
                "apkSha256" to apkSha256.take(128),
                "signingCertSha256" to signingCertSha256.take(128),
                "whatsNew" to whatsNew.trim().take(2000).ifBlank { null },
                "permissions" to permissions.take(50),
                "privacyPolicyUrl" to privacyPolicyUrl.trim().take(1000),
                "contactEmail" to contactEmail.trim().take(200),
                "status" to initialStatus,
                "featured" to false,
                "editorsPick" to false,
                "recommended" to false,
                "averageRating" to 0.0,
                "totalRatings" to 0L,
                "downloadCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            batch.set(db.collection("applications").document(appId), appPayload)

            val versionPayload = mapOf(
                "appId" to appId,
                "developerId" to uid,
                "packageName" to cleanPkg.take(200),
                "versionName" to versionName.trim().take(60),
                "versionCode" to versionCode.coerceAtLeast(1L),
                "apkUrl" to apkUrl.trim().take(2000),
                "apkSizeBytes" to apkSizeBytes.coerceAtLeast(0L),
                "apkSha256" to apkSha256.take(128),
                "signingCertSha256" to signingCertSha256.take(128),
                "whatsNew" to whatsNew.trim().ifBlank { "Initial release" }.take(2000),
                "permissions" to permissions.take(50),
                "minSdkVersion" to minSdkVersion.coerceIn(1L, 99L),
                "targetSdkVersion" to targetSdkVersion.coerceIn(1L, 99L),
                "securityScanStatus" to "pending",
                "status" to "pending_review",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(db.collection("app_versions").document(versionId), versionPayload)
            batch.commit().await()
            Result.success(appId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "applications")
            Result.failure(e)
        }
    }

    suspend fun submitApplicationUpdate(
        existingApp: ApplicationListing,
        versionName: String,
        versionCode: Long,
        apkUrl: String,
        apkSizeBytes: Long,
        apkSha256: String,
        signingCertSha256: String,
        whatsNew: String,
        permissions: List<String>,
        minSdkVersion: Long,
        targetSdkVersion: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        if (existingApp.developerId != uid) {
            return Result.failure(SecurityException("You can only update your own applications"))
        }
        if (versionCode <= existingApp.latestVersionCode) {
            return Result.failure(
                IllegalArgumentException("New versionCode ($versionCode) must be higher than current versionCode (${existingApp.latestVersionCode})")
            )
        }
        if (existingApp.signingCertSha256.isNotBlank() &&
            !existingApp.signingCertSha256.equals(signingCertSha256, ignoreCase = true)
        ) {
            return Result.failure(
                SecurityException("Signing certificate mismatch! Update APK must be signed with the same certificate as the existing release.")
            )
        }

        return try {
            val versionId = "ver_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val batch = db.batch()
            val versionPayload = mapOf(
                "appId" to existingApp.id,
                "developerId" to uid,
                "packageName" to existingApp.packageName,
                "versionName" to versionName.trim().take(60),
                "versionCode" to versionCode,
                "apkUrl" to apkUrl.trim().take(2000),
                "apkSizeBytes" to apkSizeBytes.coerceAtLeast(0L),
                "apkSha256" to apkSha256.take(128),
                "signingCertSha256" to signingCertSha256.take(128),
                "whatsNew" to whatsNew.trim().ifBlank { "Bug fixes and improvements" }.take(2000),
                "permissions" to permissions.take(50),
                "minSdkVersion" to minSdkVersion.coerceIn(1L, 99L),
                "targetSdkVersion" to targetSdkVersion.coerceIn(1L, 99L),
                "securityScanStatus" to "pending",
                "status" to "pending_review",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(db.collection("app_versions").document(versionId), versionPayload)

            batch.update(
                db.collection("applications").document(existingApp.id),
                mapOf(
                    "latestVersionId" to versionId,
                    "latestVersionName" to versionName.trim().take(60),
                    "latestVersionCode" to versionCode,
                    "apkUrl" to apkUrl.trim().take(2000),
                    "apkSizeBytes" to apkSizeBytes.coerceAtLeast(0L),
                    "apkSha256" to apkSha256.take(128),
                    "signingCertSha256" to signingCertSha256.take(128),
                    "whatsNew" to whatsNew.trim().take(2000),
                    "permissions" to permissions.take(50),
                    "status" to "pending_review",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "applications/${existingApp.id}")
            Result.failure(e)
        }
    }

    suspend fun updateDeveloperAppMetadata(
        app: ApplicationListing,
        appName: String,
        shortDescription: String,
        fullDescription: String,
        whatsNew: String,
        privacyPolicyUrl: String,
        contactEmail: String,
        submitForReview: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        if (app.developerId != uid) return Result.failure(SecurityException("Unauthorized"))
        return try {
            val nextStatus = if (submitForReview) "pending_review" else "draft"
            db.collection("applications").document(app.id).update(
                mapOf(
                    "appName" to appName.trim().take(120),
                    "shortDescription" to shortDescription.trim().take(240),
                    "fullDescription" to fullDescription.trim().take(6000),
                    "whatsNew" to whatsNew.trim().take(2000),
                    "privacyPolicyUrl" to privacyPolicyUrl.trim().take(1000),
                    "contactEmail" to contactEmail.trim().take(200),
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "applications/${app.id}")
            Result.failure(e)
        }
    }

    // --- Admin Streams (Admin Only) ---

    fun observeAllApplicationsForAdmin(): Flow<List<ApplicationListing>> =
        db.collection("applications")
            .snapshots()
            .map { snap ->
                snap.toObjects(ApplicationListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "applications")
                throw root
            }

    fun observeAllDevelopersForAdmin(): Flow<List<DeveloperProfile>> =
        db.collection("developers")
            .snapshots()
            .map { snap ->
                snap.toObjects(DeveloperProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "developers")
                throw root
            }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> =
        db.collection("users")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "users")
                throw root
            }

    fun observeAllReportsForAdmin(): Flow<List<Report>> =
        db.collection("reports")
            .snapshots()
            .map { snap ->
                snap.toObjects(Report::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "reports")
                throw root
            }

    fun observeFlaggedReviewsForAdmin(): Flow<List<Review>> =
        db.collection("reviews")
            .whereEqualTo("status", "flagged")
            .snapshots()
            .map { snap ->
                snap.toObjects(Review::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "reviews")
                throw root
            }

    // --- Admin Actions ---

    suspend fun ensureBootstrapAdminDocument(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Sign in required"))
        return try {
            val adminRef = db.collection("admins").document(user.uid)
            val snap = adminRef.get().await()
            if (!snap.exists()) {
                adminRef.set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "grantedBy" to "bootstrap",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "admins/${user.uid}")
            Result.failure(e)
        }
    }

    suspend fun initializeDefaultCategories(): Result<Unit> {
        return try {
            val batch = db.batch()
            for (seed in STANDARD_MARKETPLACE_CATEGORIES) {
                val ref = db.collection("categories").document(seed.id)
                batch.set(
                    ref,
                    mapOf(
                        "name" to seed.name,
                        "slug" to seed.slug,
                        "iconName" to seed.iconName,
                        "gameCategory" to seed.gameCategory,
                        "sortOrder" to seed.sortOrder,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "categories")
            Result.failure(e)
        }
    }

    suspend fun saveCategory(name: String, gameCategory: Boolean, sortOrder: Long): Result<Unit> {
        val cleanName = name.trim()
        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Category name too short"))
        val slug = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val id = "cat_${slug.replace("-", "_")}".take(64)
        return try {
            db.collection("categories").document(id).set(
                mapOf(
                    "name" to cleanName.take(80),
                    "slug" to slug.take(80),
                    "iconName" to if (gameCategory) "sports_esports" else "apps",
                    "gameCategory" to gameCategory,
                    "sortOrder" to sortOrder.coerceIn(0L, 1000L),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "categories/$id")
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return try {
            db.collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "categories/$categoryId")
            Result.failure(e)
        }
    }

    suspend fun approveAndPublishApplication(app: ApplicationListing): Result<Unit> {
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("applications").document(app.id),
                mapOf(
                    "status" to "published",
                    "rejectionReason" to "",
                    "publishedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            app.latestVersionId?.takeIf { it.isNotBlank() }?.let { versionId ->
                batch.update(
                    db.collection("app_versions").document(versionId),
                    mapOf(
                        "status" to "published",
                        "securityScanStatus" to "verified",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("users").document(app.developerId).collection("notifications").document(notifId),
                mapOf(
                    "userId" to app.developerId,
                    "title" to "${app.appName} is now published!",
                    "body" to "Your release v${app.latestVersionName} passed review and is live on AppGalaxy.",
                    "type" to "app_approved",
                    "targetAppId" to app.id,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "applications/${app.id}")
            Result.failure(e)
        }
    }

    suspend fun rejectOrSuspendApplication(
        app: ApplicationListing,
        newStatus: String,
        reason: String
    ): Result<Unit> {
        val cleanReason = reason.trim().ifBlank { "Does not meet AppGalaxy submission guidelines" }
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("applications").document(app.id),
                mapOf(
                    "status" to newStatus,
                    "rejectionReason" to cleanReason.take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (newStatus == "rejected") {
                app.latestVersionId?.takeIf { it.isNotBlank() }?.let { versionId ->
                    batch.update(
                        db.collection("app_versions").document(versionId),
                        mapOf(
                            "status" to "rejected",
                            "rejectionReason" to cleanReason.take(1000),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("users").document(app.developerId).collection("notifications").document(notifId),
                mapOf(
                    "userId" to app.developerId,
                    "title" to "${app.appName} update: ${newStatus.replace("_", " ")}",
                    "body" to cleanReason.take(1000),
                    "type" to "app_rejected",
                    "targetAppId" to app.id,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "applications/${app.id}")
            Result.failure(e)
        }
    }

    suspend fun removeApplication(appId: String): Result<Unit> {
        return try {
            db.collection("applications").document(appId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "applications/$appId")
            Result.failure(e)
        }
    }

    suspend fun toggleAppCurationFlags(
        appId: String,
        featured: Boolean,
        editorsPick: Boolean,
        recommended: Boolean
    ): Result<Unit> {
        return try {
            db.collection("applications").document(appId).update(
                mapOf(
                    "featured" to featured,
                    "editorsPick" to editorsPick,
                    "recommended" to recommended,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "applications/$appId")
            Result.failure(e)
        }
    }

    suspend fun setDeveloperStatus(developerId: String, status: String): Result<Unit> {
        return try {
            db.collection("developers").document(developerId).update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "developers/$developerId")
            Result.failure(e)
        }
    }

    suspend fun setUserStatus(userId: String, status: String): Result<Unit> {
        return try {
            db.collection("users").document(userId).update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun moderateReview(reviewId: String, status: String): Result<Unit> {
        return try {
            db.collection("reviews").document(reviewId).update(
                mapOf(
                    "status" to status,
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

    suspend fun resolveReport(reportId: String, status: String, note: String): Result<Unit> {
        return try {
            db.collection("reports").document(reportId).update(
                mapOf(
                    "status" to status,
                    "resolutionNote" to note.trim().take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId")
            Result.failure(e)
        }
    }

    suspend fun updateMarketplaceSettings(
        announcementTitle: String,
        announcementBody: String,
        announcementActive: Boolean,
        maxApkSizeMb: Long
    ): Result<Unit> {
        return try {
            db.collection("admin_settings").document("marketplace_config").set(
                mapOf(
                    "announcementTitle" to announcementTitle.trim().take(160),
                    "announcementBody" to announcementBody.trim().take(1000),
                    "announcementActive" to announcementActive,
                    "maxApkSizeMb" to maxApkSizeMb.coerceIn(10L, 2048L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admin_settings/marketplace_config")
            Result.failure(e)
        }
    }
}
