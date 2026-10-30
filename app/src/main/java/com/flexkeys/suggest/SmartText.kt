package com.flexkeys.suggest

import com.flexkeys.clipboard.OtpDetector
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** A chip offered in the suggestion strip: [label] is shown, tapping removes [replaceLength] characters before the cursor and types [insert]. */
class CalcHit(val label: String, val replaceLength: Int, val insert: String)

/** Quick answers for what was just typed: arithmetic ("12*8") and unit conversions ("5 km"). Pure logic, no Android. */
object SmartText {

    private const val NUM = """\d+(?:[.,]\d+)?"""
    // Operators that are safe in running text. "-", "/" and a bare "x" need spaces, so dates, phone numbers and sizes are left alone.
    private const val OP = """(?:\s*[+*×÷^]\s*|\s+[-–/]\s+|(?<=\d)x(?=\d)|\s+x\s+)"""
    private val mathAtEnd = Regex("""(?<![\w.,])(\(*$NUM\)*(?:$OP\(*$NUM\)*)+)\s*(=?)\s*$""")
    private val unitAtEnd = Regex("""(?<![\w.,])(-?$NUM)\s*(km/h|mph|kph|km|mi|miles?|kg|lbs?|cm|inch(?:es)?|in|ft|feet|oz|gal|ml|°\s?[cf]|celsius|fahrenheit)\s*$""", RegexOption.IGNORE_CASE)

    /** The chip for the text before the cursor, or null if it doesn't end in something we can answer. */
    fun findCalc(before: String): CalcHit? {
        val tail = before.takeLast(80)
        mathAtEnd.find(tail)?.let { m ->
            val expr = m.groupValues[1]
            val value = Calc.evaluate(expr) ?: return@let
            val result = Calc.format(value)
            val equals = m.groupValues[2].isNotEmpty()
            // "12*8=" gets the answer after it; "12*8" is replaced by it.
            val spaced = tail.last().isWhitespace()
            return if (equals) CalcHit("= $result", 0, if (spaced) result else " $result")
            else CalcHit("= $result", m.value.length, result)
        }
        unitAtEnd.find(tail)?.let { m ->
            val number = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let
            val converted = convert(number, m.groupValues[2].lowercase(Locale.ROOT).replace(" ", "")) ?: return@let
            val text = "${Calc.format(converted.first)} ${converted.second}"
            return CalcHit(text, 0, if (tail.last().isWhitespace()) "($text)" else " ($text)")
        }
        return null
    }

    /** The value in the paired unit, and that unit's name. */
    private fun convert(v: Double, unit: String): Pair<Double, String>? = when (unit) {
        "km" -> v * 0.621371 to "mi"
        "mi", "mile", "miles" -> v * 1.609344 to "km"
        "kg" -> v * 2.204623 to "lb"
        "lb", "lbs" -> v * 0.453592 to "kg"
        "cm" -> v / 2.54 to "in"
        "in", "inch", "inches" -> v * 2.54 to "cm"
        "ft", "feet" -> v * 0.3048 to "m"
        "oz" -> v * 28.349523 to "g"
        "gal" -> v * 3.785412 to "L"
        "ml" -> v / 29.573530 to "fl oz"
        "mph" -> v * 1.609344 to "km/h"
        "km/h", "kph" -> v * 0.621371 to "mph"
        "°c", "celsius" -> (v * 9 / 5 + 32) to "°F"
        "°f", "fahrenheit" -> ((v - 32) * 5 / 9) to "°C"
        else -> null
    }
}

/** A tiny arithmetic evaluator: numbers, + - * / x × ÷ ^ and parentheses. */
object Calc {

    fun evaluate(expression: String): Double? {
        val p = Parser(expression.replace(',', '.').replace('×', '*').replace('÷', '/').replace('–', '-'))
        return try {
            val v = p.parseExpression()
            if (!p.atEnd() || v.isNaN() || v.isInfinite()) null else v
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Whole numbers without a decimal point, others to at most two decimals ("3.1", "0.33"). */
    fun format(v: Double): String {
        if (abs(v - v.roundToLong()) < 1e-9 && abs(v) < 1e15) return v.roundToLong().toString()
        val rounded = (v * 100).roundToLong() / 100.0
        val s = String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean { skip(); return i >= s.length }
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun peek(): Char? { skip(); return s.getOrNull(i) }

        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += parseTerm() }
                    '-' -> { i++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parsePower()
            while (true) {
                when (peek()) {
                    '*', 'x', 'X' -> { i++; v *= parsePower() }
                    '/' -> { i++; val d = parsePower(); if (d == 0.0) throw IllegalArgumentException("divide by zero"); v /= d }
                    else -> return v
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            return if (peek() == '^') { i++; base.pow(parsePower()) } else base
        }

        private fun parseUnary(): Double {
            if (peek() == '-') { i++; return -parseUnary() }
            return parseAtom()
        }

        private fun parseAtom(): Double {
            if (peek() == '(') {
                i++
                val v = parseExpression()
                if (peek() == ')') i++ // a missing close bracket is fine while typing
                return v
            }
            skip()
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("number expected")
            return s.substring(start, i).toDouble()
        }
    }
}

/** What kind of thing was just copied, when the whole clip is exactly that. */
object SmartClip {

    class Found(val kind: Kind, val value: String)

    enum class Kind { CODE, EMAIL, PHONE, LINK }

    private val email = Regex("""^[\w.+%-]+@[\w-]+(\.[\w-]+)+$""")
    private val link = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
    private val phone = Regex("""^\+?[\d\s().-]{7,22}$""")

    fun find(text: String): Found? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 300) return null
        OtpDetector.find(t)?.let { return Found(Kind.CODE, it) }
        if (email.matches(t)) return Found(Kind.EMAIL, t)
        if (link.matches(t)) return Found(Kind.LINK, t)
        if (phone.matches(t) && t.count { it.isDigit() } in 7..15) return Found(Kind.PHONE, t)
        return null
    }
}
