package dev.amenhancer.module.lyrics

import dev.amenhancer.module.lyrics.source.AmLyricsIndex
import dev.amenhancer.module.lyrics.source.AmLyricsIndexEntry
import dev.amenhancer.module.lyrics.source.LunabeatCatalog
import dev.amenhancer.module.lyrics.source.LunabeatManifest
import dev.amenhancer.module.lyrics.source.LunabeatSong
import dev.amenhancer.module.model.CustomLyricsEntry
import dev.amenhancer.module.model.CustomLyricsManifest
import dev.amenhancer.module.model.CustomLyricsSources
import dev.amenhancer.module.i18n.ModuleText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CustomLyricsUpdateCoordinatorTest {

    @Test
    fun `single song failure paths use the selected process language`() {
        val originalLocale = Locale.getDefault()
        val old = entry(1, CustomLyricsSources.AUTO_CACHE, wordTtml("old"), "lyrics_old").copy(enabled = true)
        val manifest = CustomLyricsManifest(listOf(old))
        try {
            for (locale in listOf(Locale.ENGLISH, Locale.CHINESE)) {
                Locale.setDefault(locale)
                for (remoteBody in listOf<String?>(null, ttml("line"))) {
                    val result = coordinator(fetchAutoCache = { remoteBody?.let {
                        dev.amenhancer.module.hook.AutoLyricsCandidate(CustomLyricsSources.AMLL, it)
                    } }).update(manifest, { error("no new file") }, { _, _ -> error("no write") },
                        { error("no publish") }, {}, targetIds = setOf(1), requireWordTiming = true,
                    ) as CustomLyricsUpdateResult.Updated
                    val message = if (remoteBody == null) ModuleText.AUTO_CACHE_SOURCE_LOOKUP_FAILED
                        else ModuleText.REMOTE_TTML_NOT_WORD
                    assertEquals(message.text(locale = locale), result.issues.single().message)
                    assertEquals(manifest, result.manifest)
                }
                val missing = coordinator().update(manifest, { error("no file") }, { _, _ -> false },
                    { false }, {}, targetIds = setOf(2)) as CustomLyricsUpdateResult.Failed
                assertEquals(ModuleText.LYRICS_MAPPING_MISSING.text(locale = locale), missing.message)
                val invalidRecovery = CustomLyricsUpdateTransaction({ error("no file") },
                    { _, _ -> error("no write") }, { error("no publish") }, {}).apply(
                    CustomLyricsManifest(listOf(old.copy(source = CustomLyricsSources.MANUAL))),
                    listOf(CustomLyricsUpdateItem.SourceRecovered(1, CustomLyricsSources.MANUAL, CustomLyricsSources.AMLL)),
                ) as CustomLyricsUpdateResult.Failed
                assertEquals(ModuleText.LYRICS_SOURCE_RECOVERY_INVALID.text(locale = locale), invalidRecovery.message)
            }
        } finally { Locale.setDefault(originalLocale) }
    }

    @Test
    fun `selected song check keeps all other mappings and counts only the target`() {
        val requested = mutableListOf<Long>()
        val one = entry(1, CustomLyricsSources.AMLL, ttml("old"), "lyrics_one").copy(enabled = true)
        val other = entry(2, CustomLyricsSources.AM_LYRICS, ttml("other"), "lyrics_other")
        val word = wordTtml("new")
        val result = coordinator(fetchAmll = { requested += it; word },
            loadAmLyricsIndex = { error("must not query other sources") }).update(
            CustomLyricsManifest(listOf(one, other)), { "lyrics_new" }, { _, _ -> true }, { true }, {},
            targetIds = setOf(1), requireWordTiming = true,
        ) as CustomLyricsUpdateResult.Updated
        assertEquals(listOf(1L), requested)
        assertEquals(1, result.checked)
        assertEquals(1, result.updated)
        assertEquals(other, result.manifest.entries[1])
        assertEquals(one.displayName, result.manifest.entries[0].displayName)
    }

    @Test
    fun `legacy source recovery publishes metadata without rewriting identical lyrics`() {
        val body = wordTtml("same")
        val old = entry(1, CustomLyricsSources.AUTO_CACHE, body, "lyrics_old").copy(enabled = true)
        var published = 0
        val result = coordinator(fetchAutoCache = {
            dev.amenhancer.module.hook.AutoLyricsCandidate(CustomLyricsSources.LUNABEAT, body)
        }).update(CustomLyricsManifest(listOf(old)), { error("no new file") },
            { _, _ -> error("no body write") }, { published++; true }, { error("no deletion") },
            targetIds = setOf(1), requireWordTiming = true,
        ) as CustomLyricsUpdateResult.Updated
        assertEquals(1, result.unchanged)
        assertEquals(0, result.updated)
        assertEquals(1, published)
        assertEquals(old.copy(source = CustomLyricsSources.LUNABEAT), result.manifest.entries.single())
    }

    @Test
    fun `legacy changed body recovers origin while invalid or missing candidates keep old lyrics`() {
        val old = entry(1, CustomLyricsSources.AUTO_CACHE, wordTtml("old"), "lyrics_old").copy(enabled = true)
        for (body in listOf(wordTtml("new"), ttml("line"), "not ttml", null)) {
            val result = coordinator(fetchAutoCache = { body?.let {
                dev.amenhancer.module.hook.AutoLyricsCandidate(CustomLyricsSources.AMLL, it)
            } }).update(CustomLyricsManifest(listOf(old)), { "lyrics_new" }, { _, _ -> true }, { true }, {},
                targetIds = setOf(1), requireWordTiming = true) as CustomLyricsUpdateResult.Updated
            if (body == wordTtml("new")) {
                assertEquals(1, result.updated)
                assertEquals(CustomLyricsSources.AMLL, result.manifest.entries.single().source)
            } else {
                assertEquals(1, result.failed)
                assertEquals(old, result.manifest.entries.single())
            }
        }
    }

    @Test
    fun `automatic checks skip manual and disabled entries and reject non Word updates`() {
        for (old in listOf(
            entry(1, CustomLyricsSources.MANUAL, ttml("old"), "lyrics_old").copy(enabled = true),
            entry(1, CustomLyricsSources.AMLL, ttml("old"), "lyrics_old"),
            entry(1, CustomLyricsSources.AMLL, ttml("old"), "lyrics_old").copy(enabled = true),
        )) {
            var calls = 0
            val result = coordinator(fetchAmll = { calls++; ttml("new") }).update(
                CustomLyricsManifest(listOf(old)), { error("no write") }, { _, _ -> error("no write") },
                { error("no publish") }, {}, targetIds = setOf(1), requireWordTiming = true,
            ) as CustomLyricsUpdateResult.Updated
            assertEquals(old, result.manifest.entries.single())
            if (old.source == CustomLyricsSources.AMLL && old.enabled) {
                assertEquals(1, calls); assertEquals(1, result.failed)
            } else { assertEquals(0, calls); assertEquals(1, result.skipped) }
        }
    }

    @Test
    fun `amll canonical conversion recognizes unchanged and changed hashes`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
            <head><metadata/></head><body><div>
            <p begin="5s" end="6s" itunes:key="late"><span begin="5s" end="6s">late</span><span ttm:role="x-translation">Later</span></p>
            <p begin="1s" end="2s" itunes:key="one"><span begin="1s" end="2s">one</span><span ttm:role="x-translation">One</span></p>
            <p begin="00:01.000" end="3s" itunes:key="two"><span begin="00:01.000" end="3s">two</span><span ttm:role="x-translation">Two</span></p>
            </div></body></tt>
        """.trimIndent()
        val converted = AmllTtmlFormatConverter.toAppleFormat(raw).ttml
        assertTrue(converted.contains("<text for=\"one\">One\nTwo</text><text for=\"two\"> </text>"))
        val same = entry(7L, CustomLyricsSources.AMLL, converted, "lyrics_same")
        val changed = entry(8L, CustomLyricsSources.AMLL, ttml("old"), "lyrics_changed")
        val writes = AtomicInteger()
        val result = coordinator(
            fetchAmll = { raw },
        ).update(
            oldManifest = CustomLyricsManifest(listOf(same, changed)),
            fileIdFactory = object : () -> String {
                var index = 0
                override fun invoke() = "lyrics_new_${index++}"
            },
            writeRemoteFile = { _, _ -> writes.incrementAndGet(); true },
            publishManifest = { true },
            deleteRemoteFile = {},
        ) as CustomLyricsUpdateResult.Updated

        assertEquals(2, result.checked)
        assertEquals(1, result.unchanged)
        assertEquals(1, result.updated)
        assertEquals(1, writes.get())
    }

    @Test
    fun `am lyrics index fast path avoids body request`() {
        val bytes = ttml("same").toByteArray()
        val local = entry(42L, CustomLyricsSources.AM_LYRICS, ttml("same"), "lyrics_am")
        val bodyFetches = AtomicInteger()
        val remote = AmLyricsIndexEntry(
            appleMusicId = 42L,
            alternateIds = listOf(420L),
            displayName = "Remote",
            path = "am-lyrics/a.ttml",
            enabled = true,
            sizeBytes = bytes.size.toLong(),
            sha256 = CustomLyricsFilePolicy.sha256(bytes),
        )
        val result = coordinator(
            loadAmLyricsIndex = { AmLyricsIndex(listOf(remote)) },
            fetchAmLyrics = { bodyFetches.incrementAndGet(); ttml("new") },
        ).update(
            oldManifest = CustomLyricsManifest(listOf(local)),
            fileIdFactory = { "lyrics_new" },
            writeRemoteFile = { _, _ -> true },
            publishManifest = { true },
            deleteRemoteFile = {},
        ) as CustomLyricsUpdateResult.Updated

        assertEquals(1, result.unchanged)
        assertEquals(0, result.updated)
        assertEquals(0, bodyFetches.get())
    }

    @Test
    fun `lunabeat catalog hash fast path and path deduplication`() {
        val body = ttml("new")
        val remoteSong = LunabeatSong(
            title = "Song",
            artists = listOf("Artist"),
            album = "Album",
            appleMusicIds = listOf(1L, 2L),
            path = "lyrics/shared.ttml",
            sha256 = CustomLyricsFilePolicy.sha256(body.toByteArray()),
        )
        val same = entry(1L, CustomLyricsSources.LUNABEAT, body, "lyrics_same")
        val changed = entry(2L, CustomLyricsSources.LUNABEAT, ttml("old"), "lyrics_changed")
        val fetches = AtomicInteger()
        val result = coordinator(
            loadLunabeatCatalog = {
                LunabeatCatalog(
                    manifest = LunabeatManifest(2, "r1", "songs.json"),
                    songs = listOf(remoteSong),
                )
            },
            fetchLunabeat = { fetches.incrementAndGet(); body },
        ).update(
            oldManifest = CustomLyricsManifest(listOf(same, changed)),
            fileIdFactory = object : () -> String {
                var index = 0
                override fun invoke() = "lyrics_new_${index++}"
            },
            writeRemoteFile = { _, _ -> true },
            publishManifest = { true },
            deleteRemoteFile = {},
        ) as CustomLyricsUpdateResult.Updated

        assertEquals(1, result.unchanged)
        assertEquals(1, result.updated)
        assertEquals(1, fetches.get())
    }

    @Test
    fun `manual and auto cache are skipped and source failures are fail open`() {
        val manual = entry(1L, CustomLyricsSources.MANUAL, ttml("manual"), "lyrics_manual")
        val auto = entry(2L, CustomLyricsSources.AUTO_CACHE, ttml("auto"), "lyrics_auto")
        val remote = entry(3L, CustomLyricsSources.AMLL, ttml("old"), "lyrics_amll")
        val result = coordinator(fetchAmll = { null }).update(
            oldManifest = CustomLyricsManifest(listOf(manual, auto, remote)),
            fileIdFactory = { "lyrics_new" },
            writeRemoteFile = { _, _ -> error("must not write") },
            publishManifest = { error("must not publish") },
            deleteRemoteFile = {},
        ) as CustomLyricsUpdateResult.Updated

        assertEquals(2, result.skipped)
        assertEquals(1, result.failed)
        assertTrue(result.issues.any { it.appleMusicId == 3L })
    }

    private fun coordinator(
        fetchAmll: (Long) -> String? = { null },
        loadAmLyricsIndex: () -> AmLyricsIndex? = { null },
        fetchAmLyrics: (AmLyricsIndexEntry) -> String? = { null },
        loadLunabeatCatalog: () -> LunabeatCatalog? = { null },
        fetchLunabeat: (LunabeatSong) -> String? = { null },
        fetchAutoCache: ((Long) -> dev.amenhancer.module.hook.AutoLyricsCandidate?)? = null,
    ) = CustomLyricsUpdateCoordinator(
        CustomLyricsUpdateSources(
            fetchAmll = fetchAmll,
            loadAmLyricsIndex = loadAmLyricsIndex,
            fetchAmLyricsTtml = fetchAmLyrics,
            loadLunabeatCatalog = loadLunabeatCatalog,
            fetchLunabeatTtml = fetchLunabeat,
            fetchAutoCache = fetchAutoCache,
        ),
    )

    private fun entry(id: Long, source: String, ttml: String, fileId: String) =
        CustomLyricsEntry(
            appleMusicId = id,
            displayName = "Local $id",
            fileId = fileId,
            sizeBytes = ttml.toByteArray().size.toLong(),
            sha256 = CustomLyricsFilePolicy.sha256(ttml.toByteArray()),
            source = source,
            enabled = false,
        )

    private fun ttml(text: String): String =
        "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p>$text</p></div></body></tt>"

    private fun wordTtml(text: String): String =
        "<tt xmlns:itunes=\"urn\" itunes:timing=\"Word\"><body><p><span begin=\"0s\" end=\"1s\">$text</span></p></body></tt>"
}
