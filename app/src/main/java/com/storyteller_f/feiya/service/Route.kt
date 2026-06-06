package com.storyteller_f.feiya.service

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import com.storyteller_f.feiya.respondUri
import com.storyteller_f.feiya.shares
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


fun Route.contentRoute(context: Context) {
    staticResources("/", null)

    get("/shares") {
        val encodeToString = Json.encodeToString(shares.value)
        call.respond(encodeToString)
    }
    get("/shares/{count}") {
        val index = call.parameters["count"]?.toInt() ?: return@get
        val info = shares.value.getOrNull(index)
        if (info == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    info.name
                )
                    .toString()
            )
            val file = Uri.parse(info.uri)
            if (file.scheme == "file") {
                call.respondFile(file.toFile())
            } else call.respondUri(context, file)
        }

    }
}

fun getAvatarIcon(name: String): String {
    return "https://api.multiavatar.com/${name}.png"
}