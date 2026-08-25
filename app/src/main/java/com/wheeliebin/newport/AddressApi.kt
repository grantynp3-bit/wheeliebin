package com.wheeliebin.newport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A single address returned by Newport's address lookup, paired with its UPRN.
 */
data class AddressResult(val uprn: String, val fullAddress: String)

/**
 * Client for Newport City Council's address lookup (same iTouchVision widget as
 * NewportBinApi, different endpoint). Given a postcode, it returns every address
 * iTouchVision knows about for that postcode, each with its UPRN — this lets the
 * app offer a "pick your address" flow instead of requiring the UPRN to be typed
 * in directly, mirroring Newport's own "check your collection day" tool. Because
 * this uses Newport's own client/council IDs, it will only ever return results for
 * postcodes Newport City Council collects for.
 */
object AddressApi {

    private const val ENDPOINT = "https://iweb.itouchvision.com/portal/itouchvision/kmbd/address"
    private const val CLIENT_ID = 130
    private const val COUNCIL_ID = 260
    private const val LANG_CODE = "EN"

    private const val KEY_HEX = "F57E76482EE3DC3336495DEDEEF3962671B054FE353E815145E29C5689F72FEC"
    private const val IV_HEX = "2CBF4FC35C69B82362D393A4F0B9971A"

    class AddressApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun findAddresses(postcode: String): List<AddressResult> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("P_CLIENT_ID", CLIENT_ID)
            .put("P_COUNCIL_ID", COUNCIL_ID)
            .put("P_LANG_CODE", LANG_CODE)
            .put("P_POSTCODE", postcode)
            .toString()

        val encryptedHex = encrypt(payload)

        val url = URL(ENDPOINT)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("P_PARAMETER", encryptedHex)
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw AddressApiException("Newport address lookup failed (HTTP $status)")
            }
            if (body.isBlank()) {
                throw AddressApiException("Newport address lookup returned an empty response")
            }
            val trimmed = body.trim()
            if (trimmed.startsWith("<")) {
                throw AddressApiException("Newport address lookup returned an error page instead of data")
            }

            val decryptedJson = decrypt(trimmed)
            parseAddresses(decryptedJson)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAddresses(json: String): List<AddressResult> {
        val root = JSONObject(json)
        val array = root.optJSONArray("ADDRESS") ?: return emptyList()

        val results = mutableListOf<AddressResult>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uprn = item.opt("UPRN")?.toString()?.trim().orEmpty()
            val address = item.optString("FULL_ADDRESS")
            if (uprn.isBlank() || address.isBlank()) continue
            results.add(AddressResult(uprn, address))
        }
        return results
    }

    private fun keySpec() = SecretKeySpec(hexToBytes(KEY_HEX), "AES")
    private fun ivSpec() = IvParameterSpec(hexToBytes(IV_HEX))

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(), ivSpec())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return bytesToHex(encrypted)
    }

    private fun decrypt(hexText: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec(), ivSpec())
        val decrypted = cipher.doFinal(hexToBytes(hexText))
        return String(decrypted, Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val index = i * 2
            out[i] = ((Character.digit(clean[index], 16) shl 4) +
                    Character.digit(clean[index + 1], 16)).toByte()
        }
        return out
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
