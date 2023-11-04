package eu.kanade.tachiyomi.extension.en.anchira

import android.app.Application
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPack
import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPackConfiguration
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromByteArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Anchira : HttpSource(), ConfigurableSource {

    override val name = "Anchira"

    override val lang = "en"

    override val baseUrl = "https://anchira.to"

    private val apiUrl = "$baseUrl/api/v1/library"

    override val supportsLatest = true

    private val msgPack by lazy {
        MsgPack(
            MsgPackConfiguration(
                ignoreUnknownKeys = true,
            ),
        )
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 1, 2)
        .rateLimitHost(cdnUrl.toHttpUrl(), 3, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val apiHeaders by lazy {
        headersBuilder()
            .set("Accept", "*/*")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    private val preference by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl?sort=32&page=$page", apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.decodeAs<BrowseResponse>()

        val entries = result.entries.orEmpty().map(BrowseEntry::toSManga)

        return MangasPage(entries, result.hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl?page=$page", apiHeaders)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", buildAdvQuery(query, filters))

            filters.filterIsInstance<UriFilter>().forEach {
                it.addQueryParameter(this)
            }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, apiHeaders)
    }

    private fun buildAdvQuery(query: String, filterList: FilterList): String {
        val title = if (query.isNotBlank()) "title:\"$query\" " else ""

        val filters: List<String> = filterList.filterIsInstance<TextFilter>().map { filter ->
            if (filter.state.isEmpty()) return@map ""

            val included = mutableListOf<String>()
            val excluded = mutableListOf<String>()
            val name = filter.queryName

            filter.state.split(",")
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { entry ->
                    if (entry.startsWith("-")) {
                        excluded.add(entry.removePrefix("-"))
                    } else {
                        included.add(entry)
                    }
                }

            buildString {
                included.onEach {
                    append(name, ":\"", it, "\" ")
                }
                excluded.onEach {
                    append("-", name, ":\"", it, "\" ")
                }
            }
        }

        return "$title${
        filters.filterNot(String::isEmpty).joinToString(" ", transform = String::trim)
        }"
    }

    override fun getFilterList() = getFilters()

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl/${manga.url}", apiHeaders)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/g/${manga.url}"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.decodeAs<DetailsResponse>().toSManga()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapter = response.decodeAs<ChapterResponse>().toSChapter()

        return listOf(chapter)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/g/${chapter.url}"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$apiUrl/${chapter.url}", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.decodeAs<PageResponse>()

        return result.data.mapIndexed { index, img ->
            Page(
                index,
                "$baseUrl/g/${result.id}/${result.key}/${index + 1}",
                "$cdnUrl/${result.id}/${result.key}/${result.hash}/b/${img.file}",
            )
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", page.url)
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()

        val request = super.imageRequest(page)

        return request.newBuilder().apply {
            headers(imageHeaders)
            if (preference.getBoolean(pageOrigPrefKey, pageOrigPrefDefault)) {
                url(
                    request.url.toString().replace("/b/", "/a/"),
                )
            }
        }.build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = pageOrigPrefKey
            title = pageOrigPrefTitle
            setDefaultValue(pageOrigPrefDefault)
        }.also(screen::addPreference)
    }

    private inline fun <reified T> Response.decodeAs(): T {
        val bytes = use { it.body.bytes() }

        val padSize = bytes.size / 2
        val pad = bytes.copyOfRange(0, padSize)
        val data = bytes.copyOfRange(padSize, bytes.size)

        for (i in 0 until padSize) {
            data[i] = (data[i].toInt() xor pad[i].toInt()).toByte()
        }

        return msgPack.decodeFromByteArray(data)
    }

    companion object {
        private const val pageOrigPrefKey = "pref_use_original_images"
        private const val pageOrigPrefTitle = "Use Original quality Images"
        private const val pageOrigPrefDefault = false

        const val cdnUrl = "https://kisakisexo.xyz"
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
