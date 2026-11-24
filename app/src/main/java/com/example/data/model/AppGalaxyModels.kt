package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System Default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(raw: String?): ThemeMode =
            entries.firstOrNull { it.key == raw } ?: SYSTEM
    }
}

enum class InstalledAppState {
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    INSTALLED_LATEST
}

enum class AppStatus(val value: String, val label: String) {
    DRAFT("draft", "Draft"),
    PENDING_REVIEW("pending_review", "Pending Review"),
    APPROVED("approved", "Approved"),
    PUBLISHED("published", "Published"),
    REJECTED("rejected", "Rejected"),
    SUSPENDED("suspended", "Suspended");

    companion object {
        fun fromValue(raw: String?): AppStatus =
            entries.firstOrNull { it.value == raw } ?: DRAFT
    }
}

enum class DeveloperStatus(val value: String, val label: String) {
    PENDING("pending", "Pending Approval"),
    APPROVED("approved", "Verified Developer"),
    SUSPENDED("suspended", "Suspended");

    companion object {
        fun fromValue(raw: String?): DeveloperStatus =
            entries.firstOrNull { it.value == raw } ?: PENDING
    }
}

enum class DownloadStatus(val value: String, val label: String) {
    WAITING("waiting", "Waiting"),
    DOWNLOADING("downloading", "Downloading"),
    PAUSED("paused", "Paused"),
    COMPLETED("completed", "Completed"),
    FAILED("failed", "Failed"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun fromValue(raw: String?): DownloadStatus =
            entries.firstOrNull { it.value == raw } ?: WAITING
    }
}

enum class ReviewSortOption(val label: String) {
    MOST_HELPFUL("Most helpful"),
    NEWEST("Newest"),
    HIGHEST_RATED("Highest rated"),
    LOWEST_RATED("Lowest rated")
}

enum class SearchSortOption(val label: String) {
    POPULARITY("Popularity"),
    RATING("Top Rated"),
    NEWEST("Newest"),
    ALPHABETICAL("A–Z")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = "user",
    val status: String = "active",
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastLoginAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeveloperProfile(
    @DocumentId val id: String = "",
    val developerId: String = "",
    val developerName: String = "",
    val bio: String? = null,
    val contactEmail: String = "",
    val websiteUrl: String? = null,
    val status: String = "pending",
    val totalDownloads: Long = 0L,
    val publishedAppsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val slug: String = "",
    val iconName: String = "apps",
    val gameCategory: Boolean = false,
    val sortOrder: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ApplicationListing(
    @DocumentId val id: String = "",
    val developerId: String = "",
    val developerName: String = "",
    val appName: String = "",
    val packageName: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val gameApp: Boolean = false,
    val shortDescription: String = "",
    val fullDescription: String = "",
    val iconUrl: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val latestVersionId: String? = null,
    val latestVersionName: String = "",
    val latestVersionCode: Long = 1L,
    val apkUrl: String? = null,
    val apkSizeBytes: Long = 0L,
    val apkSha256: String = "",
    val signingCertSha256: String = "",
    val whatsNew: String? = null,
    val permissions: List<String> = emptyList(),
    val privacyPolicyUrl: String = "",
    val contactEmail: String = "",
    val status: String = "draft",
    val rejectionReason: String? = null,
    val featured: Boolean = false,
    val editorsPick: Boolean = false,
    val recommended: Boolean = false,
    val averageRating: Double = 0.0,
    val totalRatings: Long = 0L,
    val downloadCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val publishedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppVersion(
    @DocumentId val id: String = "",
    val appId: String = "",
    val developerId: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 1L,
    val apkUrl: String = "",
    val apkSizeBytes: Long = 0L,
    val apkSha256: String = "",
    val signingCertSha256: String = "",
    val whatsNew: String = "",
    val permissions: List<String> = emptyList(),
    val minSdkVersion: Long = 24L,
    val targetSdkVersion: Long = 36L,
    val securityScanStatus: String = "pending",
    val status: String = "pending_review",
    val rejectionReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Review(
    @DocumentId val id: String = "",
    val appId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val rating: Long = 5L,
    val comment: String = "",
    val helpfulCount: Long = 0L,
    val status: String = "approved",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DownloadRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val appId: String = "",
    val versionId: String = "",
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 1L,
    val status: String = "waiting",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val apkUrl: String = "",
    val sha256: String = "",
    val localFilePath: String? = null,
    val errorMessage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FavoriteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val appId: String = "",
    val listType: String = "favorite",
    val appName: String = "",
    val developerName: String = "",
    val iconUrl: String? = null,
    val averageRating: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "account",
    val targetAppId: String? = null,
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Report(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "application",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "open",
    val resolutionNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UploadSession(
    @DocumentId val id: String = "",
    val developerId: String = "",
    val appId: String? = null,
    val fileName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 1L,
    val totalBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val sha256: String = "",
    val signingCertSha256: String = "",
    val status: String = "initiated",
    val errorMessage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminSettings(
    @DocumentId val id: String = "",
    val announcementTitle: String = "",
    val announcementBody: String = "",
    val announcementActive: Boolean = false,
    val maxApkSizeMb: Long = 250L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ApkMetadata(
    val fileName: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Long,
    val targetSdkVersion: Long,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val signingCertSha256: String,
    val permissions: List<String>,
    val cachedFilePath: String
)

data class DefaultCategorySeed(
    val id: String,
    val name: String,
    val slug: String,
    val iconName: String,
    val gameCategory: Boolean,
    val sortOrder: Long
)

val STANDARD_MARKETPLACE_CATEGORIES = listOf(
    DefaultCategorySeed("cat_games", "Games", "games", "sports_esports", true, 1L),
    DefaultCategorySeed("cat_education", "Education", "education", "school", false, 2L),
    DefaultCategorySeed("cat_entertainment", "Entertainment", "entertainment", "movie", false, 3L),
    DefaultCategorySeed("cat_photography", "Photography", "photography", "photo_camera", false, 4L),
    DefaultCategorySeed("cat_productivity", "Productivity", "productivity", "task_alt", false, 5L),
    DefaultCategorySeed("cat_business", "Business", "business", "business_center", false, 6L),
    DefaultCategorySeed("cat_tools", "Tools", "tools", "build", false, 7L),
    DefaultCategorySeed("cat_communication", "Communication", "communication", "chat", false, 8L),
    DefaultCategorySeed("cat_lifestyle", "Lifestyle", "lifestyle", "spa", false, 9L),
    DefaultCategorySeed("cat_music_audio", "Music & Audio", "music-audio", "music_note", false, 10L),
    DefaultCategorySeed("cat_video_players", "Video Players", "video-players", "play_circle", false, 11L),
    DefaultCategorySeed("cat_social", "Social", "social", "groups", false, 12L),
    DefaultCategorySeed("cat_finance", "Finance", "finance", "account_balance", false, 13L),
    DefaultCategorySeed("cat_health_fitness", "Health & Fitness", "health-fitness", "fitness_center", false, 14L),
    DefaultCategorySeed("cat_shopping", "Shopping", "shopping", "shopping_bag", false, 15L),
    DefaultCategorySeed("cat_travel", "Travel", "travel", "flight", false, 16L),
    DefaultCategorySeed("cat_personalization", "Personalization", "personalization", "palette", false, 17L),
    DefaultCategorySeed("cat_news_magazines", "News & Magazines", "news-magazines", "newspaper", false, 18L),
    DefaultCategorySeed("cat_books_reference", "Books & Reference", "books-reference", "menu_book", false, 19L),
    DefaultCategorySeed("cat_sports", "Sports", "sports", "sports_soccer", false, 20L)
)
