package lab.my.socket

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var context: Context
    private var socket: Socket? = null
    private var input: BufferedReader? = null
    private var output: BufferedWriter? = null

    private var isConnect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = this
        ip_ed.setText(defaultSharedPreferences.getString("ip", ""))
        port_ed.setText(defaultSharedPreferences.getInt("port", 81).toString())
        sid_ed.setText(defaultSharedPreferences.getString("sid", ""))
        did_ed.setText(defaultSharedPreferences.getString("did", ""))
        msg_ed.setText(defaultSharedPreferences.getString("message", ""))

        connect_btn.setOnClickListener {
            val ip = ip_ed.text.toString().trim()
            val port = port_ed.text.toString().trim().toInt()
            defaultSharedPreferences.edit().putString("ip", ip).apply()
            defaultSharedPreferences.edit().putInt("port", port).apply()
            doAsync {
                if (socket != null) {
                    closeSocket()
                } else {
                    socket = Socket(ip, port)
                    input = socket?.getInputStream()?.bufferedReader()
                    output = socket?.getOutputStream()?.bufferedWriter()
                    startServerReplyListener(input!!)
                }
                uiThread {
                    connect_btn.text = if (socket == null) "Connect" else "Disconnect"
                }
            }
        }

        send_btn.setOnClickListener {
            val sid = sid_ed.text.toString()
            val did = did_ed.text.toString()
            val messages = msg_ed.text.toString()
            defaultSharedPreferences.edit().putString("sid", sid).apply()
            defaultSharedPreferences.edit().putString("did", did).apply()
            defaultSharedPreferences.edit().putString("message", messages).apply()

            serverResult_tv.text = "Waiting for server response..."
            try {
                doAsync {
                    output?.write("$sid$did$messages")
                    output?.newLine()
                    output?.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                serverResult_tv.text = e.message
            }

        }
    }

    private fun startServerReplyListener(reader: BufferedReader) {
        doAsync {
            try {
                val time = SimpleDateFormat("YYYY-MM-dd hh:mm:ss", Locale.getDefault())
                val response = reader.readLine()
                println(response)
                uiThread {
                    time_tv.text = time.format(Date())
                    serverResult_tv.text = response
                }
                startServerReplyListener(reader)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

    }


    private fun closeSocket() {
        output?.close()
        input?.close()
        socket?.close()
        socket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeSocket()
    }
}

