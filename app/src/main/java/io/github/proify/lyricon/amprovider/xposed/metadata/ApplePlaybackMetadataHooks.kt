/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import io.github.proify.lyricon.amprovider.xposed.hooks.ApplePlaybackHooks

internal data class PlaybackMetadataCallbacks(
    val metadata: ResolvedAppleMusicHookMethod,
    val index: ResolvedAppleMusicHookMethod,
    val state: ResolvedAppleMusicHookMethod,
)

/** Resolve the complete bootstrap before registering any playback callback. */
internal fun resolvePlaybackMetadataCallbacks(resolver: AppleMusicHookResolver): PlaybackMetadataCallbacks {
    val metadata = resolver.resolveMethod(AppleMusicHookPoint.LOCAL_MEDIA_PLAYER_METADATA_UPDATED)
    val index = resolver.resolveMethod(AppleMusicHookPoint.LOCAL_MEDIA_PLAYER_INDEX_CHANGED)
    val state = resolver.resolveMethod(AppleMusicHookPoint.LOCAL_MEDIA_PLAYER_CONTROLLER_STATE)
    for (member in listOf(AppleMusicRuntimeMember.PLAYBACK_PLAYER_CURRENT_ITEM_METHOD,
        AppleMusicRuntimeMember.PLAYBACK_QUEUE_ITEM_ITEM_METHOD, AppleMusicRuntimeMember.PLAYBACK_QUEUE_ITEM_ID_METHOD,
        AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_SUBSCRIPTION_STORE_ID_METHOD,
        AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_PERSISTENT_ID_METHOD, AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_TITLE_METHOD,
        AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_ARTIST_NAME_METHOD, AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_GENRE_NAME_METHOD,
        AppleMusicRuntimeMember.PLAYBACK_MEDIA_ITEM_DURATION_METHOD)) state.target.runtimeMemberName(member)
    return PlaybackMetadataCallbacks(metadata, index, state)
}

/** Installs LocalMediaPlayerController callbacks that feed playback metadata resolution. */
internal class ApplePlaybackMetadataHooks(
    private val runtime: AppleMusicProviderRuntime,
    private val playbackHooks: () -> ApplePlaybackHooks,
    private val metadataCoordinator: ApplePlaybackMetadataCoordinator,
) {
    fun installHooks() {
        val callbacks = resolvePlaybackMetadataCallbacks(runtime.hookResolver)
        val metadataUpdated = callbacks.metadata
        runtime.hookRegistrar.installHook(metadataUpdated.method, after = { chain, _ ->
            val mediaPlayer = chain.args.firstOrNull()
            val activePlayer = playbackHooks().activePlayer()
            // AM++ does not bring in HLE's optional remote-player module. The
            // first LocalMediaPlayer callback is therefore the authoritative
            // player identity; without this fallback every callback was
            // rejected because the lightweight playback seam started empty.
            if (activePlayer == null && mediaPlayer != null) {
                playbackHooks().attachActivePlayer(mediaPlayer)
            }
            val effectiveActivePlayer = playbackHooks().activePlayer() ?: mediaPlayer
            if (!isActivePlaybackCallback(mediaPlayer, effectiveActivePlayer)) {
                ProviderLogger.debug(
                    "忽略非活动播放器的歌曲元数据：source=onMetadataUpdated, " +
                        "callback=${mediaPlayer?.let(System::identityHashCode)}, " +
                        "active=${effectiveActivePlayer?.let(System::identityHashCode)}"
                )
                return@installHook
            }
            val callbackPlayer = mediaPlayer ?: return@installHook
            val changedItem = chain.args.getOrNull(1)
            val currentItem = runCatching {
                metadataCoordinator.currentQueueItem(callbackPlayer)
            }.getOrNull()
            val publishAsCurrent = metadataCoordinator.isCurrentQueueItem(
                changedItem,
                currentItem,
            )
            val refreshPlaybackMetadata = if (publishAsCurrent) {
                val controllerInstance = chain.thisObject
                {
                    runCatching {
                        metadataUpdated.method.invoke(
                            controllerInstance,
                            callbackPlayer,
                            changedItem,
                        )
                    }.onFailure {
                        ProviderLogger.error("Apple 播放元数据覆盖刷新失败", it)
                    }
                    Unit
                }
            } else {
                null
            }
            metadataCoordinator.handleQueueItem(
                queueItem = changedItem,
                source = "onMetadataUpdated",
                publishAsCurrent = publishAsCurrent,
                refreshPlaybackMetadata = refreshPlaybackMetadata,
            )
        })

        val indexChanged = callbacks.index
        runtime.hookRegistrar.installHook(indexChanged.method, after = { chain, _ ->
            chain.args.firstOrNull()?.let { playbackHooks().attachActivePlayer(it) }
            metadataCoordinator.refreshCurrentQueueItemIfActive(
                chain.args.firstOrNull(),
                "onPlaybackIndexChanged",
            )
        })
        // Restored playback may publish a state change without replacing the queue item.
        // Seed the lightweight active-player seam and resolve the current item in that case.
        runtime.hookRegistrar.installHook(callbacks.state.method, after = { chain, _ ->
            val player = chain.args.firstOrNull()
            if (playbackHooks().activePlayer() == null && player != null) playbackHooks().attachActivePlayer(player)
            metadataCoordinator.refreshCurrentQueueItemIfActive(player, "onPlaybackStateChanged")
        })
        ProviderLogger.info(
            "Apple 播放元数据 Hook 已安装: " +
                "metadata=${metadataUpdated.target.className}#" +
                "${metadataUpdated.target.methodName}, " +
                "index=${indexChanged.target.className}#${indexChanged.target.methodName}, " +
                "fallback=${metadataUpdated.compatibilityFallback || indexChanged.compatibilityFallback}"
        )
    }
}
