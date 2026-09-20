package com.kawamonn.store.data.api

import kotlinx.serialization.Serializable

// サーバー(web/src/lib/dto.ts)のレスポンスと1対1で対応する。項目を足すときは両方を更新すること。

@Serializable
data class CategoryDto(val slug: String, val name: String)

@Serializable
data class LatestReleaseDto(
    val releaseId: String,
    val versionName: String,
    val versionCode: Long,
    val apkSize: Long? = null,
    val minSdk: Int? = null,
    val permissions: List<String> = emptyList(),
    val releaseNotes: String = "",
    val publishedAt: String? = null,
)

@Serializable
data class AppSummaryDto(
    val id: String,
    val slug: String,
    val packageName: String,
    val name: String,
    val shortDesc: String = "",
    val iconUrl: String? = null,
    val category: CategoryDto? = null,
    val downloadCount: Long = 0,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val latest: LatestReleaseDto? = null,
)

@Serializable
data class AppDetailDto(
    val id: String,
    val slug: String,
    val packageName: String,
    val name: String,
    val shortDesc: String = "",
    val description: String = "",
    val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val category: CategoryDto? = null,
    val developerName: String = "",
    val downloadCount: Long = 0,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val latest: LatestReleaseDto? = null,
)

@Serializable
data class HomeDto(
    val featured: List<AppSummaryDto> = emptyList(),
    val newest: List<AppSummaryDto> = emptyList(),
    val popular: List<AppSummaryDto> = emptyList(),
)

@Serializable
data class AppListDto(val items: List<AppSummaryDto> = emptyList(), val total: Int = 0)

@Serializable
data class CategoriesDto(val items: List<CategoryDto> = emptyList())

/** 更新チェック用の軽量な全件インデックス。端末側で突き合わせるので、インストール済み一覧をサーバーに送らない */
@Serializable
data class IndexEntryDto(
    val slug: String,
    val packageName: String,
    val name: String,
    val iconUrl: String? = null,
    val latest: LatestReleaseDto,
)

@Serializable
data class IndexDto(val items: List<IndexEntryDto> = emptyList())

@Serializable
data class DownloadRequestDto(val deviceId: String)

@Serializable
data class DownloadInfoDto(
    val url: String,
    val sha256: String,
    val signingCertSha256: String,
    val apkSize: Long? = null,
    val versionCode: Long,
    val packageName: String,
    val expiresAt: String? = null,
)
