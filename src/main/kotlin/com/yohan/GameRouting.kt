package com.yohan

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal

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
@Serializable
data class GamePiecePlaceRequest(
    val index: Int,
    val gamePiece: Array<Array<Int>>,
)

@Serializable
data class GameCreateRequest(
    val name: String,
)
@Serializable
data class GameCreateResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(game: Game): GameCreateResponse {
            return GameCreateResponse(game.id, game.name)
        }
    }
}

@Serializable
data class GamePublicLobbyView(
    val id: Long,
    val name: String,
    val maxPlayerCount: Long,
    val currentPlayerCount: Long,
    val state: GameState,
    val owner: UserView,
) {
    companion object {
        fun from(game: Game, owner: User, currentPlayerCount: Long): GamePublicLobbyView {
            return GamePublicLobbyView(
                id = game.id,
                name = game.name,
                maxPlayerCount = game.maxPlayerCount,
                state = game.state,
                owner = UserView(
                    id = owner.id,
                    name = owner.name,
                ),
                currentPlayerCount = currentPlayerCount,
            )
        }
    }
}

@Serializable
data class InGamePlayerView(
    val id: Long,
    val name: String,
    val colorCode: Int,
    val blocks: List<InGameBlockView>,
)

@Serializable
data class InGameBlockView(
    val shape: Array<Array<Int>>,
    val isUsed: Boolean,
)

@Serializable
data class InGameView(
    val id: Long,
    val name: String,
    val state: GameState,
    val currentTurnPlayerUserId: Long,
    val players: List<InGamePlayerView>,
    val board: Array<Array<Int>>,
) {
    companion object {
        fun from(game: Game, players: List<Pair<GameUser, User>>, ): InGameView {
            return InGameView(
                id = game.id,
                name = game.name,
                state = game.state,
                currentTurnPlayerUserId = game.currentTurnPlayerUserId
                    ?: throw IllegalStateException("No current turn player"),
                players = players.map { (gameUser, user) ->
                    InGamePlayerView(
                        id = user.id,
                        name = user.name,
                        colorCode = gameUser.color?.code
                            ?: throw IllegalStateException("No color"),
                        blocks = gameUser.pieces?.map { piece ->
                            InGameBlockView(
                                shape = piece.toShapeIntArray(),
                                isUsed = piece.used,
                            )
                        } ?: throw IllegalStateException("No pieces"),
                    )
                },
                board = game.board.map { row ->
                    row.map { cell ->
                        cell.code
                    }.toTypedArray()
                }.toTypedArray(),
            )
        }
    }
}

@Serializable
data class GameRoomView(
    val id: Long,
    val name: String,
    val maxPlayerCount: Long,
    val state: GameState,
    val players: List<GameRoomPlayerView>,
    val owner: GameRoomOwnerView,
) {
    companion object {
        fun from(game: Game, owner: User, players: List<Pair<GameUser, User>>): GameRoomView {
            return GameRoomView(
                id = game.id,
                name = game.name,
                maxPlayerCount = game.maxPlayerCount,
                state = game.state,
                players = players.map { (gameUser, user) ->
                    GameRoomPlayerView(
                        id = user.id,
                        name = user.name,
                        isReady = gameUser.isReady,
                    )
                },
                owner = GameRoomOwnerView(
                    id = owner.id,
                    name = owner.name,
                ),
            )
        }
    }
}

@Serializable
data class GameRoomPlayerView(
    val id: Long,
    val name: String,
    val isReady: Boolean,
)

@Serializable
data class GameRoomOwnerView(
    val id: Long,
    val name: String,
)

@Serializable
data class UserView(
    val id: Long,
    val name: String,
)


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

        authenticate("jwt") {
            post("games", {
                tags = listOf("Games")
                description = "Create a new game"
                request {
                    body<GameCreateRequest> {
                        description = "The game to create"
                        required = true
                    }
                }
                response {
                    HttpStatusCode.Created to {
                        description = "Game successfully created"
                        body<GameCreateResponse> { description = "The created game" }
                    }
                }
            }) {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                val request = call.receive<GameCreateRequest>()
                val game = gameService.createGame(
                    name = request.name,
                    maxPlayerCount = 4,
                    ownerUserId = userId
                )
                call.respond(HttpStatusCode.Created, GameCreateResponse.from(game))
            }

            get("/games/public-lobby-view", {
                tags = listOf("Games")
                description = "Get the public lobby view of all games"
                response {
                    HttpStatusCode.OK to {
                        description = "Successfully retrieved the public lobby view of all games"
                        body<List<GamePublicLobbyView>> { description = "The public lobby view of all games" }
                    }
                }
            }) {
                val games = gameService.getGames()
                val users = userService.getUsers()
                val gameUsers = gameService.getGameUsers()
                call.respond(
                    games.map { game ->
                        val owner = users.find { it.id == game.ownerUserId }
                            ?: throw IllegalStateException("Owner not found")
                        GamePublicLobbyView.from(game, owner, gameUsers.filter { it.gameId == game.id }.size.toLong())
                    }
                )
            }

            get("/games/{gameId}/game-room-view", {
                tags = listOf("Games")
                description = "Get the lobby view of all games"
                request {
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "Successfully retrieved the lobby view of all games"
                        body<List<GamePublicLobbyView>> { description = "The lobby view of all games" }
                    }
                }
            }) {
                val games = gameService.getGames()
                val users = userService.getUsers()
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val game = games.find { it.id == gameId }
                    ?: throw IllegalArgumentException("Game not found")
                val owner = users.find { it.id == game.ownerUserId }
                    ?: throw IllegalStateException("Owner not found")
                val players = gameService.getGameUsers(gameId)
                    .map { gameUser ->
                        val user = users.find { it.id == gameUser.userId }
                            ?: throw IllegalStateException("User not found")
                        gameUser to user
                    }
                call.respond(GameRoomView.from(game, owner, players))
            }

            get("/games/{gameId}/in-game-view", {
                tags = listOf("Games")
                description = "Get the in game view"
                request {
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "Successfully retrieved the in game view of game"
                        body<List<GamePublicLobbyView>> { description = "The in game view of game" }
                    }
                }
            }) {
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                call.respond(gameService.getInGameView(gameId = gameId))
            }

            post("/games/{gameId}/start", {
                tags = listOf("Games")
                description = "Start a game"
                request {
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }
                }
            }) {
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                gameService.startGame(gameId, userId)
                call.respond(HttpStatusCode.OK)
            }

            post("/games/{gameId}/exit", {
                tags = listOf("Games")
                description = "Exit a game"
                request {
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }
                }
            }) {
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                gameService.exitGame(gameId, userId)
                call.respond(HttpStatusCode.OK)
            }

            post("/games/{gameId}/join", {
                tags = listOf("Games")
                description = "Join a game"
                request {
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }
                }
            }) {
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                gameService.joinGame(gameId, userId)
                call.respond(HttpStatusCode.OK)
            }

            post("/games/{gameId}/ready", {
                tags = listOf("Games")
                description = "Ready a player in game"
                request {
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }
                }
            }) {
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                gameService.readyGame(gameId, userId)
                call.respond(HttpStatusCode.OK)
            }
            post("/games/{gameId}/unready", {
                tags = listOf("Games")
                description = "Unready a player in game"
                request {
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }
                }
            }) {
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                gameService.unReadyGame(gameId, userId)
                call.respond(HttpStatusCode.OK)
            }
            post("/games/{gameId}/place-piece", {
                tags = listOf("Games")
                description = "Place a piece in game"
                request {
                    body<GamePiecePlaceRequest> {
                        description = "The game to create"
                        required = true
                    }
                    pathParameter<Long>("gameId") {
                        description = "id of game"
                        required = true
                    }

                }
            }) {
                val gameId = call.parameters["gameId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid gameId")
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Long::class)
                    ?: throw IllegalStateException("No userId in token")
                val request = call.receive<GamePiecePlaceRequest>()
                gameService.placePiece(
                    gameId = gameId,
                    playerUserId = userId,
                    pieceIndex = request.index,
                    pieceShape = request.gamePiece,
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}