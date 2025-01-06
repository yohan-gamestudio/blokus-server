package com.yohan

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.auth.jwt.JWTPrincipal

@Serializable
data class CreateUserRequest(
    val name: String,
)

@Serializable
data class CreateUserResponse(
    val id: Long,
    val name: String,
    val token: String,
) {
    companion object {
        fun from(user: User): CreateUserResponse {
            return CreateUserResponse(user.id, user.name, user.token.token)
        }
    }
}

@Serializable
data class GameCreateRequest(
    val name: String,
)
@Serializable
data class GameCreateResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(game: Game): GameCreateResponse {
            return GameCreateResponse(game.id, game.name)
        }
    }
}

fun Application.configureGame(
    userService: UserService,
    gameService: GameService,
) {
    routing {
        post("/users", {
            tags = listOf("Users")
            description = "Create a new user"
            request {
                body<CreateUserRequest> {
                    description = "The user to create"
                    required = true
                }
            }
            response {
                HttpStatusCode.Created to {
                    description = "User successfully created"
                    body<CreateUserResponse> { description = "The created user" }
                }
            }
        }) {
            val request = call.receive<CreateUserRequest>()
            val user = userService.createUser(request.name)
            call.respond(HttpStatusCode.Created, CreateUserResponse.from(user = user))
        }

        authenticate("jwt") {
            post("games", {
                tags = listOf("Games")
                description = "Create a new game"
                request {
                    body<GameCreateRequest> {
                        description = "The game to create"
                        required = true
                    }
                }
                response {
                    HttpStatusCode.Created to {
                        description = "Game successfully created"
                        body<GameCreateResponse> { description = "The created game" }
                    }
                }
            }) {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                val request = call.receive<GameCreateRequest>()
                val game = gameService.createGame(
                    name = request.name,
                    maxPlayerCount = 4,
                    ownerUserId = userId
                )
                call.respond(HttpStatusCode.Created, GameCreateResponse.from(game))
            }
        }
    }
}