package lab.my.socket

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.textColor
import java.io.BufferedWriter
import java.lang.ref.WeakReference


class MainActivity : AppCompatActivity() {
    companion object {
        const val MESSENGER_INTENT_KEY = "ACTIVITY_MESSENGER_INTENT_KEY"
        const val MSG_JOB_START = 0
        const val MSG_JOB_STOP = 1
        const val MSG_ONJOB_START = 2
        const val MSG_ONJOB_STOP = 3
        const val SOCKET_CONNECT = 4
        const val SOCKET_DISCONNECT = 5
        const val SOCKET_RECEIVE_MSG = 6
        const val CHANNEL_ID = "SERVER_RESPONSE"
    }

    private var handler: IncomingMessageHandler? = null
    private lateinit var context: Context
    private lateinit var syncJobScheduler: JobScheduler

    private var responseList = arrayListOf<HashMap<*, *>>()
    private val responseAdapter = ResponseAdapter()

    private var jobId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        syncJobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        handler = IncomingMessageHandler(this@MainActivity)
        context = this
        ip_ed.setText(defaultSharedPreferences.getString("ip", ""))
        port_ed.setText(defaultSharedPreferences.getInt("port", 81).toString())
        sid_ed.setText(defaultSharedPreferences.getString("sid", ""))
        did_ed.setText(defaultSharedPreferences.getString("did", ""))
        msg_ed.setText(defaultSharedPreferences.getString("message", ""))
        serverResponse_lv.adapter = responseAdapter
        createNotificationChannel()


        connect_btn.setOnClickListener {
            val ip = ip_ed.text.toString().trim()
            val port = port_ed.text.toString().trim().toInt()
            defaultSharedPreferences.edit().putString("ip", ip).apply()
            defaultSharedPreferences.edit().putInt("port", port).apply()
            startSyncJob(ip, port)

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
                    handler?.sendMessageToServer("$sid$did$messages")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    fun setSocketStatus(isConnect: Boolean) {
        if (isConnect) {
            connect_btn.text = "Disconnect"
            connect_btn.textColor = Color.RED
        } else {
            connect_btn.text = "Connect"
            connect_btn.textColor = Color.BLACK
        }
    }

    fun addData(data: HashMap<*, *>) {
        responseList.add(0, data)
        responseAdapter.notifyDataSetChanged()
    }

    override fun onStart() {
        super.onStart()
        val startServiceIntent = Intent(context, SyncMessageService::class.java)
        val messengerIncoming = Messenger(handler)
        startServiceIntent.putExtra(MESSENGER_INTENT_KEY, messengerIncoming)
        startService(startServiceIntent)
    }

    override fun onStop() {
        super.onStop()
        stopService(Intent(context, SyncMessageService::class.java))
    }

    private fun startSyncJob(ip: String, port: Int) {
        val syncMessageService = ComponentName(this, SyncMessageService::class.java)
        val extras = PersistableBundle()
        extras.putString("ip", ip)
        extras.putInt("port", port)

        val jobInfo = JobInfo.Builder(jobId++, syncMessageService)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(AlarmManager.INTERVAL_FIFTEEN_MINUTES)
                .setRequiresCharging(false)// 需要满足充电状态
                .setRequiresDeviceIdle(false)// 设备处于Idle(Doze)
                .setPersisted(true) //设备重启后是否继续执行
                .setBackoffCriteria(3000, JobInfo.BACKOFF_POLICY_LINEAR)
                .setExtras(extras)
                .build()
        syncJobScheduler.schedule(jobInfo)
    }


    private fun stopSyncJob() {
        syncJobScheduler.cancelAll()
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

            response_tv.text = dataMap["response"] as String
            time_tv.text = dataMap["time"] as String
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

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Server"
            val description = "Server response"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    inner class IncomingMessageHandler(activity: MainActivity): Handler() {
        private val weakActivity: WeakReference<MainActivity> = WeakReference(activity)
        private var output: BufferedWriter? = null
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            msg?.let {
                when (msg.what) {
                    SOCKET_CONNECT -> {
                        weakActivity.get()?.setSocketStatus(true)
                        output = msg.obj as BufferedWriter
                    }

                    SOCKET_DISCONNECT -> {
                        weakActivity.get()?.setSocketStatus(false)
                        output?.close()
                        output = null
                    }

                    SOCKET_RECEIVE_MSG -> {
                        val message = msg.obj as HashMap<*, *>
                        weakActivity.get()?.addData(message)

                    }

                    else -> {

                    }
                }
            }
        }

        public fun sendMessageToServer(message: String) {
            output?.let {
                it.write(message)
                it.newLine()
                it.flush()
            }
        }
    }
}

