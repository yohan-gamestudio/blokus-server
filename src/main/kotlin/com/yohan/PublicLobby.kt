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

@Serializable
enum class ChatMessageType {
    JOIN,
    CONNECT,
    DISCONNECT,
    LEAVE,
    MESSAGE,
}

@Serializable
data class ChatMessagePayload(
    val userId: Long,
    val userName: String,
    val message: String,
    val timestamp: String,
)

@Serializable
data class ChatMessage(
    val payload: ChatMessagePayload,
    val type: ChatMessageType,
)

class Connection(
    val session: DefaultWebSocketSession,
    val sessionId: String,
    val userName: String,
    val userId: Long,
) {
    companion object {
        fun generateRandomId(): String =
            java.util.UUID.randomUUID().toString().take(8)
    }
}

class ChatServer {
    companion object {
        private val messageHistory = Collections.synchronizedList(mutableListOf<ChatMessage>())
        private val connections = Collections.synchronizedSet<Connection>(LinkedHashSet())

        fun addMessage(message: ChatMessage) {
            messageHistory.add(message)
            // Keep only last 50 messages
            if (messageHistory.size > 50) {
                messageHistory.removeAt(0)
            }
        }

        fun getConnections() = connections

        fun addConnection(connection: Connection) = connections.add(connection)

        fun removeConnection(connection: Connection) = connections.remove(connection)

        fun getMessageHistory() = messageHistory.toList()
    }
}

fun Application.configurePublicLobby(
    userService: UserService,
) {

    routing {
        authenticate("jwt") {
            webSocket("/chat") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                val user =  userService.getUsers(userId = userId)
                val sessionId = Connection.generateRandomId()
                val thisConnection = Connection(
                    session = this,
                    sessionId = sessionId,
                    userName = "${user.name}-${Connection.generateRandomId()}",
                    userId = userId,
                )
                ChatServer.addConnection(thisConnection)

                try {
                    // Send message history to new connection
                    ChatServer.getMessageHistory().forEach { message ->
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
                    ChatServer.addMessage(joinMessage)
                    ChatServer.getConnections().forEach {
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
                    ChatServer.addMessage(connectMessage)
                    ChatServer.getConnections().forEach {
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
                        ChatServer.addMessage(chatMessage)
                        // Broadcast the message to all connections
                        ChatServer.getConnections().forEach {
                            it.session.send(Json.encodeToString(chatMessage))
                        }
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    // Remove the connection when the client disconnects
                    ChatServer.removeConnection(thisConnection)
                    val leaveMessage = ChatMessage(
                        type = ChatMessageType.DISCONNECT,
                        payload = ChatMessagePayload(
                            userId = thisConnection.userId,
                            userName = thisConnection.userName,
                            message = "${thisConnection.userName} disconnect the chat!",
                            timestamp = System.currentTimeMillis().toString(),
                        ),
                    )
                    ChatServer.addMessage(leaveMessage)
                    ChatServer.getConnections().forEach {
                        it.session.send(Json.encodeToString(leaveMessage))
                    }
                }
            }
        }
    }
}