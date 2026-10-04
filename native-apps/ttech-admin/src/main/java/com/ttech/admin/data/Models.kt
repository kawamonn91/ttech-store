package com.ttech.admin.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** サーバー(web/src/lib/admin-queries.ts)が返すJSONの形。項目が増えても壊れないよう、未知の項目は無視する */
val AdminJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class Overview(
    val users: UsersStat = UsersStat(),
    val diary: DiaryStat = DiaryStat(),
    val reviews: ReviewsStat = ReviewsStat(),
    val developers: DevelopersStat = DevelopersStat(),
    val releases: ReleasesStat = ReleasesStat(),
    val apps: AppsStat = AppsStat(),
    val downloads: DownloadsStat = DownloadsStat(),
) {
    /** 未対応の報告の合計(日記の報告 + レビューの通報) */
    val openReports: Int get() = diary.openReports + reviews.openReports
}

@Serializable data class UsersStat(val total: Int = 0, val new7d: Int = 0, val banned: Int = 0)
@Serializable data class DiaryStat(val entries: Int = 0, val openReports: Int = 0)
@Serializable data class ReviewsStat(val openReports: Int = 0)
@Serializable data class DevelopersStat(val pending: Int = 0)
@Serializable data class ReleasesStat(val pendingApproval: Int = 0)
@Serializable data class AppsStat(val published: Int = 0)
@Serializable data class DownloadsStat(val total: Int = 0, val last7d: Int = 0)

@Serializable
data class UserRow(
    val id: String,
    val email: String? = null,
    val displayName: String = "",
    val role: String = "user",
    val provider: String = "email",
    val createdAt: String = "",
    val lastSignInAt: String? = null,
    val bannedAt: String? = null,
    val banReason: String? = null,
    val diaryCount: Int = 0,
    val reviewCount: Int = 0,
    val reportsAgainst: Int = 0,
) {
    val isBanned: Boolean get() = bannedAt != null
}

@Serializable data class UsersPage(val items: List<UserRow> = emptyList(), val total: Int = 0)

@Serializable data class DeveloperInfo(val status: String, val name: String, val contactEmail: String, val website: String? = null)
@Serializable data class ActivityCounts(val diaryEntries: Int = 0, val reviews: Int = 0, val reportsAgainst: Int = 0, val reportsFiled: Int = 0, val downloads: Int = 0)
@Serializable data class RecentEntry(val id: String, val body: String, val visibility: String, val hasPhoto: Boolean = false, val createdAt: String = "")
@Serializable data class RecentReview(val id: String, val rating: Int = 0, val body: String = "", val status: String = "visible", val appName: String = "", val createdAt: String = "")
@Serializable data class ReportAgainst(val id: String, val reason: String, val detail: String? = null, val status: String, val entryBody: String? = null, val createdAt: String = "")

@Serializable
data class UserDetail(
    val id: String,
    val email: String? = null,
    val emailConfirmed: Boolean = false,
    val displayName: String = "",
    val role: String = "user",
    val provider: String = "email",
    val createdAt: String = "",
    val lastSignInAt: String? = null,
    val bannedAt: String? = null,
    val banReason: String? = null,
    val developer: DeveloperInfo? = null,
    val counts: ActivityCounts = ActivityCounts(),
    val recentEntries: List<RecentEntry> = emptyList(),
    val recentReviews: List<RecentReview> = emptyList(),
    val reportsAgainst: List<ReportAgainst> = emptyList(),
) {
    val isBanned: Boolean get() = bannedAt != null
}

@Serializable data class Person(val id: String = "", val name: String = "")
@Serializable data class ReportTarget(val userId: String? = null, val name: String = "")

@Serializable
data class Report(
    val kind: String,
    val id: String,
    val createdAt: String = "",
    val reason: String = "",
    val detail: String? = null,
    val status: String = "open",
    val reporter: Person = Person(),
    val target: ReportTarget = ReportTarget(),
    val body: String = "",
    val contentId: String? = null,
    val contentLabel: String = "",
)

@Serializable data class ReportsResponse(val items: List<Report> = emptyList())

@Serializable
data class DiaryEntry(
    val id: String,
    val userId: String,
    val userName: String = "",
    val body: String = "",
    val visibility: String = "",
    val createdAt: String = "",
    val photoUrl: String? = null,
)

@Serializable data class DiaryEntriesResponse(val items: List<DiaryEntry> = emptyList())

@Serializable
data class Developer(
    val userId: String,
    val name: String = "",
    val contactEmail: String = "",
    val website: String? = null,
    val status: String = "pending",
    val createdAt: String = "",
)

@Serializable data class DevelopersResponse(val items: List<Developer> = emptyList())

@Serializable
data class AppRow(
    val id: String,
    val slug: String = "",
    val name: String = "",
    val packageName: String = "",
    val status: String = "draft",
    val adminOnly: Boolean = false,
    val downloadCount: Long = 0,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val latestVersion: String? = null,
)

@Serializable data class AppsResponse(val items: List<AppRow> = emptyList())

@Serializable data class ReleaseApp(val id: String = "", val slug: String = "", val name: String = "")

@Serializable
data class PendingRelease(
    val id: String,
    val versionName: String? = null,
    val status: String = "scanned",
    val policyVerdict: String? = null,
    val policyFindings: List<JsonObject> = emptyList(),
    val app: ReleaseApp? = null,
)

@Serializable data class PendingReleasesResponse(val items: List<PendingRelease> = emptyList())

@Serializable
data class AuditRow(
    val id: Long,
    val action: String,
    val targetType: String = "",
    val targetId: String? = null,
    val adminName: String = "",
    val detail: JsonObject = JsonObject(emptyMap()),
    val createdAt: String = "",
)

@Serializable data class AuditResponse(val items: List<AuditRow> = emptyList())
