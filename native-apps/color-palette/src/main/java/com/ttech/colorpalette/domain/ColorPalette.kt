package com.ttech.colorpalette.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Harmony(val label: String) {
    COMPLEMENTARY("補色"),
    ANALOGOUS("類似色"),
    TRIADIC("三色配色"),
    MONOCHROMATIC("モノクロマティック"),
}

/** #RRGGBB形式かどうか。 */
fun isValidHex(hex: String): Boolean = Regex("^#[0-9a-fA-F]{6}$").matches(hex)

/** 配色生成のロジック(Webのmicrosaas版と同じ計算式)。h: 0-360, s/l: 0-100。 */
object ColorPalette {

    fun hexToHsl(hex: String): Triple<Double, Double, Double> {
        val r = hex.substring(1, 3).toInt(16) / 255.0
        val g = hex.substring(3, 5).toInt(16) / 255.0
        val b = hex.substring(5, 7).toInt(16) / 255.0
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) / 2
        val d = maxC - minC
        val s = if (d == 0.0) 0.0 else d / (1 - abs(2 * l - 1))
        var h = 0.0
        if (d != 0.0) {
            h = when (maxC) {
                r -> ((g - b) / d).mod(6.0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            }
            h *= 60
            if (h < 0) h += 360
        }
        return Triple(h, s * 100, l * 100)
    }

    fun hslToHex(h: Double, s: Double, l: Double): String {
        val sN = s / 100
        val lN = l / 100
        val c = (1 - abs(2 * lN - 1)) * sN
        val x = c * (1 - abs((h / 60).mod(2.0) - 1))
        val m = lN - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0.0)
            h < 120 -> Triple(x, c, 0.0)
            h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c)
            h < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun toHex(v: Double): String =
            ((v + m) * 255).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#${toHex(r)}${toHex(g)}${toHex(b)}"
    }

    /** ベースカラーから配色ルールに沿った5色を生成する。 */
    fun generate(baseHex: String, harmony: Harmony): List<String> {
        val (h, s, l) = hexToHsl(baseHex)
        return when (harmony) {
            Harmony.COMPLEMENTARY -> listOf(
                hslToHex((h + 360).mod(360.0), s, max(l - 20, 10.0)),
                hslToHex(h, s, l),
                hslToHex(h, s, min(l + 20, 90.0)),
                hslToHex((h + 180).mod(360.0), s, l),
                hslToHex((h + 180).mod(360.0), s, min(l + 20, 90.0)),
            )
            Harmony.ANALOGOUS -> listOf(-30, -15, 0, 15, 30).map { offset ->
                hslToHex((h + offset + 360).mod(360.0), s, l)
            }
            Harmony.TRIADIC -> listOf(
                hslToHex(h, s, max(l - 15, 10.0)),
                hslToHex(h, s, l),
                hslToHex((h + 120).mod(360.0), s, l),
                hslToHex((h + 240).mod(360.0), s, l),
                hslToHex((h + 240).mod(360.0), s, min(l + 15, 90.0)),
            )
            Harmony.MONOCHROMATIC -> listOf(30, 15, 0, -15, -30).map { dl ->
                hslToHex(h, s, min(max(l + dl, 8.0), 92.0))
            }
        }
    }
}
