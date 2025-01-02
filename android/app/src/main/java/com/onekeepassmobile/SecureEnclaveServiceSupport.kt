package com.onekeepassmobile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import onekeepass.mobile.ffi.SecureEnclaveCbService
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec


class SecureEnclaveServiceSupport(val reactContext: ReactApplicationContext) :
    SecureEnclaveCbService {

    private val TAG = "SecureEnclaveServiceSupport";

    // GCM specific size info
    private val TAG_LENGTH_BIT: Int = 128
    private val IV_LENGTH_BYTE: Int = 12

    override fun encryptBytes(identifier: String, plainData: ByteArray): ByteArray {
        try {
            Log.d(TAG, "Received data and size is ${plainData.size}")

            // Need to create or get an existing key for this alias
            val key = getKey(identifier)

            // This did not work and saw the exception "java.security.InvalidAlgorithmParameterException: Caller-provided IV not permitted"
            /*
            val iv = ByteArray(IV_LENGTH_BYTE)
            SecureRandom().nextBytes(iv)
            val ivSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            */

            // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            // We need to get the internally generated iv so that we can use it later during decryption
            val iv = cipher.iv

            // IMPORTANT: For now we use the hard coded TAG_LENGTH_BIT (128) and IV_LENGTH_BYTE 12
            // If we want to use the value GCMParameterSpec, then we need to get these values as shown and store it in the SharedPreference
            // for later use

            //val gcmParameterSpec = cipher.parameters.getParameterSpec(GCMParameterSpec::class.java)
            //Log.d(TAG,"The gcmParameterSpec used during encryption time is ${gcmParameterSpec.iv.size}, ${gcmParameterSpec.tLen}, $gcmParameterSpec")
            //Log.d(TAG, "Cipher init done for encryption and Iv size is ${iv.size}, " + " blocksize ${cipher.blockSize}, " +" parameters ${cipher.parameters}")

            var outData = cipher.doFinal(plainData)
            Log.d(TAG, "Data encrypted and size is ${outData.size}")

            // We prefix the iv wit the encrypted data
            outData = iv + outData
            Log.d(TAG, "Iv + Data encrypted and size is ${outData.size}")

            return outData
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Exception in encryptBytes  $e")
            throw e
        }
    }

    override fun decryptBytes(identifier: String, encryptedData: ByteArray): ByteArray {
        try {
            Log.d(TAG, "Received data (encrypted) and size is ${encryptedData.size}")
            val key = getKey(identifier)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            // Need to extract the iv from the incoming bytes data
            val iv = encryptedData.copyOfRange(0, IV_LENGTH_BYTE)
            // Get the data part
            val dataPart = encryptedData.copyOfRange(IV_LENGTH_BYTE, encryptedData.size)

            // As we use GCM mode, we need to use GCMParameterSpec
            val ivSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

            // Decrypt the data
            val outData = cipher.doFinal(dataPart)

            Log.d(TAG, "Data encrytped and size is ${outData.size}")

            return outData

        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Exception in decryptBytes $e")
            throw e
        }
    }

    override fun removeKey(identifier: String): Boolean {
        try {
            Log.d(TAG, "In removeKey going to get AndroidKeyStore instance")
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            Log.d(TAG, "Deleting identifier $identifier from key store")
            keyStore.deleteEntry(identifier)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Exception in removeKey $e")
            throw e
        }
    }

    fun getKey(identifierAsAlias: String): SecretKey {
        try {
            // We use 'AndroidKeyStore' as it operates within a Trusted Execution Environment (TEE)
            // provided by Trusty OS. It is in an isolated environment within the device’s
            // processor or chipset

            // See for more details here https://developer.android.com/privacy-and-security/keystore

            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            // Need to make this load call. Otherwise, we see the java.security.KeyStoreException: Uninitialized keystore
            keyStore.load(null)
            val aliasFound = keyStore.containsAlias(identifierAsAlias)

            if (!aliasFound) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
                )
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(
                        identifierAsAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                val key = keyGenerator.generateKey()

                Log.d(TAG, "Returning newly created key $key")

                return key
            } else {
                Log.d(TAG, "The alias $identifierAsAlias is found and getting the AndroidKeyStore")

                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val key = keyStore.getKey(identifierAsAlias, null) as SecretKey

                Log.d(TAG, "Returning existing  key $key")

                return key
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Exception in getKey $e")
            throw e
        }
    }
}