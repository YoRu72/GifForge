# ARABIC_GRAMMAR_NOTES.md - nahw reference for proofreading subtitles

Status: study notes (roadmap AR-GR1). Feeds AR6 (Arabic QA rules in Fix Common Errors) and AR-GR2 (the checker).

## 0. Honest scope
- Sources named by the owner: **ألفية ابن مالك** (the rules, in verse), **شرح ابن عقيل** (clear classical commentary, d. 769 AH) and **أوضح المسالك لابن هشام** (d. 761 AH; the same Alfiyya explained with the disagreements between grammarians). Ibn Hisham's **مغني اللبيب** is the reference for particles (إنّ/أنّ, لو, ما, أو/أم...).
- These notes were written from knowledge of those books WITHOUT the books or internet at hand. Verse openings are quoted only where certain. **No page numbers.** Before a rule becomes an automatic error, the owner checks it against the printed commentary; rules marked [VERIFY] are the ones I am least sure about.
- The Alfiyya covers nahw and sarf. **Spelling (إملاء)** is mostly outside it (hamzat al-wasl/qat3, the final alef/ya, ta marbuta/ha, spacing). Those need a separate source: do not claim they come from the Alfiyya.
- A proofreader flags SUGGESTIONS, never silently rewrites. Where Ibn Aqil and Ibn Hisham allow two forms, both are accepted (section 4).

## 1. Rules that matter for subtitles, by chapter (باب)
Detectability: **A** = safe pattern without diacritics; **B** = heuristic, may false-positive, show as hint; **C** = needs parsing or diacritics (not in scope now).

| ID | Chapter (الألفية) | Rule | Typical error in subtitles | Detect | Suggest |
|---|---|---|---|---|---|
| G1 | الإعراب (الأسماء الخمسة): "فارفع بواو وانصبن بالألف" | أبو/أخو/حمو/ذو/فو: waw (nom), alef (acc), ya (gen) when annexed to anything but the speaker's ya | رأيتُ أبوك / مررتُ بأخوه / إنّ أخوك | A after a preposition or نصب particle, B elsewhere | أباك / بأخيه / أخاك |
| G2 | المثنى وجمع المذكر السالم: "بالألف ارفع المثنى", "وارفع بواو وبيا اجرر وانصب سالم جمع عامر ومذنب" | dual: ا (nom) / ي (acc, gen); sound masc. plural: و / ي | جاء المعلمين; رأيت المعلمون; مررت بالمهندسون | B (needs the role of the word: verb + subject, preposition + noun) | fix the ending |
| G3 | إنّ وأخواتها: "لإن أن ليت لكن لعل كأن عكس ما لكان من عمل" | inna-sisters put the subject in the accusative and keep the predicate nominative | إنّ الطلابَ مجتهدين (predicate wrongly accusative); إنّ المعلمون (subject wrongly nominative) | B | المجتهدون / المعلمين |
| G4 | كان وأخواتها: "ترفع كان المبتدا اسما والخبر تنصبه" | kana-sisters: subject nominative, predicate accusative | كان الطقسُ جميلٌ | C without diacritics; A for the dual/plural endings (كانوا/كان + ون/ين) | |
| G5 | العدد: "ثلاثة بالتاء قل للعشرة في عد ما آحاده مذكرة" | 3-10 take the OPPOSITE gender of the counted noun and a plural genitive noun; 11-19 take a singular accusative noun; 20-90 a singular accusative noun; 100 and 1000 a singular genitive | ثلاثة بنات / ثلاث رجال / عشرون كتب / خمسة عشر طلاب | A with a small noun-gender lexicon | ثلاث بنات / ثلاثة رجال / عشرون كتابًا |
| G6 | العدد: 11-19 | both parts agree in gender except عشر/عشرة (opposite polarity rule applies to the units 3-9 only; 11 and 12 agree in gender) [VERIFY wording] | إحدى عشر رجلاً | B | |
| G7 | التمييز | after a number or a measure the noun is singular and indefinite | عشرين الكتاب (definite) | A | عشرين كتابًا |
| G8 | الاستثناء: "ما استثنت إلا مع تمام ينتصب" | after إلا in a complete, positive sentence the excepted noun is accusative; after a negative sentence the noun may follow the main word (badal) | حضر الجميع إلا المعلمون | B (skip if the sentence has نفي: ما/لا/لم/لن/ليس) | إلا المعلمين |
| G9 | ما لا ينصرف: "وجر بالفتحة ما لا ينصرف ما لم يضف أو يك بعد أل ردف" | a diptote has no tanwin and takes fatha for the genitive, unless annexed or with ال | مررت بأحمدٍ / بمساجدٍ | A when diacritics are present, B with a small lexicon (أحمد, مساجد, مصابيح...) | |
| G10 | إنّ/أنّ (همزة إن): rules of kasr and fath | kasra at the start of a sentence, after قال, after حيث, after إذ, in the apodosis; fatha when the clause can be replaced by a masdar (after أعلم, يسرني...) | قال أنّه...; أنّ الطلاب... at the start | A for "قال أنّ", "حيث أنّ" [VERIFY: حيث أنّ is a modern usage the old grammarians do not treat as correct], B for sentence start | قال إنّه |
| G11 | لا النافية للجنس: "عمل إن اجعل للا في نكرة مفردة جاءتك أو مكررة" | la + indefinite noun: no tanwin | لا رجلٌ في الدار (as negation of the genus) | C | |
| G12 | الموصول | الذي (m.s), التي (f.s), اللذان/اللذين (dual), الذين (m.pl), اللاتي/اللواتي (f.pl); agree with the antecedent | الطلاب الذي; البنات الذين | B | |
| G13 | اسم الإشارة | هذا/هذه/هذان/هذين/هؤلاء; agree with the pointed noun | هذا البنات | B | |
| G14 | التوكيد: "بالنفس أو بالعين الاسم أكدا مع ضمير طابق المؤكدا" | نفس/عين agree with the noun and take a matching pronoun | جاء الطلاب نفسه | B | نفسُهم / أنفسُهم |
| G15 | النعت | the adjective agrees in number, gender, definiteness and case with the noun (non-human plurals take feminine singular) | الكتب الجميلين; طالب مجتهدة | B | |
| G16 | حروف الجر / الإضافة | the annexed noun loses tanwin and ال; the second term is genitive | الكتابُ الطالب (idafa with ال on both) | B (confusable with noun + adjective) | كتاب الطالب |
| G17 | الإضافة إلى ياء المتكلم | kasra before ya, ya may be open or silent | | C | |
| G18 | الحال | usually indefinite, accusative | جاء الطالب المبتسمُ as a state | C | |

## 2. Where the commentaries help the proofreader
- **Ibn Aqil**: states the rule plainly, then gives the شاهد and the ruling. Use him as the tie-breaker for "is this an error?".
- **Ibn Hisham (Awdah)**: records the disagreements (البصريون/الكوفيون) and the weaker dialects. Use him to build the allowed-variants list in section 4.
- **Ibn Hisham (Mughni)**: for particles, for example the senses of ما, لو, أو and the difference between إنّ and أنّ.

## 3. How the checker (AR-GR2) should behave
1. Rules run on **plain text only** (override tags removed). No diacritics are added or removed.
2. Three severities: **error** (class A with certainty), **hint** (class B), **info**. Class C is never reported.
3. Every report carries: rule id (G1...), the matched words, a suggested form, and the one-line rule in Arabic (no padding; AR_GLOSSARY style).
4. Skip: text inside « » and "...", lines marked as comment, lines with the Effect field set, any line the owner marked reviewed.
5. Never auto-fix. The owner taps the suggestion (like a spell-check chip) or ignores it; ignores are remembered per rule per file.
6. A rule needs 10 real subtitle examples (good and bad) in tools/ar_grammar_cases.txt before it ships.

## 4. Accept both (do not flag)
- أكلوني البراغيث (verb agreeing with a plural subject placed after it): a recognised dialect, appears in speech.
- Dual always with alef (لغة بني الحارث): colloquial subtitles use it; flag only as info.
- أبٌ/أخٌ/حمٌ with نقص (أبك, أخك): the Alfiyya itself notes it as rare but valid; do not flag.
- ثلاث مئة / ثلاثمئة / ثلاثمائة: spelling variants, not grammar.
- Both genders for "طريق", "سبيل", "سوق", "لسان" and similar nouns that accept both: a number lexicon must list them as "either".
- Poetry (ضرورة الشعر): lines that look like verse (two hemistichs, or in « ») are skipped.
- Dialectal Arabic: if more than 30 percent of a line's words are outside a small MSA list, skip it.

## 5. Next steps
- AR-GR2: implement G1, G5, G7, G10 (class A) first, in `subs/ArabicCheck.kt`, with the cases file; show results as chips under the line editor.
- Then G2, G3, G8, G12, G13, G15 as hints.
- Owner review of the [VERIFY] rows against the printed commentaries.
