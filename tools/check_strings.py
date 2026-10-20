#!/usr/bin/env python3
"""AR10 string gate (runs in CI before the build).

Fails when:
  * code uses R.string.x / R.plurals.x that values/strings.xml does not define (would not compile),
  * a key exists in English but not in Arabic (values-ar) or the other way round,
  * the format arguments of a key differ between English and Arabic (%1$d vs %s ... = crash or wrong text),
  * an Arabic plural lacks one of the six categories (zero one two few many other),
  * a string contains Arabic-Indic digits (the app always shows 1234567).
Warns (does not fail) for keys that no code uses.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(ROOT, "app", "src", "main", "java")
FMT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")
AR_DIGITS = re.compile("[٠-٩۰-۹]")
AR_PLURALS = {"zero", "one", "two", "few", "many", "other"}


def text_of(el):
    return "".join(el.itertext())


def load(path):
    strings, plurals = {}, {}
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if el.tag == "string":
            strings[name] = text_of(el)
        elif el.tag == "plurals":
            plurals[name] = {i.get("quantity"): text_of(i) for i in el if i.tag == "item"}
    return strings, plurals


def args(s):
    out = []
    pos = 0
    for m in FMT.finditer(s):
        if m.group(2) == "%":
            continue
        pos += 1
        n = int(m.group(1)) if m.group(1) else pos
        conv = m.group(2).lower()
        conv = "d" if conv in "dxo" else ("f" if conv in "feg" else conv)
        out.append((n, conv))
    return sorted(set(out))


def main():
    en_s, en_p = load(os.path.join(RES, "values", "strings.xml"))
    ar_s, ar_p = load(os.path.join(RES, "values-ar", "strings.xml"))
    errors, warnings = [], []

    code = []
    for d, _, files in os.walk(SRC):
        for f in files:
            if f.endswith((".kt", ".java")):
                with open(os.path.join(d, f), encoding="utf-8") as h:
                    code.append((f, h.read()))
    used_s, used_p = set(), set()
    for f, txt in code:
        for m in re.finditer(r"R\.string\.(\w+)", txt):
            used_s.add(m.group(1))
            if m.group(1) not in en_s:
                errors.append(f"{f}: R.string.{m.group(1)} is not defined in values/strings.xml")
        for m in re.finditer(r"R\.plurals\.(\w+)", txt):
            used_p.add(m.group(1))
            if m.group(1) not in en_p:
                errors.append(f"{f}: R.plurals.{m.group(1)} is not defined in values/strings.xml")

    for k in sorted(set(en_s) - set(ar_s)):
        errors.append(f"values-ar misses string '{k}'")
    for k in sorted(set(ar_s) - set(en_s)):
        errors.append(f"values-ar has string '{k}' that English lacks")
    for k in sorted(set(en_p) ^ set(ar_p)):
        errors.append(f"plural '{k}' is not in both languages")
    for k in sorted(set(en_s) & set(ar_s)):
        if args(en_s[k]) != args(ar_s[k]):
            errors.append(f"'{k}': format arguments differ: en {args(en_s[k])} vs ar {args(ar_s[k])}")
    for k in sorted(set(en_p) & set(ar_p)):
        missing = AR_PLURALS - set(ar_p[k])
        if missing:
            errors.append(f"Arabic plural '{k}' lacks {sorted(missing)}")
        want = args(en_p[k].get("other", ""))
        for q, v in ar_p[k].items():
            got = args(v)
            # Arabic may drop the number in one/two ("ملف واحد", "ملفان"), but never add or retype an argument
            if not set(got) <= set(want):
                errors.append(f"Arabic plural '{k}' ({q}): format arguments {got} are not in English {want}")
    for k, v in list(ar_s.items()) + [(k + "/" + q, v) for k, p in ar_p.items() for q, v in p.items()]:
        if AR_DIGITS.search(v):
            errors.append(f"'{k}' uses Arabic-Indic digits; write 1234567")

    # AR-TR: style warnings (Sakkaki-style concision, glossary terms); see tools/AR_GLOSSARY.md
    STYLE = [("يمكنك", "filler يمكنك"), ("قم ب", "قم بـ"), ("الرجاء", "الرجاء"), ("تأثير", "تأثير -> مؤثّر"), ("حافة", "حافة -> الحدّ"),
             ("التدوير", "التدوير -> الدوران"), ("كاراوكي", "كاراوكي -> كاريوكي"), ("صورة متحركة", "-> GIF"), ("اقتطاع", "اقتطاع -> قصّ"),
             ("...", "... -> …"), ("أسلوب", "أسلوب -> نمط"), ("هل تريد", "هل تريد -> أ...؟"), ("سوف ", "سوف"), ("يتم ", "يتم"), ("جارٍ ", "جارٍ -> مضارع مبني للمجهول")]
    for k, v in ar_s.items():
        for pat, why in STYLE:
            if pat in v and not (pat == "تم " and v.startswith("كتم")):
                warnings.append(f"ar '{k}': {why}")
    for k in sorted(set(en_s) - used_s - {"app_name"}):
        warnings.append(f"string '{k}' is not used in code")
    for k in sorted(set(en_p) - used_p):
        warnings.append(f"plural '{k}' is not used in code")

    for w in warnings:
        print("warning:", w)
    if errors:
        print("String gate failed:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print(f"String gate OK ({len(en_s)} strings, {len(en_p)} plurals, {len(warnings)} unused)")


if __name__ == "__main__":
    main()
