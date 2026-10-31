# Vega

> A keystroke-accurate Casio fx-82MS emulator for Android. Kotlin + Jetpack Compose.

Vega is an Android scientific calculator that behaves like the hardware, not like a parser. It is a two-operand register machine, not an expression evaluator: every key press mutates a small, explicit finite state, and there is no intermediate symbolic expression. This is deliberate. It is the whole point.

    2 + 3 * 4 =   ->   20     (correct — this is what the fx-82MS does)
                             (not 14 — that's a parser, not an emulator)

## What works

### Register machine
- Two operands, one pending operator, one entry. No infix precedence. `2 + 3 × 4 =` gives 20.
- `=` repetition: `5 × = = =` gives 625 (constant multiply).
- Sticky `Math ERROR` on division by zero, negative square root, log of non-positive. Only `AC` recovers.
- `AC` resets all registers. `DEL` backspaces the current entry only.

### Unary functions (latched — applied on next key)
- `√`, `x²`, `x³`, `x⁻¹`, `log`, `ln`, `sin`, `cos`, `tan`
- In DEG mode, trig functions convert via radians.
- Latched functions apply as soon as the operand is committed (next non-digit key, or `=`).

### SHIFT-latched secondary functions
| Base | SHIFT → | Behaviour |
|---|---|---|
| `x²` | `√` | square root |
| `x³` | `∛` | cube root |
| `√` | `∛` | cube root |
| `log` | `10ˣ` | 10 to the x |
| `ln` | `eˣ` | e to the x |
| `sin` | `sin⁻¹` | arcsine (DEG-aware) |
| `cos` | `cos⁻¹` | arccosine (DEG-aware) |
| `tan` | `tan⁻¹` | arctangent (DEG-aware) |
| `x⁻¹` | `x!` | factorial (0 ≤ x ≤ 170, integer) |
| `^` | `ˣ√` | x-th root: `y SHIFT ^ n =` → y^(1/n) |
| `nCr` | `nPr` | permutations |
| `Pol` | `Rec` | polar → rectangular |
| `a b/c` | `d/c` | mixed ↔ improper fraction |
| `×10ˣ` | `π` | constant |
| `Ans` | `e` | constant |

### Two-operand operators
- `nCr` — combinations: `8 nCr 2 =` → 28
- `nPr` — permutations (via SHIFT + `nCr`): `8 SHIFT nCr 2 =` → 56
- `Pol` — polar: `5 Pol 12 =` → 13 (r), θ stored
- `Rec` — rectangular (via SHIFT + `Pol`)
- `^` — exponent: `2 ^ 10 =` → 1024
- `ˣ√` — x-th root (via SHIFT + `^`)

### Fractions
- `a b/c` converts the current decimal entry to a fraction approximation.
- `SHIFT a b/c` toggles mixed ↔ improper.

### Display
- Two-line LCD: top line shows DEG/RAD indicator, pending accumulator, and status flags (`S` shift, `F` fraction, `f` latched function). Bottom line is the entry, right-aligned monospaced.
- `MODE` toggles DEG ↔ RAD.
- 5×8 key matrix matching the fx-82MS physical layout, colour-coded by key class.

## Not implemented
- SHIFT-latched `a b/c` (mixed ↔ improper) is partially wired but not exercised in the primary test set.
- Memory keys: `M+`, `M−`, `MR`, `MC`, `STO`, `RCL`.
- Numeric systems: HEX / OCT / BIN.
- STAT / EQN / MATRIX modes.
- `ALPHA` secondary functions.

## Build

Requires Gradle 9.1.0+ (AGP 9.0.1 refuses anything older) and JDK 17.

    gradle wrapper --gradle-version 9.1.0 --distribution-type bin
    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

### Note on Compose

AGP 9.0 embeds the Kotlin compiler, so you do not apply `org.jetbrains.kotlin.android`. You still need the separate `org.jetbrains.kotlin.plugin.compose` plugin (Kotlin 2.0+ requirement), and its version must match the Kotlin version embedded in AGP. AGP 9.0.1 embeds Kotlin 2.2.10, so:

    plugins {
        id("com.android.application")
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    }

A mismatch produces the compiler-version error you will otherwise see during the first build.

### Signing

Release builds need a keystore. Create `keystore.properties` at the project root:

    storePassword=...
    keyPassword=...
    keyAlias=vega
    storeFile=keystore/vega-release.jks

`keystore.properties` and `keystore/` are git-ignored. Back them up separately.

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

The calculator state is a single immutable data class:

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

Every key press is a pure function:

    fun press(state: CalcState, key: String): CalcState

`freshEntry` is the flag that makes `sin` then `5` compute sin(5) rather than appending to the previous result. It is set by `=`, binary operators, `AC`, unary-function application, and SHIFT-latching. It is cleared by digits, `.`, `Ans`, and `×10ˣ`.

`latchedFn` implements the SHIFT-latched function behaviour: pressing SHIFT + a function key arms a function name, and the next time a digit sequence is committed (by `=`, an operator, or another function), the armed function is applied to the accumulated entry.

Because `press` is pure and the state is immutable, the entire calculator is testable against the keystroke contract without touching the UI.

## Name

Vega is the fifth-brightest star in the night sky, and historically the zero point of the photometric magnitude scale — the star against which brightness is measured. That is the role a faithful calculator should play: the reference point, not the convenience.

## Trademark

Casio and fx-82MS are trademarks of Casio Computer Co., Ltd. Vega is an independent emulation and is not affiliated with, endorsed by, or sponsored by Casio.

## License

Source is released under the MIT License. See LICENSE.
