package dev.amenhancer.module.lyrics
import dev.amenhancer.module.lyrics.source.AmLyricsClient
import dev.amenhancer.module.lyrics.source.AmllTtmlClient
import dev.amenhancer.module.lyrics.source.FileLunabeatCatalogCache
import dev.amenhancer.module.lyrics.source.HttpLyricTransport
import dev.amenhancer.module.lyrics.source.LunabeatClient
import java.io.File
internal fun createEmbeddedOnlineLyricsImporter(application: android.app.Application): CustomLyricsOnlineImporter = CustomLyricsOnlineImporter(
        fetchAmll = AmllTtmlClient(HttpLyricTransport())::fetch,
        fetchAmLyrics = AmLyricsClient(HttpLyricTransport())::fetch,
        fetchLunabeat = LunabeatClient(
            indexTransport = HttpLyricTransport(maxResponseBytes = LunabeatClient.INDEX_MAX_BYTES),
            lyricsTransport = HttpLyricTransport(),
            cache = FileLunabeatCatalogCache(File(application.filesDir, "ampp-lunabeat-cache")),
        )::fetch,
    )

