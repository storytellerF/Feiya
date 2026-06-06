package com.storyteller_f.feiya.service

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

fun Application.setupSse(): MutableSharedFlow<SseEvent> {
    install(SSE)
    val flow = MutableSharedFlow<SseEvent>()
    routing {
        sse("/sse") {
            launch {
                flow.collect {
                    send(ServerSentEvent(it.data, it.event, it.id))
                }
            }
            launch {
                while (true) {
                    send(ServerSentEvent("nil", "ping", System.currentTimeMillis().toString()))
                    delay(1000)
                }
            }
        }
    }
    return flow
}