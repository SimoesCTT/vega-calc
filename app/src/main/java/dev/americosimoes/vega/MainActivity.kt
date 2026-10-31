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

// ---- Casio register state ----
data class CalcState(
    val entry: String = "0",
    val accumulator: Double? = null,
    val pendingOp: String? = null,
    val lastOp: String? = null,
    val lastOperand: Double = 0.0,
    val ans: Double = 0.0,
    val degMode: Boolean = true,
    val freshEntry: Boolean = true,
    val errorState: Boolean = false
)

@Composable
fun CasioCalcApp() {
    var state by remember { mutableStateOf(CalcState()) }

    val bg = Color(0xFF2B2B2B)
    val lcd = Color(0xFFC8D6A2)
    val lcdText = Color(0xFF161C10)
    val keyNum = Color(0xFF3C3C3C)
    val keyFn = Color(0xFF555555)
    val keyOp = Color(0xFFE58C2F)
    val keyAc = Color(0xFFB04040)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(10.dp)
    ) {
        // ---- LCD ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(lcd, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            Text(
                text = buildTopLine(state),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = lcdText,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = state.entry,
                fontFamily = FontFamily.Monospace,
                fontSize = 32.sp,
                color = lcdText,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---- 5x8 grid (Casio fx-82MS layout) ----
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row.forEach { label ->
                        val isNum = label.firstOrNull()?.isDigit() == true || label == "."
                        val isOp = label in listOf("+","−","×","÷","=","^")
                        val isAc = label in listOf("AC","ON")
                        val color = when {
                            isAc -> keyAc
                            isOp -> keyOp
                            isNum -> keyNum
                            else -> keyFn
                        }
                        Button(
                            onClick = { state = press(state, label) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = if (label.length > 3) 11.sp else 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildTopLine(s: CalcState): String {
    val sb = StringBuilder()
    sb.append(if (s.degMode) "D" else "R")
    if (s.accumulator != null && s.pendingOp != null) {
        sb.append("  ").append(formatNum(s.accumulator))
        sb.append(" ").append(opSymbol(s.pendingOp))
    }
    return sb.toString()
}

private fun opSymbol(op: String) = when (op) {
    "+" -> "+"; "-" -> "−"; "*" -> "×"; "/" -> "÷"; "^" -> "^"; else -> op
}

// ---- Keystroke dispatcher ----
private fun press(s0: CalcState, key: String): CalcState {
    var s = s0
    if (s.errorState && key != "AC" && key != "ON") {
        s = s.copy(entry = "0", errorState = false, freshEntry = true,
            accumulator = null, pendingOp = null)
    }

    return when {
        key.length == 1 && key[0].isDigit() -> {
            val d = key
            if (s.freshEntry) s.copy(entry = d, freshEntry = false)
            else s.copy(entry = s.entry + d)
        }
        key == "." -> {
            if (s.freshEntry) s.copy(entry = "0.", freshEntry = false)
            else if (!s.entry.contains('.')) s.copy(entry = s.entry + ".")
            else s
        }
        key in listOf("+","−","×","÷","^") -> {
            val op = when(key) { "−" -> "-"; "×" -> "*"; "÷" -> "/"; else -> key }
            if (s.pendingOp != null && s.freshEntry) {
                s = applyPending(s)
            }
            s.copy(accumulator = s.entry.toDoubleOrNull() ?: 0.0,
                pendingOp = op, freshEntry = true)
        }
        key == "=" -> {
            when {
                s.pendingOp != null -> {
                    val b = s.entry.toDoubleOrNull() ?: 0.0
                    val a = s.accumulator ?: 0.0
                    val r = calc(a, b, s.pendingOp!!)
                    if (r == null) s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
                    else s.copy(entry = formatNum(r), ans = r, lastOp = s.pendingOp,
                        lastOperand = b, pendingOp = null, accumulator = null, freshEntry = true)
                }
                s.lastOp != null -> {
                    val base = s.entry.toDoubleOrNull() ?: 0.0
                    val r = calc(base, s.lastOperand, s.lastOp!!)
                    if (r == null) s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
                    else s.copy(entry = formatNum(r), ans = r, freshEntry = true)
                }
                else -> {
                    val v = s.entry.toDoubleOrNull() ?: 0.0
                    s.copy(ans = v, freshEntry = true)
                }
            }
        }
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
        key == "√" -> applyUnary(s) { sqrt(it) }
        key == "x²" -> applyUnary(s) { it * it }
        key == "x³" -> applyUnary(s) { it * it * it }
        key == "x⁻¹" -> applyUnary(s) { if (it == 0.0) null else 1.0 / it }
        key == "log" -> applyUnary(s) { if (it <= 0) null else log10(it) }
        key == "ln" -> applyUnary(s) { if (it <= 0) null else ln(it) }
        key == "sin" -> applyUnary(s) { sin(if (s.degMode) Math.toRadians(it) else it) }
        key == "cos" -> applyUnary(s) { cos(if (s.degMode) Math.toRadians(it) else it) }
        key == "tan" -> applyUnary(s) { tan(if (s.degMode) Math.toRadians(it) else it) }
        key == "Ans" -> s.copy(entry = formatNum(s.ans), freshEntry = false)
        key == "×10ˣ" -> if (!s.entry.contains('e')) s.copy(entry = s.entry + "e", freshEntry = false) else s
        key == "MODE" || key == "SHIFT" -> s.copy(degMode = !s.degMode)
        else -> s // nCr, Pol, a b/c, °′″, RCL, ALPHA = no-op
    }
}

private fun applyPending(s: CalcState): CalcState {
    val op = s.pendingOp ?: return s
    val a = s.accumulator ?: return s
    val b = s.entry.toDoubleOrNull() ?: 0.0
    val r = calc(a, b, op) ?: return s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
    return s.copy(entry = formatNum(r), ans = r, accumulator = null, pendingOp = null)
}

private fun applyUnary(s: CalcState, f: (Double) -> Double?): CalcState {
    val x = s.entry.toDoubleOrNull() ?: return s
    val r = f(x) ?: return s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
    if (r.isNaN() || r.isInfinite()) return s.copy(entry = "Math ERROR", errorState = true, freshEntry = true)
    return s.copy(entry = formatNum(r), freshEntry = true)
}

private fun calc(a: Double, b: Double, op: String): Double? = when (op) {
    "+" -> a + b
    "-" -> a - b
    "*" -> a * b
    "/" -> if (b == 0.0) null else a / b
    "^" -> a.pow(b)
    else -> null
}

private fun formatNum(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "Math ERROR"
    if (v == 0.0) return "0"
    if (abs(v) >= 1e10 || (abs(v) < 1e-9 && v != 0.0)) return String.format("%.9e", v)
    return if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format("%.10g", v).trimEnd('0').trimEnd('.')
}
