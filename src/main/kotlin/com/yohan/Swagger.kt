package com.yohan

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureSwagger() {
    install(SwaggerUI) {
        info {
            title = "blokus"
            version = "latest"
            description = "blokus API"
        }
        server {
            url = "http://localhost:8080"
            description = "Development Server"
        }
    }
    routing {
        route("api.json") {
            openApiSpec()
        }
        route("swagger") {
            swaggerUI("/api.json")
        }
    }
}
