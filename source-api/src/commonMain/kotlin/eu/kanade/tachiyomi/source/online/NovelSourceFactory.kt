package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NovelSourceFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf()
}
