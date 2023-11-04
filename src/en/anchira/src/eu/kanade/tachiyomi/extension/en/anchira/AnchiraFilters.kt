package eu.kanade.tachiyomi.extension.en.anchira

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UriFilter {
    fun addQueryParameter(url: HttpUrl.Builder)
}

class CheckBoxFilter(name: String) : Filter.CheckBox(name, false)

class CategoryFilter :
    Filter.Group<CheckBoxFilter>(
        "Categories",
        listOf(
            "Manga",
            "Doujinshi",
            "Illustration",
        ).map { CheckBoxFilter(it) },
    ),
    UriFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        var sum = 0

        state.forEach { category ->
            when (category.name) {
                "Manga" -> if (category.state) sum += 1
                "Doujinshi" -> if (category.state) sum += 2
                "Illustration" -> if (category.state) sum += 4
            }
        }

        sum.also {
            if (it > 0) {
                url.addQueryParameter("cat", it.toString())
            }
        }
    }
}

class SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf(
            "Title",
            "Pages",
            "Date Uploaded",
            "Date Published",
            "Popularity",
        ),
        Selection(3, false),
    ),
    UriFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        val sort = when (state?.index) {
            0 -> "1"
            1 -> "2"
            2 -> "4"
            4 -> "32"
            else -> ""
        }

        sort.also {
            if (it.isNotEmpty()) {
                url.addQueryParameter("sort", it)
            }
        }

        if (state?.ascending == true) {
            url.addQueryParameter("order", "1")
        }
    }
}

abstract class TextFilter(
    displayName: String,
    val queryName: String = displayName.lowercase(),
) : Filter.Text(displayName)

class TagFilter : TextFilter("Tags", "tag")
class ArtistFilter : TextFilter("Artist")
class CircleFilter : TextFilter("Circle")
class MagazineFilter : TextFilter("Magazine")
class PagesFilter : TextFilter("Pages")

fun getFilters() = FilterList(
    SortFilter(),
    CategoryFilter(),
    Filter.Header("Separate with commas (,)"),
    Filter.Header("Prepend with dash (-) to exclude"),
    TagFilter(),
    ArtistFilter(),
    CircleFilter(),
    MagazineFilter(),
    PagesFilter(),
)
