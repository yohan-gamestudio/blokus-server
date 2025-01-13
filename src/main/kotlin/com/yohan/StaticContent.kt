package com.yohan

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.configureStaticContent() {
    routing {
        staticResources("", "static", index = "index.html")
    }
}