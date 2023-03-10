package eu.kanade.tachiyomi.extension.en.earlymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.TriState.Companion.STATE_EXCLUDE
import eu.kanade.tachiyomi.source.model.Filter.TriState.Companion.STATE_INCLUDE
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class EarlyManga : HttpSource() {

    override val name = "EarlyManga"

    override val baseUrl = "https://earlym.org"

    private val apiUrl = "$baseUrl/api"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    /* Popular */
    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(OrderByFilter("", orderByFilterOptions, 0)),
        )
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    /* latest */
    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(OrderByFilter("", orderByFilterOptions, 3)),
        )
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    /* search */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val allIncludeGenres = mutableListOf<String>()
        val allExcludeGenres = mutableListOf<String>()
        val includedLanguages = mutableListOf<String>()
        val includedPubstatus = mutableListOf<String>()
        var listType = "Views"
        var listOrder = "desc"

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val includeGenres = filter.state
                        .filter { it.state == STATE_INCLUDE }
                        .map { it.name }
                    allIncludeGenres.addAll(includeGenres)

                    val excludeGenres = filter.state
                        .filter { it.state == STATE_EXCLUDE }
                        .map { it.name }
                    allExcludeGenres.addAll(excludeGenres)
                }
                is TypeFilter -> {
                    val includeTypes = filter.getValue()
                    includedLanguages.addAll(includeTypes)
                }
                is StatusFilter -> {
                    val includeStatus = filter.state
                        .filter { it.state }
                        .map { it.name }
                    includedPubstatus.addAll(includeStatus)
                }
                is OrderByFilter -> {
                    listType = filter.values[filter.state]
                }
                is SortFilter -> {
                    listOrder = filter.getValue()
                }
                else -> {}
            }
        }

        val payload = buildJsonObject {
            put("excludedGenres_all", JsonArray(allExcludeGenres.map { JsonPrimitive(it) }))
            put("includedGenres_all", JsonArray(allIncludeGenres.map { JsonPrimitive(it) }))
            put("includedLanguages", JsonArray(includedLanguages.map { JsonPrimitive(it) }))
            put("includedPubstatus", JsonArray(includedPubstatus.map { JsonPrimitive(it) }))
            put("list_order", JsonPrimitive(listOrder))
            put("list_type", JsonPrimitive(listType))
            put("term", JsonPrimitive(query))
        }.toString().toRequestBody(jsonMediaType.toMediaType())

        val apiHeaders = headersBuilder()
            .set("Accept", ACCEPT)
            .add("Content-Length", payload.contentLength().toString())
            .add("Content-Type", payload.contentType().toString())
            .build()

        return POST("$apiUrl/search/advanced/post?page=$page", apiHeaders, payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }

        val result = json.decodeFromString<SearchResponse>(response.body.string())

        return MangasPage(
            result.data.map {
                SManga.create().apply {
                    url = "/manga/${it.id}/${it.slug}"
                    title = it.title
                    thumbnail_url = "$baseUrl/storage/uploads/covers_optimized_mangalist/manga_${it.id}/${it.cover}"
                }
            },
            hasNextPage = result.meta.last_page > result.meta.current_page,
        )
    }

    private var genresMap: Map<String, List<String>> = emptyMap()

    private val orderByFilterOptions: List<String> = listOf(
        "Views",
        "Bookmarks",
        "Added date",
        "Updated date",
        "Number of chapters",
        "Rating",
    )

    private val sortByFilterOptions: List<Pair<String, String>> = listOf(
        Pair("Descending", "desc"),
        Pair("Ascending", "asc"),
    )

    private val typeFilterOptions: List<Pair<String, String>> = listOf(
        Pair("Manga", "Japanese"),
        Pair("Manhwa", "Korean"),
        Pair("Manhua", "Chinese"),
        Pair("Comic", "English"),
    )

    private val statusFilterOptions: List<SimpleGenre> = listOf(
        SimpleGenre("Ongoing"),
        SimpleGenre("Completed"),
        SimpleGenre("Cancelled"),
        SimpleGenre("Hiatus"),
    )

    private class OrderByFilter(title: String, options: List<String>, state: Int = 0) :
        Filter.Select<String>(title, options.toTypedArray(), state)

    private class SortFilter(title: String, private val options: List<Pair<String, String>>) :
        Filter.Select<String>(title, options.map { it.first }.toTypedArray()) {
        fun getValue() = options[state].second
    }

    private class TypeFilter(title: String, private val options: List<Pair<String, String>>) :
        Filter.Group<SimpleGenre>(title, options.map { SimpleGenre(it.first) }) {
        fun getValue() = options
            .filterNot { SimpleGenre(it.first) in state }
            .map { it.second }
    }

    private class StatusFilter(title: String, options: List<SimpleGenre>) :
        Filter.Group<SimpleGenre>(title, options)

    private class GenreFilter(title: String, genres: List<Genre>) :
        Filter.Group<Genre>(title, genres)

    class Genre(name: String, val value: String = "") : Filter.TriState(name)

    class SimpleGenre(name: String, val value: String = "") : Filter.CheckBox(name)

    override fun getFilterList(): FilterList {
        val filters = mutableListOf(
            OrderByFilter("Order by", orderByFilterOptions),
            SortFilter("Sort By", sortByFilterOptions),
            Filter.Separator(),
            TypeFilter("Type", typeFilterOptions),
            StatusFilter("Status", statusFilterOptions),
            Filter.Separator(),
        )

        filters += if (genresMap.isNotEmpty()) {
            genresMap.map { it ->
                GenreFilter(it.key, it.value.map { Genre(it) })
            }
        } else {
            listOf(Filter.Header("Press 'Reset' to attempt to show the genres"))
        }

        return FilterList(filters)
    }

    private var fetchGenresAttempts = 0
    private var fetchGenresFailed = false

    private fun fetchGenres() {
        if (fetchGenresAttempts <= 3 && (genresMap.isEmpty() || fetchGenresFailed)) {
            val genres = runCatching {
                client.newCall(genresRequest()).execute()
                    .use { parseGenres(it) }
            }

            fetchGenresFailed = genres.isFailure
            genresMap = genres.getOrNull().orEmpty()
            fetchGenresAttempts++
        }
    }

    private fun genresRequest(): Request {
        return GET("$apiUrl/search/filter", headers)
    }

    private fun parseGenres(response: Response): Map<String, List<String>> {
        val filterResponse = json.decodeFromString<FilterResponse>(response.body.string())

        val result = mutableMapOf<String, List<String>>()

        result["Genres"] = filterResponse.genres.map { it.name } //
        result["Sub Genres"] = filterResponse.sub_genres.map { it.name } //
        result["Content"] = filterResponse.contents.map { it.name } //
        result["Demographic"] = filterResponse.demographics.map { it.name } //
        result["Format"] = filterResponse.formats.map { it.name } //
        result["Themes"] = filterResponse.themes.map { it.name } //

        return result
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = json.decodeFromString<MangaResponse>(response.body.string()).main_manga
        return SManga.create().apply {
            url = "/manga/${result.id}/${result.slug}"
            title = result.title
            author = result.authors?.joinToString { it.trim() }
            artist = result.artists?.joinToString { it.trim() }
            description = "${result.desc.trim()}\n\nAlternative Names: ${result.alt_titles?.joinToString { it.name.trim() }}"
            genre = result.all_genres?.joinToString { it.name.trim() }
            status = result.pubstatus[0].name.parseStatus()
            thumbnail_url = "$baseUrl/storage/uploads/covers/manga_${result.id}/${result.cover}"
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$apiUrl${manga.url}/chapterlist", headers)
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<List<ChapterList>>(response.body.string())

        val mangaUrl = response.request.url.toString()
            .substringBefore("/chapterlist")
            .substringAfter(apiUrl)

        return result.map { chapter ->
            SChapter.create().apply {
                url = "$mangaUrl/${chapter.id}/chapter-${chapter.slug}"
                name = "Chapter ${chapter.chapter_number}" + if (chapter.title.isNullOrEmpty()) "" else ": ${chapter.title}"
                date_upload = chapter.created_at.let {
                    try {
                        dateFormat.parse(it.toString())?.time ?: 0L
                    } catch (e: ParseException) {
                        0L
                    }
                }
            }
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$apiUrl${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<PageListResponse>(response.body.string()).chapter
        val chapterUrl = response.request.url.toString()
            .replace("/api", "")

        return result.images.mapIndexed { index, img ->
            Page(index = index, url = chapterUrl, imageUrl = "$baseUrl/storage/uploads/manga/manga_${result.manga_id}/chapter_${result.slug}/$img")
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers.newBuilder().set("Referer", page.url).build())
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("not Used")
    }

    private fun String.parseStatus(): Int {
        return when (this) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Cancelled" -> SManga.CANCELLED
            "Hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    companion object {
        private const val ACCEPT = "application/json, text/plain, */*"
        private const val jsonMediaType = "application/json"
    }
}
