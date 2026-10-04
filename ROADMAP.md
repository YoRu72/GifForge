# MediaForge (formerly GifForge) roadmap (source of truth - never drop a step)
Last update: s18j (Arabic display fix: removed forced font features; zero letter spacing in every UI language + CI guard tools/check_arabic.sh; subtitler Step 2: styled live preview so blur/border/shadow/scale/rotation/fades are visible, new Look panel with font picker, size, bold/italic, alignment, 3x3 pad, X/Y position, colours). Before: s18i (Arabic proofreading pass 1 + tools/AR_GLOSSARY.md). Before: s18h (A13.b translate mode). Before: s18g (A17 video export through FFmpeg wired: soft MKV/MP4, hard burn-in with fonts folder; roadmap gains tracks OPT, FEAT, AR-TR, AEG-S). Before: s18f (A17 export chooser UI + subtitle-file export; A19 part 2 Text/Shapes tabs). Before: s18e (A13 first part: video docked in the subtitle screen with real-time timing). Before: s18d (kashida bug 'الـا' fixed; A19 part 1: GIF host/editor/trim translated). Before: s18c (grid + collapsible line settings + keyframe effects with opacity fades). Before: s18b (Drop 2: A7 partly, A9 nav, A10, A11 delivered; Drops 3-6 planned as three tracks AR / AEG / SE).
Legend: [x] done, [~] partial, [ ] todo. Work in small parts so no tokens are wasted. Every reply ships the current zip.

## PRIORITY LEG A (ADDED by owner, s18): Arabic-first + UI + Videos section. Done BEFORE all other open steps. Delivered in drops 1 to 6 (see below).
Rules from the owner: Arabic matters most; Arabic is its own system (not bolted onto English); numbers always Western 1234567; Arabic copy is concise classical Arabic (Sakkaki-style: exact, no filler, no colloquial); the supplied font UthmanTN1 is the app's Arabic font; never break the UI.
### Drop 1 (s18, this zip): Arabic foundation + font and flip bugs
- [x] A1 Language system: Settings > Language (device / English / العربية), applied live without restart; separate locale layer (l10n/AppLang.kt), RTL layout direction, Locale carries -u-nu-latn so every number is 1234567; res/values-ar is the Arabic string set (plurals use real Arabic forms)
- [x] A2 Supplied font UthmanTN1 shipped in the app (res/font), used as the Arabic UI typeface (taller line height so marks never clip) and copied once into the font library
- [x] A3 Glyph-font bug: Arabic/symbol/icon fonts used to preview with Latin sample text they do not contain, so Android drew the sample in the system font (looked like Arial). Now the cmap is read and the preview uses glyphs the font really has (Arabic sample, digits, or its own symbols)
- [x] A4 Flipped text bug: Left/Center/Right were mapped to NORMAL/OPPOSITE, which mirror on right-to-left text. Alignment is now physical (TextLayouts.kt) for text layers and caption bars; preview frame, crop box, timelines, range sliders and the 3x3 position pad are locked left-to-right inside Arabic screens
- [x] A5 Arabic text drawing: shared layout builder (no letter spacing, full-height lines for tashkeel, fallback line spacing), one code path for layers and bars so preview == export
- [x] A6 Translated screens: Home, Settings, Fonts, import messages. Roadmap step 10.5 is split: shaping/bidi/digits/font here, rest in A7 to A12
### Drop 2 (s18b, this zip): whole-app Arabic + UI cleanup (Rexplayer style) + Videos hub
- [~] A7 Every English literal moved to string resources + Arabic. DONE: Editor shell and tabs, Trim, Crop, timing, layer look, export settings, result dialog, Bars. LEFT -> A19
- [ ] A8 Layer editor offers the font's own sample text when the font lacks the layer's letters (see A29)
- [~] A9 UI cleanup modelled on Rexplayer. DONE: bottom navigation (Home / Videos / Fonts / Settings), one entry per section. LEFT: card lists with thumbnails, consistent spacing, editor tabs as a bottom sheet so the preview is never squeezed (A9b)
- [x] A10 Videos hub (VideosScreen): open video/GIF, media finder, open subtitle, new subtitle, recent subtitles
- [~] A11 Subtitle screen, Aegisub style (reworked s18c on the owner's note: the LINE is plain, its SETTINGS collapse). Top: compact grid (#, start, end, style [wide screens], text), tap = active line, long-press = multi-select. Docked below: line text, then two collapsible sections (both closed by default): Line settings (start, end, style, actor, effect, layer, margins, comment) and Effects. Insert/duplicate/delete, previous/next line, save via SAF in the file's own format and encoding. Times stay left-to-right in Arabic. LEFT: undo/redo, search/replace, split/join (A14)
- [ ] A12 A+B loop kept and moved into the Videos player controls

## THE THREE TRACKS (owner: update evenly). Every drop below carries one Arabic item (AR), one Aegisub item (AEG), one Subtitle Edit item (SE). Study steps come first inside their drop.
Sources: github.com/Aegisub/Aegisub (and TypesettingTools fork), github.com/SubtitleEdit/subtitleedit. Roadmap sections 17-29 below hold the detailed Aegisub-derived steps; they are scheduled here.

### Drop 3: subtitling workspace + export chooser (core of the owner's request)
- [~] A13 (s18e DONE: video pane above the grid, overlay of the current line, transport + frame step, Set start / Set end from the playhead, 'End + next start' tap timing, +-1 frame and +-10 ms nudges for start and end, play line, loop line, follow-video selection, attach/detach video from the toolbar, hub card 'Subtitle a video'. LEFT: per-line play inside the grid, keyframe snapping, waveform (AEG-B), real ASS overlay (A21), Arabic-safe caret tools AR3) Subtitling workspace: video on top, line list below, edit box docked (Aegisub + Subtitle Edit layout), tap-to-set start/end from the playhead, nudge buttons (+-1 frame, +-10 ms), per-line play and loop, keyframe snapping. Timing is done by the owner on the video itself
- [~] A13.b (s18h DONE: Translate mode button in the subtitle screen; original line read-only above the editable translation, copy original, clear, jump to next untranslated, 'x of y translated', grid numbers coloured translated/untranslated, insert/duplicate/delete locked in this mode so lines stay 1:1; works with the video pane. LEFT: load a separate original file side by side, per-line RTL tools (AR3), machine translation (SE7)) (SE) Subtitle Edit style 'translate mode': original column read-only + translation column editable, next/previous, jump to untranslated; for Aegisub's translation assistant see A15
- [ ] A14 (AEG) List editing with undo/redo, search/replace (regex), split/join/duplicate/swap/sort, autosave + crash recovery (18.c to 18.f)
- [~] A15 (s18h: covered by A13.b for the core flow; LEFT: mark-translated flag stored in the file, styling assistant) (AEG) Translation assistant: original line / translation line side by side, next/previous, copy original, mark translated (23.e)
- [~] A17 (s18f DONE: ExportChooser.kt sheet from the Export button of the subtitle screen, three paths: file only / soft / hard; FILE path works now: any format of the registry x UTF-8, UTF-8 BOM, UTF-16 LE, Windows-1256, ISO-8859-6; soft/hard collect container MKV/MP4, encoder H.264/HEVC/VP9/AV1/copy, CRF, resolution, audio copy into VideoExportSettings and say plainly that the video engine is still missing. LEFT: A17.a-A17.e below; also a Videos-hub export entry for videos without subtitles) Export chooser, shown BEFORE exporting: (1) video with subtitles - soft (muxed track, mp4/mkv) or hard (burned in); encoder choice H.264/HEVC/VP9/AV1, container, quality (CRF/bitrate), resolution, audio copy; (2) subtitle file only - any format, any encoding (UTF-8 / UTF-8 BOM / UTF-16 / Windows-1256 / ISO-8859-6...). Needs 10.B + 10.C (FFmpeg) first: they move up to A17.a, A17.b
  - [~] A17.a (s18g) FFmpeg engine: VideoExporter.kt calls the FFmpegKit API by reflection; artifact set by Gradle property ffmpegKit (gradle.properties, default com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1 = UNVERIFIED, arm64-v8a only; original com.arthenica packages are gone from Maven Central, forks: ffmpegkit-maintained/ffmpeg (dev.ffmpegkit-maintained:ffmpeg-kit-free, no libass/x264 in the free tier: check) ). OWNER CHECK in CI: (1) coordinate resolves, (2) the tier has libass (hard subs), libx264/x265, libvpx, svtav1 - otherwise pick another tier or build our own (android.sh) in CI. LEFT: own static FFmpeg build in CI as the safe path, other ABIs, progress bar from statistics, SAF read without copying
  - [x] A17.b (s18g) decode path for export = FFmpeg reads a cached copy of the video (copy step is slow for big files -> OPT3)
  - [x] A17.c soft mux (s18g): MKV keeps the ASS track as is, MP4 gets a text track from SRT (styles lost, said in the sheet); track language chosen (Arabic default)
  - [x] A17.d burn-in (s18g): FFmpeg ass filter + fontsdir = the app font folder, so imported fonts (UthmanTN1...) render; scale is applied before burning. Preview == export is NOT guaranteed until A21
  - [ ] A17.e encoder presets (fast/balanced/small), hardware encoder option (MediaCodec) for speed, two-pass, bitrate mode, trim range, audio track pick
  - [x] A17.f subtitle-only export dialog (s18f)
- [ ] AR3 (AR) Subtitle editing in Arabic: caret/selection in RTL fields, LRM/RLM/isolates insert button, per-line direction toggle, Arabic punctuation (، ؛ ؟ « »), Win-1256/UTF-8 choice on save, mojibake repair (A18 + A23 + A25)

### Drop 4: effects with keyframes + Arabic depth + Aegisub core
- [~] A16 (AEG) [s18c: a, b, c, e(opacity fades) DONE; d preview LEFT until the renderer A21] Effects with keyframes, per point on the text: blur (\blur, \be), border, shadow, fade, scale, rotation, colour, alpha. Value-over-time GRAPH editor: tracks per property, add/drag keyframes, linear / ease-in / ease-out / hold curves, scrub on the video, live preview. Compiled to ASS \t(t1,t2,accel,...) and \fad. Text range: whole line, a selection, or per word/letter (karaoke-style stagger)
  - [x] A16.a model (subs/Effects.kt): Track(property, keyframes[time, value, curve]); properties opacity, blur, border, shadow, scale, rotation, letter spacing; curves linear / ease in / ease out / hold
  - [~] A16.b graph (EffectsPanel.kt, under each line, Alight Motion style): tap to add a keyframe, drag to move (stays between neighbours), numeric time/value, curve per segment, delete. LEFT: pinch zoom on time, snap to playhead and to other lines, scrub with the video
  - [x] A16.c compiler to ASS: one override block per line, plain tags (\alpha, \blur, \t with accel) plus a harmless 'MF1;' note so the keyframes reopen editable; Aegisub and libass play the tags unchanged
  - [ ] A16.d live preview on the video through the renderer (A21)
  - [~] A16.e presets: opacity fade in / fade out / both DONE; blur in/out, pulse, typewriter, shake, glow LEFT
  - [ ] A16.f per word / per letter ranges (stagger), colour and alpha-per-channel tracks
  - [ ] A16.g GIF/video export uses the same compiler (A17)
- [ ] A21 (AEG) ASS rendering engine + decision gate libass vs Kotlin (21.a-21.g), shaping + bidi from day one
- [ ] SE1 (SE) Study Subtitle Edit (10.4): formats list, Fix Common Errors rules, sync tools, translate, waveform, batch, OCR -> SUBTITLEEDIT_NOTES.md; then pick the order of SE2 to SE20
- [ ] AR4 (AR) Arabic text engine: shaping (HarfBuzz via Android layout), lam-alef, tashkeel placement, kashida/tatweel justification, mixed LTR numbers and Latin words inside RTL lines, preview == export (A21 hook)
- [ ] AR5 (AR) Per-run fonts in one layer: Arabic runs in the chosen Arabic font (UthmanTN1 for example), Latin runs in a Latin font (fixes 'no Latin glyphs' fallback to system font)

### Drop 5: Aegisub parity + Subtitle Edit core tools + Arabic tools
- [ ] AEG-A (AEG) Styles manager + editor, script properties, attachments, fonts collector (19.a-19.d)
- [ ] AEG-B (AEG) Video timing: frame stepping, keyframes, waveform + touch timing, snap, spectrogram optional (20.a-20.f)
- [ ] AEG-C (AEG) Timing tools: Shift Times, Timing Post-Processor, CPS/QC checker, Resolution Resampler, styling assistant, spell checker (23.a-23.f)
- [ ] SE2 (SE) Fix Common Errors: overlap, short/long duration, gaps, empty lines, double spaces, dialog dashes, uppercase i, music symbols, line balancing; each rule toggled, preview of every fix, apply selected
- [ ] SE3 (SE) Synchronization: shift all, point sync (two points), visual sync, change frame rate (23.976 <-> 25 ...), adjust to a new duration
- [ ] SE4 (SE) Split/merge: split long line at best point, merge short lines, merge/split whole files, remove text for hearing impaired ([music], speaker names), change casing, remove duplicates
- [ ] SE5 (SE) Reading-speed profiles (Netflix, BBC, custom): CPS, WPM, max line length, max lines, min gap; colour-coded in the list
- [ ] AR6 (AR) Arabic QA rules inside Fix Common Errors: RTL punctuation, tashkeel kept/stripped, alef/yeh/kaf normalisation, digits to 1234567, CPS counted without tashkeel, line-length by letters
- [ ] AR7 (AR) Arabic font manager: filter fonts that support Arabic, sample نص تجريبي, warn when the font lacks glyphs of the current text, mark fonts with tashkeel support

### Drop 6: Subtitle Edit breadth + Aegisub scripting + Arabic finish
- [ ] SE6 (SE) Batch convert (folder in, format/encoding/frame rate out), compare two subtitles, statistics, find/replace across files
- [ ] SE7 (SE) Auto-translate (online engines behind a key, plus offline option later) with Arabic as target and source; keeps tags; side by side with the original; machine text marked for review
- [ ] SE8 (SE) Waveform + spectrogram + shot changes (shares AEG-B), audio-to-text (offline speech model) as an optional download, text-to-speech check
- [ ] SE9 (SE) OCR for image subtitles (SUP, VobSub) and more formats (SMI, JSON, Netflix TTML, EBU STL, PAC...) as the format registry grows
- [ ] AEG-D (AEG) Visual typesetting: drag pos/move/origin, rotate/scale/shear handles, clips, drawings (22.a-22.c), karaoke timing (22.d, 21.g)
- [ ] AEG-E (AEG) Lua automation + templater (10.3, 26-28) after the study step
- [ ] AR8 (AR) Arabic karaoke and effects: per-letter timing that respects joined letters (a connected word is one shaped run), kashida stretch effects
- [ ] AR9 (AR) Arabic help, about, tips and error text; plural audit for every count; screen-reader labels in Arabic
- [ ] AR10 (AR) Arabic QA gate in CI: key parity values vs values-ar, plural categories complete, no hard-coded Latin literal in UI code (script in tools/)

### Bug and polish list (always open, fixed as found)
- [x] B4 'الا' drawn as 'الـا': Material text styles carry letter spacing, and Android turns ligatures off when spacing is not 0, so lam-alef never formed. Arabic typography now forces letterSpacing 0 (ArabicType.kt). s18j: the explicit liga/calt/init/medi/fina feature list was REMOVED, it broke the joining of the UthmanTN1 font (stray letters, split words); default shaping is correct, never set fontFeatureSettings for Arabic. Verify on device; if any screen still shows it, search for Text(letterSpacing=...) there
- [ ] B1 Enum labels (PlayMode, LayerBlend) still English -> A19
- [ ] B2 fmtTime and sizes use Western digits already; verify every other number path (export names, dialogs) after A19
- [ ] B3 Arabic-Indic digits typed by the keyboard are converted to 1234567 in numeric fields

## A19-A30 detail (string and Arabic work that Drop 2 left open)
- [~] A19 (s18f: TextTab, ElementsTab DONE; LEFT: MediaBrowserScreen (filters use string keys as logic values: split label from value first), enum labels) (s18d: GifEditorHost, GifCropScreen, GifTrimPanel DONE; LEFT: MediaBrowserScreen, TextTab, ElementsTab, enum labels) Remaining literals -> resources + Arabic: MediaBrowserScreen (about 32), TextTab (18), GifTrimPanel (15), ElementsTab (15), GifCropScreen, GifEditorHost, enum labels; list every file touched
- [ ] A23 Normalisation tools (Arabic): alef/yeh/kaf/heh forms, strip or keep tashkeel, tatweel, digits to 1234567
- [ ] A25 Encodings in the UI: detect + pick another (17.d alternatives list), mojibake repair button
- [ ] A29 Arabic sample text in font preview when the font has Arabic and no Latin (done for the font directory in A3; extend to the editor font picker)

## Leg 1: editor polish
- [x] 9  Text-over-seeker fix, compact seeker below video, 3x3 snap pad (pad also sets L/C/R alignment)
- [x] 10 Font directory: default preview 12345abcd, optional custom preview bar, lazy loading, bad fonts skipped
- [~] 11 Font browser/installer [x]; open .ttf/.otf straight into the app (Ibis Paint-style) [x intent-filter + import]
- [x] 16 Rename app to MediaForge - MOVED UP, done right after 11 (package com.mediaforge.app, JNI, lib mediaforge_native, theme, strings, Pictures/MediaForge, CI artifact)
- [x] 12 Meme caption bars and other elements (12.a + 12.b done)
    - [x] 12.a Caption bars: top/bottom strip, text auto-fit, bg/text colour, font, align, height; live preview == export (new Bars tab)
    - [x] 12.b Shapes tab: rectangle/rounded/ellipse/line/arrow with fill+opacity, outline, rotation; drag/pinch/twist on preview; duplicate/delete; emoji as text layers. (Image/SVG layers come with 10.E; per-layer opacity + blend modes = step 13)
- [x] 13 Opacity and blend modes: per-layer opacity + 16 blend modes (7 work on every Android, 9 need Android 10+) for text, emoji and shapes; layers composited onto the video frame; 'See blended frame' exact preview (live preview approximates blends). Bars excluded (they sit outside the video). Encoder refactored into shared FrameComposer.
- [x] 14 Settings screen + export settings: app Settings (theme system/light/dark, dynamic colour, new-project defaults for fps/quality/clip length/max width/loop/fast, auto-save to gallery, file-name prefix, keep-awake, clear temp files); editor export options: max width (never upscales), playback Normal/Reverse/Ping-pong, output size + frame count readout; Save button in result dialog when not auto-saved
- [x] 14.5 GIF trimming (ADDED by owner) - DONE: GIF editor with frame range slider, +/-1 frame nudges, stills of first/last kept frame, kept frames/time readout, trim + crop together in one export (native gif decode/compositing shared by preview and export). STILL TODO for GIFs: speed/reverse/ping-pong, text/shapes/bars on existing GIFs (fold into later steps, e.g. after 15). Original note: "Crop a GIF" becomes a full GIF editor - trim start/end with playhead + frame readout, together with crop; later speed/reverse/ping-pong/text on existing GIFs (v1 could not trim GIFs)
- [x] 15 Text timing layers: per-layer 'show only between A and B' (text, emoji, shapes) set from the playhead, range slider, go-to A/B; live preview follows the playhead; export honours it (also with reverse/ping-pong). Layers with identical windows share one bitmap.
- [x] 14.f Fonts directory in Settings (ADDED by owner): font count/size, folder path, open font directory, import files/folder, editable font preview text (default 12345abcd), delete all imported fonts

## Step 10 extras (owner's order was before 10.1; 10.1 study was done first on request - extras still pending) - formats, images, performance
- [ ] 10.A Format audit: what device decodes natively, where it fails (webm, mkv, hevc, av1, avi, flv, 3gp, ts, mov...)
- [ ] 10.B (moved up: A17.a) FFmpeg build in GitHub Actions + Rust/JNI bridge (check ffmpeg-kit status; own static build; WASM only as fallback)
- [ ] 10.C (moved up: A17.b) Decode pipeline: MediaCodec/ExoPlayer first, FFmpeg fallback, any video/audio/image format
- [ ] 10.D Image -> GIF (single image, image sequences, animated webp/apng/avif input)
- [ ] 10.E Import SVG/PNG/WebP/etc as layers on videos and GIFs
- [ ] 10.F Encoder performance: sequential decode, scaled frames, buffer reuse, native RGBA, streaming
- [ ] 10.G Memory/battery/low-end tuning, release build + R8, baseline profile
- [ ] 10.H Whole-app format coverage (browser, editor, share, export, intent filters)

## Studies and Arabic
- [x] 10.1 Aegisub study part 1 -> AEGISUB_NOTES.md (architecture, ASS model, full override-tag list, feature map, Android decisions)   https://github.com/TypesettingTools/Aegisub
- [x] 10.2 (DONE: audio display/timing, Shift Times, Timing Post-Processor, release-note lessons -> AEGISUB_NOTES.md part 2; the smaller tools are read when their step starts) Aegisub study part 2: video/audio/timing docs + src/audio, src/video (keyframes, waveform), Shift Times, Timing Post-Processor, Kanji Timer, Resolution Resampler, Translation/Styling assistants, Spell Checker, Paste Over, Select Lines
- [ ] 10.3 Aegisub study part 3: Automation (Lua API, modules karaskel/util/unicode/cleantags/clipboard/re), Karaoke Templater rules, included macros, styles/fonts collector/attachments/properties/commands/options/autosave -> decide Lua runtime
- [ ] 10.3b Aegisub study part 4 (only if needed): libaegisub/ass + subtitle_format_*.cpp edge cases -> test corpus
- [ ] 10.4 (SCHEDULED as SE1, Drop 4) Study Subtitle Edit (formats, Fix Common Errors, sync, waveform/spectrogram, batch, spell check, auto-translate, OCR) and merge with Aegisub notes: what each does best   https://github.com/SubtitleEdit/subtitleedit
- [~] 10.5 (mostly moved to Priority Leg A; A1-A6 done) Arabic support: shaping + RTL/bidi on GIF text (override, start/end align, diacritics, tatweel), Arabic sample (نص تجريبي) in font preview, subtitle encodings (UTF-8/16, Win-1256, ISO-8859-6) + punctuation fix, renderer with shaping+bidi from day one, RTL-mirrored UI + optional Arabic translation. Preview and export must match.

## Unified editor (ADDED by owner: GIF and video must have the same abilities)
- [x] 14.6 One editor for video AND GIF: a GIF is converted once to a hidden all-intra H.264 proxy video (GifProxy, cached as mfproxy_*.mp4; transparency -> white; tiny GIFs enlarged to >=128px short side), then EditorScreen edits it, so trim/crop/text/shapes/bars/blend/timing/speed/reverse/ping-pong all work on GIFs. Home has one "Edit video or GIF" entry; media browser routes GIFs the same way. Old lossless "quick GIF editor" stays as automatic fallback if the phone's encoder refuses. Native: sequential GIF reader (nativeGifOpen/Next/Close). Later (10.B/10.C): same proxy idea for WebM/MKV etc via FFmpeg.

## Leg 2: Videos section (Aegisub-style) - split into small parts so no tokens are wasted
### 17 Subtitle data model + formats
- [x] 17.a Model (SubFile, AssStyle, AssEvent, attachments, extradata), ASS/SSA parse+write, SRT parse+write, format registry, minimal byte decoding (package subs/; written without a compiler - verify in CI)
- [ ] 17.a2 JVM unit tests for subs/ (ASS and SRT round trips, SSA legacy alignment, commas in text, attachments) + CI step `gradle testDebugUnitTest`
- [x] 17.b WebVTT (cues, <i><b><u>, <v> speaker, align/line <-> \an), SBV, LRC (multi-stamp, offset, metadata, enhanced word stamps <-> \k), plain TXT (# comments, untimed)
- [x] 17.c MicroDVD SUB (fps kept in script info, {y:ibus} {c:$bbggrr}, | breaks) and TTML/DFXP (clock/offset/frame times, spans, <br/>, align). SMI and others later if wanted
- [x] 17.d Encodings.kt: BOM, UTF-16 without BOM, strict UTF-8, scored legacy charsets (Win-1256, ISO-8859-6, Cyrillic, Greek, Hebrew, Thai, Turkish, Central European, Western, SJIS/GBK/Big5/EUC-KR), alternatives list for a 'try another encoding' picker, forced decodeAs, encode with BOM choice; RtlFix.kt moves leading punctuation to the end of RTL lines. Heuristic: if it proves weak, add uchardet via NDK (like Aegisub)
- [x] 17.e (done as A10) Videos section UI: home entry, open/save via SAF, recent files, new empty file, format picker
### 18 Subtitle editor without video
- [~] 18.a (A11 done: Aegisub-style grid + selection) Grid
- [~] 18.b (A11: docked edit area with collapsible Line settings; inline tag buttons and video docking = A13) Edit box (text, times, style, layer, margins, actor, effect, comment) + inline tag buttons (i b u s, colour, alignment)
- [ ] 18.c Undo/redo (snapshot history) + dirty state
- [ ] 18.d Search and replace (plain/regex, case, fields: text/style/actor/effect)
- [ ] 18.e Line operations: insert before/after, duplicate, delete, join, split at cursor, swap, sort, select lines, paste over
- [ ] 18.f Autosave/backup + crash recovery; save / save as / export format choice
### 19 Styles, properties, fonts
- [ ] 19.a Styles manager + style editor (all fields, live preview)
- [ ] 19.b Script properties (PlayRes, WrapStyle, ScaledBorderAndShadow, YCbCr matrix) + style catalog/storage
- [ ] 19.c Embedded fonts/graphics: uuencode/decode, attach/extract, attachment manager
- [ ] 19.d Fonts collector (which fonts a script uses, copy/zip, missing-font warnings)
### 20 Video preview and timing
- [ ] 20.a Video preview linked to the grid (active line <-> playhead), play line, loop line
- [ ] 20.b Frame stepping, frame/time display, jump to line start/end
- [ ] 20.c Keyframe list (MediaExtractor sync samples) + snapping
- [ ] 20.d Audio decode -> waveform peaks cache (MediaCodec PCM)
- [ ] 20.e Waveform timing UI: drag start/end, snap to keyframes/neighbours, set-from-playhead
- [ ] 20.f Spectrogram (FFT) - optional
### 21 ASS rendering engine
- [ ] 21.a Override-tag parser (AST, unknown tags kept) + tag builder + DECISION GATE: libass via NDK in CI vs Kotlin renderer
- [ ] 21.b Core render: style, colours/alpha, outline/shadow, alignment, margins, wrap modes, layers; shaping + bidi from day one
- [ ] 21.c Transforms: fscx/fscy/fsp, frz/frx/fry, fax/fay, org
- [ ] 21.d Animation: \t with accel, \move, \fad, \fade
- [ ] 21.e Clips and drawings: \clip/\iclip rect + vector, \p drawings (m n l b s p c)
- [ ] 21.f Blur/be/border/shadow quality + performance cache
- [ ] 21.g Karaoke rendering (\k \K \kf \ko \kt)
### 22 Visual typesetting and karaoke tools
- [ ] 22.a Drag position/move on preview, origin; alignment pad
- [ ] 22.b Rotate/scale/shear handles; colour picker
- [ ] 22.c Clip tools (rect + vector) and drawing editor
- [ ] 22.d Karaoke timing: split syllables, set \k from waveform/tap, kanji timer
### 23 Timing and checking tools
- [ ] 23.a Time shifter (all/selected, by time/frames, forward/back)
- [ ] 23.b Timing post-processor (lead-in/out, snap to keyframes, join adjacent)
- [ ] 23.c Reading speed (CPS), line length/lines count checker, overlap/gap QC list
- [ ] 23.d Resolution resampler (PlayRes change + margins/tags scaling)
- [ ] 23.e Translation assistant (side by side) and styling assistant
- [ ] 23.f Spell checker (Android SpellChecker API or hunspell) 
### 24 Elements and tracking
- [ ] 24.a Elements: images/shapes as ASS drawings, import SVG paths
- [ ] 24.b Motion tracking (template matching/LK) -> \pos/\fscx/\frz keyframes
### 25 Export
- [ ] 25.a Soft-coded: mux subtitle track (needs FFmpeg/NDK from 10.B) 
- [ ] 25.b Hard-coded: burn styled subs into video (render per frame + MediaCodec or FFmpeg)
- [ ] 25.c Burn subtitles into GIFs (reuses FrameComposer layer pipeline)
### 26-29 Automation and extras
- [ ] 26.a Lua runtime on Android (built in CI) + sandbox
- [ ] 26.b Automation API: aegisub.* and subtitles object (read/modify/append/delete), progress, dialogs from Lua tables
- [ ] 26.c Macro manager/menu, included macros, error reporting
- [ ] 26.d Modules: karaskel, util, unicode, cleantags, clipboard, re
- [ ] 27 Full Lua scripting compatibility pass (run real Aegisub scripts)
- [ ] 28.a Karaoke templater: declare/parse template lines, execution order
- [ ] 28.b Modifiers and $-variables, math expressions, code lines/blocks and environment
- [ ] 28.c Effects library + presets (ASS karaoke effects)
- [ ] 29.a Subtitle Edit tools: batch convert, Fix Common Errors (contents decided after 10.4)
- [ ] 29.b OCR for image subtitles, auto-translate, extra sync tools

## Assumptions
- Item 7 = app Settings screen + export settings.
- 3x3 pad covers above/below inside the video; extra bars come with step 12.
- Renamed app installs as a NEW app (new applicationId); old GifForge install and its fonts are not migrated. Zip filename keeps the GifForge prefix so gifforge-push.sh still finds it; repo name in that script is still 'gifforge' (change R= when you want).
- Push: gifforge-push.sh (holds the owner's token; do not modify or leak it).

- The Arabic font file UthmanTN1 (KFGQPC Uthman Taha Naskh) was supplied by the owner and is bundled; check its licence before publishing the app. It has Arabic letters, marks and digits but no Latin letters, so English text beside it is drawn by the system font.
- Not compiled here (no Gradle in this environment): build with the CI workflow and report any error.

## ADDED s18g by owner: four more tracks (every drop carries one item of each where possible; token-cheap steps)
### OPT - optimization
- [ ] OPT1 Startup and memory: lazy font list, thumbnails with size-capped cache, no full-file reads (video/sub), Compose stability (immutable events list, keys in LazyColumn), baseline profile + R8 (joins 10.G)
- [ ] OPT2 Subtitle grid for 10 000+ lines: stable keys, no per-row allocations, text measured once; search/replace and Fix Common Errors run off the main thread
- [ ] OPT3 Export speed: read the video through FFmpeg's SAF protocol instead of copying it to cache; MediaCodec hardware encoder path; progress + ETA from statistics; cancel is instant; temp files cleaned on start
- [ ] OPT4 Player: one shared ExoPlayer for editor and subtitling, frame-accurate seek cache, proxy for heavy 4K/HEVC
- [ ] OPT5 APK size: split per ABI, strip unused icons/libs, font subsetting for the bundled Arabic font
### FEAT - more features
- [ ] FEAT1 Video tools in the Videos hub (not GIF-only): trim/cut, join, speed, rotate/flip, crop, mute/replace audio, extract audio, compress, screenshots, GIF from range - all through the export engine with the same chooser
- [ ] FEAT2 Batch: apply one subtitle/style to many videos, batch export queue with notifications
- [ ] FEAT3 Subtitle projects: save workspace (video + subtitle + settings), reopen from Videos hub; share/open .ass from other apps (intent filters)
- [ ] FEAT4 Style presets for Arabic (font, size, outline, shadow, bottom margin) and one-tap 'movie / anime / karaoke' looks
- [ ] FEAT5 Waveform from the audio track with tap timing (AEG-B), scene-change snapping
### AR-TR - Arabic translation quality (owner's first message: proper localisation, Sakkaki-style concision, no filler, no colloquial)
- [~] AR-TR1 (s18i: pass 1 over all 285 strings DONE: GIF term unified, trim/crop/rotation/outline/bold/tab terms fixed, agreement fixed, '…' unified, soft/hard wording, glossary file tools/AR_GLOSSARY.md; LEFT: second pass after owner feedback on device, new strings must follow the glossary) Full copy audit of values-ar against a glossary (one Arabic term per concept: ترجمة/تصدير/طبقة/إطار/مسار...), consistent verb forms (imperative for buttons, nouns for titles), no mixed registers
- [~] AR-TR2 (s18i: done together with pass 1) Shorten every string to the least words that stay exact; remove filler words and repeated 'يمكنك'; punctuation and quotes Arabic (، ؛ ؟ « »); digits always 1234567
- [ ] AR-TR3 Plural audit (zero/one/two/few/many/other) for every quantity string; gender and agreement checks
- [ ] AR-TR4 Translate-while-timing: translation assistant shows original and Arabic side by side (A13.b/A15), per-line RTL/LTR tools (AR3), Arabic QA rules (AR6)
- [ ] AR-TR5 Check with the supplied font on device: every screen, no clipped marks, no stray kashida (B4), no flipped text
### AEG-S - study Aegisub more (reading steps, notes go to AEGISUB_NOTES.md; each step ends with a small code change)
- [ ] AEG-S1 Video/audio providers and keyframes (src/video_provider*, audio_provider*, keyframe loading): what to copy for waveform + keyframe snapping
- [ ] AEG-S2 Timing tools in source: Shift Times, Timing Post-Processor, Resolution Resampler, Kanji Timer: exact rules and defaults
- [ ] AEG-S3 ASS renderer expectations (libass vs Aegisub's own use of libass; override tag parsing in libaegisub/ass): test corpus for A21
- [ ] AEG-S4 Automation 4 Lua API + karaoke templater docs (feeds AEG-E)
- [ ] AEG-S5 UI patterns: grid columns, edit box, audio display, hotkeys -> touch equivalents, what to drop on a phone


## s18j (owner's Step 2: subtitler fonts, placement, effects that really show)
- [x] Why effects looked like 'just a name': the video overlay stripped every {..} block and drew plain text. Now ui/SubOverlay.kt + media/AssPreview.kt draw style + tags + MF1 keyframes at the playhead (software bitmap so BlurMaskFilter works on every Android version).
- [x] subs/AssLook.kt: reads/writes fn, fs, b, i, an, pos, c, 3c in the line's first non-effect block (the effect block stays first). Text edits and the video export (libass) therefore agree.
- [x] ui/LookPanel.kt: font picker (library fonts by family, each in its own face, import a font from the dialog), size, bold/italic, alignment chips + 3x3 numpad pad + X/Y sliders (same controls as the GIF text tab), text/outline colour, auto position, reset. Applies to all marked lines, else the active line.
- [x] B4 guard: Latin typography now also has letterSpacing 0 (Arabic text appears in English UI too; the font has lam-alef only under liga); tools/check_arabic.sh runs first in CI and fails on fontFeatureSettings or non-zero letterSpacing (mark a justified exception with 'ar-ok').
- NOT bundled-font safe: the UthmanTN1 licence forbids modification, so the font file must never be patched (an rlig copy of liga would have fixed B4 at the root).
- Known limits: spacing effect is not previewed on Arabic text (spacing switches ligatures off); mid-line tag changes and \t written by hand are not previewed (MediaForge keyframes are); exact libass parity stays A21; free-position drag on the video is still open.
- NOT compiled in the authoring sandbox (no Kotlin/Gradle there): first CI run is the compile check.
