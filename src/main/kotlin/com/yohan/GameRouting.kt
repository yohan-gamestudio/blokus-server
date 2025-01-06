package com.yohan

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import io.github.smiley4.ktorswaggerui.dsl.routing.post

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
    }
}