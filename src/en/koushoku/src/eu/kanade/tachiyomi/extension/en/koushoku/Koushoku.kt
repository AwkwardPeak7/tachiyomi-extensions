package eu.kanade.tachiyomi.extension.en.koushoku

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class Koushoku : ParsedHttpSource(), ConfigurableSource {

    override val name = "Koushoku"

    override val baseUrl = "https://ksk.moe"

    override val lang = "en"

    override val supportsLatest = true

    override val versionId = 2

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(4)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val preference: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/weekly".page(page), headers)
    }

    override fun popularMangaSelector() = "#galleries article"
    override fun popularMangaNextPageSelector() = "footer nav li:has(a.active) + li:not(:last-child) > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.attr("title")
        }
        thumbnail_url = element.select("img").attr("abs:src")
        author = element.select("h3 span:nth-child(1)").text()
        genre = element.select("footer span").joinToString { it.text() }
        description = element.select("header div").text().let {
            "Length: ${it.replace(pageNumRegex, "$1")} pages"
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/browse".page(page)
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableIgnoreCode(404)
            .map { searchMangaParse(it) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/browse".page(page)
            .toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addQueryParameter("s", query.trim())
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> filter.addQueryParameter(url)
                is TitleFilter -> filter.addQueryParameter(url)
                is TagFilter -> filter.addQueryParameter(url)
                is ArtistFilter -> filter.addQueryParameter(url)
                is CircleFilter -> filter.addQueryParameter(url)
                is ParodyFilter -> filter.addQueryParameter(url)
                is MagazineFilter -> filter.addQueryParameter(url)
                is PageFilter -> filter.addQueryParameter(url)
                else -> { }
            }
        }

        val isAdvancedQuery = url.build()
            .queryParameterNames
            .filterNot { it == "s" }
            .isNotEmpty()

        if (isAdvancedQuery) {
            url.addQueryParameter("adv", "1")
        }

        return GET(url.build(), headers)
    }

    override fun getFilterList() = filters

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaParse(response: Response): MangasPage {
        val peekedBody = response.peekBody(Long.MAX_VALUE)
        val document = Jsoup.parse(peekedBody.string())
        val error = document.selectFirst(".search-errors p")?.text()

        if (error.isNullOrEmpty()) {
            return super.searchMangaParse(response)
        } else {
            response.close()
            throw Exception(error)
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                        .replace("/view/", "/read/")
                        .let { "$it/1" }
                },
            ),
        )
    }

    override fun pageListParse(document: Document): List<Page> {
        val id = document.location()
            .substringAfter("/read/")
            .substringBeforeLast("/")

        val cdnUrl = document.select("meta[itemprop=image]")
            .attr("content")
            .baseUrl()
            .let { it.takeIf { it.isNotEmpty() } ?: baseUrl }

        val script = document.select("script:containsData(window.metadata)").html()
            .substringAfter("=")
            .substringBeforeLast(",").let { "$it}" }
            .replace("original", "\"original\"")
            .replace("resampled", "\"resampled\"")

        val availableImages = json.decodeFromString<ImageQualities>(script)

        val (selectedImages, quality) = when (preference.imageQuality) {
            "original" -> availableImages.original to "original"
            "resampled" -> availableImages.resampled to "resampled"
            else -> emptyList<Image>() to ""
        }

        return selectedImages.mapIndexed { idx, img ->
            Page(
                index = idx,
                imageUrl = "$cdnUrl/$quality/$id/${img.file}",
            )
        }
    }

    @Serializable
    data class ImageQualities(
        val original: List<Image>,
        val resampled: List<Image>,
    )

    @Serializable
    data class Image(
        @SerialName("n") val file: String,
    )

    private fun String.baseUrl(): String {
        val index = this.indexOf('/', this.indexOf("//") + 2)
        return this.substring(0, index)
    }

    private fun Call.asObservableIgnoreCode(code: Int): Observable<Response> {
        return asObservable().doOnNext { response ->
            if (!response.isSuccessful && response.code != code) {
                response.close()
                throw Exception("HTTP error ${response.code}")
            }
        }
    }

    private fun String.page(page: Int): String {
        return "$this${if (page > 1) "/page/$page" else ""}"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = qualityPref
            title = "Image Quality"
            entries = arrayOf("Original", "Resampled")
            entryValues = arrayOf("original", "resampled")
            setDefaultValue("resampled")
            summary = "%s"
        }.let { screen.addPreference(it) }
    }

    private val SharedPreferences.imageQuality
        get() = getString(qualityPref, "resampled")!!

    companion object {
        private val pageNumRegex by lazy { Regex("""(\d+)\w+""") }
        private const val qualityPref = "pref_image_quality"
    }

    // unused
    override fun mangaDetailsParse(document: Document) = throw UnsupportedOperationException("Not Used")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not Used")
    override fun chapterListSelector() = ""
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not Used")
}
