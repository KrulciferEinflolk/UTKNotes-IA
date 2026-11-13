package com.example.data.local.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.ui.AiEngineType
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val DEFAULT_AI_STUDIO_MODEL = "gemini-2.5-flash"

data class SuggestedAiModel(
    val id: String,
    val label: String,
    val badge: String
)

val CURATED_AI_STUDIO_MODELS: List<SuggestedAiModel> = listOf(
    SuggestedAiModel(
        id = "gemini-2.5-flash",
        label = "gemini-2.5-flash",
        badge = "Recomendado"
    ),
    SuggestedAiModel(
        id = "gemini-2.5-flash-lite",
        label = "gemini-2.5-flash-lite",
        badge = "Más cuota"
    ),
    SuggestedAiModel(
        id = "gemini-2.5-pro",
        label = "gemini-2.5-pro",
        badge = "Razonamiento"
    )
)

@JsonClass(generateAdapter = true)
data class AiStudioModelInfo(
    val id: String,
    val displayName: String,
    val description: String = ""
)

sealed class ApiKeyValidationState {
    object Idle : ApiKeyValidationState()
    object Validating : ApiKeyValidationState()
    data class Valid(val modelCount: Int) : ApiKeyValidationState()
    data class Invalid(val safeMessage: String) : ApiKeyValidationState()
}

data class AiApiSettingsState(
    val hasApiKey: Boolean = false,
    val maskedKeyPreview: String = "",
    val isKeystoreEncrypted: Boolean = true,
    val isConnectionVerified: Boolean = false,
    val selectedModel: String = DEFAULT_AI_STUDIO_MODEL,
    val discoveredModels: List<AiStudioModelInfo> = emptyList(),
    val validationState: ApiKeyValidationState = ApiKeyValidationState.Idle
)

data class EncryptedPayload(
    val encodedValue: String,
    val isHardwareBacked: Boolean
)

interface KeyValueStorage {
    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

interface SecretCipher {
    fun encrypt(plainText: String): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): String?
}

class SharedPreferencesKeyValueStorage(
    private val prefs: SharedPreferences
) : KeyValueStorage {
    override fun getString(key: String, defaultValue: String?): String? =
        prefs.getString(key, defaultValue)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

class AndroidKeystoreSecretCipher(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : SecretCipher {

    private val appSaltSeed = "${context.packageName}:utk_notes_ai_studio_v1"

    override fun encrypt(plainText: String): EncryptedPayload {
        val bytes = plainText.toByteArray(StandardCharsets.UTF_8)
        return try {
            val secretKey = getOrCreateKeystoreKey()
            val encrypted = aesGcmEncrypt(secretKey, bytes)
            EncryptedPayload(
                encodedValue = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                isHardwareBacked = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Keystore AES-GCM unavailable, retrying alias once", e)
            runCatching {
                deleteKeystoreKey()
                val regeneratedKey = getOrCreateKeystoreKey()
                val encrypted = aesGcmEncrypt(regeneratedKey, bytes)
                EncryptedPayload(
                    encodedValue = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                    isHardwareBacked = true
                )
            }.getOrElse { fallbackEx ->
                Log.w(TAG, "Using device-scoped AES-GCM fallback cipher", fallbackEx)
                val fallbackKey = deriveDeviceFallbackKey()
                val encrypted = aesGcmEncrypt(fallbackKey, bytes)
                EncryptedPayload(
                    encodedValue = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                    isHardwareBacked = false
                )
            }
        }
    }

    override fun decrypt(payload: EncryptedPayload): String? {
        if (payload.encodedValue.isBlank()) return null
        return try {
            val rawBlob = Base64.decode(payload.encodedValue, Base64.NO_WRAP)
            val key = if (payload.isHardwareBacked) {
                getExistingKeystoreKey() ?: return null
            } else {
                deriveDeviceFallbackKey()
            }
            val plainBytes = aesGcmDecrypt(key, rawBlob) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt stored API key", e)
            null
        }
    }

    private fun getExistingKeystoreKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        getExistingKeystoreKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun deleteKeystoreKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun deriveDeviceFallbackKey(): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(appSaltSeed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun aesGcmEncrypt(key: SecretKey, plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        val output = ByteArray(2 + iv.size + cipherText.size)
        output[0] = PAYLOAD_VERSION
        output[1] = iv.size.toByte()
        System.arraycopy(iv, 0, output, 2, iv.size)
        System.arraycopy(cipherText, 0, output, 2 + iv.size, cipherText.size)
        return output
    }

    private fun aesGcmDecrypt(key: SecretKey, blob: ByteArray): ByteArray? {
        if (blob.size < 4 || blob[0] != PAYLOAD_VERSION) return null
        val ivLen = blob[1].toInt() and 0xFF
        if (ivLen <= 0 || blob.size <= 2 + ivLen) return null
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val cipherText = blob.copyOfRange(2 + ivLen, blob.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    companion object {
        private const val TAG = "AiStudioCipher"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "utk_notes_ai_studio_aes_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PAYLOAD_VERSION: Byte = 1
    }
}

class AiStudioConfigStore internal constructor(
    private val storage: KeyValueStorage,
    private val cipher: SecretCipher
) {
    constructor(context: Context) : this(
        storage = SharedPreferencesKeyValueStorage(
            context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        ),
        cipher = AndroidKeystoreSecretCipher(context.applicationContext)
    )

    fun getSavedEngineType(): AiEngineType {
        val raw = storage.getString(KEY_ENGINE_MODE, AiEngineType.GOOGLE_AI_STUDIO.name)
        return runCatching {
            AiEngineType.valueOf(raw ?: AiEngineType.GOOGLE_AI_STUDIO.name)
        }.getOrDefault(AiEngineType.GOOGLE_AI_STUDIO)
    }

    fun saveEngineType(engine: AiEngineType) {
        storage.putString(KEY_ENGINE_MODE, engine.name)
    }

    fun getSelectedModel(): String {
        val raw = storage.getString(KEY_SELECTED_MODEL, DEFAULT_AI_STUDIO_MODEL)
        return normalizeModelId(raw.orEmpty())
    }

    fun saveSelectedModel(modelId: String): AiApiSettingsState {
        val normalized = normalizeModelId(modelId)
        storage.putString(KEY_SELECTED_MODEL, normalized)
        return getSettingsSnapshot()
    }

    fun saveApiKey(rawKey: String): AiApiSettingsState {
        val sanitized = rawKey.trim()
        if (sanitized.isEmpty()) {
            return clearApiKey()
        }
        val encrypted = cipher.encrypt(sanitized)
        storage.putString(KEY_ENCRYPTED_API_KEY, encrypted.encodedValue)
        storage.putBoolean(KEY_IS_HARDWARE_ENCRYPTED, encrypted.isHardwareBacked)
        storage.putString(KEY_MASKED_KEY_PREVIEW, maskApiKey(sanitized))
        storage.putBoolean(KEY_CONNECTION_VERIFIED, false)
        return getSettingsSnapshot(validationState = ApiKeyValidationState.Idle)
    }

    fun getDecryptedApiKey(): String? {
        val encoded = storage.getString(KEY_ENCRYPTED_API_KEY, null) ?: return null
        if (encoded.isBlank()) return null
        val isHardwareBacked = storage.getBoolean(KEY_IS_HARDWARE_ENCRYPTED, true)
        return cipher.decrypt(EncryptedPayload(encoded, isHardwareBacked))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun clearApiKey(): AiApiSettingsState {
        storage.remove(KEY_ENCRYPTED_API_KEY)
        storage.remove(KEY_IS_HARDWARE_ENCRYPTED)
        storage.remove(KEY_MASKED_KEY_PREVIEW)
        storage.remove(KEY_CONNECTION_VERIFIED)
        storage.remove(KEY_DISCOVERED_MODELS_JSON)
        return getSettingsSnapshot(validationState = ApiKeyValidationState.Idle)
    }

    fun setConnectionVerified(verified: Boolean): AiApiSettingsState {
        storage.putBoolean(KEY_CONNECTION_VERIFIED, verified)
        return getSettingsSnapshot()
    }

    fun getDiscoveredModels(): List<AiStudioModelInfo> {
        val json = storage.getString(KEY_DISCOVERED_MODELS_JSON, null) ?: return emptyList()
        return deserializeDiscoveredModels(json)
    }

    fun saveDiscoveredModels(
        models: List<AiStudioModelInfo>,
        markVerified: Boolean = true
    ): AiApiSettingsState {
        val serialized = serializeDiscoveredModels(models)
        storage.putString(KEY_DISCOVERED_MODELS_JSON, serialized)
        if (markVerified) {
            storage.putBoolean(KEY_CONNECTION_VERIFIED, true)
        }
        return getSettingsSnapshot(
            validationState = if (markVerified) ApiKeyValidationState.Valid(models.size) else ApiKeyValidationState.Idle
        )
    }

    fun getSettingsSnapshot(
        validationState: ApiKeyValidationState = ApiKeyValidationState.Idle
    ): AiApiSettingsState {
        val hasStoredBlob = !storage.getString(KEY_ENCRYPTED_API_KEY, null).isNullOrBlank()
        val storedPreview = storage.getString(KEY_MASKED_KEY_PREVIEW, null).orEmpty()
        val isHardwareEncrypted = storage.getBoolean(KEY_IS_HARDWARE_ENCRYPTED, true)
        val decryptedIfNeeded = if (hasStoredBlob && storedPreview.isBlank()) {
            getDecryptedApiKey()
        } else {
            null
        }
        val effectiveHasKey = hasStoredBlob && (storedPreview.isNotBlank() || decryptedIfNeeded != null)
        val effectivePreview = when {
            !effectiveHasKey -> ""
            storedPreview.isNotBlank() -> storedPreview
            decryptedIfNeeded != null -> maskApiKey(decryptedIfNeeded)
            else -> "••••••••"
        }
        val isVerified = effectiveHasKey &&
            (validationState is ApiKeyValidationState.Valid || storage.getBoolean(KEY_CONNECTION_VERIFIED, false))

        return AiApiSettingsState(
            hasApiKey = effectiveHasKey,
            maskedKeyPreview = effectivePreview,
            isKeystoreEncrypted = isHardwareEncrypted,
            isConnectionVerified = isVerified,
            selectedModel = getSelectedModel(),
            discoveredModels = getDiscoveredModels(),
            validationState = validationState
        )
    }

    companion object {
        // Dedicated prefs file so DriveSyncManager's legacy cleanup on "utk_notes_prefs" never touches it.
        const val PREFS_FILE_NAME = "utk_ai_studio_secure_prefs"
        private const val KEY_ENGINE_MODE = "ai_engine_mode"
        private const val KEY_SELECTED_MODEL = "ai_studio_selected_model"
        private const val KEY_ENCRYPTED_API_KEY = "ai_studio_encrypted_key_v1"
        private const val KEY_IS_HARDWARE_ENCRYPTED = "ai_studio_key_is_keystore"
        private const val KEY_MASKED_KEY_PREVIEW = "ai_studio_masked_preview"
        private const val KEY_CONNECTION_VERIFIED = "ai_studio_connection_verified"
        private const val KEY_DISCOVERED_MODELS_JSON = "ai_studio_discovered_models_json"

        fun normalizeModelId(raw: String): String {
            val cleaned = raw
                .trim()
                .replace(Regex("""\s+"""), "")
                .removePrefix("models/")
                .removePrefix("MODELS/")
                .trim('/')
            return cleaned.ifBlank { DEFAULT_AI_STUDIO_MODEL }
        }

        fun maskApiKey(rawKey: String): String {
            val trimmed = rawKey.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.length >= 10) {
                "${trimmed.take(4)}••••••••${trimmed.takeLast(4)}"
            } else if (trimmed.length >= 4) {
                "••••${trimmed.takeLast(2)}"
            } else {
                "••••"
            }
        }

        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }

        private val discoveredModelsAdapter by lazy {
            val listType = Types.newParameterizedType(List::class.java, AiStudioModelInfo::class.java)
            moshi.adapter<List<AiStudioModelInfo>>(listType)
        }

        internal fun serializeDiscoveredModels(models: List<AiStudioModelInfo>): String {
            return discoveredModelsAdapter.toJson(models.distinctBy { it.id })
        }

        internal fun deserializeDiscoveredModels(rawJson: String): List<AiStudioModelInfo> {
            if (rawJson.isBlank()) return emptyList()
            return try {
                discoveredModelsAdapter.fromJson(rawJson).orEmpty().mapNotNull { item ->
                    val rawId = item.id.trim()
                    if (rawId.isEmpty()) return@mapNotNull null
                    val id = normalizeModelId(rawId)
                    AiStudioModelInfo(
                        id = id,
                        displayName = item.displayName.ifBlank { id },
                        description = item.description
                    )
                }.distinctBy { it.id }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
