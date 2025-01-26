package com.yohan

import java.time.OffsetDateTime
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.websocket.send
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    var ownerUserId: Long,
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

    fun getUsers(userId: Long): User {
        return users.first { it.id == userId }
    }
    fun exists(userId: Long): Boolean {
        return users.any { it.id == userId }
    }
}

class GameService (
    private val userService: UserService,
) {
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
        val allGameUsers = gameUsers.filter { it.gameId == gameId }
        val gameUser = allGameUsers.find { it.userId == userId } ?: throw Exception("User not found in game")

        if (allGameUsers.size == 1) {
            gameUsers.remove(gameUser)
            games.remove(game)
        } else {
            val gameUsersExceptExitUser = gameUsers.filter { it.userId != userId }.first()
            game.ownerUserId = gameUsersExceptExitUser.userId
            gameUsers.remove(gameUser)
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

    suspend fun startGame(gameId: Long, userId: Long) {
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
        RoomServer.getConnections(
            gameId = gameId,
            gameService = this,
        ).forEach {
            it.session.send(Json.encodeToString(RoomEvent(
                type = RoomMessageType.GAME_START,
                payload = RoomMessagePayload(
                    game = getInGameView(gameId = gameId)
                ),
            )))
        }
    }

    fun getGames(): List<Game> {
        return games.toList()
    }
    fun getGameUsers(gameId: Long): List<GameUser> {
        return gameUsers.filter { it.gameId == gameId }
    }
    fun getGameUsers(): List<GameUser> {
        return gameUsers
    }
    fun getGame(gameId: Long): Game {
        return games.find { it.id == gameId } ?: throw Exception("Game not found")
    }
    fun getInGameView(gameId: Long): InGameView {
        val users = userService.getUsers()
        val game = games.find { it.id == gameId } ?: throw Exception("Game not found")
        val players = gameUsers.filter { it.gameId == gameId }
            .map { gameUser ->
                val user = users.find { it.id == gameUser.userId }
                    ?: throw IllegalStateException("User not found")
                gameUser to user
            }
        return InGameView.from(
            game = game,
            players = players,
        )
    }
    suspend fun placePiece(gameId: Long, playerUserId: Long, pieceIndex: Int, pieceShape: Array<Array<Int>>) {
        val game = games.find { it.id == gameId } ?: throw Exception("Game not found")
        val gameUser = gameUsers.find { it.gameId == gameId && it.userId == playerUserId } ?: throw Exception("User not found in game")
        if (game.currentTurnPlayerUserId != playerUserId) {
            throw Exception("Not your turn")
        }

        val piece = gameUser.pieces?.first { GamePieceType.entries.toTypedArray()[pieceIndex] == it.type } ?: throw Exception("Piece not found")
        if (piece.used) {
            throw Exception("Piece already used")
        }
        piece.used = true

        game.board.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, cell ->
                val shouldPlacePiece = pieceShape[rowIndex][columnIndex] != 0
                val isBoardEmpty = game.board[rowIndex][columnIndex] == GameColor.EMPTY
                if (shouldPlacePiece && isBoardEmpty.not()) {
                    throw Exception("Piece cannot be placed here")
                }
            }
        }

        game.board.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, cell ->
                val shouldPlacePiece = pieceShape[rowIndex][columnIndex] != 0
                if (shouldPlacePiece) {
                    game.board[rowIndex][columnIndex] = piece.color
                }
            }
        }

        val currentGameUsers = gameUsers.filter { it.gameId == gameId }
            .sortedBy {
                when (it.color) {
                    GameColor.BLUE -> 0
                    GameColor.YELLOW -> 1
                    GameColor.RED -> 2
                    GameColor.GREEN -> 3
                    else -> throw IllegalStateException("Invalid color")
                }
            }
        val currentGameUserIndex = currentGameUsers.indexOf(gameUser)
        val nextGameUserIndex = (currentGameUserIndex + 1) % currentGameUsers.size
        game.currentTurnPlayerUserId = currentGameUsers[nextGameUserIndex].userId

        val roomEvent = RoomEvent(
            type = RoomMessageType.GAME_STATE_UPDATED,
            payload = RoomMessagePayload(
                game = getInGameView(gameId = gameId)
            ),
        )
        RoomServer.addMessage(roomEvent)
        RoomServer.getConnections(
            gameId = gameId,
            gameService = this,
        ).forEach {
            it.session.send(Json.encodeToString(roomEvent))
        }
    }
}

const val jwtSecret = "secret"