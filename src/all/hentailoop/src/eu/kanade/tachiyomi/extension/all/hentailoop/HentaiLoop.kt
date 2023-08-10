package eu.kanade.tachiyomi.extension.all.hentailoop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.lang.Exception

open class HentaiLoop(
    final override val lang: String,
    private val browsePath: String,
    private val langId: String?,
) : ParsedHttpSource() {

    override val name = "HentaiLoop"

    override val baseUrl = "https://hentailoop.com"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl + browsePath + pagePath(page)

        return GET(url, headers)
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.select(".title").text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "div.nav-links a.next"
    override fun popularMangaSelector() = ".manga-card"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl + browsePath + pagePath(page) + "?sortmanga=date"

        return GET(url, headers)
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()

    private var offset = 0

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) offset = 0

        val requestPayload = buildJsonObject {
            put("query", query.trim())
            putJsonArray("filters") {
                addJsonObject {
                    put("name", "manga-genres")
                    putJsonArray("filterValues") {
                        (filters.firstInstanceOrNull<GenreFilter>()?.checked ?: emptyList())
                            .forEach(::add)
                    }
                    put("operator", "in")
                }
                addJsonObject {
                    put("name", "post_tag")
                    putJsonArray("filterValues") {
                        (filters.firstInstanceOrNull<TagFilter>()?.included ?: emptyList())
                            .forEach(::add)
                    }
                    put("operator", "in")
                }
                addJsonObject {
                    put("name", "post_tag")
                    putJsonArray("filterValues") {
                        (filters.firstInstanceOrNull<TagFilter>()?.excluded ?: emptyList())
                            .forEach(::add)
                    }
                    put("operator", "ex")
                }
                addJsonObject {
                    put("name", "manga-languages")
                    putJsonArray("filterValues") { langId?.let(::add) }
                    put("operator", "in")
                }
            }
            putJsonArray("specialFilters") {
                addJsonObject {
                    put("name", "yearFilter")
                    put("yearOperator", filters.firstInstanceOrNull<YearFilterMode>()?.selected ?: "in")
                    put("yearValue", filters.firstInstanceOrNull<YearFilter>()?.year ?: "")
                }
                addJsonObject {
                    put("name", "pagesFilter")
                    putJsonObject("values") {
                        put("min", filters.firstInstanceOrNull<MinPageFilter>()?.page ?: 0)
                        put("max", filters.firstInstanceOrNull<MaxPageFilter>()?.page ?: 2000)
                    }
                }
                addJsonObject {
                    put("name", "checkboxFilter")
                    putJsonObject("values") {
                        put("purpose", "uncensored-filter")
                        put("checked", filters.firstInstanceOrNull<UncensoredFilter>()?.state ?: false)
                    }
                }
            }
            put("sorting", filters.firstInstanceOrNull<SortFilter>()?.selected ?: "views")
        }

        val requestBody = FormBody.Builder().apply {
            add("action", "advanced_search")
            add("subAction", "search_query")
            add("request", json.encodeToString(requestPayload))
            if (offset > 0) add("offset", offset.toString())
        }.build()

        val ajaxHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga-service/advanced-search/")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, requestBody)
    }

    override fun getFilterList() = getFilters()

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<AjaxSearchResponse>(response.body.string())

        if (!result.success) {
            if (result.data.type == "captcha") {
                throw Exception("Solve captcha under Advanced Search in WebView")
            }
            throw Exception(result.data.message)
        }

        offset += result.data.posts?.size ?: 0

        val entries = result.data.posts?.map {
            val element = Jsoup.parseBodyFragment(
                it.replace("\\\"", "\""),
            ).body()

            searchMangaFromElement(element)
        } ?: emptyList()

        val hasNextPage = result.data.more == true

        return MangasPage(entries, hasNextPage)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.left-side a")!!.attr("href"))
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        title = element.selectFirst("div.title")!!.ownText()
    }

    override fun searchMangaSelector() = ""
    override fun searchMangaNextPageSelector() = null

    @Serializable
    data class AjaxSearchResponse(
        val data: SearchData,
        val success: Boolean,
    )

    @Serializable
    data class SearchData(
        val message: String? = null,
        val type: String? = null,

        val posts: List<String>? = emptyList(),
        val more: Boolean? = false,
    )

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("div.manga-title").text()
        document.select("div.manga-summary").let { element ->
            author = element.select("a[href*=/artists/]").text()
            genre = element.select("a[rel=tag]")
                .joinToString { it.text().trim() }
        }
        description = document.select("div.pre-meta:not([id=download]) div")
            .joinToString("\n", postfix = "\n\n") { it.text().trim() }
        description += document.selectFirst("div.desc-itself .text")?.text() ?: ""
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = manga.url.removeSuffix("/") + "/read/"
                    name = "Chapter"
                },
            ),
        )
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".page-container .gallery-item img:not(noscript img)")
            .mapIndexed { index, img ->
                Page(index, "", img.attr("abs:data-src"))
            }
    }

    private fun pagePath(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? =
        filterIsInstance<T>().firstOrNull()

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException("Not Used")
    }
    override fun chapterListSelector(): String {
        throw UnsupportedOperationException("Not Used")
    }
    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not Used")
    }
}
