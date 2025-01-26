package com.yohan

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.time.Instant

fun Application.configureSecurity(userService: UserService) {
    install(Authentication) {
        jwt("jwt") {
            realm = "ktor sample app"
            authHeader { call ->
                val authorizationHeader = call.request.headers["Authorization"]
                val queryToken = call.request.queryParameters["token"]
                
                when {
                    !authorizationHeader.isNullOrBlank() -> try {
                        HttpAuthHeader.Single("Bearer", authorizationHeader.removePrefix("Bearer "))
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                    !queryToken.isNullOrBlank() -> try {
                        HttpAuthHeader.Single("Bearer", queryToken)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                    else -> null
                }
            }
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .build()
            )
            validate { credential ->
                val tokenExpiredDate = credential.payload.expiresAt
                val userId = credential.payload.getClaim("userId").asLong()

                if (tokenExpiredDate == null) {
                    respond(HttpStatusCode.Unauthorized)
                }

                if (!userService.exists(userId = userId)) {
                    respond(HttpStatusCode.Unauthorized)
                }

                if (tokenExpiredDate.toInstant().isAfter(Instant.now())) {
                    JWTPrincipal(credential.payload)
                } else {
                    respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }

    routing {
        post("/token/create-temp") {
            val user = userService.createUser("temp")
            call.respond(HttpStatusCode.Created, user.token.token)
        }
    }

    routing {
        authenticate("jwt") {
            get("/token/test-token") {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}