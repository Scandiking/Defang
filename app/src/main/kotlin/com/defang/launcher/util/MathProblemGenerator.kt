package com.defang.launcher.util

import kotlin.random.Random

/** A generated arithmetic problem and its answer. */
data class MathProblem(val question: String, val answer: Int)

/**
 * Generates arithmetic problems for the math-unlock gate, scaled by a 1..5
 * difficulty level (a settings slider). Level 1 is single-digit addition; level
 * 5 is multiplication and two-step expressions. Subtraction is always kept
 * non-negative and answers are whole numbers, so every problem is solvable on
 * paper without negatives or fractions.
 *
 * The wide range is deliberate: some people breeze through mental arithmetic and
 * some don't, and the friction only works if the problem is hard enough to make
 * you stop and think — but not so hard it becomes a wall.
 */
object MathProblemGenerator {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 5
    const val DEFAULT_LEVEL = 3

    private const val TIMES = "×"  // ×
    private const val MINUS = "−"  // −

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
            else -> {
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
        }
    }
}
