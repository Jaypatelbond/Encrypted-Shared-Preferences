package com.encryptedsharedprefrencesdemo.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A singleton class that provides encrypted shared preferences functionality.
 * This class uses the Android Keystore and EncryptedSharedPreferences to securely store and retrieve preferences.
 */
@Singleton
class EncPref @Inject constructor(@ApplicationContext private val context: Context) {

    private val EMPTY_ASSOCIATED_DATA = ByteArray(0)
    private val ENC_SUFFIX = "_defaultEnc"
    private var KEY_URI = "android-keystore://"
    private var mDefaultPref: SharedPreferences? = null
    private var aead: Aead? = null

    init {
        setupEncryption()
        initPref()
    }

    private fun setupEncryption() {
        try {
            HybridConfig.register()
            KEY_URI = "$KEY_URI${context.packageName}"
            aead = getOrGenerateKeyHandle()?.getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Failed to initialize AEAD encryption: ${e.message}")
        }
    }

    private fun getOrGenerateKeyHandle(): KeysetHandle? {
        return try {
            AndroidKeysetManager.Builder()
                .withSharedPref(
                    context,
                    base64Encode(context.packageName.toByteArray(Charsets.US_ASCII)),
                    base64Encode("${context.packageName}$ENC_SUFFIX".toByteArray(Charsets.US_ASCII))
                )
                .withKeyTemplate(KeyTemplates.get("AES128_GCM"))
                .withMasterKeyUri(KEY_URI)
                .build()
                .keysetHandle
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Error generating or retrieving key handle")
            null
        }
    }

    private fun initPref() {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            mDefaultPref = EncryptedSharedPreferences.create(
                context,
                context.packageName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Failed to create EncryptedSharedPreferences: ${e.message}", e)
        }
    }

    /**
     * Encodes the input byte array to a Base64 string.
     *
     * @param input The byte array to encode.
     * @return The Base64 encoded string.
     */
    private fun base64Encode(input: ByteArray): String? {
        return try {
            Base64.encodeToString(input, Base64.DEFAULT)
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Base64 encoding failed: ${e.message}", e)
            ""
        }
    }

    /**
     * Decodes the Base64 encoded input string to a byte array.
     *
     * @param input The Base64 encoded string to decode.
     * @return The decoded byte array.
     */
    private fun base64Decode(input: String): ByteArray? {
        return try {
            Base64.decode(input, Base64.DEFAULT)
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Base64 decoding failed: ${e.message}", e)
            null
        }
    }

    /**
     * Encrypts the given text using the AEAD encryption.
     *
     * @param textToEncrypt The text to encrypt.
     * @return The encrypted text in Base64 format, or the original text if encryption fails.
     */
    private fun encryptText(textToEncrypt: String?): String? {
        return try {
            val cipherText = aead?.encrypt(
                textToEncrypt?.toByteArray(Charsets.UTF_8),
                EMPTY_ASSOCIATED_DATA
            )
            cipherText?.let { base64Encode(it) }
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Encryption failed: ${e.message}")
            null
        }
    }

    /**
     * Decrypts the given Base64 encoded text using AEAD decryption.
     *
     * @param textToDecrypt The Base64 encoded text to decrypt.
     * @return The decrypted text, or the original text if decryption fails.
     */
    private fun decryptText(textToDecrypt: String?): String? {
        return try {
            val decoded = base64Decode(textToDecrypt ?: "")
            val plainText = aead?.decrypt(decoded, EMPTY_ASSOCIATED_DATA)
            plainText?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Decryption failed: ${e.message}")
            null
        }
    }


    /**
     * Retrieves a value from shared preferences and decrypts it.
     *
     * @param key The key associated with the value.
     * @return The decrypted value, or `null` if not found.
     */
    private fun getValueFromPref(key: String): String? {
        Timber.tag("EncPref").d("Getting value for key: $key")
        return try {
            val encryptedKey = base64Encode(key.toByteArray(Charsets.US_ASCII))
            val encryptedValue = mDefaultPref?.getString(encryptedKey, "")
            Timber.tag("EncPref").d("Raw encrypted value from pref for key $key: $encryptedValue")

            val result = decryptText(encryptedValue)
            Timber.tag("EncPref").d("Retrieved decrypted value for key $key: $result")
            result
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Failed to get value from preferences: ${e.message}")
            null
        }
    }

    /**
     * Encrypts and stores a value in shared preferences.
     *
     * @param key The key associated with the value.
     * @param value The value to store.
     */
    private fun setValueOnPref(key: String, value: String) {
        Timber.tag("EncPref").d("Setting value for key: $key, value: $value")
        try {
            val encryptedValue = encryptText(value) ?: value
            Timber.tag("EncPref").d("Encrypted value to be stored for key $key: $encryptedValue")

            mDefaultPref?.edit()?.apply {
                putString(
                    base64Encode(key.toByteArray(Charsets.US_ASCII)),
                    encryptedValue
                )
                apply()
            }
            Timber.tag("EncPref").d("Successfully set value for key: $key")
        } catch (e: Exception) {
            Timber.tag("EncPref").e("Failed to set value in preferences: ${e.message}")
        }
    }

    /**
     * Retrieves a [String] value from shared preferences.
     *
     * @param key The key associated with the value.
     * @param defaultValue The default value to return if the key is not found.
     * @return The value associated with the key, or the default value if not found.
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return getValueFromPref(key) ?: defaultValue
    }

    /**
     * Stores a [String] value in shared preferences.
     *
     * @param key The key associated with the value.
     * @param value The value to store.
     */
    fun putString(key: String, value: String) {
        setValueOnPref(
            key,
            value
        )
    }

    /**
     * Retrieves a [Boolean] value from shared preferences.
     *
     * @param key The key associated with the value.
     * @param defaultValue The default value to return if the key is not found.
     * @return The boolean value associated with the key, or the default value if not found.
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        val value = getValueFromPref(key)
        return if (value.isNullOrEmpty()) defaultValue else value.toBoolean()
    }

    /**
     * Stores a [Boolean] value in shared preferences.
     *
     * @param key The key associated with the value.
     * @param value The boolean value to store.
     */
    fun putBoolean(key: String, value: Boolean) {
        setValueOnPref(
            key,
            value.toString()
        )
    }

    /**
     * Retrieves a [Long] value from shared preferences.
     *
     * @param key The key associated with the value.
     * @param defaultValue The default value to return if the key is not found.
     * @return The long value associated with the key, or the default value if not found.
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        val value = getValueFromPref(key)
        return if (value.isNullOrEmpty()) defaultValue else value.toLong()
    }

    /**
     * Stores a [Long] value in the preferences.
     *
     * @param key The key under which the value will be stored.
     * @param value The [Long] value to be stored.
     */
    fun putLong(key: String, value: Long) {
        setValueOnPref(
            key,
            value.toString()
        )
    }

    /**
     * Retrieves an [Int] value from the preferences.
     *
     * @param key The key from which the value will be retrieved.
     * @param defaultValue The default value to return if the key does not exist or the value is invalid (default is 0).
     * @return The [Int] value associated with the key, or [defaultValue] if the key does not exist or the value is invalid.
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        val value = getValueFromPref(key)
        return if (value.isNullOrEmpty()) defaultValue else value.toInt()
    }

    /**
     * Stores an [Int] value in the preferences.
     *
     * @param key The key under which the value will be stored.
     * @param value The [Int] value to be stored.
     */
    fun putInt(key: String, value: Int) {
        setValueOnPref(
            key,
            value.toString()
        )
    }

    /**
     * Retrieves a [Double] value from the preferences.
     *
     * @param key The key from which the value will be retrieved.
     * @param defaultValue The default value to return if the key does not exist or the value is invalid (default is 0.0).
     * @return The [Double] value associated with the key, or [defaultValue] if the key does not exist or the value is invalid.
     */
    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        val value = getValueFromPref(key)
        return if (value.isNullOrEmpty()) defaultValue else value.toDouble()
    }

    /**
     * Stores a [Double] value in the preferences.
     *
     * @param key The key under which the value will be stored.
     * @param value The [Double] value to be stored.
     */
    fun putDouble(key: String, value: Double) {
        setValueOnPref(
            key,
            value.toString()
        )
    }

    /**
     * Retrieves a [Float] value from the preferences.
     *
     * @param key The key from which the value will be retrieved.
     * @param defaultValue The default value to return if the key does not exist or the value is invalid (default is 0.0f).
     * @return The [Float] value associated with the key, or [defaultValue] if the key does not exist or the value is invalid.
     */
    fun getFloat(key: String, defaultValue: Float = 0.0f): Float {
        val value = getValueFromPref(key)
        return if (value.isNullOrEmpty()) defaultValue else value.toFloat()
    }

    /**
     * Stores a [Float] value in the preferences.
     *
     * @param key The key under which the value will be stored.
     * @param value The [Float] value to be stored.
     */
    fun putFloat(key: String, value: Float) {
        setValueOnPref(
            key,
            value.toString()
        )
    }

    /**
     * Clears all values from the preferences.
     */
    fun clear() {
        try {
            mDefaultPref?.edit { clear() }
        } catch (e: Exception) {
            e.printStackTrace() // Log the exception
        }
    }

    /**
     * Removes a specific key and its associated value from the preferences.
     *
     * @param key The key to be removed.
     */
    fun removeKey(key: String) {
        try {
            mDefaultPref?.edit()?.apply {
                remove(
                    base64Encode(
                        key.toByteArray(Charsets.US_ASCII)
                    )
                )
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace() // Log the exception
        }
    }
}


