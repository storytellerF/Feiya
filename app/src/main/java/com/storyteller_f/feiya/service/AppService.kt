package com.storyteller_f.feiya.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.feiya.MainActivity
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.appendText
import com.storyteller_f.feiya.cacheInvalid
import com.storyteller_f.feiya.dataStore
import com.storyteller_f.feiya.removeUri
import com.storyteller_f.feiya.saveFile
import com.storyteller_f.feiya.savedUriFile
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

val Context.portFlow
    get() = dataStore.data.map {
        it[stringPreferencesKey("port")]?.toInt() ?: AppService.DEFAULT_PORT
    }

val specialEvent = MutableStateFlow<Int?>(null)

class AppService : LifecycleService() {
    val server = AppServer(this)

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.d(TAG, "onBind() called with: intent = $intent")
        return ServiceBinder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            TAG,
            "onStartCommand() called with: intent = $intent, flags = $flags, startId = $startId"
        )
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate() called")
        super.onCreate()
        installNotificationChannel()
        installDefaultNotificationChannel()

        lifecycleScope.launch {
            combine(portFlow, specialEvent) { port, eventPort ->
                eventPort to port
            }.collect { (event, port) ->
                Log.i(TAG, "onCreate: port $event $port")
                server.onReceiveEventPort(port, event)
            }
        }
        lifecycleScope.launch {
            server.state.collectLatest {
                when (it) {
                    is ServerState.Started -> postForegroundNotify("Work on ${it.port}")

                    is ServerState.Error -> postForegroundNotify(
                        it.exceptionMessage
                    )

                    is ServerState.Stopped -> postForegroundNotify("Stopped")

                    ServerState.Init -> postForegroundNotify("Idle")
                }
            }
        }

    }

    private fun installNotificationChannel() {
        val channelId = FOREGROUND_CHANNEL_ID
        val channel =
            NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_MIN)
                .apply {
                    setName("running")
                    setDescription(getString(R.string.foreground_service_channel_description))
                }.build()
        val managerCompat = NotificationManagerCompat.from(this)
        if (managerCompat.getNotificationChannel(channelId) == null)
            managerCompat.createNotificationChannel(channel)
    }

    private fun installDefaultNotificationChannel() {
        val channelId = DEFAULT_CHANNEL_ID
        val channel =
            NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_MIN)
                .apply {
                    setName("default")
                    setDescription("default")
                }.build()
        val managerCompat = NotificationManagerCompat.from(this)
        if (managerCompat.getNotificationChannel(channelId) == null)
            managerCompat.createNotificationChannel(channel)
    }


    private fun postForegroundNotify(message: String) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            val notification =
                NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(message)
                    .build()
            notification.contentIntent = openMainActivity()
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun postNotify(message: String) {
        val managerCompat = NotificationManagerCompat.from(this)
        if (managerCompat.areNotificationsEnabled()) {
            val notification =
                NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(message)
                    .build()
            notification.contentIntent = openMainActivity()
            managerCompat.notify(DEFAULT_NOTIFICATION_ID, notification)
        }
    }

    private fun openMainActivity() = PendingIntentCompat.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        0,
        false
    )

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        super.onDestroy()
        server.stopBlocking()
        lifecycleScope.cancel()
    }

    fun onUserGrantNotificationPermission() {
        postNotify("已同意权限")
    }

    fun saveToLocal(uri: Uri, info: SharedFileInfo) {
        lifecycleScope.launch {
            saveFile(File(info.name).extension, uri)
            removeUri(info)
            cacheInvalid()//when save to local
            server.emitRefreshEvent()
        }
    }

    fun sendMessage(message: String) {
        lifecycleScope.launch {
            server.sendMessage(message)
        }
    }

    fun restart() {
        postNotify("restart")
        specialEvent.value = EVENT_RESTART
    }

    fun stop() {
        postNotify("stop")
        specialEvent.value = EVENT_STOP
    }

    class ServiceBinder(val service: AppService) : Binder() {
        fun sendMessage(it: String) {
            service.sendMessage(it)
        }

        fun saveToLocal(uri: Uri, it: SharedFileInfo) {
            service.saveToLocal(uri, it)
        }

        fun restart() {
            service.restart()
        }

        fun stop() {
            service.stop()
        }

        fun appendUri(uri: Uri) {
            service.appendUri(uri)
        }

        fun deleteUri(uri: Uri, info: SharedFileInfo) {
            service.deleteUri(uri, info)
        }

    }

    private fun appendUri(uri: Uri) {
        println("appendUri $uri")
        lifecycleScope.launch {
            savedUriFile.appendText(uri.toString())
            cacheInvalid()//when save
            server.emitRefreshEvent()
        }
    }

    private fun deleteUri(uri: Uri, info: SharedFileInfo) {
        lifecycleScope.launch {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                removeUri(info)
            }
            cacheInvalid()//when delete
            server.emitRefreshEvent()
        }

    }

    companion object {
        private const val TAG = "AppService"
        private const val FOREGROUND_NOTIFICATION_ID = 10
        private const val FOREGROUND_CHANNEL_ID = "foreground"
        private const val DEFAULT_NOTIFICATION_ID = 10
        private const val DEFAULT_CHANNEL_ID = "default"
        const val DEFAULT_PORT = 8080
        const val LISTENER_ADDRESS = "0.0.0.0"
        const val DEFAULT_ADDRESS = "127.0.0.1"

        const val VALID_PORT = 1_000
        const val EVENT_STOP = -1
        const val EVENT_RESTART = -2
        const val EVENT_OFF = -3
    }
}

data class SseEvent(val data: String, val event: String? = null, val id: String? = null)

@Serializable
data class SharedFileInfo(val uri: String, val name: String)

suspend fun ApplicationCall.respondSse(eventFlow: Flow<SseEvent>) {
    response.cacheControl(CacheControl.NoCache(null))
    respondBytesWriter(contentType = ContentType.Text.EventStream) {
        eventFlow.collect { event ->
            println("sse event $event")
            if (event.id != null) {
                writeStringUtf8("id: ${event.id}\n")
            }
            if (event.event != null) {
                writeStringUtf8("event: ${event.event}\n")
            }
            for (dataLine in event.data.lines()) {
                writeStringUtf8("data: $dataLine\n")
            }
            writeStringUtf8("\n")
            flush()
        }
    }
}