package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackAlbumMetadataTest {
    // Apple Music 6.5.3 PlaybackItem/BasePlaybackItem expose this getter,
    // not getAlbumName/getAlbumTitle/getAlbum/getAttributes.
    open class NativePlaybackItem(private val album: String?) {
        fun getCollectionName(): String? = album
    }
    class Song(album: String?) : NativePlaybackItem(album)
    class LegacyPlaybackItem {
        fun getAlbumName(): String = "Legacy album"
    }
    class BothGetters {
        fun getCollectionName(): String = "Playback album"
        fun getAlbumName(): String = "Legacy album"
    }
    class EmptyNativeWithFallback {
        fun getCollectionName(): String = "  "
        fun getAlbumName(): String = "  Fallback album  "
    }
    class Album {
        fun getTitle(): String = "Nested album"
    }
    class NestedAlbumItem {
        fun getAlbum(): Album = Album()
    }

    @Test fun actualPlaybackGetterProvidesAlbumAndTrimsWhitespace() {
        assertEquals("Live album", itemAlbum(NativePlaybackItem("  Live album  ")))
    }

    @Test fun inheritedPlaybackGetterProvidesAlbum() {
        assertEquals("Live album", itemAlbum(Song("Live album")))
    }

    @Test fun verifiedNativeGetterTakesPriorityOverLegacyNames() {
        assertEquals("Playback album", itemAlbum(BothGetters()))
    }

    @Test fun blankNativeAlbumKeepsLegacyFallbacks() {
        assertEquals("Fallback album", itemAlbum(EmptyNativeWithFallback()))
        assertEquals("Legacy album", itemAlbum(LegacyPlaybackItem()))
        assertEquals("Nested album", itemAlbum(NestedAlbumItem()))
    }

    @Test fun absentOrEmptyAlbumRemainsMissingInsteadOfInventingMetadata() {
        assertNull(itemAlbum(NativePlaybackItem(null)))
        assertNull(itemAlbum(NativePlaybackItem("  ")))
        assertNull(itemAlbum(Any()))
    }
}
