package com.defang.launcher.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathProblemGeneratorTest {

    private val levels = MathProblemGenerator.MIN_LEVEL..MathProblemGenerator.MAX_LEVEL

    private fun samples(level: Int) =
        (0 until 2_000).map { MathProblemGenerator.generate(level, Random(it)) }

    /** Evaluates the displayed question with normal precedence (× and ÷ before + and −). */
    private fun evaluate(question: String): Int {
        val tokens = question.split(" ")
        val terms = mutableListOf(tokens[0].toInt())
        val addOps = mutableListOf<String>()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val n = tokens[i + 1].toInt()
            when (op) {
                "×" -> terms[terms.lastIndex] = terms.last() * n
                "÷" -> {
                    assertEquals("uneven division in '$question'", 0, terms.last() % n)
                    terms[terms.lastIndex] = terms.last() / n
                }
                else -> { addOps += op; terms += n }
            }
            i += 2
        }
        var result = terms[0]
        addOps.forEachIndexed { idx, op -> result = if (op == "+") result + terms[idx + 1] else result - terms[idx + 1] }
        return result
    }

    @Test
    fun `every level's answer matches its question`() {
        for (level in levels) for (p in samples(level)) {
            assertEquals("level $level: '${p.question}'", evaluate(p.question), p.answer)
        }
    }

    @Test
    fun `answers are never negative and fit the six-digit keypad`() {
        for (level in levels) for (p in samples(level)) {
            assertTrue("level $level: '${p.question}' = ${p.answer}", p.answer in 0..999_999)
        }
    }

    @Test
    fun `level 6 uses three-digit numbers or division`() {
        for (p in samples(6)) {
            val hasThreeDigits = p.question.split(" ").any { it.length == 3 && it.all(Char::isDigit) }
            assertTrue("'${p.question}'", hasThreeDigits || "÷" in p.question)
        }
    }

    @Test
    fun `level 7 always mixes precedence`() {
        for (p in samples(7)) {
            val hasMulDiv = "×" in p.question || "÷" in p.question
            val hasAddSub = "+" in p.question || "−" in p.question
            assertTrue("'${p.question}'", hasMulDiv && hasAddSub)
        }
    }

    @Test
    fun `out-of-range levels clamp instead of crashing`() {
        MathProblemGenerator.generate(0, Random(1))
        MathProblemGenerator.generate(MathProblemGenerator.MAX_LEVEL + 5, Random(1))
    }
}
