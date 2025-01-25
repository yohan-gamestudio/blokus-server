package com.yohan

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val tokenService = TokenService()
    val userService = UserService(
        tokenService = tokenService,
    )
    val gameService = GameService(
        userService = userService,
    )
    configureSockets()
    configureSerialization()
    configureSecurity(
        userService = userService,
    )
    configureSwagger()
    configureGame(
        userService = userService,
        gameService = gameService,
    )
    configurePublicLobby(
        userService = userService,
    )
    configureRoom(
        userService = userService,
        gameService = gameService,
    )
    configureStaticContent()
    configureCors()
}
