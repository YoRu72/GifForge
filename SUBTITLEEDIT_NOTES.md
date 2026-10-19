# Subtitle Edit study (SE1), s18o (carried into s18w)

Source: github.com/SubtitleEdit/subtitleedit (libse = the format and rule library, the WinForms UI on top).
The repository and nikse.dk help pages could not be fetched from the authoring sandbox (GitHub tree pages are blocked
for automated readers, the help site needs JavaScript), so this study is written from knowledge of Subtitle Edit 3.6/4.0.
Rows marked [VERIFY] need a check against the source before the step that uses them.

## 1. Architecture worth copying
- One internal model (`Subtitle` -> `Paragraph` with start/end/text/style/actor/layer) and about 300 format classes, each
  with `IsMine(lines)`, `LoadSubtitle`, `ToText`. Same shape as our `SubFormat` registry; ASS stays our lossless model.
- Rules are small classes behind one interface (`IFixCommonError.Fix(subtitle, callbacks)`), each reporting
  "fix count" and a preview row (line #, what, before, after). Our `FixCommonErrors.Rule` + `Fix` follow this.
- Language is detected per file (`LanguageAutoDetect`) and several rules only run for some languages (Arabic, Turkish,
  Danish, Spanish...). Our Arabic rules check per line with `RtlFix.isRtl`.

## 2. Fix Common Errors catalogue (SE names) and our plan
| SE rule | What it does | MediaForge |
|---|---|---|
| Remove empty lines / unused line breaks | drops blank paragraphs, trims leading/trailing breaks | done (FixRule.EMPTY) |
| Fix overlapping display times | end of a line after the next start | SE2 done (OVERLAP, per layer, same-start pairs left alone) |
| Fix short display times | below minimum duration; extends into free time | SE2 done (SHORT_DURATION) |
| Fix long display times | above maximum duration | SE2 done (LONG_DURATION) |
| Fix short gaps | gap below minimum between lines | SE2 done (SHORT_GAP) |
| Remove unneeded spaces | double spaces, space before punctuation, around breaks | SE2 partly (SPACES: doubles, ends, around \N) |
| Fix lowercase "i" alone | English " i " -> " I " | SE2 done (LONE_I, Latin lines only) |
| Fix invalid italic tags | unbalanced <i></i> | later (SE2b) |
| Remove unneeded periods / fix commas / missing spaces | ".." -> ".", ",," -> ",", "a,b" -> "a, b" | later (SE2b, LTR only) |
| Break long lines / unbreak short lines | uses max line length + "unbreak lines shorter than" | SE4 |
| Fix dialogs (dash style) | "- A\N- B" style | later (SE2b) |
| Start with uppercase after paragraph / after period | sentence casing | later (LTR only) |
| Add missing quotes, fix ellipsis, music symbols | | later |
| Fix common OCR errors | word lists per language | SE9 (needs OCR) |
| Fix Turkish ANSI, Danish i, Spanish ¿¡ | language-only rules | not planned |
| Arabic (SE has "Fix Arabic/RTL" via RTL tools: reverse start/end punctuation, fix RTL via Unicode control chars) | | done: RtlFix + ARABIC_PUNCT rule + AR3 tools (RLM/isolates/direction toggle) |

Defaults used (SE General settings) [VERIFY]: minimum duration 1000 ms, maximum 8000 ms, minimum gap 24 ms,
max characters per second 25 (SE 3.6 had 25, later profiles use 17-20), single line max length 43, max 2 lines.

## 3. Other Subtitle Edit tools and their step here
- Synchronization: adjust all times (= our Shift Times, done s18o), visual sync, point sync (two points), change frame
  rate, change speed in % -> SE3.
- Split/merge: split long lines, merge short lines, merge lines with same text, remove text for hearing impaired,
  change casing -> SE4.
- Rules profiles (Netflix, Amazon, BBC...): CPS, WPM, line length, min gap in frames, shot-change rules -> SE5.
- Batch convert, compare, statistics, multiple replace (rule lists, regex) -> SE6.
- Auto-translate (engines behind keys), translate side by side (done as A13.b) -> SE7.
- Waveform/spectrogram, shot changes from FFmpeg scene detection, speech-to-text (Whisper) -> SE8.
- OCR of image subtitles (Tesseract / nOCR / binary image compare), many more formats -> SE9.

## 4. Order chosen
SE2 core (s18o) -> SE3 sync (frame-rate change, point sync) -> SE5 profiles + CPS colouring in the grid -> SE4 split/merge
-> SE2b remaining text rules -> SE6 -> SE7 -> SE8 -> SE9.
