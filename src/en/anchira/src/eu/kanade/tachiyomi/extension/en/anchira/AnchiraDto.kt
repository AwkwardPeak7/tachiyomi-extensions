package eu.kanade.tachiyomi.extension.en.anchira

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrowseResponse(
    val entries: List<BrowseEntry>? = emptyList(),
    val limit: Int,
    val page: Int,
    val total: Int,
) {
    val hasNextPage get() = (limit * page) < total
}

@Serializable
data class BrowseEntry(
    val id: Int,
    val key: String,
    val title: String,
    val cover: ImageFile,
    val pages: Int,
    val tags: List<Tag>,

) {
    fun toSManga() = SManga.create().apply {
        title = this@BrowseEntry.title
        url = "$id/$key"
        thumbnail_url = "${Anchira.cdnUrl}/$id/$key/m/${cover.file}"
        artist = tags.filter { it.namespace == 1 }.joinToString { it.name.trim() }
        author = tags.filter { it.namespace == 2 }.let { circle ->
            if (circle.isEmpty()) {
                artist
            } else {
                circle.joinToString { it.name.trim() }
            }
        }
        description = buildString {
            tags.filter { it.namespace == 4 }.also { magazine ->
                if (magazine.isNotEmpty()) {
                    append("Magazine: ", magazine.joinToString { it.name.trim() }, "\n")
                }
            }
            append("Length: ", pages, " pages\n")
        }
        genre = tags.filter { it.namespace == null }.joinToString { it.name.trim() }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }
}

@Serializable
data class DetailsResponse(
    val id: Int,
    val key: String,
    val filename: String,
    val title: String,
    @SerialName("thumb_index") val thumbIndex: Int,
    val size: Long,
    @SerialName("size_resampled") val sizeResampled: Long,
    val pages: Int,
    val tags: List<Tag>,
    val data: List<ImageFile>,
) {
    fun toSManga() = SManga.create().apply {
        title = this@DetailsResponse.title
        url = "$id/$key"
        thumbnail_url = "${Anchira.cdnUrl}/$id/$key/m/${data[thumbIndex].file}"
        artist = tags.filter { it.namespace == 1 }.joinToString { it.name.trim() }
        author = tags.filter { it.namespace == 2 }.let { circle ->
            if (circle.isEmpty()) {
                artist
            } else {
                circle.joinToString { it.name.trim() }
            }
        }
        description = buildString {
            append("File Name: ", filename, "\n")
            tags.filter { it.namespace == 4 }.also { magazine ->
                if (magazine.isNotEmpty()) {
                    append("Magazine: ", magazine.joinToString { it.name.trim() })
                }
            }
            append("Size: ", size / 1024 / 1024, "MiB", "/ ", sizeResampled / 1024 / 1024, "MiB", "\n")
            append("Length: ", pages, " pages\n")
        }
        genre = tags.filter { it.namespace == null }.joinToString { it.name.trim() }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }
}

@Serializable
data class ChapterResponse(
    val id: Int,
    val key: String,
    val upload: User? = null,
    @SerialName("published_at") val published: Long,
) {
    fun toSChapter() = SChapter.create().apply {
        name = "Chapter"
        url = "$id/$key"
        date_upload = published * 1000
        scanlator = upload?.username
    }
}

@Serializable
data class User(
    val username: String? = null,
)

@Serializable
data class Tag(
    val name: String,
    val namespace: Int? = null,
)

@Serializable
data class PageResponse(
    val id: Int,
    val key: String,
    val hash: String,
    val data: List<ImageFile>,
)

@Serializable
data class ImageFile(
    @SerialName("n") val file: String,
)
