package com.storyteller_f.feiya.service

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.util.filter
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

var channelWaitWorker: CompletableDeferred<MutableSharedFlow<SseEvent>>? = null

sealed interface ServerState {
    data object Init : ServerState
    class Started(
        val port: Int,
        val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
        val chatSession: DefaultClientWebSocketSession,
        val client: HttpClient,
        val channel: MutableSharedFlow<SseEvent>,
        val messageList: MutableStateFlow<List<Message>>,
        val time: Long
    ) : ServerState

    data class Stopped(val reason: String) : ServerState

    data class Error(val cause: Throwable) : ServerState {

        val exceptionMessage = (cause.localizedMessage ?: cause::class.qualifiedName
        ?: cause::class.toString())
    }
}

class AppServer(service: AppService) {
    private val scope = service.lifecycleScope
    private val context = service
    val state = MutableStateFlow<ServerState>(ServerState.Init)

    val messagesCache get() = (state.value as? ServerState.Started)?.messageList?.asStateFlow()

    private suspend fun startInternal(port: Int) {
        Log.d(TAG, "startInternal() called")
        try {
            val client = httpClient()
            val (server, channel) = setupServer(port, client)
            val (session, messageFlow) = setupSelfClient(port, client)
            val time = System.currentTimeMillis()
            state.value = ServerState.Started(
                port,
                server,
                session,
                client,
                channel,
                messageFlow,
                time
            )
            Log.i(TAG, "startInternal: $time")
        } catch (th: Throwable) {
            Log.e(TAG, "startInternal: ${th.localizedMessage}", th)
            emitErrorState(th)
        }
    }

    private suspend fun setupServer(
        port: Int,
        client: HttpClient
    ): Pair<EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>, MutableSharedFlow<SseEvent>> {
        channelWaitWorker = CompletableDeferred()
        clientRef = WeakReference(client)
        contextRef = WeakReference(context)
        return embeddedServer(
            Netty,
            port = port,
            host = AppService.LISTENER_ADDRESS,
        ) {
            module()
        }.start(wait = false) to channelWaitWorker!!.await()
    }


    private suspend fun setupSelfClient(
        port: Int,
        client: HttpClient
    ): Pair<DefaultClientWebSocketSession, MutableStateFlow<List<Message>>> {

        val messagesCache = MutableStateFlow<List<Message>>(emptyList())
        val sessionWaitWorker = CompletableDeferred<DefaultClientWebSocketSession>()
        scope.launch {
            client.webSocket(
                method = HttpMethod.Get,
                host = "127.0.0.1",
                port = port,
                path = "/chat"
            ) {
                sessionWaitWorker.complete(this)
                try {
                    while (true) {
                        val receiveDeserialized = receiveDeserialized<Message>()
                        messagesCache.value = messagesCache.value.plus(receiveDeserialized)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startInternal: self webSocket", e)
                }
            }
            Log.i(TAG, "startInternal: self webSocket end")
        }
        return (sessionWaitWorker.await() to messagesCache)
    }

    private fun httpClient(): HttpClient {
        return HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = 20.seconds
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }
    }

    private suspend fun stopInternal(cause: String) {
        Log.d(TAG, "stopInternal() called")
        val serverState = state.value
        if (serverState is ServerState.Started) {
            serverState.chatSession.close()
            serverState.client.close()
            serverState.server.stop()
            state.value = ServerState.Stopped(cause)
        }
    }


    private suspend fun stopIfNeed(cause: String) {
        when (state.value) {
            is ServerState.Started -> {
                stopInternal(cause)
            }

            is ServerState.Init -> {
                return
            }

            is ServerState.Error -> {
                return
            }

            is ServerState.Stopped -> {
                return
            }
        }
    }

    private suspend fun startIfNeed(port: Int) {
        when (val current = state.value) {
            is ServerState.Started -> {
                if (current.port == port) {
                    return
                } else {
                    stopInternal("port changed")
                    startInternal(port)
                }
            }

            is ServerState.Init -> {
                startInternal(port)
            }

            is ServerState.Error -> {
                startInternal(port)
            }

            is ServerState.Stopped -> {
                startInternal(port)
            }
        }
    }

    private fun emitErrorState(cause: Throwable) {
        state.value = ServerState.Error(cause)
    }

    suspend fun onReceiveEventPort(port: Int, event: Int?) {
        if (event != null) {
            when (event) {
                AppService.EVENT_STOP -> {
                    //stop server
                    stopIfNeed("stop event.")
                }

                AppService.EVENT_RESTART -> {
                    Log.i(TAG, "onReceiveEventPort: restart")
                    stopIfNeed("restart event.")
                    startIfNeed(port)
                }
            }
        } else {
            when {
                port > AppService.VALID_PORT -> {
                    //start server
                    startIfNeed(port)
                }

                else -> {
                    stopIfNeed("invalid port")
                    val cause = IllegalAccessException("invalid port $port")
                    emitErrorState(cause)
                }
            }
        }

    }


    fun stopBlocking() {
        runBlocking {
            stopInternal("service stopped")
        }
    }

    suspend fun sendMessage(content: String) {
        val serverState = state.value
        if (serverState is ServerState.Started) {
            val session = serverState.chatSession
            session.send(content)
        }
    }

    suspend fun emitRefreshEvent() {
        println("emitRefreshEvent")
        val serverState = state.value
        if (serverState is ServerState.Started) {
            serverState.channel.emit(
                SseEvent(
                    "refresh",
                    "message",
                    System.currentTimeMillis().toString()
                )
            )
        }
    }


    companion object {
        private const val TAG = "AppServer"
    }
}

var contextRef: WeakReference<out Context>? = null
var clientRef: WeakReference<HttpClient>? = null

fun Application.module() {
    val context = contextRef?.get()!!
    val client = clientRef?.get()!!
    plugPlugins()
    channelWaitWorker?.complete(setupSse())
    configureRouting(context)
    webSocketsService()
    setupAvatarProxy(client)
}

private val avatarPattern = Regex("/avatar/(\\w+).png")

/**
 * 请求url
 * http://localhost:80080/avatar/user1.png
 */
private fun Application.setupAvatarProxy(client: HttpClient) {
    // Let's intercept all the requests at the [ApplicationCallPipeline.Call] phase.
    intercept(ApplicationCallPipeline.Call) {
        val uri = call.request.uri
        avatarPattern.find(uri).runCatching {
            this?.groups?.get(1)!!.value
        }.onSuccess { pngName ->
            // We create a GET request to the wikipedia domain and return the call (with the request and the unprocessed response).
            val response =
                client.request(getAvatarIcon(pngName))

            // Get the relevant headers of the client response.
            val proxiedHeaders = response.headers
            val contentType = proxiedHeaders[HttpHeaders.ContentType]
            val contentLength = proxiedHeaders[HttpHeaders.ContentLength]

            call.respond(object : OutgoingContent.WriteChannelContent() {
                override val contentLength: Long? = contentLength?.toLong()
                override val contentType: ContentType? =
                    contentType?.let { ContentType.parse(it) }
                override val headers: Headers = Headers.build {
                    appendAll(proxiedHeaders.filter { key, _ ->
                        !key.equals(
                            HttpHeaders.ContentType,
                            ignoreCase = true
                        ) && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                    })
                }
                override val status: HttpStatusCode = response.status
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    response.bodyAsChannel().copyAndClose(channel)
                }
            })
        }.onFailure {
            proceed()
        }

    }
}