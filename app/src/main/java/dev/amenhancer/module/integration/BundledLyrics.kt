package dev.amenhancer.module.integration

import android.app.Application
import android.content.Context
import android.os.Build
import dev.amenhancer.module.config.ConfigurationReader
import dev.amenhancer.module.hook.ModernXposedRuntime
import dev.amenhancer.plugin.runtime.PluginStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/** Install the owned plugin before the SDK loads it; preserve newer/disabled/deleted installations. */
internal object BundledLyrics {
    const val ID = "com.tcrrry.lyrics"
    const val VERSION = 36L
    fun updateAsset(id: String, path: String): java.io.InputStream? {
        if (id != ID || path != "update-channel.json") return null
        return JSONObject().put("repository", "tcrrry/AMPP-Lyrics").put("tagPrefix", "v")
            .put("version", dev.amenhancer.module.BuildConfig.VERSION_NAME.substringBefore('-'))
            .put("label", "AM++ Lyrics 主程序").toString().byteInputStream()
    }
    fun builtinReader(reader: ConfigurationReader): ConfigurationReader = object : ConfigurationReader by reader {
        override fun values(): Map<String, *> = reader.values().toMutableMap().apply {
            // Leave stored preferences intact; only the competing built-in matching path is masked.
            put("custom_lyrics_enabled", false)
            put("automatic_lyrics_enabled", false)
        }
    }

    fun install(application: Application, store: PluginStore) {
        val state = application.getSharedPreferences("tcrrry-bundled-lyrics", Context.MODE_PRIVATE)
        val old = store.installed().firstOrNull { it.manifest.id == ID }
        if (old == null && state.contains("installed")) return
        if (old == null || old.manifest.versionCode < VERSION) {
            val info = checkNotNull(ModernXposedRuntime.activeModule()?.moduleApplicationInfo)
            val bytes = ZipFile(info.sourceDir).use { zip ->
                val entry = checkNotNull(zip.getEntry("assets/bundled/tcrrry-lyrics.zip"))
                check(entry.size in 1..(64L * 1024 * 1024))
                zip.getInputStream(entry).use { it.readBytes() }
            }
            store.prepare(bytes.inputStream(), Build.VERSION.SDK_INT).use { prepared ->
                check(prepared.manifest.id == ID && prepared.manifest.versionCode == VERSION)
                store.commit(prepared)
            }
            if (old == null) store.setEnabled(ID, true)
        }
        migrate(application, store.dataDirectory(ID))
        check(state.edit().putLong("installed", VERSION).commit())
    }

    internal fun encode(values: Map<String, *>): String = JSONObject().apply {
        values.forEach { (key, value) ->
            val type = when (value) {
                is String -> "string"; is Int -> "int"; is Long -> "long"; is Float -> "float"
                is Boolean -> "boolean"; is Set<*> -> "set"; else -> return@forEach
            }
            put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
        }
    }.toString()

    internal fun migrate(context: Context, destination: File) {
        runCatching { migrateSecrets(context, destination) }
            .onFailure { ModernXposedRuntime.log("API key migration deferred; original encrypted values retained") }
        val marker = File(destination, "integrated-v1.7-migrated")
        if (marker.exists()) return
        val prefs = File(destination, "preferences").apply { check(mkdirs() || isDirectory) }
        val names = listOf("ampp-embedded-settings", "ampp-current-lyrics-source", "tcrrry_lyrics_policy",
            "japanese_pronunciation", "supplement_translation", "lyrics_operation_guide", "translation_api_profiles", "desktop_lyrics_secrets")
        for (name in names) {
            val target = File(prefs, "$name.json")
            if (target.exists()) continue
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (values.isNotEmpty()) atomicWrite(target, encode(values).toByteArray())
        }
        val legacy = File(context.filesDir, "ampp-embedded-files")
        val files = File(destination, "ampp-embedded-files").apply { check(mkdirs() || isDirectory) }
        var total = 0L
        legacy.listFiles()?.filter { it.isFile && it.name.matches(Regex("[A-Za-z0-9_.-]{1,240}")) }?.forEach { source ->
            total += source.length()
            check(total <= 64L * 1024 * 1024)
            val target = File(files, source.name)
            if (!target.exists()) atomicWrite(target, source.readBytes())
        }
        atomicWrite(marker, byteArrayOf(1))
    }
    private fun migrateSecrets(context: Context, destination: File) {
        val directory = File(destination, "secrets").apply { check(mkdirs() || isDirectory) }
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        context.noBackupFilesDir.listFiles()?.filter {
            it.isFile && it.name.matches(Regex("translation-key(?:-[a-z0-9_-]+)?")) && it.length() <= 64 * 1024
        }?.forEach { source ->
            val suffix = source.name.removePrefix("translation-key")
            val target = File(directory, source.name)
            if (target.exists()) return@forEach
            runCatching {
                val oldKey = store.getKey("translation-key$suffix", null) as? javax.crypto.SecretKey ?: return@runCatching
                val bytes = android.util.Base64.decode(source.readText(), android.util.Base64.NO_WRAP)
                require(bytes.size > 12)
                val decode = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(javax.crypto.Cipher.DECRYPT_MODE, oldKey, javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                }
                val alias = "com.tcrrry.lyrics.translation-key$suffix"
                val key = (store.getKey(alias, null) as? javax.crypto.SecretKey) ?: javax.crypto.KeyGenerator
                    .getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                        init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
                    }.generateKey()
                val encode = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key) }
                val plain = decode.doFinal(bytes.copyOfRange(12, bytes.size))
                try { atomicWrite(target, android.util.Base64.encode(encode.iv + encode.doFinal(plain), android.util.Base64.NO_WRAP)) }
                finally { plain.fill(0) }
            }.onFailure { ModernXposedRuntime.log("API key migration deferred; original encrypted value retained") }
        }
    }
    private fun atomicWrite(file: File, bytes: ByteArray) {
        val temp = File.createTempFile("migration-", ".tmp", file.parentFile)
        try {
            java.io.FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
    }
}
