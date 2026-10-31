package dev.americosimoes.vega

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CasioCalcApp() }
    }
}

data class CalcState(
    val entry: String = "0",
    val accumulator: Double? = null,
    val pendingOp: String? = null,
    val lastOp: String? = null,
    val lastOperand: Double = 0.0,
    val ans: Double = 0.0,
    val degMode: Boolean = true,
    val freshEntry: Boolean = true,
    val errorState: Boolean = false,
    val shiftArmed: Boolean = false,
    val latchedFn: String? = null,
    val fractionMode: Boolean = false,
    val fractionTriple: Triple<Long, Long, Long>? = null,
    val polarTheta: Double = 0.0
)

@Composable
fun CasioCalcApp() {
    var state by remember { mutableStateOf(CalcState()) }
    val bg = Color(0xFF2B2B2B); val lcd = Color(0xFFC8D6A2); val lcdText = Color(0xFF161C10)
    val keyNum = Color(0xFF3C3C3C); val keyFn = Color(0xFF555555)
    val keyOp = Color(0xFFE58C2F); val keyAc = Color(0xFFB04040)

    Column(modifier = Modifier.fillMaxSize().background(bg).padding(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().height(100.dp)
            .background(lcd, RoundedCornerShape(4.dp)).padding(8.dp)) {
            Text(text = buildTopLine(state), fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = lcdText, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(text = state.entry, fontFamily = FontFamily.Monospace,
                fontSize = 32.sp, color = lcdText, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(), maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        val rows = listOf(
            listOf("SHIFT","ALPHA","MODE","ON","DEL"),
            listOf("x⁻¹","nCr","Pol","x³","+"),
            listOf("a b/c","√","x²","^","−"),
            listOf("log","ln","(−)","°′″","×"),
            listOf("sin","cos","tan","RCL","÷"),
            listOf("7","8","9","AC","×10ˣ"),
            listOf("4","5","6","=","Ans"),
            listOf("1","2","3","0",".")
        )
        Column(modifier = Modifier.weight(1f)) {
            rows.forEach { row ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { label ->
                        val isNum = label.firstOrNull()?.isDigit() == true || label == "."
                        val isOp = label in listOf("+","−","×","÷","=","^")
                        val isAc = label in listOf("AC","ON")
                        val color = when { isAc -> keyAc; isOp -> keyOp; isNum -> keyNum; else -> keyFn }
                        Button(onClick = { state = press(state, label) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            contentPadding = PaddingValues(0.dp)) {
                            Text(text = label, color = Color.White,
                                fontSize = if (label.length > 3) 11.sp else 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun buildTopLine(s: CalcState): String {
    val sb = StringBuilder()
    if (s.shiftArmed) sb.append("S ")
    if (s.fractionMode) sb.append("F ")
    if (s.latchedFn != null) sb.append("f ")
    sb.append(if (s.degMode) "D" else "R")
    if (s.accumulator != null && s.pendingOp != null) {
        sb.append("  ").append(formatNum(s.accumulator))
        sb.append(" ").append(opSymbol(s.pendingOp))
    }
    return sb.toString()
}

private fun opSymbol(op: String) = when (op) {
    "+" -> "+"; "-" -> "−"; "*" -> "×"; "/" -> "÷"; "^" -> "^"
    "nCr" -> "C"; "nPr" -> "P"; "root" -> "ˣ√"; "Pol" -> "Pol"; "Rec" -> "Rec"; else -> op
}

private fun press(s0: CalcState, key: String): CalcState {
    var s = s0

    // --- SHIFT arming ---
    if (s.shiftArmed && key != "SHIFT") {
        val target = shiftTarget(key)
        var n = s.copy(shiftArmed = false)
        if (target == null) return press(n, key)   // unshifted press
        return when (target) {
            "PI" -> n.copy(entry = "3.14159265358979", freshEntry = true)
            "E"  -> n.copy(entry = "2.71828182845905", freshEntry = true)
            "DC" -> toggleDc(n)
            "root" -> {
                if (n.pendingOp != null && !n.freshEntry) n = doEquals(n)
                n.copy(accumulator = n.toNum(), pendingOp = "root", freshEntry = true, latchedFn = null)
            }
            "nPr" -> {
                if (n.pendingOp != null && !n.freshEntry) n = doEquals(n)
                n.copy(accumulator = n.toNum(), pendingOp = "nPr", freshEntry = true, latchedFn = null)
            }
            "Rec" -> {
                if (n.pendingOp != null && !n.freshEntry) n = doEquals(n)
                n.copy(accumulator = n.toNum(), pendingOp = "Rec", freshEntry = true, latchedFn = null)
            }
            else -> applyUnaryKey(n, target)
        }
    }
    if (key == "SHIFT") return s.copy(shiftArmed = !s.shiftArmed)

    // --- error recovery ---
    if (s.errorState && key != "AC" && key != "ON") {
        s = s.copy(entry = "0", errorState = false, freshEntry = true,
            accumulator = null, pendingOp = null, latchedFn = null)
    }

    return when {
        key.length == 1 && key[0].isDigit() -> {
            if (s.freshEntry) s.copy(entry = key, freshEntry = false)
            else s.copy(entry = s.entry + key)
        }
        key == "." -> {
            if (s.freshEntry) s.copy(entry = "0.", freshEntry = false)
            else if (!s.entry.contains('.')) s.copy(entry = s.entry + ".")
            else s
        }
        key in listOf("+","−","×","÷","^") -> {
            val op = when(key) { "−" -> "-"; "×" -> "*"; "÷" -> "/"; else -> key }
            // apply latched unary first, if any
            if (s.latchedFn != null) s = applyLatchedToEntry(s)
            if (s.pendingOp != null) s = doEquals(s)
            s.copy(accumulator = s.toNum(), pendingOp = op, freshEntry = true, latchedFn = null)
        }
        key == "nCr" -> twoOperand(s, "nCr")
        key == "Pol" -> twoOperand(s, "Pol")
        key == "=" -> doEquals(s)
        key == "AC" || key == "ON" -> CalcState()
        key == "DEL" -> {
            if (s.freshEntry) s
            else if (s.entry.length <= 1) s.copy(entry = "0")
            else s.copy(entry = s.entry.dropLast(1))
        }
        key == "(−)" -> {
            if (s.entry.startsWith("-")) s.copy(entry = s.entry.substring(1))
            else s.copy(entry = "-${s.entry}")
        }
        key == "a b/c" -> toggleFraction(s)
        key == "√" -> applyUnaryKey(s, "sqrt")
        key == "x²" -> applyUnaryKey(s, "x2")
        key == "x³" -> applyUnaryKey(s, "x3")
        key == "x⁻¹" -> applyUnaryKey(s, "inv")
        key == "log" -> applyUnaryKey(s, "log")
        key == "ln" -> applyUnaryKey(s, "ln")
        key == "sin" -> applyUnaryKey(s, "sin")
        key == "cos" -> applyUnaryKey(s, "cos")
        key == "tan" -> applyUnaryKey(s, "tan")
        key == "Ans" -> s.copy(entry = formatNum(s.ans), freshEntry = false)
        key == "×10ˣ" -> if (!s.entry.contains('e')) s.copy(entry = s.entry + "e", freshEntry = false) else s
        key == "MODE" -> s.copy(degMode = !s.degMode)
        else -> s
    }
}

private fun shiftTarget(key: String): String? = when (key) {
    "x²" -> "sqrt"
    "x³" -> "cbrt"
    "√"  -> "cbrt"
    "log" -> "10x"
    "ln" -> "ex"
    "sin" -> "asin"
    "cos" -> "acos"
    "tan" -> "atan"
    "x⁻¹" -> "fact"
    "^" -> "root"
    "nCr" -> "nPr"
    "Pol" -> "Rec"
    "a b/c" -> "DC"
    "×10ˣ" -> "PI"
    "Ans" -> "E"
    else -> null
}

// Apply unary function. If freshEntry is true (nothing typed yet), LATCH it.
// If freshEntry is false (user just typed a number), APPLY IMMEDIATELY (postfix).
private fun applyUnaryKey(s: CalcState, fn: String): CalcState {
    if (s.freshEntry) {
        // prefix / latched: arm the function, wait for operand
        return s.copy(latchedFn = fn, freshEntry = true)
    }
    // postfix: apply immediately to current entry
    val x = s.toNum()
    val r = evaluate(fn, x, s.degMode)
    return if (r == null || r.isNaN() || r.isInfinite())
        s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
    else
        s.copy(entry = formatNum(r), freshEntry = true, latchedFn = null)
}

private fun applyLatchedToEntry(s: CalcState): CalcState {
    val fn = s.latchedFn ?: return s
    val r = evaluate(fn, s.toNum(), s.degMode)
    return if (r == null || r.isNaN() || r.isInfinite())
        s.copy(entry = "Math ERROR", errorState = true, freshEntry = true, latchedFn = null)
    else
        s.copy(entry = formatNum(r), freshEntry = true, latchedFn = null)
}

private fun evaluate(fn: String, x: Double, deg: Boolean): Double? = when (fn) {
    "sqrt" -> if (x < 0) null else sqrt(x)
    "cbrt" -> cbrt(x)
    "x2"   -> x * x
    "x3"   -> x * x * x
    "inv"  -> if (x == 0.0) null else 1.0 / x
    "log"  -> if (x <= 0) null else log10(x)
    "ln"   -> if (x <= 0) null else ln(x)
    "10x"  -> 10.0.pow(x)
    "ex"   -> exp(x)
    "sin"  -> sin(if (deg) Math.toRadians(x) else x)
    "cos"  -> cos(if (deg) Math.toRadians(x) else x)
    "tan"  -> tan(if (deg) Math.toRadians(x) else x)
    "asin" -> if (abs(x) > 1) null else (if (deg) Math.toDegrees(asin(x)) else asin(x))
    "acos" -> if (abs(x) > 1) null else (if (deg) Math.toDegrees(acos(x)) else acos(x))
    "atan" -> if (deg) Math.toDegrees(atan(x)) else atan(x)
    "fact" -> if (x < 0 || x != floor(x) || x > 170) null else {
        var r = 1.0; var i = x.toInt(); while (i > 1) { r *= i; i-- }; r
    }
    else -> null
}

private fun twoOperand(s0: CalcState, op: String): CalcState {
    var s = s0
    if (s.latchedFn != null) s = applyLatchedToEntry(s)
    if (s.pendingOp != null) s = doEquals(s)
    return s.copy(accumulator = s.toNum(), pendingOp = op, freshEntry = true, latchedFn = null)
}

private fun doEquals(s0: CalcState): CalcState {
    var s = s0
    if (s.latchedFn != null) s = applyLatchedToEntry(s)

    if (s.pendingOp == null && s.lastOp == null) {
        return s.copy(ans = s.toNum(), freshEntry = true)
    }
    if (s.pendingOp in listOf("nCr", "nPr")) {
        val n = s.accumulator ?: 0.0
        val r = s.toNum()
        if (n < 0 || r < 0 || r > n || n != floor(n) || r != floor(r) || n > 170)
            return s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
        val res = if (s.pendingOp == "nCr") comb(n.toInt(), r.toInt()) else perm(n.toInt(), r.toInt()).toDouble()
        return s.copy(entry = formatNum(res), ans = res,
            lastOp = s.pendingOp, lastOperand = r,
            pendingOp = null, accumulator = null, freshEntry = true)
    }
    if (s.pendingOp == "Pol") {
        val x = s.accumulator ?: 0.0
        val y = s.toNum()
        val r = hypot(x, y)
        var th = atan2(y, x); if (s.degMode) th = Math.toDegrees(th)
        return s.copy(entry = formatNum(r), ans = r, polarTheta = th,
            lastOp = "Pol", lastOperand = y,
            pendingOp = null, accumulator = null, freshEntry = true)
    }
    if (s.pendingOp == "Rec") {
        val r = s.accumulator ?: 0.0
        val theta = if (s.degMode) Math.toRadians(s.toNum()) else s.toNum()
        val x = r * cos(theta)
        val y = r * sin(theta)
        return s.copy(entry = formatNum(x), ans = x, polarTheta = y,
            lastOp = "Rec", lastOperand = s.toNum(),
            pendingOp = null, accumulator = null, freshEntry = true)
    }
    if (s.pendingOp == "root") {
        val y = s.accumulator ?: 0.0
        val n = s.toNum()
        if (n == 0.0) return s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
        val res = y.pow(1.0 / n)
        return if (res.isNaN() || res.isInfinite())
            s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
        else s.copy(entry = formatNum(res), ans = res,
            lastOp = "root", lastOperand = n,
            pendingOp = null, accumulator = null, freshEntry = true)
    }
    if (s.pendingOp != null) {
        val a = s.accumulator ?: 0.0
        val b = s.toNum()
        val r = calc(a, b, s.pendingOp!!)
        return if (r == null)
            s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
        else s.copy(entry = formatNum(r), ans = r, lastOp = s.pendingOp, lastOperand = b,
            pendingOp = null, accumulator = null, freshEntry = true)
    }
    if (s.lastOp != null) {
        val a = s.toNum()
        val r = calc(a, s.lastOperand, s.lastOp!!)
        return if (r == null)
            s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
        else s.copy(entry = formatNum(r), ans = r, freshEntry = true)
    }
    return s
}

private fun calc(a: Double, b: Double, op: String): Double? = when (op) {
    "+" -> a + b; "-" -> a - b; "*" -> a * b
    "/" -> if (b == 0.0) null else a / b
    "^" -> a.pow(b)
    else -> null
}

private fun comb(n: Int, r: Int): Double {
    if (r < 0 || r > n) return 0.0
    var res = 1.0
    for (i in 1..r) res = res * (n - i + 1) / i
    return res
}
private fun perm(n: Int, r: Int): Double {
    if (r < 0 || r > n) return 0.0
    var res = 1.0
    for (i in 0 until r) res *= (n - i)
    return res
}

private fun toggleFraction(s: CalcState): CalcState {
    val v = s.toNum()
    val f = approximateFraction(v) ?: return s
    val (num, den) = f
    return s.copy(entry = "$num/$den", fractionMode = true, freshEntry = true,
        fractionTriple = Triple(num / den, num % den, den))
}

private fun toggleDc(s: CalcState): CalcState {
    val f = s.fractionTriple ?: return s
    val (whole, num, den) = f
    return if (s.fractionMode)
        s.copy(entry = "${whole * den + num}/$den", fractionMode = false)
    else
        s.copy(entry = if (whole != 0L) "$whole⌐$num/$den" else "$num/$den", fractionMode = true)
}

private fun approximateFraction(v: Double, maxDen: Long = 1_000_000): Pair<Long, Long>? {
    val sign = if (v < 0) -1 else 1
    var h0 = 0L; var h1 = 1L; var k0 = 1L; var k1 = 0L
    var x = abs(v)
    repeat(64) {
        val a = floor(x).toLong()
        val h2 = a * h1 + h0; val k2 = a * k1 + k0
        if (k2 > maxDen) return Pair(sign * h1, k1)
        h0 = h1; h1 = h2; k0 = k1; k1 = k2
        val frac = x - a
        if (frac < 1e-12) return Pair(sign * h1, k1)
        x = 1.0 / frac
    }
    return Pair(sign * h1, k1)
}

private fun CalcState.toNum(): Double = try {
    if (entry.isEmpty() || entry == "-") 0.0
    else if (entry.contains('e')) {
        val (m, e) = entry.split('e', limit = 2)
        (m.toDoubleOrNull() ?: 0.0) * 10.0.pow(e.toDoubleOrNull() ?: 0.0)
    } else entry.toDouble()
} catch (e: Exception) { 0.0 }

private fun formatNum(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "Math ERROR"
    if (v == 0.0) return "0"
    if (abs(v) >= 1e10 || (abs(v) < 1e-9 && v != 0.0)) return String.format("%.9e", v)
    return if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format("%.10g", v).trimEnd('0').trimEnd('.')
}
