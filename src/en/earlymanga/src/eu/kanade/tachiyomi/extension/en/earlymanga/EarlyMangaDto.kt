package eu.kanade.tachiyomi.extension.en.earlymanga

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val data: Array<SearchData>,
    val meta: SearchMeta,
)

@Serializable
data class SearchData(
    val id: Int,
    val title: String,
    val slug: String,
    val cover: String,
)

@Serializable
data class MangaResponse(
    val main_manga: MangaData,
)

@Serializable
data class MangaData(
    val id: Int,
    val title: String,
    val slug: String,
    val alt_titles: Array<NameVal>?,
    val authors: Array<String>?,
    val artists: Array<String>?,
    val all_genres: Array<NameVal>?,
    val pubstatus: Array<NameVal>,
    val desc: String = "Unknown",
    val cover: String,
)

@Serializable
data class NameVal(
    val name: String,
)

@Serializable
data class ChapterList(
    val id: Int,
    val slug: String,
    val title: String?,
    val created_at: String?,
    val chapter_number: String,
)

@Serializable
data class PageListResponse(
    val chapter: Chapter,
)

@Serializable
data class Chapter(
    val id: Int,
    val manga_id: Int,
    val slug: String,
    val images: Array<String>,
)

@Serializable
data class SearchMeta(
    val current_page: Int,
    val last_page: Int,
)

@Serializable
data class FilterResponse(
    val genres: Array<Genre>,
    val sub_genres: Array<Genre>,
    val contents: Array<Genre>,
    val demographics: Array<Genre>,
    val formats: Array<Genre>,
    val themes: Array<Genre>,
)

@Serializable
data class Genre(
    val name: String,
)
