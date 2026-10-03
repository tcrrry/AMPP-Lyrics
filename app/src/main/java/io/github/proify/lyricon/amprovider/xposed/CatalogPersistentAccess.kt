package io.github.proify.lyricon.amprovider.xposed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.juren233.hyperlyricsenhanced.BuildConfig
import com.juren233.hyperlyricsenhanced.common.RootConstants
import com.juren233.hyperlyricsenhanced.common.lyric.AppleOriginalMetadataPolicy
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.CatalogAccess
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.CatalogResponseSnapshot
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.CatalogEntitySnapshot
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.CatalogArtistSnapshot
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.CatalogIdentity
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.PreparedOriginalResolution
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.CatalogSong
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.LocalizedRequest
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.LockedIsrcFallbackTask
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.OriginalEntityRequest
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.CatalogRequestLocalization
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Alias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.OriginalResolution
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.LocalizedLookup
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.RequestPriority
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.LocalizedEntityType
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.CURRENT_LANGUAGE
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.CACHE_SIZE
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.LOCALIZED_CACHE_SIZE
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.LOCALIZED_ARTIST_ALIAS_CACHE_SIZE
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.REQUEST_PRIORITY_CACHE_SIZE
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.ORIGINAL_METADATA_CACHE_SCHEMA
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.LOCALIZED_BATCH_SIZE
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.MAX_LOCALIZED_BATCHES_RUNNING
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.MAX_BACKGROUND_LOCALIZED_BATCHES_RUNNING
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.MAX_LOCKED_ISRC_FALLBACK_RUNNING
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.LOCALIZED_BATCH_DELAY_MS
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.ORIGINAL_ENTITY_BATCH_SIZE
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.MAX_ORIGINAL_ENTITY_BATCHES_RUNNING
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.MAX_BACKGROUND_ORIGINAL_ENTITY_BATCHES_RUNNING
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.ORIGINAL_ENTITY_BATCH_DELAY_MS
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.QUERY_SLOW_RESPONSE_MS
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.QUERY_TIMEOUT_MS
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.ARTIST_ALIAS_CACHE_SCHEMA
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.CATALOG_REQUEST_TOKEN_PARAM
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.ORIGINAL_LANGUAGE_PROBE_ORDER
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.COLLABORATION_ARTIST_PATTERNS
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.collaborationArtistCache

import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isCoroutineSuspended
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.originalSongCacheKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.originalDirectEntityCacheKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.legacyAmbiguousSongCacheKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.originalEntityCacheKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.originalEntityCacheLookupKeys
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.localizedMetadataCacheKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isLocalizedArtistAliasCacheKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.originalLanguageCacheKeyVariants
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.selectNextRequestIndex
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.canStartRequest
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.higherPriority
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.priorityForRequestScope
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.normalizeRequestScopeIds
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.languageTagsForGenre
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.knownLanguageTagsForGenre
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.languageTagsForIsrc
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.languageTagsForOriginalMetadata
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.languageTagsForOriginalMetadata
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.canonicalOriginalLanguage
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.supportedOriginalLanguageOrNull
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.storefrontForOriginalLanguage
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isLegacyTraditionalChineseLanguage
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.canonicalCachedOriginalAlias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.regionalOriginalAliases
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isAcceptableOriginalAlias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.selectExactOriginalEntityAlias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.selectExactIdentityAlias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.containsHanCharacters
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.hasCjkArtistScript
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.containsJapaneseKana
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.containsHangul
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.storefrontForContentUiLanguage
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.languageTagsForContentUiLanguage
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.languageTagForContentUiLanguage
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.localizedStorefrontHeaderValue
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.normalizedArtistNameKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.artistCacheKey
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.storefrontFromContentPath
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isAccountScopedPlaybackPath
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.selectLocalizedArtistName
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.selectOriginalAlias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isConfidentOriginalSongAlias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isReusableOriginalSongAlias
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isCollaborationArtistName
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.isOriginalTitle
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.shouldResolve
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.nonLatinLetterCount
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.normalize
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.shouldCacheCatalogIdentity
import io.github.proify.lyricon.amprovider.xposed.AppleInternalCatalogResolver.Companion.shouldRetryEmptyCatalogIdentity

internal fun AppleInternalCatalogResolver.warmPersistentOriginalCache() {
        if (!persistentLocalizedCacheEnabled) return
        persistentOriginalCache.warmRecentAsync { count ->
            if (count != null) {
                ProviderLogger.info("Apple 原地区元数据缓存预热完成: entries=$count")
            } else {
                ProviderLogger.info("Apple 原地区元数据缓存预热延后")
            }
        }
    }


internal fun AppleInternalCatalogResolver.warmPersistentLocalizedCache(selection: Int) {
        if (!persistentLocalizedCacheEnabled) return
        if (storefrontForContentUiLanguage(selection) == null) return
        val shouldWarm = synchronized(warmedSelections) {
            if (selection in warmedSelections) false
            else synchronized(warmingSelections) { warmingSelections.add(selection) }
        }
        if (!shouldWarm) return
        val prefix = "$selection:"
        persistentLocalizedCache.warmRecentAsync(prefix) { delayedAliases ->
            if (delayedAliases != null) {
                finishPersistentCacheWarm(selection, delayedAliases)
            } else {
                synchronized(warmingSelections) { warmingSelections.remove(selection) }
                ProviderLogger.info("Apple 地区元数据缓存预热延后: selection=$selection")
            }
        }
    }


internal fun AppleInternalCatalogResolver.finishPersistentCacheWarm(selection: Int, aliases: Map<String, Alias>) {
        val artistAliases = aliases.filterKeys(::isLocalizedArtistAliasCacheKey)
        val metadataAliases = aliases.filterKeys { key ->
            !isLocalizedArtistAliasCacheKey(key)
        }
        synchronized(localizedCache) { localizedCache.putAll(metadataAliases) }
        synchronized(localizedArtistAliasCache) {
            localizedArtistAliasCache.putAll(artistAliases)
        }
        synchronized(warmedSelections) { warmedSelections.add(selection) }
        synchronized(warmingSelections) { warmingSelections.remove(selection) }
        ProviderLogger.info(
            "Apple 地区元数据缓存预热完成: selection=$selection, " +
                "metadata=${metadataAliases.size}, artistAlias=${artistAliases.size}, " +
                "entries=${aliases.size}"
        )
    }


internal fun AppleInternalCatalogResolver.finishLocalizedCacheHit(request: LocalizedRequest, alias: Alias) {
        synchronized(localizedCache) { localizedCache[request.cacheKey] = alias }
        val callbacks = synchronized(localizedInFlight) {
            localizedInFlight.remove(request.requestKey).orEmpty()
        }
        ProviderLogger.info(
            "Apple 地区元数据持久缓存命中: id=${request.mediaId}, " +
                "entityType=${request.entityType}, selection=${request.selection}"
        )
        callbacks.forEach { callback -> callback(alias) }
    }

