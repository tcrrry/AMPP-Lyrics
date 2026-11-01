package dev.amenhancer.module.hook

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HleArtistComposeProfileStructuralTest {
    private fun source(relative: String): String = sequenceOf(
        File(relative),
        File("../$relative"),
    ).firstOrNull(File::isFile)?.readText()
        ?: error("Missing $relative")

    private val hooks by lazy {
        source(
            "host-applemusic/src/main/java/io/github/proify/lyricon/amprovider/xposed/metadata/AppleArtistSurfaceHooks.kt",
        )
    }

    private fun composeBody(): String =
        hooks.substringAfter("fun installComposeProfileHooks()").substringBefore("fun handleFinalBinding(")

    @Test
    fun composeArtistHeaderReusesTheArtistAliasPathAndSongRegionEvidence() {
        val body = composeBody()
        assertTrue(body.contains("kind = InAppLibraryEntityKind.ARTIST"))
        assertTrue(body.contains("librarySurfaceHooks.applyAliasToEntity("))
        assertTrue(body.contains("host.scheduleMetadataResolution("))
        assertTrue(body.contains("associateTopSongWithProfileArtist(owner, mediaId)"))
        assertTrue(body.contains("requestPageOriginalMetadata(songIds)"))
        // P1: associate before enrich so missing song artists still get the profile candidate.
        val associateAt = body.indexOf("associateTopSongWithProfileArtist(owner, mediaId)")
        val enrichAt = body.indexOf("host.enrichLibraryEntity(", associateAt)
        assertTrue("associateTopSong must precede enrichLibraryEntity", associateAt >= 0 && enrichAt > associateAt)
    }

    @Test
    fun composeProfileClearsSavedSuccessWhenLeavingSuccessAndIsolatesRefreshQueueIdentity() {
        val body = composeBody()
        assertTrue(body.contains("clearComposeProfileOwner(owner, installation)"))
        assertTrue(body.contains("slot = COMPOSE_PROFILE_REFRESH_SLOT"))
        assertTrue(body.contains("sessions.finishRefresh(session)"))
        assertTrue(body.contains("runtime.hookRegistrar.onInstallationClose"))
        assertTrue(body.contains("sessions.isCurrent(session)"))
        assertTrue(
            source(
                "host-applemusic/src/main/java/io/github/proify/lyricon/amprovider/xposed/metadata/AppleArtistSurfaceHooks.kt",
            ).contains("private const val COMPOSE_PROFILE_REFRESH_SLOT = 1"),
        )
    }

    @Test
    fun composeArtistHeaderNeverInfersLanguageFromTheArtistNameOrSwapsLogos() {
        val body = composeBody()
        assertFalse(body.contains("containsHanCharacters"))
        assertFalse(body.contains("hasCjkArtistScript"))
        assertFalse(body.contains("containsJapaneseKana"))
        assertFalse(body.contains("containsHangul"))
        assertFalse(body.contains("AppleMediaApiTextAttribute.NAME"))
        assertFalse(body.contains("ARTIST_LOGO"))
        assertFalse(body.contains("editorialArtwork"))
    }

    @Test
    fun resolvedAliasesRepublishLiveComposeArtistPages() {
        val applier = source(
            "host-applemusic/src/main/java/io/github/proify/lyricon/amprovider/xposed/metadata/AppleInAppMetadataApplier.kt",
        )
        assertTrue(applier.contains("artistSurfaceHooks.refreshComposeProfiles(mediaId)"))
        assertTrue(applier.contains("artistSurfaceHooks.hasComposeProfileRefs(mediaId)"))
        val bridge = source("host-applemusic/src/main/java/dev/amenhancer/module/hook/HleMetadataSurfaceBridge.kt")
        assertTrue(bridge.contains("artistSurfaceHooks.hasComposeProfileSurface()"))
    }

    @Test
    fun everyIndexedProfileKeepsItsComposeDeclarationAndInvocationInventoryTogether() {
        val directory = "host-applemusic/src/main/resources/host-profiles"
        val roles = listOf("compose_profile_view_model", "compose_profile_success_state")
        val index = JSONObject(source("$directory/index.json")).getJSONArray("profiles")
        (0 until index.length()).map(index::getString).forEach { name ->
            val profile = JSONObject(source("$directory/$name"))
            val targets = profile.getJSONObject("hookTargets").getJSONArray("ARTIST_SURFACE_CLASSES")
            val declared = (0 until targets.length()).mapNotNull { targetIndex ->
                targets.getJSONObject(targetIndex).getJSONObject("runtimeMemberNames")
                    .optString("ARTIST_RUNTIME_ROLE").takeIf(roles::contains)
            }
            assertEquals(name, if (profile.has("artistComposeProfile")) roles else emptyList<String>(), declared)
            if (declared.isNotEmpty()) {
                val contract = profile.getJSONObject("artistComposeProfile")
                assertEquals(name, 1, contract.getInt("contractVersion"))
                assertTrue(contract.getJSONObject("methods").keys().asSequence().toSet().containsAll(listOf(
                    "setUiState", "headerData", "shelves", "entityType", "entityTitle", "entityViews", "viewEntities",
                )))
                assertTrue(contract.getJSONObject("fields").keys().asSequence().toSet().containsAll(listOf(
                    "id", "title", "header", "shelves", "entity", "revision",
                )))
                assertTrue(contract.getJSONObject("constructors").has("success"))
            }
        }
    }

    @Test
    fun unownedSuccessAndArtistChangesDropTheOwnersPreviousRefs() {
        val hooks = source(
            "host-applemusic/src/main/java/io/github/proify/lyricon/amprovider/xposed/metadata/AppleArtistSurfaceHooks.kt",
        )
        val state = hooks.substringAfter("private fun onComposeProfileState(").substringBefore("val session = sessions.replace")
        assertTrue(state.contains("members.isArtistEntity(entity)"))
        assertTrue(state.contains("clearComposeProfileOwner(owner, installation)"))
        assertTrue(hooks.contains("sessions.replace(owner, mediaId, state)"))
        assertFalse(hooks.contains("ConcurrentLinkedQueue<WeakReference<Any>>"))
    }
}
