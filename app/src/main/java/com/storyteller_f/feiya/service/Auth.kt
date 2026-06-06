package com.storyteller_f.feiya.service

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.storyteller_f.feiya.dataStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.form
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserSession(val name: String)

fun Application.configureRouting(context: Context) {
    install(Authentication) {
        form {
            validate { credential ->
                val pass = context.dataStore.data.map {
                    it[stringPreferencesKey("pass")]
                }.firstOrNull()
                println("form validate $credential $pass")
                if (pass == null || credential.password == pass) {
                    UserIdPrincipal("pass")
                } else null
            }
            challenge {
                println("form challenge")
                call.respond(HttpStatusCode.Unauthorized, "Credentials are not valid")
            }
        }
        session<UserSession>("auth-session") {
            validate { session ->
                println("session validate")
                session
            }
            challenge {
                println("session challenge")
                call.respondRedirect("/login")
            }
        }
    }
    routing {
        authenticate {
            post("/login") {
                println("post login")
                call.sessions.set(UserSession(name = UUID.randomUUID().toString()))
                call.respondRedirect("/")
            }
        }
        authenticate("auth-session") {
            contentRoute(context)
        }

    }
}
