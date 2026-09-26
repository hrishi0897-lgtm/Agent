package com.example.agentapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val serverUrl = "http://127.0.0.1:8765"
    private val mainHandler =
        Handler(Looper.getMainLooper())
    private val jsonMedia =
        "application/json".toMediaType()

    private lateinit var chatLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var projectLabel: TextView
    private lateinit var switchProjectButton: Button

    private var currentProject: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatLog = findViewById(R.id.chatLog)
        scrollView = findViewById(R.id.scrollView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        projectLabel = findViewById(R.id.projectLabel)
        switchProjectButton =
            findViewById(R.id.switchProjectButton)

        sendButton.setOnClickListener {
            val text =
                messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                appendLog("You: $text")
                messageInput.setText("")
                sendMessage(text)
            }
        }

        switchProjectButton.setOnClickListener {
            showProjectPicker()
        }

        refreshProjects()
    }

    private fun appendLog(line: String) {
        mainHandler.post {
            chatLog.append("$line\n\n")
            scrollView.post {
                scrollView.fullScroll(
                    ScrollView.FOCUS_DOWN
                )
            }
        }
    }

    private fun setProjectLabel(name: String) {
        currentProject = name
        projectLabel.text = "Project: $name"
    }

    private fun sendMessage(message: String) {
        val body = JSONObject()
            .put("message", message)
            .toString().toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("$serverUrl/chat")
            .post(body)
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call, e: IOException
                ) {
                    appendLog(
                        "Could not reach server: " +
                        e.message
                    )
                }

                override fun onResponse(
                    call: Call, response: Response
                ) {
                    val text =
                        response.body?.string() ?: "{}"
                    val json = JSONObject(text)
                    val turnId = json.optString("turn_id")
                    val project =
                        json.optString("project")
                    if (project.isNotEmpty()) {
                        mainHandler.post {
                            setProjectLabel(project)
                        }
                    }
                    if (turnId.isNotEmpty()) {
                        pollStatus(turnId)
                    }
                }
            }
        )
    }

    private fun pollStatus(turnId: String) {
        val url =
            "$serverUrl/status?turn_id=$turnId"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call, e: IOException
                ) {
                    appendLog(
                        "Lost connection: " + e.message
                    )
                }

                override fun onResponse(
                    call: Call, response: Response
                ) {
                    val text =
                        response.body?.string() ?: "{}"
                    val json = JSONObject(text)
                    when (json.optString("status")) {
                        "needs_confirmation" -> {
                            val cmd =
                                json.optString("command")
                            mainHandler.post {
                                showConfirmDialog(
                                    turnId, cmd
                                )
                            }
                        }
                        "done" -> {
                            val reply = json.optString(
                                "response"
                            )
                            appendLog("Agent: $reply")
                        }
                        else -> {
                            mainHandler.postDelayed(
                                { pollStatus(turnId) },
                                1000
                            )
                        }
                    }
                }
            }
        )
    }

    private fun showConfirmDialog(
        turnId: String, command: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("Allow this command?")
            .setMessage(command)
            .setPositiveButton("Allow") { _, _ ->
                sendConfirm(turnId, true)
            }
            .setNegativeButton("Deny") { _, _ ->
                sendConfirm(turnId, false)
            }
            .setCancelable(false)
            .show()
    }

    private fun sendConfirm(
        turnId: String, allow: Boolean
    ) {
        val body = JSONObject()
            .put("turn_id", turnId)
            .put("allow", allow)
            .toString().toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("$serverUrl/confirm")
            .post(body)
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call, e: IOException
                ) {
                    appendLog(
                        "Confirm failed: " + e.message
                    )
                }

                override fun onResponse(
                    call: Call, response: Response
                ) {
                    pollStatus(turnId)
                }
            }
        )
    }

    private fun refreshProjects() {
        val request = Request.Builder()
            .url("$serverUrl/projects")
            .get()
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call, e: IOException
                ) {
                    appendLog(
                        "Could not load projects: " +
                        e.message
                    )
                }

                override fun onResponse(
                    call: Call, response: Response
                ) {
                    val text =
                        response.body?.string() ?: "{}"
                    val json = JSONObject(text)
                    val current =
                        json.optString("current")
                    mainHandler.post {
                        if (current.isNotEmpty()) {
                            setProjectLabel(current)
                        }
                    }
                }
            }
        )
    }

    private fun showProjectPicker() {
        val request = Request.Builder()
            .url("$serverUrl/projects")
            .get()
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call, e: IOException
                ) {
                    appendLog(
                        "Could not load projects: " +
                        e.message
                    )
                }

                override fun onResponse(
                    call: Call, response: Response
                ) {
                    val text =
                        response.body?.string() ?: "{}"
                    val json = JSONObject(text)
                    val arr: JSONArray =
                        json.optJSONArray("projects")
                            ?: JSONArray()
                    val names = ArrayList<String>()
                    for (i in 0 until arr.length()) {
                        names.add(arr.getString(i))
                    }
                    mainHandler.post {
                        showProjectListDialog(names)
                    }
                }
            }
        )
    }

    private fun showProjectListDialog(
        names: ArrayList<String>
    ) {
        val items = Array(names.size + 1) { i ->
            if (i < names.size) names[i]
            else "+ New project"
        }

        AlertDialog.Builder(this)
            .setTitle("Switch project")
            .setItems(items) { _, which ->
                if (which < names.size) {
                    switchTo(names[which])
                } else {
                    showCreateProjectDialog()
                }
            }
            .show()
    }

    private fun switchTo(name: String) {
        postProject(name, "switch")
    }

    private fun showCreateProjectDialog() {
        val input = EditText(this)
        input.inputType =
            InputType.TYPE_CLASS_TEXT
        input.hint = "project-name"

        AlertDialog.Builder(this)
            .setTitle("New project")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name =
                    input.text.toString().trim()
                if (name.isNotEmpty()) {
                    postProject(name, "create")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun postProject(
        name: String, action: String
    ) {
        val body = JSONObject()
            .put("name", name)
            .put("action", action)
            .toString().toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("$serverUrl/project")
            .post(body)
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call, e: IOException
                ) {
                    appendLog(
                        "Project switch failed: " +
                        e.message
                    )
                }

                override fun onResponse(
                    call: Call, response: Response
                ) {
                    val text =
                        response.body?.string() ?: "{}"
                    val json = JSONObject(text)
                    val ok = json.optBoolean("ok")
                    val current =
                        json.optString("current")
                    if (!ok) {
                        val err =
                            json.optString("error")
                        appendLog(
                            "Project error: $err"
                        )
                        return
                    }
                    mainHandler.post {
                        setProjectLabel(current)
                        appendLog(
                            "Switched to project: " +
                            current
                        )
                    }
                }
            }
        )
    }
}
