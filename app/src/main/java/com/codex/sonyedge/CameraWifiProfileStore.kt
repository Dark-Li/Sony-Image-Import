package com.codex.sonyedge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persists camera Wi-Fi metadata while keeping the password encrypted at rest. */
class CameraWifiProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): CameraWifiProfile? {
        val ssid = preferences.getString(KEY_SSID, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val encryptedPassword = preferences.getString(KEY_ENCRYPTED_PASSWORD, null)
            ?: return null
        val initializationVector = preferences.getString(KEY_INITIALIZATION_VECTOR, null)
            ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(initializationVector, BASE64_FLAGS)),
            )
            val password = cipher.doFinal(
                Base64.decode(encryptedPassword, BASE64_FLAGS),
            ).toString(Charsets.UTF_8)
            CameraWifiProfile(
                cameraModel = preferences.getString(KEY_CAMERA_MODEL, null),
                ssid = ssid,
                password = password,
            )
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun save(profile: CameraWifiProfile) {
        require(profile.ssid.isNotBlank()) { "Camera Wi-Fi SSID must not be blank" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedPassword = cipher.doFinal(profile.password.toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString(KEY_CAMERA_MODEL, profile.cameraModel?.trim()?.takeIf { it.isNotEmpty() })
            .putString(KEY_SSID, profile.ssid.trim())
            .putString(
                KEY_ENCRYPTED_PASSWORD,
                Base64.encodeToString(encryptedPassword, BASE64_FLAGS),
            )
            .putString(
                KEY_INITIALIZATION_VECTOR,
                Base64.encodeToString(cipher.iv, BASE64_FLAGS),
            )
            .apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
        runCatching {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
        load(null)
    }

    data class CameraWifiProfile(
        val cameraModel: String?,
        val ssid: String,
        val password: String,
    ) {
        override fun toString(): String =
            "CameraWifiProfile(cameraModel=$cameraModel, ssid=$ssid, password=<redacted>)"
    }

    private companion object {
        const val PREFERENCES_NAME = "camera_wifi_profile"
        const val KEY_CAMERA_MODEL = "camera_model"
        const val KEY_SSID = "ssid"
        const val KEY_ENCRYPTED_PASSWORD = "encrypted_password"
        const val KEY_INITIALIZATION_VECTOR = "initialization_vector"

        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.codex.sonyedge.camera_wifi_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val BASE64_FLAGS = Base64.NO_WRAP
    }
}
