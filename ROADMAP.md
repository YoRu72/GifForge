# MediaForge (formerly GifForge) roadmap (source of truth - never drop a step)
Legend: [x] done, [~] partial, [ ] todo. Work in small parts so no tokens are wasted. Every reply ships the current zip.

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
- [ ] 10.B FFmpeg build in GitHub Actions + Rust/JNI bridge (check ffmpeg-kit status; own static build; WASM only as fallback)
- [ ] 10.C Decode pipeline: MediaCodec/ExoPlayer first, FFmpeg fallback, any video/audio/image format
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
- [ ] 10.4 Study Subtitle Edit (formats, Fix Common Errors, sync, waveform/spectrogram, batch, spell check, auto-translate, OCR) and merge with Aegisub notes: what each does best   https://github.com/SubtitleEdit/subtitleedit
- [ ] 10.5 Arabic support: shaping + RTL/bidi on GIF text (override, start/end align, diacritics, tatweel), Arabic sample (نص تجريبي) in font preview, subtitle encodings (UTF-8/16, Win-1256, ISO-8859-6) + punctuation fix, renderer with shaping+bidi from day one, RTL-mirrored UI + optional Arabic translation. Preview and export must match.

## Unified editor (ADDED by owner: GIF and video must have the same abilities)
- [x] 14.6 One editor for video AND GIF: a GIF is converted once to a hidden all-intra H.264 proxy video (GifProxy, cached as mfproxy_*.mp4; transparency -> white; tiny GIFs enlarged to >=128px short side), then EditorScreen edits it, so trim/crop/text/shapes/bars/blend/timing/speed/reverse/ping-pong all work on GIFs. Home has one "Edit video or GIF" entry; media browser routes GIFs the same way. Old lossless "quick GIF editor" stays as automatic fallback if the phone's encoder refuses. Native: sequential GIF reader (nativeGifOpen/Next/Close). Later (10.B/10.C): same proxy idea for WebM/MKV etc via FFmpeg.

## Leg 2: Videos section (Aegisub-style) - split into small parts so no tokens are wasted
### 17 Subtitle data model + formats
- [x] 17.a Model (SubFile, AssStyle, AssEvent, attachments, extradata), ASS/SSA parse+write, SRT parse+write, format registry, minimal byte decoding (package subs/; written without a compiler - verify in CI)
- [ ] 17.a2 JVM unit tests for subs/ (ASS and SRT round trips, SSA legacy alignment, commas in text, attachments) + CI step `gradle testDebugUnitTest`
- [x] 17.b WebVTT (cues, <i><b><u>, <v> speaker, align/line <-> \an), SBV, LRC (multi-stamp, offset, metadata, enhanced word stamps <-> \k), plain TXT (# comments, untimed)
- [x] 17.c MicroDVD SUB (fps kept in script info, {y:ibus} {c:$bbggrr}, | breaks) and TTML/DFXP (clock/offset/frame times, spans, <br/>, align). SMI and others later if wanted
- [x] 17.d Encodings.kt: BOM, UTF-16 without BOM, strict UTF-8, scored legacy charsets (Win-1256, ISO-8859-6, Cyrillic, Greek, Hebrew, Thai, Turkish, Central European, Western, SJIS/GBK/Big5/EUC-KR), alternatives list for a 'try another encoding' picker, forced decodeAs, encode with BOM choice; RtlFix.kt moves leading punctuation to the end of RTL lines. Heuristic: if it proves weak, add uchardet via NDK (like Aegisub)
- [ ] 17.e Videos section UI: home entry, open/save via SAF, recent files, new empty file, format picker
### 18 Subtitle editor without video
- [ ] 18.a Grid (virtualized list: #, start, end, style, text, flags) + selection
- [ ] 18.b Edit box (text, times, style, layer, margins, actor, effect, comment) + inline tag buttons (i b u s, colour, alignment)
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
