package com.example.fulluikotlin.ui.utils

import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    const val initVector = "DevelopedByAmwer"
    const val CryptoSecretKey = "should 16 char!!"

    fun encryptThis(value: String): String {
        return try {
            val iv = IvParameterSpec(initVector.toByteArray(StandardCharsets.UTF_8))
            val skeySpec = SecretKeySpec(CryptoSecretKey.toByteArray(StandardCharsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv)
            val encrypted = cipher.doFinal(value.toByteArray())
            stringToHex(Base64.encodeToString(encrypted, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e("Cant Encrypt String", e.toString())
            value
        }
    }

    fun decryptThis(encrypted: String?): String {
        if (encrypted.isNullOrEmpty()) {
            return ""
        }
        return try {
            val b64Encrypted = hexToString(encrypted)
            val iv = IvParameterSpec(initVector.toByteArray(StandardCharsets.UTF_8))
            val skeySpec = SecretKeySpec(CryptoSecretKey.toByteArray(StandardCharsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv)
            val original = cipher.doFinal(Base64.decode(b64Encrypted, Base64.DEFAULT))
            String(original)
        } catch (e: Exception) {
            Log.e("Cant Decrypt String", e.toString())
            encrypted
        }
    }

    fun stringToHex(arg: String): String {
        return String.format("%040x", BigInteger(1, arg.toByteArray(StandardCharsets.UTF_8)))
    }

    fun hexToString(arg: String): String {
        return buildString {
            for (i in arg.indices step 2) {
                val hexPair = arg.substring(i, i + 2)
                val decimal = hexPair.toInt(16)
                append(decimal.toChar())
            }
        }
    }
}