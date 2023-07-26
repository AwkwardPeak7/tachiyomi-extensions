package eu.kanade.tachiyomi.extension.en.hentaiforce

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.injectLazy

class HentaiForce : ParsedHttpSource() {

    override val name = "HentaiForce"

    override val baseUrl = "https://hentaiforce.net"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl + if (page == 1) "" else "/page/$page"

        return GET(url, headers)
    }

    override fun popularMangaSelector() = ".listing-galleries-container > .gallery-wrapper"
    override fun popularMangaNextPageSelector() = ".page-item a[rel=next]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a.gallery-thumb").let {
            setUrlWithoutDomain(it.attr("href"))
            thumbnail_url = it.select("img").imgAttr()
        }
        title = element.select(".gallery-name").text()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/search?q=${query.trim()}&page=$page")

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("#gallery-main-info h1").text()
        thumbnail_url = document.select("#gallery-main-cover img").imgAttr()
        genre = document.selectMetaData("tag")
        author = document.selectMetaData("artists")
        artist = author
        description = buildDescription(document)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = "${manga.url}/1"
                    name = "Chapter"
                },
            ),
        )
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(readerPages)")?.html()
            ?: return emptyList()

        val b64 = script.substringAfter("atob(\"")
            .substringBefore("\")")

        val rawJson = Base64.decode(b64, Base64.DEFAULT).let(::String)

        val hentaiForcePages = json.decodeFromString<HentaiForcePages>(rawJson)

        return hentaiForcePages.pages.keys.mapIndexed { idx, key ->
            Page(
                index = idx,
                imageUrl = hentaiForcePages.imgUrl
                    .replace("%c", hentaiForcePages.pages[key]!!.location)
                    .replace("%s", hentaiForcePages.pages[key]!!.fileName),
            )
        }
    }

    @Serializable
    data class HentaiForcePages(
        @SerialName("baseUriImg")
        val imgUrl: String,
        val pages: Map<String, HentaiForcePage>,
    )

    @Serializable
    data class HentaiForcePage(
        @SerialName("l") val location: String,
        @SerialName("f") val fileName: String,
    )

    private fun Elements.imgAttr(): String {
        return when {
            hasAttr("data-src") -> this.attr("abs:data-src")
            else -> this.attr("abs:src")
        }
    }

    private fun buildDescription(document: Document): String {
        val parodies = document.selectMetaData("parodies")
        val characters = document.selectMetaData("characters")
        val groups = document.selectMetaData("groups")
        val language = document.selectMetaData("language")
        val category = document.selectMetaData("category")
        val pages = document.select(".tag-container:contains(pages)")
            .text().substringAfter(":").trim()

        return buildString {
            if (!parodies.isNullOrEmpty()) {
                append("Parodies: $parodies\n")
            }
            if (!characters.isNullOrEmpty()) {
                append("Characters: $characters\n")
            }
            if (!groups.isNullOrEmpty()) {
                append("Groups: $groups\n")
            }
            if (!language.isNullOrEmpty()) {
                append("Languages: $language\n")
            }
            if (!category.isNullOrEmpty()) {
                append("Category: $category\n")
            }
            if (pages.isNotEmpty()) {
                append("Length: $pages pages\n")
            }
        }
    }

    private fun Document.selectMetaData(contains: String): String? {
        return this.selectFirst(".tag-container:contains($contains)")
            ?.select("a")?.joinToString { it.ownText() }
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not Used")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not Used")
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not Used")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not Used")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not Used")
    override fun chapterListSelector() = throw UnsupportedOperationException("Not Used")
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not Used")
}
