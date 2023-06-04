package eu.kanade.tachiyomi.extension.en.koushoku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

val filters = FilterList(
    SortFilter(
        "Sort",
        arrayOf(
            Sortable("Uploaded Date", "4"),
            Sortable("Published Date", "16"),
            Sortable("Title", "1"),
            Sortable("Pages", "2"),
            Sortable("Popularity", "32"),
        ),
    ),
    TitleFilter(),
    TagFilter(),
    ArtistFilter(),
    CircleFilter(),
    ParodyFilter(),
    MagazineFilter(),
    PageFilter(),
)

class SortFilter(
    displayName: String,
    private val sortable: Array<Sortable>,
) : Filter.Sort(
    displayName,
    sortable.map(Sortable::title).toTypedArray(),
    Selection(0, false),
) {
    fun addQueryParameter(url: HttpUrl.Builder) {
        if (state != null) {
            val sort = sortable[state!!.index].value
            val order = when (state!!.ascending) {
                true -> "1"
                false -> "2"
            }

            // avoid 302 response
            if (state!!.index != 0) {
                url.addQueryParameter("sort", sort)
            }
            if (state!!.ascending) {
                url.addQueryParameter("order", order)
            }
        }
    }
}

data class Sortable(
    val title: String,
    val value: String,
) {
    override fun toString(): String = title
}

class TitleFilter : Filter.Group<TextFilter>(
    "Title",
    listOf(
        TextFilter("Title", "title"),
    ),
) {
    fun addQueryParameter(url: HttpUrl.Builder) = state.first().addQueryParameter(url)
}
class TagFilter : TextModeFilter("Tags", "t", "tc")
class ArtistFilter : TextModeFilter("Artist", "a", "ac")
class CircleFilter : TextModeFilter("Circle", "c", "gc")
class ParodyFilter : TextModeFilter("Parody", "p", "pc")
class MagazineFilter : TextModeFilter("Magazine", "m", "mc")
class PageFilter : Filter.Group<TextFilter>(
    "Pages",
    listOf(
        TextFilter("Min Pages", "ps"),
        TextFilter("Max Pages", "pe"),
    ),
) {
    fun addQueryParameter(url: HttpUrl.Builder) {
        state.first().addQueryParameter(url)
        state.last().addQueryParameter(url)
    }
}

open class TextFilter(
    name: String,
    private val urlParm: String,
) : Filter.Text(name) {
    fun addQueryParameter(url: HttpUrl.Builder) {
        if (state.isNotBlank()) {
            url.addQueryParameter(urlParm, state)
        }
    }
}

class TextBox(name: String) : Filter.Text(name)

class ConditionFilter : Filter.Select<String>("Mode", arrayOf("OR", "AND"))

open class TextModeFilter(
    name: String,
    private val urlParm: String,
    private val andParm: String,
    text: TextBox = TextBox(name),
    mode: ConditionFilter = ConditionFilter(),
) : Filter.Group<Any>(
    name,
    listOf(
        text,
        mode,
        Header("Separate with commas (,)"),
        Header("Prepend with dash (-) to exclude"),
    ),
) {
    fun addQueryParameter(url: HttpUrl.Builder) {
        val textState = (state.first() as TextBox).state
        if (textState.isNotBlank()) {
            url.addQueryParameter(urlParm, textState.trim())
            if ((state[1] as ConditionFilter).state == 1) {
                url.addQueryParameter(andParm, "1")
            }
        }
    }
}
