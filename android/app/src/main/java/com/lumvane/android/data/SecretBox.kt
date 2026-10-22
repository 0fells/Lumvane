package com.lumvane.android.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
}

internal object SecretBox {
    const val PREFIX = "enc1:"

    val default: SecretCipher by lazy { CachingSecretCipher(KeystoreSecretCipher()) }

    val migrationStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    fun decryptOrNull(cipher: SecretCipher, stored: String, what: String): String? =
        try {
            cipher.decrypt(stored)
        } catch (e: Exception) {
            warn("cannot decrypt $what: ${e.javaClass.simpleName}")
            null
        }

    fun warn(msg: String) {
        runCatching { Log.w("SecretBox", msg) }
    }
}

class CachingSecretCipher(private val inner: SecretCipher, private val maxEntries: Int = 1024) : SecretCipher {
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > maxEntries
    }

    override fun encrypt(plain: String): String = inner.encrypt(plain).also { if (SecretBox.isEncrypted(it)) remember(it, plain) }

    override fun decrypt(stored: String): String {
        if (!SecretBox.isEncrypted(stored)) return stored
        synchronized(cache) { cache[stored] }?.let { return it }
        return inner.decrypt(stored).also { remember(stored, it) }
    }

    private fun remember(stored: String, plain: String) = synchronized(cache) { cache[stored] = plain }
}

class KeystoreSecretCipher(private val alias: String = "lumvane_db_v1") : SecretCipher {
    @Volatile private var cachedKey: SecretKey? = null

    private fun key(): SecretKey = cachedKey ?: synchronized(this) {
        cachedKey ?: loadOrCreateKey().also { cachedKey = it }
    }

    private fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        check(iv.size == IV_SIZE) { "unexpected IV size ${iv.size}" }
        val out = iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return SecretBox.PREFIX + Base64.getEncoder().encodeToString(out)
    }

    override fun decrypt(stored: String): String {
        if (!SecretBox.isEncrypted(stored)) return stored
        val raw = Base64.getDecoder().decode(stored.substring(SecretBox.PREFIX.length))
        require(raw.size > IV_SIZE) { "ciphertext too short" }
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_SIZE)))
        return String(c.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}

class SecretMigration(
    private val profiles: ProfileDao,
    private val groups: GroupDao,
    private val cipher: SecretCipher,
    private val settings: SettingsDao? = null,
) {
    suspend fun run(): Boolean {
        var changed = false
        for (p in profiles.getAllRaw()) {
            if (p.beanJson.isEmpty() || SecretBox.isEncrypted(p.beanJson)) continue
            profiles.replaceBeanJson(p.id, p.beanJson, cipher.encrypt(p.beanJson))
            changed = true
        }
        for (g in groups.getAll()) {
            if (g.url.isEmpty() || SecretBox.isEncrypted(g.url)) continue
            groups.replaceUrl(g.id, g.url, cipher.encrypt(g.url))
            changed = true
        }
        settings?.getAll()?.forEach { r ->
            if (r.key !in SettingsCodec.SECRET_KEYS || r.value.isEmpty() || SecretBox.isEncrypted(r.value)) return@forEach
            settings.replaceValue(r.key, r.value, cipher.encrypt(r.value))
            changed = true
        }
        return changed
    }
}
