package com.yohan

import java.time.OffsetDateTime
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.Serializable

data class UserToken(
    val token: String,
    val expirationDate: OffsetDateTime
)

data class User(
    val id: Long,
    val name: String,
    val token: UserToken
)

@Serializable
enum class GameState {
    WAITING,
    ONGOING,
}

data class Game(
    val id: Long,
    val name: String,
    val created: OffsetDateTime,
    var state: GameState,
    val playerUserIds: List<Long>,
    val maxPlayerCount: Long,
    val ownerUserId: Long,
)

data class GameUser(
    val gameId: Long,
    val userId: Long,
    val isReady: Boolean,
)

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
    private val users = mutableListOf<User>()
    fun createUser(name: String): User {
        val userId = users.size.toLong()
        val userToken = tokenService.generateToken(userId)
        val createdUser = User(
            id = userId,
            name = name,
            token = userToken
        )
        users.add(createdUser)
        return createdUser
    }

    fun getUsers(): List<User> {
        return users.toList()
    }
}

class GameService {
    private val games = mutableListOf<Game>()
    private val gameUsers = mutableListOf<GameUser>()
    fun createGame(name: String, maxPlayerCount: Long, ownerUserId: Long): Game {
        val createdGame = Game(
            id = games.size.toLong(),
            name = name,
            created = OffsetDateTime.now(),
            state = GameState.WAITING,
            playerUserIds = emptyList(),
            maxPlayerCount = maxPlayerCount,
            ownerUserId = ownerUserId,
        )
        games.add(createdGame)
        return createdGame
    }

    fun startGame(gameId: Long) {
        val game = games.find { it.id == gameId } ?: throw Exception("Game not found")
        game.state = GameState.ONGOING
    }

    fun getGames(): List<Game> {
        return games.toList()
    }
}

const val jwtSecret = "secret"