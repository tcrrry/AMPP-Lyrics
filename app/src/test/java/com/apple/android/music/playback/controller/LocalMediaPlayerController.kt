package com.apple.android.music.playback.controller

import com.apple.android.music.playback.player.MediaPlayer
import com.apple.android.music.playback.model.PlayerQueueItem

/** Exact 1606 signatures plus same-name decoys that must never be selected. */
class LocalMediaPlayerController {
    fun onMetadataUpdated(player: MediaPlayer, item: PlayerQueueItem?) = Unit
    fun onPlaybackIndexChanged(player: MediaPlayer, old: Int, new: Int) = Unit
    fun onPlaybackIndexChanged(player: MediaPlayer, new: Int) = Unit
    fun onPlaybackStateChanged(player: MediaPlayer, old: Int, new: Int) = Unit
    fun onPlaybackStateChanged(player: Any, old: Int, new: Int) = Unit
}
