package com.khedr.smartassistant.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Direct Gemini Developer API client. A valid API key with access/quota for this model is required. */
object GeminiFlashClient {
    const val MODEL = "gemini-3.5-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    data class Result(val success: Boolean, val text: String = "", val message: String = "")

    fun generate(apiKey: String, incoming: String, contactName: String, relationship: String, canned: List<String>): Result {
        val prompt = """
أنت مساعد لصياغة رد قصير على رسالة واتساب نيابة عن المستخدم.
اسم المرسل: $contactName
معلومات العلاقة والأسلوب: ${relationship.ifBlank { "غير محددة" }}
الردود المفضلة للمستخدم (استعن بها إذا كانت مناسبة ولا تكررها حرفياً بشكل غير طبيعي): ${canned.joinToString(" | ")}
الرسالة الواردة: $incoming
اكتب الرد المقترح فقط باللغة المناسبة للرسالة وبأسلوب طبيعي. لا تخترع مواعيد أو وعوداً أو معلومات شخصية. إذا كانت الرسالة حساسة أو تطلب قراراً أو مالاً، اقترح ردّاً محايداً يطلب التوضيح ولا تتخذ قراراً.
راجع الإملاء والنحو، واحذف التكرار والرموز المبالغ فيها والكلمات غير المناسبة. لا تضف علامات اقتباس حول الرد.
        """.trimIndent()
        return request(apiKey, prompt)
    }

    fun test(apiKey: String): Result = request(apiKey, "Reply with exactly: Gemini API connection successful")

    private fun request(apiKey: String, prompt: String): Result {
        var conn: HttpURLConnection? = null
        return try {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
            conn = connection
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("temperature", 0.65).put("maxOutputTokens", 220))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = try { JSONObject(raw).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                Result(false, message = when (code) {
                    400, 401, 403 -> "المفتاح غير صالح أو لا يملك صلاحية لهذا النموذج. ${msg.orEmpty()}"
                    429 -> "تم تجاوز حد الاستخدام أو الحصة المتاحة. ${msg.orEmpty()}"
                    else -> "تعذر الاتصال بـ Gemini (HTTP $code). ${msg.orEmpty()}"
                })
            } else {
                val root = JSONObject(raw)
                val text = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.let { parts ->
                    (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }.joinToString("\n")
                }.orEmpty().trim()
                if (text.isBlank()) Result(false, message = "لم يرجع Gemini نصاً صالحاً.") else Result(true, text = text)
            }
        } catch (e: Exception) {
            Result(false, message = "فشل الاتصال: ${e.localizedMessage ?: "تحقق من الإنترنت"}")
        } finally { conn?.disconnect() }
    }
}
