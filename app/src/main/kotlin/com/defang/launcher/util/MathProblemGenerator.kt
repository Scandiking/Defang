package com.defang.launcher.util

import kotlin.random.Random

/** A generated arithmetic problem and its answer. */
data class MathProblem(val question: String, val answer: Int)

/**
 * Generates arithmetic problems for the math-unlock gate, scaled by a 1..7
 * difficulty level (a settings slider). Level 1 is single-digit addition; level
 * 5 is multiplication and two-step expressions; 6 and 7 add three-digit
 * numbers, division and operator precedence (issue #47). Subtraction is always
 * kept non-negative and division always comes out even, so every answer is a
 * whole number ≥ 0 — typeable on the digit-only keypad.
 *
 * The wide range is deliberate: some people breeze through mental arithmetic and
 * some don't, and the friction only works if the problem is hard enough to make
 * you stop and think — but not so hard it becomes a wall.
 */
object MathProblemGenerator {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 7
    const val DEFAULT_LEVEL = 3

    private const val TIMES = "×"  // ×
    private const val MINUS = "−"  // −
    private const val DIVIDE = "÷"

    fun generate(level: Int, random: Random = Random.Default): MathProblem {
        return when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
            1 -> {
                // Single-digit addition only.
                val a = random.nextInt(1, 10)
                val b = random.nextInt(1, 10)
                MathProblem("$a + $b", a + b)
            }
            2 -> {
                // Single-digit addition or subtraction.
                val a = random.nextInt(1, 10)
                val b = random.nextInt(1, 10)
                if (random.nextBoolean()) {
                    MathProblem("$a + $b", a + b)
                } else {
                    val (hi, lo) = if (a >= b) a to b else b to a
                    MathProblem("$hi $MINUS $lo", hi - lo)
                }
            }
            3 -> {
                // Two-digit addition or subtraction.
                val a = random.nextInt(10, 100)
                val b = random.nextInt(10, 100)
                if (random.nextBoolean()) {
                    MathProblem("$a + $b", a + b)
                } else {
                    val (hi, lo) = if (a >= b) a to b else b to a
                    MathProblem("$hi $MINUS $lo", hi - lo)
                }
            }
            4 -> {
                // Two-digit add/subtract, or small multiplication.
                when (random.nextInt(3)) {
                    0 -> {
                        val a = random.nextInt(10, 100)
                        val b = random.nextInt(10, 100)
                        MathProblem("$a + $b", a + b)
                    }
                    1 -> {
                        val a = random.nextInt(10, 100)
                        val b = random.nextInt(10, 100)
                        val (hi, lo) = if (a >= b) a to b else b to a
                        MathProblem("$hi $MINUS $lo", hi - lo)
                    }
                    else -> {
                        val a = random.nextInt(2, 13)
                        val b = random.nextInt(2, 13)
                        MathProblem("$a $TIMES $b", a * b)
                    }
                }
            }
            5 -> {
                // Larger multiplication, or a two-step expression.
                if (random.nextBoolean()) {
                    val a = random.nextInt(12, 40)
                    val b = random.nextInt(3, 20)
                    MathProblem("$a $TIMES $b", a * b)
                } else {
                    val a = random.nextInt(2, 13)
                    val b = random.nextInt(2, 13)
                    val c = random.nextInt(10, 50)
                    MathProblem("$a $TIMES $b + $c", a * b + c)
                }
            }
            6 -> {
                // Three-digit add/subtract, or division that comes out even.
                when (random.nextInt(3)) {
                    0 -> {
                        val a = random.nextInt(100, 1000)
                        val b = random.nextInt(100, 1000)
                        MathProblem("$a + $b", a + b)
                    }
                    1 -> {
                        val a = random.nextInt(100, 1000)
                        val b = random.nextInt(100, 1000)
                        val (hi, lo) = if (a >= b) a to b else b to a
                        MathProblem("$hi $MINUS $lo", hi - lo)
                    }
                    else -> {
                        val divisor = random.nextInt(3, 13)
                        val quotient = random.nextInt(12, 41)
                        MathProblem("${divisor * quotient} $DIVIDE $divisor", quotient)
                    }
                }
            }
            else -> {
                // Precedence: the product (or quotient) must be worked out
                // first, and a left-to-right reading gives the wrong answer.
                when (random.nextInt(3)) {
                    0 -> {
                        val b = random.nextInt(3, 13)
                        val c = random.nextInt(6, 20)
                        val a = random.nextInt(b * c, 1000)
                        MathProblem("$a $MINUS $b $TIMES $c", a - b * c)
                    }
                    1 -> {
                        val a = random.nextInt(100, 1000)
                        val b = random.nextInt(3, 13)
                        val c = random.nextInt(6, 20)
                        MathProblem("$a + $b $TIMES $c", a + b * c)
                    }
                    else -> {
                        val divisor = random.nextInt(3, 13)
                        val quotient = random.nextInt(12, 41)
                        val c = random.nextInt(100, 1000)
                        MathProblem("$c + ${divisor * quotient} $DIVIDE $divisor", c + quotient)
                    }
                }
            }
        }
    }
}
