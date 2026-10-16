package com.qtekfun.ultimatealarm.core.data

import kotlin.random.Random

/**
 * RF-R5: what the user must do to dismiss (never to snooze, README assumption 5). Stored in the alarm row as text:
 * `math:<difficulty>:<operations>`, `qr:<code>`, `nfc:<tag id hex>`, each with an optional `|alias`.
 */
sealed interface Challenge {
    data class Math(val difficulty: Int = 1, val operations: Int = 3) : Challenge

    /** A QR or barcode registered by scanning it once. */
    data class Code(val value: String, val alias: String = "") : Challenge

    /** An NFC tag registered by holding it once. */
    data class Nfc(val tagId: String, val alias: String = "") : Challenge

    fun encode(): String = when (this) {
        is Math -> "$MATH${difficulty.coerceIn(1, MAX_DIFFICULTY)}:${operations.coerceIn(1, MAX_OPERATIONS)}"
        is Code -> "$CODE${escape(value)}${aliasSuffix(alias)}"
        is Nfc -> "$NFC$tagId${aliasSuffix(alias)}"
    }

    companion object {
        const val MAX_DIFFICULTY = 3
        const val MAX_OPERATIONS = 10
        private const val MATH = "math:"
        private const val CODE = "qr:"
        private const val NFC = "nfc:"

        fun decode(text: String?): Challenge? = runCatching {
            when {
                text == null -> null
                text.startsWith(MATH) -> text.removePrefix(MATH).split(':').let {
                    Math(it[0].toInt().coerceIn(1, MAX_DIFFICULTY), it[1].toInt().coerceIn(1, MAX_OPERATIONS))
                }
                text.startsWith(CODE) -> text.removePrefix(CODE).let { body ->
                    val (value, alias) = splitAlias(body)
                    Code(unescape(value), alias).takeIf { it.value.isNotEmpty() }
                }
                text.startsWith(NFC) -> text.removePrefix(NFC).let { body ->
                    val (id, alias) = splitAlias(body)
                    Nfc(id, alias).takeIf { it.tagId.isNotEmpty() }
                }
                else -> null
            }
        }.getOrNull()

        /**
         * RF-R5 degradation: a challenge whose hardware is missing becomes maths, so the alarm can always be dismissed.
         */
        fun effective(challenge: Challenge?, hasCamera: Boolean, hasNfc: Boolean): Challenge? = when (challenge) {
            is Code -> if (hasCamera) challenge else Math()
            is Nfc -> if (hasNfc) challenge else Math()
            else -> challenge
        }

        private fun aliasSuffix(alias: String) = if (alias.isBlank()) "" else "|${escape(alias)}"

        private fun splitAlias(body: String): Pair<String, String> {
            val bar = body.indexOf('|')
            return if (bar < 0) body to "" else body.substring(0, bar) to unescape(body.substring(bar + 1))
        }

        private fun escape(text: String) = text.replace("%", "%25").replace("|", "%7C")

        private fun unescape(text: String) = text.replace("%7C", "|").replace("%25", "%")
    }
}

/** One maths problem: [text] to show (e.g. "37 + 58") and its [answer]. */
data class MathProblem(val text: String, val answer: Int)

/** RF-R5 maths: difficulty 1 adds two-digit numbers, 2 also subtracts and multiplies small numbers, 3 mixes three terms. */
object MathProblems {
    fun generate(difficulty: Int, random: Random = Random.Default): MathProblem = when (difficulty.coerceIn(1, Challenge.MAX_DIFFICULTY)) {
        1 -> random.nextInt(10, 60).let { a -> random.nextInt(10, 40).let { b -> MathProblem("$a + $b", a + b) } }
        2 -> when (random.nextInt(3)) {
            0 -> random.nextInt(20, 99).let { a -> random.nextInt(10, 99).let { b -> MathProblem("$a + $b", a + b) } }
            1 -> random.nextInt(50, 99).let { a -> random.nextInt(10, a).let { b -> MathProblem("$a − $b", a - b) } }
            else -> random.nextInt(3, 13).let { a -> random.nextInt(3, 10).let { b -> MathProblem("$a × $b", a * b) } }
        }
        else -> {
            val a = random.nextInt(6, 15)
            val b = random.nextInt(3, 10)
            val c = random.nextInt(10, 99)
            MathProblem("$a × $b + $c", a * b + c)
        }
    }

    fun check(problem: MathProblem, input: String): Boolean = input.trim().toIntOrNull() == problem.answer
}
