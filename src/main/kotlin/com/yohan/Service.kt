package com.yohan

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.OffsetDateTime
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

data class UserToken(
    val token: String,
    val expirationDate: OffsetDateTime
)

data class User(
    val id: Long,
    val name: String,
    val token: UserToken
)

enum class GameState {
    WAITING,
    ONGOING,
}

data class Game(
    val id: Long,
    val name: String,
    val created: OffsetDateTime,
    val state: GameState,
    val playerUserIds: List<Long>,
    val maxPlayerCount: Long,
    val ownerUserId: Long,
)

data class GameUser(
    val gameId: Long,
    val userId: Long,
    val isReady: Boolean,
)

val users = MutableStateFlow<List<User>>(emptyList())
val games = MutableStateFlow<List<Game>>(emptyList())
val gameUsers = MutableStateFlow<List<GameUser>>(emptyList())

class TokenService {
    fun generateToken(userId: Long): UserToken {
        val expirationDate = OffsetDateTime.now().plusWeeks(1)
        val token = JWT.create()
            .withClaim("userId", userId)
            .withExpiresAt(expirationDate.toInstant())
            .sign(Algorithm.HMAC256(jwtSecret))

        return UserToken(token, expirationDate)
    }
}

class UserService(
    private val tokenService: TokenService,
) {
    suspend fun createUser(name: String): User {
        val userId = users.value.size.toLong()
        val userToken = tokenService.generateToken(userId)
        val createdUser = User(
            id = userId,
            name = name,
            token = userToken
        )
        users.value = users.value + createdUser
        return createdUser
    }

    suspend fun getUsers(): List<User> {
        return users.value
    }
}

class GameService {
    suspend fun createGame(name: String, maxPlayerCount: Long, ownerUserId: Long): Game {
        val createdGame = Game(
            id = games.value.size.toLong(),
            name = name,
            created = OffsetDateTime.now(),
            state = GameState.WAITING,
            playerUserIds = emptyList(),
            maxPlayerCount = maxPlayerCount,
            ownerUserId = ownerUserId,
        )
        games.value = games.value + createdGame
        return createdGame
    }

    suspend fun startGame(gameId: Long) {
        val game = games.value.find { it.id == gameId } ?: throw Exception("Game not found")
        games.value = games.value.map {
            if (it.id == gameId) {
                it.copy(state = GameState.ONGOING)
            } else {
                it
            }
        }
    }
}

const val jwtSecret = "secret"