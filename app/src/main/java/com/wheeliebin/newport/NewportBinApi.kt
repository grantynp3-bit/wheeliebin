package com.wheeliebin.newport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Client for Newport City Council's bin collection lookup, which is powered by the
 * iTouchVision "iCollectionDay" widget. The endpoint takes an AES-encrypted JSON payload
 * (containing a UPRN) in a request header, and returns an AES-encrypted JSON response.
 *
 * These constants and the encryption scheme come from the widget Newport City Council
 * embeds on https://www.newport.gov.uk/recycling-and-waste/collections/check-your-collection-day
 * (client/council IDs are specific to Newport; other councils using the same iTouchVision
 * platform use different IDs).
 */
object NewportBinApi {

    private const val ENDPOINT = "https://iweb.itouchvision.com/portal/itouchvision/kmbd/collectionDay"
    private const val CLIENT_ID = 130
    private const val COUNCIL_ID = 260
    private const val LANG_CODE = "EN"

    private const val KEY_HEX = "F57E76482EE3DC3336495DEDEEF3962671B054FE353E815145E29C5689F72FEC"
    private const val IV_HEX = "2CBF4FC35C69B82362D393A4F0B9971A"

    // Newport's iTouchVision API returns dates as "dd-MM-yyyy" (e.g. "25-08-2026"),
    // not ISO 8601 — using the wrong formatter here silently drops every collection.
    private val apiDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    class BinApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun fetchBinDates(uprn: String): List<BinCollection> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("P_CLIENT_ID", CLIENT_ID)
            .put("P_COUNCIL_ID", COUNCIL_ID)
            .put("P_LANG_CODE", LANG_CODE)
            .put("P_UPRN", uprn)
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
                throw BinApiException("Newport bin lookup failed (HTTP $status)")
            }
            if (body.isBlank()) {
                throw BinApiException("Newport bin lookup returned an empty response")
            }
            val trimmed = body.trim()
            if (trimmed.startsWith("<")) {
                throw BinApiException("Newport bin lookup returned an error page instead of data")
            }

            val decryptedJson = decrypt(trimmed)
            parseCollections(decryptedJson)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCollections(json: String): List<BinCollection> {
        val root = JSONObject(json)
        val array = root.optJSONArray("collectionDay")
            ?: throw BinApiException("Unexpected response shape from Newport bin lookup")

        val results = mutableListOf<BinCollection>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val type = item.optString("binType").ifBlank { "Bin" }
            val rawDate = item.optString("collectionDay")
            val date = runCatching { LocalDate.parse(rawDate, apiDateFormat) }.getOrNull() ?: continue
            results.add(BinCollection(type, date))
        }
        return results.sortedBy { it.date }
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
