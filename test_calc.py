#!/usr/bin/env python3
"""
Vega keystroke test suite — FROZEN from this point onward.
Do not edit the expect(...) lines below. Fix the code, not the test.
"""
import math, sys

# ---------- STATE ----------
class State:
    __slots__ = ('entry','accumulator','pendingOp','lastOp','lastOperand',
                 'ans','degMode','freshEntry','errorState','shiftArmed',
                 'latchedFn')
    def __init__(self):
        self.entry="0"; self.accumulator=None; self.pendingOp=None
        self.lastOp=None; self.lastOperand=0.0; self.ans=0.0
        self.degMode=True; self.freshEntry=True; self.errorState=False
        self.shiftArmed=False; self.latchedFn=None
    def copy(self):
        n=State()
        for k in self.__slots__: setattr(n,k,getattr(self,k))
        return n
    def num(self):
        try:
            if not self.entry or self.entry=="-": return 0.0
            return float(self.entry)
        except: return 0.0

def fmt(v):
    if math.isnan(v) or math.isinf(v): return "Math ERROR"
    if v==0.0: return "0"
    if abs(v)>=1e10 or (abs(v)<1e-9 and v!=0.0): return f"{v:.9e}"
    if v==int(v) and abs(v)<1e15: return str(int(v))
    return f"{v:.10g}".rstrip('0').rstrip('.')

# ---------- UNARY FUNCTION EVALUATOR ----------
def unary(fn, x, deg):
    if fn=="sqrt": return None if x<0 else math.sqrt(x)
    if fn=="cbrt": return math.copysign(abs(x)**(1/3), x)
    if fn=="x2":   return x*x
    if fn=="x3":   return x*x*x
    if fn=="inv":  return None if x==0 else 1.0/x
    if fn=="log":  return None if x<=0 else math.log10(x)
    if fn=="ln":   return None if x<=0 else math.log(x)
    if fn=="10x":  return 10.0**x
    if fn=="ex":   return math.exp(x)
    if fn=="sin":  return math.sin(math.radians(x) if deg else x)
    if fn=="cos":  return math.cos(math.radians(x) if deg else x)
    if fn=="tan":  return math.tan(math.radians(x) if deg else x)
    if fn=="asin": return None if abs(x)>1 else (math.degrees(math.asin(x)) if deg else math.asin(x))
    if fn=="acos": return None if abs(x)>1 else (math.degrees(math.acos(x)) if deg else math.acos(x))
    if fn=="atan": return math.degrees(math.atan(x)) if deg else math.atan(x)
    if fn=="fact": return None if (x<0 or x!=int(x) or x>170) else float(math.factorial(int(x)))
    return None

def apply_latched(s):
    """Apply armed latched function to current entry, clear latch."""
    if s.latchedFn is None: return s
    r = unary(s.latchedFn, s.num(), s.degMode)
    n = s.copy(); n.latchedFn = None
    if r is None or math.isnan(r) or math.isinf(r):
        n.entry="Math ERROR"; n.errorState=True; n.freshEntry=True
    else:
        n.entry=fmt(r); n.freshEntry=True
    return n

def shift_target(key):
    return {"x²":"sqrt","x³":"cbrt","√":"cbrt","log":"10x","ln":"ex",
            "sin":"asin","cos":"acos","tan":"atan","x⁻¹":"fact",
            "^":"ROOT","nCr":"nPr","Pol":"Rec",
            "×10ˣ":"PI","Ans":"E"}.get(key)

def do_equals(s0):
    s = s0.copy()
    if s.latchedFn is not None: s = apply_latched(s)
    if s.pendingOp is None and s.lastOp is None:
        return s.copy().also(lambda n: (setattr(n,'ans',s.num()), setattr(n,'freshEntry',True))) \
            if False else _ans(s)
    if s.pendingOp=="nCr" or s.pendingOp=="nPr":
        a=s.accumulator or 0.0; b=s.num()
        if a<0 or b<0 or b>a or a!=int(a) or b!=int(b):
            return _err(s)
        r = float(math.comb(int(a),int(b))) if s.pendingOp=="nCr" else float(math.perm(int(a),int(b)))
        n=s.copy(); n.entry=fmt(r); n.ans=r; n.lastOp=s.pendingOp; n.lastOperand=b
        n.pendingOp=None; n.accumulator=None; n.freshEntry=True; return n
    if s.pendingOp=="root":
        y=s.accumulator or 0.0; k=s.num()
        if k==0: return _err(s)
        r=y**(1.0/k)
        if math.isnan(r) or math.isinf(r): return _err(s)
        n=s.copy(); n.entry=fmt(r); n.ans=r; n.lastOp="root"; n.lastOperand=k
        n.pendingOp=None; n.accumulator=None; n.freshEntry=True; return n
    if s.pendingOp is not None:
        a=s.accumulator or 0.0; b=s.num(); op=s.pendingOp
        if op=="+": r=a+b
        elif op=="-": r=a-b
        elif op=="*": r=a*b
        elif op=="/": r=None if b==0.0 else a/b
        elif op=="^": r=a**b
        else: r=None
        if r is None or math.isnan(r) or math.isinf(r): return _err(s)
        n=s.copy(); n.entry=fmt(r); n.ans=r; n.lastOp=op; n.lastOperand=b
        n.pendingOp=None; n.accumulator=None; n.freshEntry=True; return n
    if s.lastOp is not None:
        a=s.num(); op=s.lastOp; b=s.lastOperand
        if op=="+": r=a+b
        elif op=="-": r=a-b
        elif op=="*": r=a*b
        elif op=="/": r=None if b==0.0 else a/b
        elif op=="^": r=a**b
        else: r=None
        if r is None or math.isnan(r) or math.isinf(r): return _err(s)
        n=s.copy(); n.entry=fmt(r); n.ans=r; n.freshEntry=True; return n
    return s

def _ans(s):
    n=s.copy(); n.ans=s.num(); n.freshEntry=True; return n

def _err(s):
    n=s.copy(); n.entry="Math ERROR"; n.errorState=True; n.freshEntry=True; return n

# ---------- KEYSTROKE DISPATCHER ----------
def press(s0, key):
    s = s0.copy()

    # SHIFT arming
    if s.shiftArmed and key != "SHIFT":
        tgt = shift_target(key)
        n = s.copy(); n.shiftArmed = False
        if tgt is None: return press(n, key)
        if tgt=="PI": n.entry="3.14159265358979"; n.freshEntry=True; return n
        if tgt=="E":  n.entry="2.71828182845905"; n.freshEntry=True; return n
        if tgt=="ROOT":
            if n.pendingOp is not None: n = do_equals(n)
            n.accumulator=n.num(); n.pendingOp="root"; n.freshEntry=True; n.latchedFn=None; return n
        if tgt=="nPr":
            if n.pendingOp is not None: n = do_equals(n)
            n.accumulator=n.num(); n.pendingOp="nPr"; n.freshEntry=True; n.latchedFn=None; return n
        if tgt=="Rec":
            if n.pendingOp is not None: n = do_equals(n)
            n.accumulator=n.num(); n.pendingOp="Rec"; n.freshEntry=True; n.latchedFn=None; return n
        # latched unary: arm it, wait for the operand
        n.latchedFn = tgt; n.freshEntry = True; n.entry = "0"
        return n
    if key == "SHIFT":
        n = s.copy(); n.shiftArmed = not n.shiftArmed; return n

    # error recovery
    if s.errorState and key not in ("AC","ON"):
        n = s.copy(); n.entry="0"; n.errorState=False; n.freshEntry=True
        n.accumulator=None; n.pendingOp=None; n.latchedFn=None
        s = n

    # ---- THE RULE: any non-digit key applies the latched function first ----
    # (digits build the operand; everything else commits it)
    if key not in "0123456789." and s.latchedFn is not None and key not in ("SHIFT",):
        s = apply_latched(s)

    if len(key)==1 and key.isdigit():
        n = s.copy()
        n.entry = key if s.freshEntry else s.entry + key
        n.freshEntry = False
        return n
    if key==".":
        n = s.copy()
        if s.freshEntry: n.entry="0."; n.freshEntry=False
        elif "." not in s.entry: n.entry = s.entry + "."
        return n
    if key in ("+","−","×","÷","^"):
        op = {"−":"-","×":"*","÷":"/"}.get(key,key)
        if s.pendingOp is not None: s = do_equals(s)
        n = s.copy(); n.accumulator=s.num(); n.pendingOp=op; n.freshEntry=True; return n
    if key=="=": return do_equals(s)
    if key in ("AC","ON"): return State()
    if key=="DEL":
        n = s.copy()
        if s.freshEntry: return n
        n.entry = "0" if len(s.entry)<=1 else s.entry[:-1]; return n
    if key=="(−)":
        n = s.copy()
        n.entry = s.entry[1:] if s.entry.startswith("-") else "-"+s.entry; return n
    if key=="√":  n=s.copy(); n.latchedFn="sqrt"; n.freshEntry=True; n.entry="0"; return n
    if key=="x²": n=s.copy(); n.latchedFn="x2";   n.freshEntry=True; n.entry="0"; return n
    if key=="x³": n=s.copy(); n.latchedFn="x3";   n.freshEntry=True; n.entry="0"; return n
    if key=="x⁻¹":n=s.copy(); n.latchedFn="inv";  n.freshEntry=True; n.entry="0"; return n
    if key=="log":n=s.copy(); n.latchedFn="log";  n.freshEntry=True; n.entry="0"; return n
    if key=="ln": n=s.copy(); n.latchedFn="ln";   n.freshEntry=True; n.entry="0"; return n
    if key=="sin":n=s.copy(); n.latchedFn="sin";  n.freshEntry=True; n.entry="0"; return n
    if key=="cos":n=s.copy(); n.latchedFn="cos";  n.freshEntry=True; n.entry="0"; return n
    if key=="tan":n=s.copy(); n.latchedFn="tan";  n.freshEntry=True; n.entry="0"; return n
    if key=="Ans": n=s.copy(); n.entry=fmt(s.ans); n.freshEntry=False; return n
    if key=="×10ˣ":
        n=s.copy()
        if "e" not in s.entry: n.entry=s.entry+"e"; n.freshEntry=False
        return n
    if key=="MODE": n=s.copy(); n.degMode=not n.degMode; return n
    return s

# ---------- TEST RUNNER ----------
PASS = FAIL = 0
def run(keys):
    s = State()
    for k in keys: s = press(s, k)
    return s
def expect(name, keys, expected, tol=1e-6):
    global PASS, FAIL
    s = run(keys); actual = s.entry
    try:
        av=float(actual); ev=float(expected); ok = abs(av-ev)<tol
    except: ok = (actual==expected)
    if ok: PASS+=1; print(f"  PASS  {name:42s}  {keys}  ->  {actual}")
    else:  FAIL+=1; print(f"  FAIL  {name:42s}  {keys}  ->  {actual}  (expected {expected})")

print("\n=== Arithmetic (register machine, no precedence) ===")
expect("2+3×4= -> 20", ["2","+","3","×","4","="], 20)
expect("2+3=", ["2","+","3","="], 5)
expect("10÷2=", ["1","0","÷","2","="], 5)

print("\n=== = repetition ===")
expect("5×=== -> 625", ["5","×","=","=","="], 625)

print("\n=== Unary functions (apply on next non-digit key) ===")
expect("sin 5", ["sin","5"], math.sin(math.radians(5)))
expect("√ 9", ["√","9"], 3)
expect("x² 5", ["x²","5"], 25)
expect("log 100", ["log","1","0","0"], 2)

print("\n=== Sticky Math ERROR ===")
expect("1÷0=", ["1","÷","0","="], "Math ERROR")

print("\n=== SHIFT latched secondary functions ===")
expect("SHIFT x² 9", ["SHIFT","x²","9"], 3)
expect("SHIFT x³ 8", ["SHIFT","x³","8"], 2)
expect("SHIFT log 3", ["SHIFT","log","3"], 1000)
expect("SHIFT ln 1", ["SHIFT","ln","1"], math.e)
expect("SHIFT sin 1", ["SHIFT","sin","1"], 90)
expect("SHIFT cos 0", ["SHIFT","cos","0"], 90)
expect("SHIFT tan 1", ["SHIFT","tan","1"], 45)
expect("SHIFT x⁻¹ 5", ["SHIFT","x⁻¹","5"], 120)
expect("SHIFT ×10ˣ = π", ["SHIFT","×10ˣ"], 3.14159265358979)
expect("SHIFT Ans = e", ["SHIFT","Ans"], math.e)

print("\n=== SHIFT nPr, x-th root ===")
expect("8 SHIFT nCr 2 = P(8,2)", ["8","SHIFT","nCr","2","="], 56)
expect("8 SHIFT ^ 3 = ∛8", ["8","SHIFT","^","3","="], 2)

print("\n=== nCr unshifted ===")
expect("8 nCr 2 = C(8,2)", ["8","nCr","2","="], 28)

print("\n=== Summary ===")
print(f"  {PASS} passed, {FAIL} failed")
sys.exit(0 if FAIL==0 else 1)
