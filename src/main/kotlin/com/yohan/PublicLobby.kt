package com.yohan

import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ChatMessage(
    val username: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class Connection(val session: DefaultWebSocketSession) {
    companion object {
        val lastId = AtomicInteger(0)
    }
    val name = "user${lastId.getAndIncrement()}"
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

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}

fun Application.configurePublicLobby() {

    routing {
        authenticate("jwt") {
            webSocket("/chat") {
                val thisConnection = Connection(this)
                ChatServer.addConnection(thisConnection)

                try {
                    // Send message history to new connection
                    ChatServer.getMessageHistory().forEach { message ->
                        send(Json.encodeToString(message))
                    }

                    // Broadcast join message
                    val joinMessage = ChatMessage(
                        username = "System",
                        message = "${thisConnection.name} joined the chat!"
                    )
                    ChatServer.addMessage(joinMessage)
                    ChatServer.getConnections().forEach {
                        it.session.send(Json.encodeToString(joinMessage))
                    }

                    // Handle incoming messages
                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val receivedText = frame.readText()
                        val chatMessage = ChatMessage(
                            username = thisConnection.name,
                            message = receivedText
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
                        username = "System",
                        message = "${thisConnection.name} left the chat!"
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