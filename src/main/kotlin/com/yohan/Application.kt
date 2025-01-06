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
//    configureSockets()
//    configureSerialization()
    configureSecurity(
        userService = userService,
    )
//    configureRouting()
    configureSwagger()
}
