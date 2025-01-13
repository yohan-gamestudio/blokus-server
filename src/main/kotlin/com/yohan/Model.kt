package com.yohan

data class GamePiece(
    val color: GameColor,
    val type: GamePieceType,
    val used: Boolean,
)

fun GamePiece.toShapeIntArray(): Array<Array<Int>> {
    return this.type.shapeInfo.shape.map { row ->
        row.map { cell ->
            if (cell) this.color.code else 0
        }.toTypedArray()
    }.toTypedArray()
}

data class ShapeInfo (
    val shape: Array<Array<Boolean>>,
)

enum class GamePieceType(val shapeInfo: ShapeInfo) {
    TYPE1(
        ShapeInfo(
            arrayOf(
                arrayOf(true),
            )
        )
    ),
    TYPE2(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true),
            )
        )
    ),
    TYPE3(
        ShapeInfo(
            arrayOf(
                arrayOf(false, true),
                arrayOf(true, true),
            )
        )
    ),
    TYPE4(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true, true),
            )
        )
    ),
    TYPE5(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true, true, true),
            )
        )
    ),
    TYPE6(
        ShapeInfo(
            arrayOf(
                arrayOf(false, false, true),
                arrayOf(true, true, true),
            )
        )
    ),
    TYPE7(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true, false),
                arrayOf(false, true, true),
            )
        )
    ),
    TYPE8(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true),
                arrayOf(true, true),
            )
        )
    ),
    TYPE9(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true, true),
                arrayOf(false, true, false),
            )
        )
    ),
    TYPE10(
        ShapeInfo(
            arrayOf(
                arrayOf(false, true, true),
                arrayOf(true, true, false),
                arrayOf(false, true, false),
            )
        )
    ),
    TYPE11(
        ShapeInfo(
            arrayOf(
                arrayOf(true),
                arrayOf(true),
                arrayOf(true),
                arrayOf(true),
                arrayOf(true),
            )
        )
    ),
    TYPE12(
        ShapeInfo(
            arrayOf(
                arrayOf(true, false),
                arrayOf(true, false),
                arrayOf(true, false),
                arrayOf(true, true),
            )
        )
    ),
    TYPE13(
        ShapeInfo(
            arrayOf(
                arrayOf(false, true),
                arrayOf(true, true),
                arrayOf(true, false),
                arrayOf(true, false),
            )
        )
    ),
    TYPE14(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true),
                arrayOf(true, true),
                arrayOf(true, false),
            )
        )
    ),
    TYPE15(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true, true),
                arrayOf(false, true, false),
                arrayOf(false, true, false),
            )
        )
    ),
    TYPE16(
        ShapeInfo(
            arrayOf(
                arrayOf(true, false, true),
                arrayOf(true, true, true),
            )
        )
    ),
    TYPE17(
        ShapeInfo(
            arrayOf(
                arrayOf(false, false, true),
                arrayOf(false, false, true),
                arrayOf(true, true, true),
            )
        )
    ),
    TYPE18(
        ShapeInfo(
            arrayOf(
                arrayOf(false, false, true),
                arrayOf(false, true, true),
                arrayOf(true, true, false),
            )
        )
    ),
    TYPE19(
        ShapeInfo(
            arrayOf(
                arrayOf(false, true, false),
                arrayOf(true, true, true),
                arrayOf(false, true, false),
            )
        )
    ),
    TYPE20(
        ShapeInfo(
            arrayOf(
                arrayOf(false, true),
                arrayOf(true, true),
                arrayOf(false, true),
                arrayOf(false, true),
            )
        )
    ),
    TYPE21(
        ShapeInfo(
            arrayOf(
                arrayOf(true, true, false),
                arrayOf(false, true, false),
                arrayOf(false, true, true),
            )
        )
    ),
}