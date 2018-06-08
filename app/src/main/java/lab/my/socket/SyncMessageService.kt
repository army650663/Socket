package lab.my.socket

import android.annotation.TargetApi
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import android.os.Message
import android.os.Messenger
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import lab.my.socket.MainActivity.Companion.CHANNEL_ID
import lab.my.socket.MainActivity.Companion.SOCKET_CONNECT
import lab.my.socket.MainActivity.Companion.SOCKET_RECEIVE_MSG
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*






@TargetApi(Build.VERSION_CODES.LOLLIPOP)
/**
 * Author:      chenshaowei
 * Version      V1.0
 * Description:
 * Modification History:
 * Date         Author          version         Description
 * ---------------------------------------------------------------------
 * 2018/6/7      chenshaowei         V1.0.0          Create
 * Why and What is modified:
 */
class SyncMessageService: JobService() {
    private var notificationId = 0
    private var messenger: Messenger? = null
    private var socket: Socket? = null
    private var input: BufferedReader? = null
    private var output: BufferedWriter? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            messenger = it.getParcelableExtra(MainActivity.MESSENGER_INTENT_KEY)
        }

        return START_NOT_STICKY
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        println("onStartJob")
        val ip = params?.extras?.getString("ip")
        val port = params?.extras?.getInt("port")
        if (ip != null && port != null) {
            doAsync {
                if (socket == null) {
                    socket = Socket(ip, port)
                    socket?.let {socket ->
                        input = socket.getInputStream().bufferedReader()
                        output = socket.getOutputStream().bufferedWriter()
                        input?.let { input->
                            startServerReplyListener(input)
                        }
                        if (socket.isConnected) {
                            sendMessage(SOCKET_CONNECT, output)
                        }
                    }

                } else {
                    closeAll()
                    sendMessage(MainActivity.SOCKET_DISCONNECT)
                    jobFinished(params, false)
                }
            }
        }

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        println("onStopJob")
        closeAll()
        return true
    }


    private fun startServerReplyListener(reader: BufferedReader) {
        doAsync {
            try {
                val time = SimpleDateFormat("YYYY-MM-dd hh:mm:ss", Locale.getDefault())
                val response = reader.readLine()
                val map = hashMapOf<String, String>()
                map["response"] = response
                map["time"] = time.format(Date())
                uiThread {
                    showNotification("Server response", response)
                }
                Log.i("server response", response)
                sendMessage(SOCKET_RECEIVE_MSG, map)
                startServerReplyListener(reader)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

    }

    private fun sendMessage(messageId: Int, params: Any? = null) {
        messenger?.let {
            val message = Message.obtain()
            message.what = messageId
            message.obj = params
            it.send(message)
        }
    }



    private fun showNotification(title: String, content: String) {
        println("title: $title, content: $content")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.abc_ic_star_black_48dp)
        val notificationManager = NotificationManagerCompat.from(this)

        notificationManager.notify(notificationId++, notificationBuilder.build())
    }

    private fun closeAll() {
        socket?.close()
        input?.close()
        output?.close()
        socket = null
        input = null
        output = null
    }

}