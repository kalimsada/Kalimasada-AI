package com.kalimasada.aitrinity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        val prefs = getSharedPreferences("trinity_secure_prefs", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                var gemini by remember { mutableStateOf(prefs.getString("gemini", "") ?: "") }
                var groq by remember { mutableStateOf(prefs.getString("groq", "") ?: "") }
                var cohere by remember { mutableStateOf(prefs.getString("cohere", "") ?: "") }
                var prompt by remember { mutableStateOf("") }
                var answer by remember { mutableStateOf("Selamat datang di AI TRINITY. Masukkan API key penyedia AI, lalu mulai bertanya.") }
                var busy by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("Siap") }
                val scope = rememberCoroutineScope()
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("AI TRINITY 1.0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Tim AI: Gemini + Groq + Cohere", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    var showKeys by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showKeys = !showKeys }) { Text(if (showKeys) "Tutup pengaturan API" else "Atur API key") }
                    if (showKeys) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            OutlinedTextField(gemini, { gemini = it }, label = { Text("Gemini API key") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(groq, { groq = it }, label = { Text("Groq API key") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(cohere, { cohere = it }, label = { Text("Cohere API key") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                prefs.edit().putString("gemini", gemini).putString("groq", groq).putString("cohere", cohere).apply()
                                status = "API key tersimpan di perangkat"
                                showKeys = false
                            }) { Text("Simpan API key") }
                            Text("Gunakan API key milik Anda. Jangan bagikan key kepada siapa pun.", style = MaterialTheme.typography.bodySmall)
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
                            busy = true; status = "Menghubungi model..."
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { askTeam(q, gemini, groq, cohere) }
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

private fun postJson(endpoint: String, apiKey: String, body: JSONObject, auth: String = "Bearer"): String {
    val conn = (URL(endpoint).openConnection() as HttpURLConnection)
    conn.requestMethod = "POST"
    conn.connectTimeout = 25000
    conn.readTimeout = 45000
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    if (auth == "x-goog") conn.setRequestProperty("x-goog-api-key", apiKey)
    else if (auth == "Cohere") conn.setRequestProperty("Authorization", "Bearer $apiKey")
    else conn.setRequestProperty("Authorization", "Bearer $apiKey")
    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    val text = stream.bufferedReader().use { it.readText() }
    if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}: ${text.take(250)}")
    return text
}

private fun askTeam(q: String, gemini: String, groq: String, cohere: String): Pair<String, String> {
    val results = mutableListOf<Pair<String, String>>()
    val errors = mutableListOf<String>()
    if (gemini.isNotBlank()) try {
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", q)))))
        val raw = postJson("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent", gemini, body, "x-goog")
        val text = JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        results.add("Gemini" to text)
    } catch (e: Exception) { errors.add("Gemini: ${e.message}") }
    if (groq.isNotBlank()) try {
        val body = JSONObject().put("model", "llama-3.3-70b-versatile").put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
        val raw = postJson("https://api.groq.com/openai/v1/chat/completions", groq, body)
        val text = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        results.add("Groq" to text)
    } catch (e: Exception) { errors.add("Groq: ${e.message}") }
    if (cohere.isNotBlank()) try {
        val body = JSONObject().put("model", "command-r-08-2024").put("message", q)
        val raw = postJson("https://api.cohere.com/v2/chat", cohere, body, "Cohere")
        val text = JSONObject(raw).getJSONObject("message").getJSONArray("content").getJSONObject(0).getString("text")
        results.add("Cohere" to text)
    } catch (e: Exception) { errors.add("Cohere: ${e.message}") }
    if (results.isEmpty()) return Pair("Belum ada model yang berhasil menjawab. Periksa API key dan koneksi internet.\n\n" + errors.joinToString("\n"), "Tidak ada model aktif")
    val combined = results.joinToString("\n\n") { "【${it.first}】\n${it.second}" }
    val status = "${results.size}/3 model berhasil" + if (errors.isNotEmpty()) " • ${errors.size} gagal" else ""
    return Pair("Hasil analisis tim:\n\n$combined\n\n---\nCatatan: jawaban di atas menggabungkan hasil model yang benar-benar berhasil merespons. Sintesis otomatis lintas-model akan ditambahkan pada tahap berikutnya.", status)
}
