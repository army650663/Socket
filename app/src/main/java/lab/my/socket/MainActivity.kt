package lab.my.socket

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
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
    private var responseList = arrayListOf<HashMap<String, String>>()
    private val responseAdapter = ResponseAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = this
        ip_ed.setText(defaultSharedPreferences.getString("ip", ""))
        port_ed.setText(defaultSharedPreferences.getInt("port", 81).toString())
        sid_ed.setText(defaultSharedPreferences.getString("sid", ""))
        did_ed.setText(defaultSharedPreferences.getString("did", ""))
        msg_ed.setText(defaultSharedPreferences.getString("message", ""))
        serverResponse_lv.adapter = responseAdapter
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

            try {
                doAsync {
                    output?.write("$sid$did$messages")
                    output?.newLine()
                    output?.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    private fun startServerReplyListener(reader: BufferedReader) {
        doAsync {
            try {
                val time = SimpleDateFormat("YYYY-MM-dd hh:mm:ss", Locale.getDefault())
                val response = reader.readLine()
                val map = hashMapOf<String, String>()
                map["response"] = response
                map["time"] = time.format(Date())
                responseList.add(0, map)
                println(responseList.toString())
                uiThread {
                    responseAdapter.notifyDataSetChanged()
                }
                startServerReplyListener(reader)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

    }

    inner class ResponseAdapter : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            var view: View
            if (convertView == null) {
                view = LayoutInflater.from(this@MainActivity).inflate(R.layout.cell_server_response, parent, false)
            } else {
                view = convertView
            }
            val dataMap = responseList[position]
            val response_tv: TextView = view.findViewById(R.id.response_tv)
            val time_tv: TextView = view.findViewById(R.id.time_tv)

            response_tv.text = dataMap["response"]
            time_tv.text = dataMap["time"]
            return view
        }

        override fun getItem(position: Int): Any {
            return responseList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return responseList.size
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

