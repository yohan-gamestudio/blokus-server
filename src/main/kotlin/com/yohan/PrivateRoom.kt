package com.yohan

import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@Serializable
enum class RoomMessageType {
    JOIN,
    CONNECT,
    DISCONNECT,
    GAME_STATE_UPDATED,
    LEAVE,
}

@Serializable
data class RoomMessagePayload(
    val game: InGameView,
)

@Serializable
data class RoomEvent(
    val payload: RoomMessagePayload,
    val type: RoomMessageType,
)

class RoomConnection(
    val session: DefaultWebSocketSession,
    val sessionId: String,
    val userId: Long,
) {
    companion object {
        fun generateRandomId(): String = UUID.randomUUID().toString().take(8)
    }
}

class RoomServer {
    companion object {
        private val messageHistory = Collections.synchronizedList(mutableListOf<RoomEvent>())
        private val connections = Collections.synchronizedSet<RoomConnection>(LinkedHashSet())

        fun addMessage(message: RoomEvent) {
            messageHistory.add(message)
            // Keep only last 50 messages
            if (messageHistory.size > 50) {
                messageHistory.removeAt(0)
            }
        }

        fun getConnections(
            gameId: Long,
            gameService: GameService,
        ): Set<RoomConnection> {
            val gameUsers = gameService.getGameUsers(gameId = gameId)
            return connections.filter { connection ->
                gameUsers.any { it.userId == connection.userId }
            }.toSet()
        }

        fun addConnection(connection: RoomConnection) = connections.add(connection)

        fun removeConnection(connection: RoomConnection) = connections.remove(connection)

        fun getMessageHistory() = messageHistory.toList()
    }
}

class RoomChatConnection(
    val session: DefaultWebSocketSession,
    val sessionId: String,
    val userName: String,
    val userId: Long,
) {
    companion object {
        fun generateRandomId(): String = UUID.randomUUID().toString().take(8)
    }
}

class RoomChatServer {
    companion object {
        private val messageHistoryMap = Collections.synchronizedMap(mutableMapOf<Long, MutableList<ChatMessage>>())
        private val connections = Collections.synchronizedSet<RoomChatConnection>(LinkedHashSet())

        fun addMessage(
            gameId: Long,
            message: ChatMessage
        ) {
            val messageHistory = messageHistoryMap.getOrPut(gameId) { Collections.synchronizedList(mutableListOf()) }

            messageHistory.add(message)
            // Keep only last 50 messages
            if (messageHistory.size > 50) {
                messageHistory.removeAt(0)
            }
        }

        fun getConnections(
            gameId: Long,
            gameService: GameService,
        ): Set<RoomChatConnection> {
            val gameUsers = gameService.getGameUsers(gameId = gameId)
            return connections.filter { connection ->
                gameUsers.any { it.userId == connection.userId }
            }.toSet()
        }

        fun addConnection(connection: RoomChatConnection) = connections.add(connection)

        fun removeConnection(connection: RoomChatConnection) = connections.remove(connection)

        fun getMessageHistory(gameId: Long) =
            messageHistoryMap.getOrPut(gameId) { Collections.synchronizedList(mutableListOf()) }.toList()
    }
}

fun Application.configureRoom(
    userService: UserService,
    gameService: GameService,
) {

    routing {
        authenticate("jwt") {
            webSocket("/games/{gameId}/room") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val user =  userService.getUsers(userId = userId)
                val sessionId = RoomConnection.generateRandomId()
                val thisConnection = RoomConnection(
                    session = this,
                    sessionId = sessionId,
                    userId = user.id,
                )
                RoomServer.addConnection(thisConnection)

                try {
                    // Broadcast connect message
                    val connectMessage = RoomEvent(
                        type = RoomMessageType.CONNECT,
                        payload = RoomMessagePayload(
                            game = gameService.getInGameView(gameId = gameId)
                        ),
                    )
                    RoomServer.addMessage(connectMessage)
                    RoomServer.getConnections(
                        gameId = gameId,
                        gameService = gameService,
                    ).forEach {
                        it.session.send(Json.encodeToString(connectMessage))
                    }

                    // Handle incoming messages
                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val receivedText = frame.readText()
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    // Remove the connection when the client disconnects
                    RoomServer.removeConnection(thisConnection)
                    val leaveMessage = RoomEvent(
                        type = RoomMessageType.DISCONNECT,
                        payload = RoomMessagePayload(
                            game = gameService.getInGameView(gameId = gameId)
                        ),
                    )
                    RoomServer.addMessage(leaveMessage)
                    RoomServer.getConnections(
                        gameId = gameId,
                        gameService = gameService,
                    ).forEach {
                        it.session.send(Json.encodeToString(leaveMessage))
                    }
                }
            }
            webSocket("/games/{gameId}/chat") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val user =  userService.getUsers(userId = userId)
                val sessionId = RoomConnection.generateRandomId()
                val thisConnection = RoomChatConnection(
                    session = this,
                    sessionId = sessionId,
                    userName = user.name,
                    userId = user.id,
                )
                RoomChatServer.addConnection(thisConnection)

                try {
                    // Send message history to new connection
                    RoomChatServer.getMessageHistory(gameId = gameId).forEach { message ->
                        send(Json.encodeToString(message))
                    }

                    // Broadcast join message
                    val joinMessage = ChatMessage(
                        type = ChatMessageType.JOIN,
                        payload = ChatMessagePayload(
                            userId = thisConnection.userId,
                            userName = thisConnection.userName,
                            message = "${thisConnection.userName} joined the chat!",
                            timestamp = System.currentTimeMillis().toString(),
                        ),
                    )
                    RoomChatServer.addMessage(
                        gameId = gameId,
                        message = joinMessage,
                    )
                    RoomChatServer.getConnections(
                        gameId = gameId,
                        gameService = gameService,
                    ).forEach {
                        it.session.send(Json.encodeToString(joinMessage))
                    }

                    // Broadcast join message
                    val connectMessage = ChatMessage(
                        type = ChatMessageType.CONNECT,
                        payload = ChatMessagePayload(
                            userId = thisConnection.userId,
                            userName = thisConnection.userName,
                            message = "${thisConnection.userName} connected the chat!",
                            timestamp = System.currentTimeMillis().toString(),
                        ),
                    )
                    RoomChatServer.addMessage(
                        gameId = gameId,
                        message = connectMessage,
                    )
                    RoomChatServer.getConnections(
                        gameId = gameId,
                        gameService = gameService,
                    ).forEach {
                        it.session.send(Json.encodeToString(connectMessage))
                    }

                    // Handle incoming messages
                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val receivedText = frame.readText()
                        val chatMessage = ChatMessage(
                            type = ChatMessageType.MESSAGE,
                            payload = ChatMessagePayload(
                                userId = thisConnection.userId,
                                userName = thisConnection.userName,
                                message = receivedText,
                                timestamp = System.currentTimeMillis().toString(),
                            ),
                        )
                        RoomChatServer.addMessage(
                            gameId = gameId,
                            message = chatMessage,
                        )
                        // Broadcast the message to all connections
                        RoomChatServer.getConnections(
                            gameId = gameId,
                            gameService = gameService,
                        ).forEach {
                            it.session.send(Json.encodeToString(chatMessage))
                        }
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    // Remove the connection when the client disconnects
                    RoomChatServer.removeConnection(thisConnection)
                    val leaveMessage = ChatMessage(
                        type = ChatMessageType.DISCONNECT,
                        payload = ChatMessagePayload(
                            userId = thisConnection.userId,
                            userName = thisConnection.userName,
                            message = "${thisConnection.userName} disconnect the chat!",
                            timestamp = System.currentTimeMillis().toString(),
                        ),
                    )
                    RoomChatServer.addMessage(
                        gameId = gameId,
                        leaveMessage,
                    )
                    RoomChatServer.getConnections(
                        gameId = gameId,
                        gameService = gameService,
                    ).forEach {
                        it.session.send(Json.encodeToString(leaveMessage))
                    }
                }
            }
        }
    }
}