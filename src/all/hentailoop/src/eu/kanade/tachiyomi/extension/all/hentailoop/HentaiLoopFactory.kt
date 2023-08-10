package eu.kanade.tachiyomi.extension.all.hentailoop

import eu.kanade.tachiyomi.source.SourceFactory

class HentaiLoopFactory : SourceFactory {
    override fun createSources() = listOf(
        HentaiLoop("all", "/manga/", null),
        HentaiLoop("en", "/languages/english/", "6"),
        HentaiLoop("zh", "/languages/chinese/", "7988"),
        HentaiLoop("ja", "/languages/japanese/", "1682"),
        HentaiLoop("ko", "/languages/korean/", "8142"),
        HentaiLoop("fr", "/languages/french/", "11015"),
        HentaiLoop("id", "/languages/indonesian/", "29166"),
        HentaiLoop("pt", "/languages/portuguese/", "15636"),
        HentaiLoop("ru", "/languages/russian/", "16405"),
        HentaiLoop("es", "/languages/spanish/", "7970"),
        HentaiLoop("th", "/languages/thai/", "8230"),
        HentaiLoop("tr", "/languages/turkish/", "22635"),
    )
}
