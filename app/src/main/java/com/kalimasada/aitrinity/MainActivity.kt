package com.kalimasada.aitrinity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("kalimasada_prefs", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                var gemini by remember { mutableStateOf(prefs.getString("gemini", "") ?: "") }
                var meta by remember { mutableStateOf(prefs.getString("meta", "") ?: "") }
                var grok by remember { mutableStateOf(prefs.getString("grok", "") ?: "") }
                var prompt by remember { mutableStateOf("") }
                var answer by remember { mutableStateOf("Selamat datang di KALIMASADA 1.0. Hubungkan API key Gemini, Meta AI, dan Grok untuk memulai.") }
                var busy by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("Siap") }
                var showKeys by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("KALIMASADA 1.0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Satu tim AI: Gemini + Meta AI + Grok", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { showKeys = !showKeys }) { Text(if (showKeys) "Tutup pengaturan" else "Pengaturan koneksi AI") }
                    if (showKeys) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            OutlinedTextField(gemini, { gemini = it }, label = { Text("Google Gemini API key") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(meta, { meta = it }, label = { Text("Meta Model API key") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(grok, { grok = it }, label = { Text("xAI Grok API key") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                prefs.edit().putString("gemini", gemini.trim()).putString("meta", meta.trim()).putString("grok", grok.trim()).apply()
                                status = "Pengaturan tersimpan di perangkat"
                                showKeys = false
                            }) { Text("Simpan koneksi") }
                            Text("API key resmi diperlukan dari masing-masing penyedia. Key disimpan di perangkat; jangan bagikan atau unggah ke GitHub.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Jawaban tim", fontWeight = FontWeight.SemiBold)
                    Surface(Modifier.weight(1f).fillMaxWidth(), tonalElevation = 2.dp) {
                        Text(answer, Modifier.padding(12.dp).verticalScroll(rememberScrollState()))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(prompt, { prompt = it }, label = { Text("Tulis pertanyaan...") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !busy && prompt.isNotBlank(), onClick = {
                            val q = prompt.trim()
                            busy = true
                            status = "Menghubungi tim AI..."
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { askTeam(q, gemini, meta, grok) }
                                answer = result.first
                                status = result.second
                                busy = false
                            }
                        }) { Text(if (busy) "Memproses..." else "Kirim") }
                    }
                }
            }
        }
    }
}

private fun postJson(endpoint: String, key: String, body: JSONObject, headerName: String = "Authorization", prefix: String = "Bearer "): JSONObject {
    val conn = URL(endpoint).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 20000
        conn.readTimeout = 90000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty(headerName, prefix + key)
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("HTTP $code: ${response.take(180)}")
        return JSONObject(response)
    } finally {
        conn.disconnect()
    }
}

private fun chatBody(model: String, prompt: String): JSONObject =
    JSONObject().put("model", model)
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))

private fun askTeam(q: String, gemini: String, meta: String, grok: String): Pair<String, String> {
    val results = mutableListOf<Pair<String, String>>()
    val errors = mutableListOf<String>()

    if (gemini.isNotBlank()) try {
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", q)))))
        val raw = postJson("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", gemini, body, "x-goog-api-key", "")
        val text = raw.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        results.add("Gemini" to text)
    } catch (e: Exception) { errors.add("Gemini: ${e.message}") }

    if (meta.isNotBlank()) try {
        val raw = postJson("https://api.meta.ai/v1/chat/completions", meta, chatBody("muse-spark-1.3", q))
        results.add("Meta AI" to raw.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
    } catch (e: Exception) { errors.add("Meta AI: ${e.message}") }

    if (grok.isNotBlank()) try {
        val raw = postJson("https://api.x.ai/v1/chat/completions", grok, chatBody("grok-4.7", q))
        results.add("Grok" to raw.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
    } catch (e: Exception) { errors.add("Grok: ${e.message}") }

    if (results.isEmpty()) return Pair("Belum ada model yang berhasil menjawab. Masukkan minimal satu API key yang valid di Pengaturan koneksi AI.\n\n" + errors.joinToString("\n"), "Tidak ada model aktif")

    val contributions = results.joinToString("\n\n") { "【${it.first}】\n${it.second}" }
    var synthesis = ""
    if (gemini.isNotBlank()) try {
        val synthesisPrompt = "Pertanyaan pengguna:\n$q\n\nJawaban anggota tim:\n$contributions\n\nSatukan poin terbaik menjadi satu jawaban akhir yang akurat, praktis, dan ringkas. Sebutkan jika ada perbedaan pendapat atau ketidakpastian. Jangan mengarang fakta."
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Anda adalah koordinator tim KALIMASADA. Sintesis jawaban tim secara objektif."))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", synthesisPrompt)))))
        val raw = postJson("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", gemini, body, "x-goog-api-key", "")
        synthesis = raw.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    } catch (e: Exception) { errors.add("Sintesis Gemini: ${e.message}") }

    val output = if (synthesis.isNotBlank()) {
        "JAWABAN AKHIR TIM KALIMASADA\n\n$synthesis\n\n---\nRINCIAN JAWABAN MODEL\n\n$contributions"
    } else {
        "HASIL TIM KALIMASADA\n\n$contributions\n\nSintesis otomatis belum tersedia. Pastikan API Gemini aktif untuk menyatukan jawaban."
    }
    val status = "${results.size}/3 model berhasil" + if (errors.isNotEmpty()) " • ${errors.size} kendala" else ""
    return Pair(output + if (errors.isNotEmpty()) "\n\nCatatan:\n" + errors.joinToString("\n") else "", status)
}
