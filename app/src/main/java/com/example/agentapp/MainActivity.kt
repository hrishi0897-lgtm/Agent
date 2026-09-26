package com.example.agentapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val serverUrl = "http://127.0.0.1:8765"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMedia = "application/json".toMediaType()

    private lateinit var chatLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatLog = findViewById(R.id.chatLog)
        scrollView = findViewById(R.id.scrollView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                appendLog("You: $text")
                messageInput.setText("")
                sendMessage(text)
            }
        }
    }

    private fun appendLog(line: String) {
        mainHandler.post {
            chatLog.append("$line\n\n")
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun sendMessage(message: String) {
        val body = JSONObject().put("message", message).toString().toRequestBody(jsonMedia)
        val request = Request.Builder().url("$serverUrl/chat").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                appendLog("Could not reach the agent server: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val turnId = json.optString("turn_id")
                if (turnId.isNotEmpty()) pollStatus(turnId)
            }
        })
    }

    private fun pollStatus(turnId: String) {
        val request = Request.Builder().url("$serverUrl/status?turn_id=$turnId").get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                appendLog("Lost connection while waiting: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: "{}")
                when (json.optString("status")) {
                    "needs_confirmation" -> {
                        val command = json.optString("command")
                        mainHandler.post { showConfirmDialog(turnId, command) }
                    }
                    "done" -> {
                        appendLog("Agent: ${json.optString("response")}")
                    }
                    else -> {
                        mainHandler.postDelayed({ pollStatus(turnId) }, 1000)
                    }
                }
            }
        })
    }

    private fun showConfirmDialog(turnId: String, command: String) {
        AlertDialog.Builder(this)
            .setTitle("Allow this command?")
            .setMessage(command)
            .setPositiveButton("Allow") { _, _ -> sendConfirm(turnId, true) }
            .setNegativeButton("Deny") { _, _ -> sendConfirm(turnId, false) }
            .setCancelable(false)
            .show()
    }

    private fun sendConfirm(turnId: String, allow: Boolean) {
        val body = JSONObject().put("turn_id", turnId).put("allow", allow)
            .toString().toRequestBody(jsonMedia)
        val request = Request.Builder().url("$serverUrl/confirm").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                appendLog("Could not send confirmation: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                pollStatus(turnId)
            }
        })
    }
}
