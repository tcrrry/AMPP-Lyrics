package dev.amenhancer.module.hook

import android.os.SystemClock
import com.juren233.hyperlyricsenhanced.BuildConfig
import io.github.proify.lyricon.amprovider.xposed.*
import io.github.proify.lyricon.amprovider.xposed.hooks.AppleFrameworkMetadataHooks
import java.util.concurrent.atomic.AtomicLong

/**
 * Wires the complete HLE metadata surface without bringing HLE's lyric
 * provider lifecycle into AM++. The surface modules are the original HLE
 * implementations; this class only supplies the host callbacks that connect
 * them to the transplanted resolver, cache and AM++ process lifecycle.
 */
internal class HleMetadataSurfaceBridge(
    internal val runtime: AppleMusicProviderRuntime,
    internal val catalogResolver: AppleInternalCatalogResolver,
    internal val metadataStore: AppleMetadataOverrideStore,
    internal val playbackCoordinator: ApplePlaybackMetadataCoordinator,
    internal val playbackHooks: io.github.proify.lyricon.amprovider.xposed.hooks.ApplePlaybackHooks,
    internal val frameworkHooks: AppleFrameworkMetadataHooks,
    internal val contentItemHooks: AppleContentItemMetadataHooks,
    internal val queueMetadataHooks: AppleQueueMetadataHooks,
    internal val actionSheetMetadataHooks: AppleActionSheetMetadataHooks,
    internal val configuredContentUiLanguage: Int,
    internal val restoreOriginalMetadata: Boolean,
    internal val profileId: String,
) {
    internal val traceSequence = AtomicLong(0L)
    internal val registry = AppleInAppMetadataRegistry()
    internal lateinit var refreshQueue: AppleInAppMetadataRefreshQueue

    internal lateinit var surfaceRuntime: AppleMetadataSurfaceRuntime
    internal lateinit var librarySurfaceHooks: AppleLibrarySurfaceHooks
    internal lateinit var dataBindingHooks: AppleDataBindingMetadataHooks
    internal lateinit var collectionSurfaceHooks: AppleCollectionSurfaceHooks
    internal lateinit var artistSurfaceHooks: AppleArtistSurfaceHooks
    internal lateinit var listenNowHooks: AppleListenNowHooks
    internal lateinit var mediaApiMetadataCoordinator: AppleMediaApiMetadataCoordinator
    internal lateinit var resolutionCoordinator: AppleInAppMetadataResolutionCoordinator
    internal lateinit var metadataApplier: AppleInAppMetadataApplier
    internal lateinit var metadataRegistrationCoordinator: AppleInAppMetadataRegistrationCoordinator
    internal lateinit var metadataOverrideApplicationCoordinator:
        AppleMetadataOverrideApplicationCoordinator
    internal lateinit var media3MetadataCoordinator: AppleMedia3MetadataCoordinator
    internal lateinit var playbackItemConversionHooks: ApplePlaybackItemConversionHooks
    internal lateinit var inAppArtworkContinuityHooks: AppleInAppArtworkContinuityHooks
    internal lateinit var visibleMetadataDiagnostics: AppleVisibleMetadataDiagnostics

    fun install() {
        MediaMetadataCache.setProfile(profileId)
        refreshQueue = AppleInAppMetadataRefreshQueue(
            postToMain = { callback -> runtime.mainHandler.post { callback() } },
            diagnostics = if (BuildConfig.DEBUG) {
                { stats ->
                    ProviderLogger.diagnostic(
                        "HLE metadata refresh frame: enqueued=${stats.enqueued}, " +
                            "merged=${stats.merged}, executed=${stats.executed}, " +
                            "failed=${stats.failed}, maxDepth=${stats.maxDepth}, " +
                            "durationMs=${stats.durationNanos / 1_000_000.0}",
                    )
                }
            } else {
                null
            },
        )
        val hosts = createHostAdapters()
        surfaceRuntime = AppleMetadataSurfaceRuntime(
            runtime = runtime,
            host = hosts.surface,
        )
        librarySurfaceHooks = AppleLibrarySurfaceHooks(
            runtime = runtime,
            metadataStore = metadataStore,
            host = hosts.library,
            refreshQueue = refreshQueue,
        )
        dataBindingHooks = AppleDataBindingMetadataHooks(
            runtime = runtime,
            host = hosts.dataBinding,
            refreshQueue = refreshQueue,
        )
        collectionSurfaceHooks = AppleCollectionSurfaceHooks(
            runtime = runtime,
            metadataStore = metadataStore,
            librarySurfaceHooks = librarySurfaceHooks,
            dataBindingHooks = dataBindingHooks,
            host = hosts.collection,
            refreshQueue = refreshQueue,
        )
        artistSurfaceHooks = AppleArtistSurfaceHooks(
            runtime = runtime,
            metadataStore = metadataStore,
            librarySurfaceHooks = librarySurfaceHooks,
            dataBindingHooks = dataBindingHooks,
            host = hosts.artist,
            refreshQueue = refreshQueue,
        )
        mediaApiMetadataCoordinator = AppleMediaApiMetadataCoordinator(
            runtime = runtime,
            metadataStore = metadataStore,
            catalogResolver = catalogResolver,
            librarySurfaceHooks = librarySurfaceHooks,
            artistSurfaceHooks = artistSurfaceHooks,
            host = hosts.mediaApi,
            refreshQueue = refreshQueue,
        )
        resolutionCoordinator = AppleInAppMetadataResolutionCoordinator(
            runtime = runtime,
            metadataStore = metadataStore,
            catalogResolver = catalogResolver,
            host = hosts.resolution,
        )
        listenNowHooks = AppleListenNowHooks(
            runtime = runtime,
            metadataStore = metadataStore,
            catalogResolver = catalogResolver,
            host = hosts.listenNow,
            refreshQueue = refreshQueue,
        )
        metadataApplier = AppleInAppMetadataApplier(
            runtime = runtime,
            metadataStore = metadataStore,
            registry = registry,
            contentItemMetadataHooks = contentItemHooks,
            librarySurfaceHooks = librarySurfaceHooks,
            collectionSurfaceHooks = collectionSurfaceHooks,
            artistSurfaceHooks = artistSurfaceHooks,
            dataBindingHooks = dataBindingHooks,
            listenNowHooks = listenNowHooks,
            queueMetadataHooks = queueMetadataHooks,
            traceSequence = traceSequence,
            logMetadataIdentity = { event, details ->
                ProviderLogger.diagnostic("$event: $details")
            },
        )
        media3MetadataCoordinator = AppleMedia3MetadataCoordinator(
            runtime = runtime,
            metadataStore = metadataStore,
            resolutionCoordinator = resolutionCoordinator,
            frameworkMetadataHooks = frameworkHooks,
            queueMetadataHooks = queueMetadataHooks,
            playbackMetadataCoordinator = playbackCoordinator,
            traceSequence = traceSequence,
        )
        metadataRegistrationCoordinator = AppleInAppMetadataRegistrationCoordinator(
            runtime = runtime,
            metadataStore = metadataStore,
            registry = registry,
            resolutionCoordinator = resolutionCoordinator,
            catalogResolver = catalogResolver,
            contentItemMetadataHooks = contentItemHooks,
            metadataApplier = metadataApplier,
            surfaceRuntime = surfaceRuntime,
            dataBindingHooks = dataBindingHooks,
            configuredContentUiLanguage = { this@HleMetadataSurfaceBridge.configuredContentUiLanguage },
        )
        visibleMetadataDiagnostics = AppleVisibleMetadataDiagnostics(
            runtime = runtime,
            host = hosts.visibleDiagnostics,
        )
        metadataOverrideApplicationCoordinator = AppleMetadataOverrideApplicationCoordinator(
            runtime = runtime,
            metadataStore = metadataStore,
            registry = registry,
            resolutionCoordinator = resolutionCoordinator,
            catalogResolver = catalogResolver,
            surfaceRuntime = surfaceRuntime,
            metadataApplier = metadataApplier,
            librarySurfaceHooks = librarySurfaceHooks,
            dataBindingHooks = dataBindingHooks,
            listenNowHooks = listenNowHooks,
            actionSheetMetadataHooks = actionSheetMetadataHooks,
            playbackMetadataCoordinator = playbackCoordinator,
            frameworkMetadataHooks = frameworkHooks,
            visibleMetadataDiagnostics = visibleMetadataDiagnostics,
            media3MetadataCoordinator = media3MetadataCoordinator,
            configuredContentUiLanguage = { this@HleMetadataSurfaceBridge.configuredContentUiLanguage },
            traceSequence = traceSequence,
        )
        playbackItemConversionHooks = ApplePlaybackItemConversionHooks(
            runtime = runtime,
            host = hosts.playbackItem,
        )
        inAppArtworkContinuityHooks = AppleInAppArtworkContinuityHooks(
            runtime = runtime,
            host = hosts.artworkContinuity,
        )

        installSafely("metadata-surface-lifecycle") { surfaceRuntime.installLifecycleHooks() }
        installSafely("library-entity") { librarySurfaceHooks.installEntityHooks() }
        installSafely("library-compose") { librarySurfaceHooks.installComposeHooks() }
        installSafely("library-epoxy") { librarySurfaceHooks.installEpoxyHooks() }
        installSafely("data-binding") { dataBindingHooks.installDataBindingHooks() }
        installSafely("recycler") { dataBindingHooks.installRecyclerHooks() }
        installSafely("collection") { collectionSurfaceHooks.installHooks() }
        installSafely("artist-top-songs") { artistSurfaceHooks.installTopSongHooks() }
        installSafely("artist-profile") { artistSurfaceHooks.installProfileHooks() }
        installSafely("listen-now-artwork") { listenNowHooks.installArtworkContinuityHooks() }
        installSafely("listen-now-binding") { listenNowHooks.installMetadataBindingHooks() }
        installSafely("recently-searched") { mediaApiMetadataCoordinator.installRecentlySearchedHooks() }
        installSafely("in-app-artwork-continuity") { inAppArtworkContinuityHooks.installHooks() }
        installSafely("playback-item-conversion") { playbackItemConversionHooks.installHooks() }
    }

    fun ensureOverride(
        mediaId: String,
        preBind: Boolean = false,
        priority: AppleInternalCatalogResolver.RequestPriority =
            AppleInternalCatalogResolver.RequestPriority.VISIBLE,
    ) = resolutionCoordinator.ensureOverride(mediaId, preBind, priority)

    fun ensureOverrides(
        mediaIds: Collection<String>,
        preBind: Boolean = false,
        originalResolutionLimit: Int = mediaIds.size,
    ) = resolutionCoordinator.ensureOverrides(mediaIds, preBind, originalResolutionLimit)

    fun registerMetadata(
        mediaId: String,
        metadata: Any,
        requestResolution: Boolean,
        preBind: Boolean,
        priority: AppleInternalCatalogResolver.RequestPriority,
    ) = metadataRegistrationCoordinator.registerMetadata(
        mediaId = mediaId,
        metadata = metadata,
        requestResolution = requestResolution,
        preBind = preBind,
        priority = priority,
    )

    fun registerPlaybackItem(
        mediaId: String,
        playbackItem: Any,
        notifyChange: Boolean,
        analyzeMetadata: Boolean,
    ) = metadataRegistrationCoordinator.registerPlaybackItem(
        mediaId = mediaId,
        playbackItem = playbackItem,
        notifyChange = notifyChange,
        analyzeMetadata = analyzeMetadata,
    )

    fun media3MetadataId(
        metadata: Any,
        fallback: String?,
        trustedFallback: Boolean,
    ): String? = media3MetadataCoordinator.mediaId(metadata, fallback, trustedFallback)

    fun media3MetadataDetails(metadata: Any): String =
        media3MetadataCoordinator.details(metadata)

    fun activePlaybackIdentity(): ActivePlaybackMediaIdentity =
        if (::media3MetadataCoordinator.isInitialized) {
            media3MetadataCoordinator.activePlaybackIdentity()
        } else {
            val mediaId = playbackCoordinator.currentMetadataId()
            ActivePlaybackMediaIdentity(
                mediaId = mediaId,
                source = "queue",
                candidates = mediaId.orEmpty(),
            )
        }

    fun effectiveAlias(mediaId: String): AppleInternalCatalogResolver.Alias? =
        if (::resolutionCoordinator.isInitialized) {
            resolutionCoordinator.effectiveAlias(mediaId)
        } else {
            metadataStore.originalMetadata(mediaId) ?: metadataStore.configuredMetadata(mediaId)
        }

    fun applyAliasToPlaybackItem(
        playbackItem: Any,
        alias: AppleInternalCatalogResolver.Alias,
        notifyChange: Boolean,
    ) = metadataApplier.applyAliasToPlaybackItem(playbackItem, alias, notifyChange)

    /**
     * Keep playback resolution publication on the same HLE coordinator path as
     * the original provider.  The coordinator owns the in-app rebind policy,
     * album/artist propagation, and persistent original-region bookkeeping;
     * writing only the two stores here leaves library rows stale until playback.
     */
    fun applyPlaybackMetadataOverride(
        mediaId: String,
        alias: AppleInternalCatalogResolver.Alias,
        forceInAppRebind: Boolean = true,
        rememberLocalizedArtist: Boolean = true,
        originalMetadata: Boolean = false,
        originalMetadataConfirmed: Boolean = false,
        artistOnly: Boolean = false,
        propagateArtistEntity: Boolean = true,
    ) {
        if (MediaMetadataCache.profile() != profileId) {
            ProviderLogger.debug(
                "忽略过期元数据 profile 回调: expected=$profileId, active=${MediaMetadataCache.profile()}"
            )
            return
        }
        metadataOverrideApplicationCoordinator.apply(
            mediaId = mediaId,
            alias = alias,
            forceInAppRebind = forceInAppRebind,
            rememberLocalizedArtist = rememberLocalizedArtist,
            originalMetadata = originalMetadata,
            originalMetadataConfirmed = originalMetadataConfirmed,
            artistOnly = artistOnly,
            propagateArtistEntity = propagateArtistEntity,
        )
    }

    internal fun applyPlaybackMetadataOverrideFromHost(
        mediaId: String,
        alias: AppleInternalCatalogResolver.Alias,
        forceInAppRebind: Boolean,
        rememberLocalizedArtist: Boolean,
        originalMetadata: Boolean,
        originalMetadataConfirmed: Boolean,
        artistOnly: Boolean,
        propagateArtistEntity: Boolean,
    ) {
        if (originalMetadata && !restoreOriginalMetadata) return
        if (::metadataOverrideApplicationCoordinator.isInitialized) {
            applyPlaybackMetadataOverride(
                mediaId = mediaId,
                alias = alias,
                forceInAppRebind = forceInAppRebind,
                rememberLocalizedArtist = rememberLocalizedArtist,
                originalMetadata = originalMetadata,
                originalMetadataConfirmed = originalMetadataConfirmed,
                artistOnly = artistOnly,
                propagateArtistEntity = propagateArtistEntity,
            )
        } else {
            if (originalMetadata) {
                metadataStore.rememberOriginalMetadata(mediaId, alias, originalMetadataConfirmed)
            } else {
                metadataStore.rememberConfiguredMetadata(mediaId, alias)
            }
            frameworkHooks.refreshMediaSessionMetadata(mediaId, alias)
        }
    }

    fun applyAliasToContainerItem(
        containerItem: Any,
        kind: InAppContainerKind,
        alias: AppleInternalCatalogResolver.Alias,
        notifyChange: Boolean = true,
    ) = metadataApplier.applyAliasToContainerItem(containerItem, kind, alias, notifyChange)

    fun markMetadataVisible(mediaIds: Collection<String>) = surfaceRuntime.markVisible(mediaIds)

    fun isCurrentMetadataSurfaceMediaId(mediaId: String): Boolean =
        surfaceRuntime.isCurrentMediaId(mediaId)

    fun setPlaybackMediaId(mediaId: String) = surfaceRuntime.setPlaybackMediaId(mediaId)

    fun requestPriority(mediaId: String): AppleInternalCatalogResolver.RequestPriority =
        surfaceRuntime.requestContext(mediaId).priority

    fun contentItemLocalizedEntityType(contentItem: Any): AppleInternalCatalogResolver.LocalizedEntityType? =
        metadataRegistrationCoordinator.contentItemLocalizedEntityType(contentItem)

    fun recordComposeMediaId(mediaId: String) = librarySurfaceHooks.recordComposeMediaId(mediaId)

    fun recordCurrentRecyclerMediaId(mediaId: String) {
        dataBindingHooks.recordCurrentRecyclerMediaId(mediaId)
    }

    fun shouldRequestOverride(mediaId: String): Boolean =
        resolutionCoordinator.shouldRequestOverride(mediaId)

    fun shouldShareOriginalSongLanguage(
        localizedTitle: String?,
        localizedArtist: String?,
        alias: AppleInternalCatalogResolver.Alias?,
    ): Boolean = restoreOriginalMetadata && resolutionCoordinator.shouldShareOriginalSongLanguage(
        localizedTitle = localizedTitle,
        localizedArtist = localizedArtist,
        alias = alias,
    )

    fun rememberOriginalLanguageForArtist(mediaId: String, language: String) {
        if (restoreOriginalMetadata) {
            resolutionCoordinator.rememberOriginalLanguageForArtist(mediaId, language)
        }
    }

    fun containerNavigationBinding(containerItem: Any): InAppContainerNavigationRef? =
        metadataRegistrationCoordinator.containerNavigationBinding(containerItem)

    fun registerContainerItem(
        mediaId: String,
        containerItem: Any,
        kind: InAppContainerKind,
    ) = metadataRegistrationCoordinator.registerContainerItem(mediaId, containerItem, kind)

    fun rawContentItemValue(contentItem: Any, runtimeMember: AppleMusicRuntimeMember): Any? =
        metadataRegistrationCoordinator.rawContentItemValue(contentItem, runtimeMember)

    fun knownValues(mediaId: String, field: VisibleTextField): Set<String> = buildSet {
        val account = metadataStore.accountMetadata(mediaId)
        val value = alias(mediaId)
        when (field) {
            VisibleTextField.TITLE -> Unit
            VisibleTextField.ARTIST -> {
                account?.artist?.let(::add)
                value?.artist?.let(::add)
                registry.livePlaybackItemRefs(mediaId).forEach { ref ->
                    ref.originalArtist?.toString()?.let(::add)
                }
            }
            VisibleTextField.ALBUM -> {
                value?.album?.let(::add)
                registry.livePlaybackItemRefs(mediaId).forEach { ref ->
                    ref.originalCollectionName
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
        }
    }

    fun hasLivePlaybackItem(mediaId: String): Boolean = registry.hasLivePlaybackItem(mediaId)

    fun markPlaybackItemHistory(playbackItem: Any) = registry.markPlaybackItemContract(
        playbackItem,
        InAppPlaybackItemContract.HISTORY,
    )

    fun recordArtistAssociation(mediaId: String, item: Any, rawTitle: String?) {
        val artistKeys = metadataRegistrationCoordinator.contentItemArtistCacheKeys(item, rawTitle)
        if (artistKeys.isNotEmpty()) {
            metadataStore.mergeArtistKeys(mediaId, artistKeys)
        }
        resolutionCoordinator.mergePlaybackAssociatedArtistIds(
            mediaId = mediaId,
            artistIds = io.github.proify.lyricon.amprovider.xposed.artistIdsFromAssociationKeys(artistKeys) +
                metadataRegistrationCoordinator.contentItemCatalogLookupIds(item, mediaId = "")
                    .filterNot { it == mediaId },
        )
    }

    /**
     * Concrete adapters for the HLE host interfaces.  These objects are built once during
     * installation and keep all compatibility/fail-open handling at the seam.  Hot callbacks
     * therefore use normal virtual dispatch instead of method-name/argument-array lookup on
     * every invocation.
     */
    internal data class HostAdapters(
        val surface: AppleMetadataSurfaceHost,
        val library: AppleLibrarySurfaceHost,
        val dataBinding: AppleDataBindingMetadataHost,
        val collection: AppleCollectionSurfaceHost,
        val artist: AppleArtistSurfaceHost,
        val mediaApi: AppleMediaApiMetadataHost,
        val resolution: AppleInAppMetadataResolutionHost,
        val listenNow: AppleListenNowHost,
        val visibleDiagnostics: AppleVisibleMetadataDiagnosticsHost,
        val playbackItem: ApplePlaybackItemConversionHost,
        val artworkContinuity: AppleInAppArtworkContinuityHost,
    )

    internal inline fun <T> hostCall(
        name: String,
        fallback: T,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (error: Throwable) {
            runCatching {
                ProviderLogger.debug("HLE typed host callback $name failed: ${error.message}")
            }
            fallback
        }
    }

    internal fun createHostAdapters(): HostAdapters = createMetadataHostAdapters()

    internal fun installSafely(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            ProviderLogger.error("HLE $name surface hook unavailable", it)
        }
    }

    internal fun currentIdentity(): ActivePlaybackMediaIdentity = ActivePlaybackMediaIdentity(
        mediaId = playbackCoordinator.currentMetadataId(),
        source = "ampp_hle",
        candidates = playbackCoordinator.currentMetadataId().orEmpty(),
    )

    internal fun alias(mediaId: String?): AppleInternalCatalogResolver.Alias? =
        mediaId?.let(::effectiveAlias)

    internal fun markVisible(ids: Collection<String>) {
        surfaceRuntime.markVisible(ids)
    }

    internal fun shouldRetryOriginalMetadataCacheProbe(mediaId: String): Boolean =
        io.github.proify.lyricon.amprovider.xposed.shouldRetryOriginalMetadataCacheProbe(
            originalResolved = metadataStore.isOriginalResolved(mediaId),
            lastMissUptimeMillis = metadataStore.originalCacheMissUptimeMillis(mediaId),
            nowUptimeMillis = SystemClock.uptimeMillis(),
        )

    internal fun ensureOverride(
        mediaId: String,
        priority: AppleInternalCatalogResolver.RequestPriority =
            AppleInternalCatalogResolver.RequestPriority.VISIBLE,
    ) {
        if (::resolutionCoordinator.isInitialized && resolutionCoordinator.shouldRequestOverride(mediaId)) {
            resolutionCoordinator.ensureOverride(mediaId, preBind = false, priority = priority)
        }
    }

    internal fun applyAliasToObject(
        target: Any,
        value: AppleInternalCatalogResolver.Alias,
        notifyChange: Boolean,
    ) {
        val writes = listOf(
            "setTitle" to value.title,
            "setArtistName" to value.artist,
            "setCollectionName" to value.album,
        )
        writes.forEach { (method, text) ->
            if (text.isBlank()) return@forEach
            runCatching { AppleReflection.call(target, method, text) }
        }
        if (notifyChange) {
            runCatching { AppleReflection.call(target, "notifyChange") }
        }
    }

}
