package com.apple.android.music.playback.player

import com.apple.android.music.playback.model.PlayerQueueItem

interface MediaPlayer { fun getCurrentItem(): PlayerQueueItem? }
