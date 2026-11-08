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
                var openai by remember { mutableStateOf(prefs.getString("openai", "") ?: "") }
                var claude by remember { mutableStateOf(prefs.getString("claude", "") ?: "") }
                var gemini by remember { mutableStateOf(prefs.getString("gemini", "") ?: "") }
                var prompt by remember { mutableStateOf("") }
                var answer by remember { mutableStateOf("Selamat datang di KALIMASADA 1.0. Hubungkan API key ChatGPT, Claude, dan Gemini untuk memulai.") }
                var busy by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("Siap") }
                var showKeys by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("KALIMASADA 1.0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Satu tim AI: ChatGPT + Claude + Gemini", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { showKeys = !showKeys }) { Text(if (showKeys) "Tutup pengaturan" else "Pengaturan koneksi AI") }
                    if (showKeys) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            OutlinedTextField(openai, { openai = it }, label = { Text("OpenAI API key (ChatGPT)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(claude, { claude = it }, label = { Text("Anthropic API key (Claude)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(gemini, { gemini = it }, label = { Text("Google API key (Gemini)") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                prefs.edit().putString("openai", openai.trim()).putString("claude", claude.trim()).putString("gemini", gemini.trim()).apply()
                                status = "Pengaturan tersimpan di perangkat"
                                showKeys = false
                            }) { Text("Simpan koneksi") }
                            Text("API key diperlukan dari masing-masing penyedia. Simpan hanya di perangkat Anda dan jangan bagikan.", style = MaterialTheme.typography.bodySmall)
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
                                val result = withContext(Dispatchers.IO) { askTeam(q, openai, claude, gemini) }
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

private fun request(endpoint: String, key: String, body: JSONObject, provider: String): String {
    val conn = URL(endpoint).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        when (provider) {
            "openai" -> conn.setRequestProperty("Authorization", "Bearer $key")
            "claude" -> {
                conn.setRequestProperty("x-api-key", key)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
            }
            else -> conn.setRequestProperty("x-goog-api-key", key)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("HTTP $code: ${response.take(180)}")
        return response
    } finally {
        conn.disconnect()
    }
}

private fun askTeam(q: String, openai: String, claude: String, gemini: String): Pair<String, String> {
    val results = mutableListOf<Pair<String, String>>()
    val errors = mutableListOf<String>()
    if (openai.isNotBlank()) try {
        val body = JSONObject().put("model", "gpt-4o-mini")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
        val raw = request("https://api.openai.com/v1/chat/completions", openai, body, "openai")
        results.add("ChatGPT" to JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
    } catch (e: Exception) { errors.add("ChatGPT: ${e.message}") }
    if (claude.isNotBlank()) try {
        val body = JSONObject().put("model", "claude-3-5-haiku-latest").put("max_tokens", 1200)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
        val raw = request("https://api.anthropic.com/v1/messages", claude, body, "claude")
        results.add("Claude" to JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text"))
    } catch (e: Exception) { errors.add("Claude: ${e.message}") }
    if (gemini.isNotBlank()) try {
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", q)))))
        val raw = request("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent", gemini, body, "gemini")
        val answer = JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        results.add("Gemini" to answer)
    } catch (e: Exception) { errors.add("Gemini: ${e.message}") }
    if (results.isEmpty()) return Pair("Belum ada model yang berhasil menjawab. Buka Pengaturan koneksi AI, masukkan minimal satu API key yang valid, lalu coba lagi.\n\n" + errors.joinToString("\n"), "Tidak ada model aktif")
    val combined = results.joinToString("\n\n") { "【${it.first}】\n${it.second}" }
    val status = "${results.size}/3 model berhasil" + if (errors.isNotEmpty()) " • ${errors.size} gagal" else ""
    return Pair("Hasil dari tim KALIMASADA:\n\n$combined\n\n---\nSetiap model menjawab secara terpisah; belum ada sintesis otomatis antar-model.", status)
}
