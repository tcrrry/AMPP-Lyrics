package io.github.proify.lyricon.amprovider.xposed

import com.apple.android.music.playback.controller.LocalMediaPlayerController
import com.apple.android.music.playback.player.MediaPlayer
import com.apple.android.music.playback.model.PlayerQueueItem
import com.apple.android.music.playback.model.PlayerMediaItem
import org.junit.Assert.*
import org.junit.Test

class AppleMusic700MetadataBootstrapTest {
    private fun resolver(controller: Class<*> = LocalMediaPlayerController::class.java) = AppleMusicHookResolver(
        version = AppleMusicVersion("7.0.0-beta", 1606),
        classLookup = { name -> if (name == LocalMediaPlayerController::class.java.name) controller else Class.forName(name) },
    )

    @Test fun `complete bootstrap chooses exact index and state signatures despite decoys`() {
        val callbacks = resolvePlaybackMetadataCallbacks(resolver())
        assertEquals("onMetadataUpdated", callbacks.metadata.method.name)
        assertEquals("onPlaybackIndexChanged", callbacks.index.method.name)
        assertEquals("onPlaybackStateChanged", callbacks.state.method.name)
        for (callback in listOf(callbacks.index, callbacks.state)) {
            assertArrayEquals(arrayOf(MediaPlayer::class.java, Integer.TYPE, Integer.TYPE), callback.method.parameterTypes)
            assertFalse(callback.compatibilityFallback)
        }
        assertEquals(9, callbacks.state.target.runtimeMemberNames.size)
    }

    @Test fun `missing native index signature rejects bootstrap instead of accepting same name`() {
        assertThrows(NoSuchMethodException::class.java) { resolvePlaybackMetadataCallbacks(resolver(WrongIndex::class.java)) }
    }

    @Test fun `wrong native state player type rejects complete bootstrap`() {
        assertThrows(NoSuchMethodException::class.java) { resolvePlaybackMetadataCallbacks(resolver(WrongState::class.java)) }
    }

    @Test fun `profile playback getters follow the active item after a song changes`() {
        val target = resolvePlaybackMetadataCallbacks(resolver()).state.target
        val first = queue("77", "第一首", 100L)
        val second = queue("88", "第二首", 200L)
        var current = first
        val player = object : MediaPlayer { override fun getCurrentItem() = current }
        fun value(member: AppleMusicRuntimeMember, owner: Any): Any? = AppleReflection.call(owner, target.runtimeMemberName(member))
        fun currentId(): String {
            val item = value(AppleMusicRuntimeMember.PLAYBACK_PLAYER_CURRENT_ITEM_METHOD, player)!!
            val metadata = value(AppleMusicRuntimeMember.PLAYBACK_QUEUE_ITEM_ITEM_METHOD, item)!!
            return value(AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_SUBSCRIPTION_STORE_ID_METHOD, metadata) as String
        }
        assertEquals("77", currentId())
        current = second
        assertEquals("88", currentId())
        assertEquals(200L, value(AppleMusicRuntimeMember.PLAYBACK_QUEUE_ITEM_ID_METHOD, current))
        val item = current.getItem()
        assertEquals("第二首", value(AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_TITLE_METHOD, item))
        assertEquals("艺人", value(AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_ARTIST_NAME_METHOD, item))
        assertEquals("Pop", value(AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_GENRE_NAME_METHOD, item))
        assertEquals(180000L, value(AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_DURATION_METHOD, item))
        assertEquals(88L, value(AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_PERSISTENT_ID_METHOD, item))
    }

    private fun queue(id: String, title: String, queueId: Long) = object : PlayerQueueItem {
        override fun getPlaybackQueueId() = queueId
        override fun getItem() = object : PlayerMediaItem {
            override fun getGenreName() = "Pop"
            override fun getDuration() = 180000L
            override fun getTitle() = title
            override fun getSubscriptionStoreId() = id
            override fun getPersistentId() = id.toLong()
            override fun getArtistName() = "艺人"
        }
    }
    class WrongIndex {
        fun onMetadataUpdated(player: MediaPlayer, item: PlayerQueueItem?) = Unit
        fun onPlaybackIndexChanged(player: MediaPlayer, new: Int) = Unit
        fun onPlaybackStateChanged(player: MediaPlayer, old: Int, new: Int) = Unit
    }
    class WrongState {
        fun onMetadataUpdated(player: MediaPlayer, item: PlayerQueueItem?) = Unit
        fun onPlaybackIndexChanged(player: MediaPlayer, old: Int, new: Int) = Unit
        fun onPlaybackStateChanged(player: Any, old: Int, new: Int) = Unit
    }
}
