package com.yohan

import java.time.OffsetDateTime
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.Serializable
import kotlin.Array

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
    val maxPlayerCount: Long,
    val ownerUserId: Long,
    var currentTurnPlayerUserId: Long? = null,
    val board: Array<Array<GameColor>> = Array(20) { Array(20) { GameColor.EMPTY } },
)

data class GameUser(
    val gameId: Long,
    val userId: Long,
    var isReady: Boolean,
    var color: GameColor? = null,
    var pieces: List<GamePiece>? = null,
)

@Serializable
enum class GameColor(val code: Int) {
    EMPTY(0),
    RED(1),
    BLUE(2),
    GREEN(3),
    YELLOW(4),
}

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
    fun readyGame(gameId: Long, userId: Long) {
        games.find { it.id == gameId } ?: throw Exception("Game not found")
        val gameUser = gameUsers.find { it.gameId == gameId && it.userId == userId } ?: throw Exception("User not found in game")
        gameUser.isReady = true
    }
    fun unReadyGame(gameId: Long, userId: Long) {
        games.find { it.id == gameId } ?: throw Exception("Game not found")
        val gameUser = gameUsers.find { it.gameId == gameId && it.userId == userId } ?: throw Exception("User not found in game")
        gameUser.isReady = false
    }
    fun joinGame(gameId: Long, userId: Long) {
        val game = games.find { it.id == gameId } ?: throw Exception("Game not found")
        if (game.state != GameState.WAITING) {
            throw Exception("Game is not in waiting state")
        }
        val existingGameUsers = gameUsers.filter { it.gameId == gameId }
        if (existingGameUsers.size >= game.maxPlayerCount) {
            throw Exception("Game is full")
        }
        val alreadyExistingGameUser = existingGameUsers.find { it.gameId == gameId && it.userId == userId } != null
        if (alreadyExistingGameUser) {
            throw Exception("User is already in the game")
        }
        gameUsers.add(GameUser(gameId, userId, false))
    }
    fun exitGame(gameId: Long, userId: Long) {
        val game = games.find { it.id == gameId } ?: throw Exception("Game not found")
        if (game.ownerUserId != userId) {
            throw Exception("Only the owner can exit the game")
        }
        gameUsers.find { it.gameId == gameId && it.userId == userId }?.let {
            gameUsers.remove(it)
        }
    }
    fun createGame(name: String, maxPlayerCount: Long, ownerUserId: Long): Game {
        val createdGame = Game(
            id = games.size.toLong(),
            name = name,
            created = OffsetDateTime.now(),
            state = GameState.WAITING,
            maxPlayerCount = maxPlayerCount,
            ownerUserId = ownerUserId,
        )
        gameUsers.add(GameUser(createdGame.id, ownerUserId, false))
        games.add(createdGame)
        return createdGame
    }

    fun startGame(gameId: Long, userId: Long) {
        val game = games.find { it.id == gameId } ?: throw Exception("Game not found")
        val gameUsers = gameUsers.filter { it.gameId == gameId }
        val owner = gameUsers.find { it.userId == game.ownerUserId } ?: throw Exception("Owner not found")
        if (gameUsers.size < 2) {
            throw Exception("Not enough players to start the game")
        }
        owner.isReady = true
        if (gameUsers.any { !it.isReady }) {
            throw Exception("Not all players are ready")
        }

        val colors = mutableSetOf<GameColor>(GameColor.RED, GameColor.BLUE, GameColor.GREEN, GameColor.YELLOW)

        for (gameUser in gameUsers) {
            val color = colors.first()
            colors.remove(color)
            gameUser.color = color
            gameUser.pieces = GamePieceType.entries.map {
                GamePiece(color, it, false)
            }.toList()
        }

        game.currentTurnPlayerUserId = gameUsers.random().userId
        game.state = GameState.ONGOING
    }

    fun getGames(): List<Game> {
        return games.toList()
    }
    fun getGameUsers(gameId: Long): List<GameUser> {
        return gameUsers.filter { it.gameId == gameId }
    }
}

const val jwtSecret = "secret"