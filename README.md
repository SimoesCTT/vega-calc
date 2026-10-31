# Vega

> A keystroke-accurate Casio fx-82MS emulator for Android. Kotlin + Jetpack Compose.

Vega is an Android scientific calculator that behaves like the hardware, not like a parser. It is a two-operand register machine, not an expression evaluator: every key press mutates a small, explicit finite state, and there is no intermediate symbolic expression. This is deliberate. It is the whole point.

    2 + 3 * 4 =   ->   20     (correct — this is what the fx-82MS does)
                             (not 14 — that's a parser, not an emulator)

The app reproduces the physical calculator's corner cases faithfully:

- 2 + 3 * 4 = -> 20 (folding at each operator press, no infix precedence)
- 5 * = -> 25 (= repeats the last binary operation)
- sin then 5 -> computes sin of 5 (unary functions apply immediately and clear the entry — the next digit replaces, it doesn't append)
- AC resets everything; DEL only backspaces the current entry
- SHIFT / MODE toggle DEG and RAD, shown on the LCD top line
- Division by zero, negative square root, log of non-positive -> sticky Math ERROR until AC
- Two-line LCD: top line shows DEG/RAD and the pending accumulator; bottom line is the entry, right-aligned monospaced
- 5x8 key grid matching the fx-82MS physical matrix, colour-coded by key class (numeric / function / operator / AC)

## Why

Every "Casio-style" calculator on the Play Store is a text field with a 5x8 grid painted around it, and an expression parser behind =. The look is Casio. The feel is not — because the corner cases (chained operators, = repetition, unary-then-digit, sticky error) diverge as soon as you press the wrong combination of keys. Nobody ships a faithful state machine because a faithful state machine is harder to build and gives "wrong" answers to algebraic input. That's fine; we're not trying to be algebraic.

See paper.tex for the full design argument, the register table, the press dispatcher, and the correctness invariants.

## Status

Working COMP-mode emulation. Deliberately not implemented in v0.1:

- SHIFT-latched secondary functions (the flag latches, but the functions aren't wired)
- Numeric systems (HEX / OCT / BIN)
- STAT / EQN / MATRIX modes
- Memory keys M+ / M- / MR / MC

None of these affect the keystroke semantics for the functions that are implemented. They are scope choices, not omissions.

## Build

Requires Gradle 9.1.0+ (AGP 9.0.1 refuses anything older) and JDK 17.

    gradle wrapper --gradle-version 9.1.0 --distribution-type bin
    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

### Note on Compose

AGP 9.0 embeds the Kotlin compiler, so you do not apply org.jetbrains.kotlin.android. You still need the separate org.jetbrains.kotlin.plugin.compose plugin (Kotlin 2.0+ requirement), and its version must match the Kotlin version embedded in AGP. AGP 9.0.1 embeds Kotlin 2.2.10, so:

    plugins {
        id("com.android.application")
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    }

A mismatch produces the compiler-version error you will otherwise see during the first build.

## Stack

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.0.1 |
| Kotlin (embedded in AGP) | 2.2.10 |
| Compose Compiler plugin | 2.2.10 |
| Compose BOM | 2025.12.00 |
| activity-compose | 1.11.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| JDK | 17 |

## Design notes

The state is a single immutable data class:

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
    )

Every key press is a pure function:

    press(state: CalcState, key: String): CalcState

freshEntry is the single most important flag — it's what makes sin then 5 compute sin(5) instead of sin(result of whatever came before). It is set by =, binary operators, AC, and unary functions; it is cleared by digits, ., Ans, and the x10^x key. That is the hardware's behaviour, exactly.

Because press is pure, the whole calculator is testable against property-based invariants — folding, freshness, =-repetition, sticky errors — without touching the UI.

## Name

Vega is the fifth-brightest star in the night sky, and historically the zero point of the photometric magnitude scale — the star against which brightness is measured. That's the role a faithful calculator should play: the reference point, not the convenience.

## Trademark

Casio and fx-82MS are trademarks of Casio Computer Co., Ltd. Vega is an independent emulation and is not affiliated with, endorsed by, or sponsored by Casio.

## License

Source is released under the MIT License. See LICENSE.
