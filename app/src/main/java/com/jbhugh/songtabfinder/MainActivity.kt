package com.jbhugh.songtabfinderfree

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jbhugh.songtabfinderfree.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var recordButton: Button
    private lateinit var clearButton: Button
    private lateinit var statusText: TextView
    private lateinit var songsterrLink: Button
    private lateinit var ugLink: Button
    private lateinit var progressBar: ProgressBar
    private var mediaRecorder: MediaRecorder? = null
    private val RECORD_REQUEST_CODE = 101
    private val client = OkHttpClient()

    private val accessKey = "3ebe28971e7fd6561cc91d9c10a67283"
    private val accessSecret = "4mtioPufwFF90vUv4R5HpF5ZS3GphuEdKNBX2A45"
    private val host = "identify-us-west-2.acrcloud.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordButton = findViewById(R.id.record_button)
        clearButton = findViewById(R.id.clear_button)
        statusText = findViewById(R.id.status_text)
        songsterrLink = findViewById(R.id.songsterr_link)
        ugLink = findViewById(R.id.ug_link)
        progressBar = findViewById(R.id.progress_bar)

        // Check mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_REQUEST_CODE)
        }

        recordButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                statusText.text = "Listening..."
                progressBar.visibility = View.VISIBLE
                recordButton.isEnabled = false
                thread { identifySong() }
            } else {
                statusText.text = "Mic permission denied"
            }
        }

        clearButton.setOnClickListener {
            statusText.text = "Ready to record"
            songsterrLink.visibility = View.GONE
            ugLink.visibility = View.GONE
            recordButton.isEnabled = true
        }
    }

    private fun identifySong() {
        val audioFile = File(cacheDir, "temp_audio.mp3")
        try {
            Log.d("ACRCloud", "Starting recording to ${audioFile.absolutePath}")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            Log.d("ACRCloud", "Recording started")
            Thread.sleep(5000) // 5s
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.d("ACRCloud", "Recording stopped, file size: ${audioFile.length()} bytes")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                runOnUiThread {
                    statusText.text = "Recording failed: No audio file"
                    progressBar.visibility = View.GONE
                    recordButton.isEnabled = true
                }
                Log.e("ACRCloud", "Audio file missing or empty")
                return
            }

            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
            val signature = generateSignature(accessKey, accessSecret, "POST", "/v1/identify", timestamp)
            val request = Request.Builder()
                .url("https://$host/v1/identify")  // Updated to HTTPS
                .post(createRequestBody(audioFile, accessKey, signature, timestamp))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    runOnUiThread {
                        statusText.text = "API error: ${response.code}"
                        progressBar.visibility = View.GONE
                        recordButton.isEnabled = true
                    }
                    Log.e("ACRCloud", "API failed with code: ${response.code}")
                    return@use
                }
                val json = JSONObject(response.body?.string() ?: "{}")
                Log.d("ACRCloud", "Full Response: $json")

                val statusMsg = json.getJSONObject("status").getString("msg")
                if (statusMsg == "Success" && json.has("metadata") && json.getJSONObject("metadata").has("music")) {
                    val musicArray = json.getJSONObject("metadata").getJSONArray("music")
                    if (musicArray.length() > 0) {
                        val music = musicArray.getJSONObject(0)
                        val title = music.getString("title")
                        val artist = if (music.has("artists") && music.getJSONArray("artists").length() > 0) {
                            music.getJSONArray("artists").getJSONObject(0).getString("name")
                        } else "Unknown Artist"
                        runOnUiThread {
                            statusText.text = "$title - $artist"
                            updateLinks(title, artist)
                            progressBar.visibility = View.GONE
                            recordButton.isEnabled = true
                        }
                    } else {
                        runOnUiThread {
                            statusText.text = "Couldn't identify song (no music data)"
                            progressBar.visibility = View.GONE
                            recordButton.isEnabled = true
                        }
                    }
                } else {
                    runOnUiThread {
                        statusText.text = "Couldn't identify song ($statusMsg)"
                        progressBar.visibility = View.GONE
                        recordButton.isEnabled = true
                    }
                }
                audioFile.delete()
            }
        } catch (e: Exception) {
            runOnUiThread {
                statusText.text = "Identification failed: ${e.message}"
                progressBar.visibility = View.GONE
                recordButton.isEnabled = true
            }
            Log.e("ACRCloud", "Error: $e")
            mediaRecorder?.release()
            mediaRecorder = null
            if (audioFile.exists()) audioFile.delete()
        }
    }

    private fun generateSignature(accessKey: String, accessSecret: String, method: String, uri: String, timestamp: String): String {
        val dataType = "audio"
        val signatureVersion = "1"
        val stringToSign = "$method\n$uri\n$accessKey\n$dataType\n$signatureVersion\n$timestamp"
        Log.d("ACRCloud", "String to sign: $stringToSign")
        val hmacSha1 = javax.crypto.Mac.getInstance("HmacSHA1")
        val secretKey = javax.crypto.spec.SecretKeySpec(accessSecret.toByteArray(), "HmacSHA1")
        hmacSha1.init(secretKey)
        val signedBytes = hmacSha1.doFinal(stringToSign.toByteArray())
        val signature = Base64.getEncoder().encodeToString(signedBytes)
        Log.d("ACRCloud", "Generated signature: $signature")
        return signature
    }

    private fun createRequestBody(audioFile: File, accessKey: String, signature: String, timestamp: String): RequestBody {
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("sample", "audio.mp3", audioFile.asRequestBody("audio/mpeg".toMediaType()))
            .addFormDataPart("access_key", accessKey)
            .addFormDataPart("data_type", "audio")
            .addFormDataPart("signature_version", "1")
            .addFormDataPart("signature", signature)
            .addFormDataPart("sample_bytes", audioFile.length().toString())
            .addFormDataPart("timestamp", timestamp)
            .build()
    }

    private fun updateLinks(title: String, artist: String) {
        val encodedQuery = Uri.encode("$title $artist")
        val songsterrUrl = "https://www.songsterr.com/?pattern=$encodedQuery"
        val ugUrl = "https://www.ultimate-guitar.com/search.php?search_type=title&value=$title+$artist"

        // Songsterr
        runOnUiThread {
            songsterrLink.text = "Songsterr"
            songsterrLink.visibility = View.VISIBLE
            songsterrLink.isEnabled = true
            songsterrLink.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(songsterrUrl)))
            }
        }

        // Ultimate Guitar
        thread {
            val ugRequest = Request.Builder().url(ugUrl).head().build()
            try {
                client.newCall(ugRequest).execute().use { response ->
                    runOnUiThread {
                        ugLink.text = "Ultimate Guitar"
                        ugLink.visibility = View.VISIBLE
                        ugLink.isEnabled = response.isSuccessful
                        if (response.isSuccessful) {
                            ugLink.setOnClickListener {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ugUrl)))
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    ugLink.text = "Ultimate Guitar"
                    ugLink.isEnabled = false
                    ugLink.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Mic permission required"
        }
    }
}