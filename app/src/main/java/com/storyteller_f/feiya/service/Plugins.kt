package com.storyteller_f.feiya.service

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import org.slf4j.event.Level


fun Application.plugPlugins() {
    install(StatusPages) {
        exception<Throwable> { call: ApplicationCall, cause ->
            call.respondText(
                cause.localizedMessage ?: cause.javaClass.canonicalName,
                status = HttpStatusCode.InternalServerError
            )
        }
    }
    install(CallLogging) {
        level = Level.DEBUG
    }
    install(PartialContent)
    install(AutoHeadResponse)
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
        }
    }
}
