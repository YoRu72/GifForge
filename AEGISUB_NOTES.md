# Aegisub study notes (step 10.1 = part 1; parts 2 and 3 come next, see ROADMAP.md)
Sources: github.com/TypesettingTools/Aegisub (README, tree), aegisub.org/docs/latest (ASS Override Tags page + docs navigation). Latest release 3.4.2 (Jan 2025).

## 1. What Aegisub is made of (from the repo)
- C++ (74%), C (15%), Lua (3%), MoonScript (1%); built with meson; GUI is wxWidgets (we replace with Compose).
- `libaegisub/` core library (ASS parsing, formats, charset detection, path specifiers, options) -> our `subs/` package.
- `src/` application: commands, grid, edit box, video, audio, dialogs. `automation/` Lua/MoonScript macros + include modules. `docs/`, `po/` (translations), `tests/`.
- Third-party: libass (ASS renderer), ffms2 (video/audio decode + keyframes), FFTW3 (spectrum), hunspell (spellcheck), uchardet (encoding detection), ICU, Boost, LuaJIT built with Lua-5.2 compatibility (Automation), fontconfig, OpenAL/PulseAudio/ALSA (audio playback).
- Takeaway: Aegisub itself renders with libass, so exact Aegisub-compatible output on Android means libass (NDK, built in CI). Fallback = our own Kotlin renderer.

## 2. Feature map (docs navigation) -> our steps
- Editing: subtitle grid, edit box, styles, Paste Over, Select Lines, Spell Checker, Translation Assistant, Styling Assistant -> 18, 19, 23
- Video/typesetting: working with video, visual typesetting (pos/move/rotate/scale/clip tools), colour picker, resolution resampler, fonts collector -> 20, 22, 23, 19
- Audio/timing: waveform + spectrum, timing to audio, Shift Times, Timing Post-Processor, Kanji Timer, karaoke timing -> 20, 22, 23
- Automation: Lua reference (registration, subtitles object, progress, dialogs, misc APIs), modules (karaskel, utils, unicode, cleantags, clipboard, re), Karaoke Templater (declare lines, execution order, modifiers, $-variables, code lines/blocks, environment), standard macros, automation manager -> 26, 27, 28
- Misc: options, autosave, path specifiers, script properties, resolution + YCbCr matrix, attachment manager, commands/hotkeys -> 18.f, 19, 14

## 3. ASS file model (what `subs/` implements)
Sections: `[Script Info]` (Key: Value + `;` comments), `[V4+ Styles]` (Format + Style lines; old SSA is `[V4 Styles]` with legacy alignment 1-3/5-7/9-11 and TertiaryColour), `[Events]` (Format + Dialogue/Comment lines; Text is the last field and may contain commas), `[Fonts]`/`[Graphics]` (uuencoded attachments), Aegisub extras `[Aegisub Project Garbage]` (Audio File, Video File, Video AR Mode, Video Zoom Percent, Scroll Position, Active Line, Video Position) and `[Aegisub Extradata]`.
- Style fields: Name, Fontname, Fontsize, Primary/Secondary/Outline/BackColour, Bold(-1/0), Italic, Underline, StrikeOut, ScaleX/Y, Spacing, Angle, BorderStyle(1 outline+shadow, 3 opaque box), Outline, Shadow, Alignment(numpad), MarginL/R/V, Encoding.
- Event fields: Layer, Start, End (H:MM:SS.cc, centiseconds), Style, Name(actor), MarginL/R/V, Effect, Text.
- Colours `&HAABBGGRR` (BGR order, alpha 00 = opaque, FF = transparent); override form `&HBBGGRR&`. Missing PlayRes -> libass uses 384x288.
- Script properties: PlayResX/Y, WrapStyle 0-3, ScaledBorderAndShadow, YCbCr Matrix, Collisions, LayoutRes.

## 4. Override tags (complete list from the docs page)
Special chars (outside `{}`): `\N` hard break, `\n` soft break (only wrap mode 2), `\h` hard space.
Line-level (once per line, position irrelevant; `\pos`/`\move`, `\clip`/`\iclip`, `\fad`/`\fade` are mutually exclusive): `\pos(x,y)`, `\move(x1,y1,x2,y2[,t1,t2])`, `\org(x,y)` (only first used, cannot animate), `\fad(in,out)`, `\fade(a1,a2,a3,t1,t2,t3,t4)` (alpha 0-255 decimal), `\clip`/`\iclip` rect `(x1,y1,x2,y2)` or vector `([scale,]drawing)`.
Inline (apply to following text until overridden; omitted parameter = style default): `\i \b(weight 100-900) \u \s`, `\bord \xbord \ybord \shad \xshad \yshad` (x/yshad may be negative), `\be \blur`, `\fn \fs \fscx \fscy \fsp`, `\frx \fry \frz(\fr) \fax \fay`, `\fe`, colours `\c \1c \2c \3c \4c`, alpha `\alpha \1a \2a \3a \4a`, `\an` (numpad 1-9) and legacy `\a` (1-3 bottom, 5-7 top, 9-11 middle), karaoke `\k \K \kf \ko \kt` (centiseconds), `\q` wrap 0-3, `\r[style]`, drawing `\p<n>` (scale 2^(n-1)) and `\pbo`.
Animation `\t([t1,t2,][accel,]tags)`: times in ms from line start, accel exponent (1 = linear). Animatable: `\fs \fsp \c \1c-\4c \alpha \1a-\4a \fscx \fscy \frx \fry \frz \fr \fax \fay \bord \xbord \ybord \shad \xshad \yshad \clip \iclip(rect only) \be \blur`.
Drawing commands: `m n l b s p c` (move, move-no-close, line, cubic bezier, b-spline, extend, close). Aegisub's vector-clip tool only handles m/l/b.
Other facts: `\bord` after `\xbord/\ybord` overrides both; `\blur` preferred over `\be`; border/shadow scale only if ScaledBorderAndShadow; rotation is about `\org` (defaults to the position anchor); shear is applied after rotation; rendering is 72 DPI so 1pt = 1 script pixel.

## 5. Android port decisions so far
1. Data model is immutable data classes (cheap undo/redo by snapshots) in package `com.mediaforge.app.subs`; ms internally, centiseconds only when writing ASS.
2. Format registry (`SubFormat`): parse(text)->SubFile, write(SubFile)->text; ASS is the internal model, other formats map into/out of it (SRT tags <-> \i \b \u \s \c).
3. Tag handling: parse override blocks into an AST (step 21.a), keep unknown tags verbatim so round-trips never lose data.
4. Renderer: decision gate at 21.a - build libass (+FreeType/HarfBuzz/FriBidi) in CI for exact Aegisub output incl. Arabic shaping, else Kotlin Canvas renderer. Either feeds the same FrameComposer layer pipeline used for GIF text.
5. Video/audio: keyframes via MediaExtractor sync samples, waveform via MediaCodec PCM peaks; FFmpeg fallback from 10.B for odd formats; GIF/odd sources use the proxy-video idea (GifProxy).
6. Lua automation: Aegisub needs LuaJIT with 5.2 compat; on Android plan a Lua runtime built in CI (decide in 10.3 study).

## 6. Still to study (next parts)
- 10.2: video/audio/timing docs + `src/audio*`, `src/video*`, keyframe and waveform code, Shift Times, Timing Post-Processor, Kanji Timer, Resolution Resampler, Translation/Styling assistants, Spell Checker, Paste Over, Select Lines.
- 10.3: Lua API pages, karaskel/util/unicode/cleantags/re modules, Karaoke Templater rules, included macros, styles/fonts collector/attachments/properties/commands/options/autosave.
- 10.3b (if needed): read `libaegisub/ass` + `src/subtitle_format_*.cpp` for edge cases and build a test corpus.

---
# Part 2 (step 10.2): audio, timing, tools  (sources: Aegisub docs "Timing subtitles to audio", "Working with Audio", "Shift times", "Timing Post-Processor", 3.4 release notes)

## Audio display and timing (-> steps 20.d, 20.e, 20.f, 22.d)
- Audio is decoded and cached (RAM or disk cache; WAV is read directly). Since 3.4 it loads in the background so you can start working at the beginning of the file while the rest decodes. -> our peaks cache must be progressive: build waveform peaks chunk by chunk and show partial results.
- Waveform (amplitude) and spectrum analyzer (frequency, tuned to human voice; vertical zoom becomes colour intensity). Spectrum makes line starts/ends easier to see with background noise/music. Quality is an option. -> spectrogram is optional (20.f), FFT on decoded PCM.
- Markers in the display: pink = video keyframes, white broken line = current video frame, thick red/orange = start/end of the active line. Click a grid line -> it is highlighted and the display auto-scrolls to centre it (toggle; usually off while timing).
- Dragging line boundaries snaps to other lines and keyframes while Shift is held (or the reverse, if "snap by default" is set). -> on touch: a Snap toggle button instead of Shift.
- Toolbar: play selected area, auto-scroll, spectrum, karaoke mode, Medusa-style timing shortcuts. Karaoke mode = split a line into syllables on the waveform and write \k tags (-> 22.d).
- Timing protips mentioned: scene-timing (use keyframes as scene cuts).

## Shift Times (-> 23.a)
- Shift by time or by frames (needs video; shifting by 0 frames snaps stamps to frame times), forward/backward.
- Affects: all rows / selected rows / selection onward (first selected line and everything below).
- Times: start and end / start only / end only. Start-only changes duration (can reach zero).
- Negative results are clamped to 0. There is a shift history (amount+direction, s/e/s+e, rows) that can be reloaded and cleared.

## Timing Post-Processor (-> 23.b)
- Three steps applied in this order: (1) lead-in and lead-out added, (2) adjacent lines closer than a threshold are made continuous (extend/contract starts/ends), (3) snap starts/ends to the nearest video keyframe if closer than a threshold (an automatic scene-timer; needs keyframes).
- Can be limited to chosen styles (e.g. only the dialogue style). Setting keyframe thresholds to a frame or two (and disabling the rest) quickly fixes one-frame bleeds.

## Other release-note facts worth copying
- Autosave existed for years but was hard to find and could be overwritten by reopening the original -> our autosave (18.f) must be visible and must offer "restore" on open.
- Edit-box style buttons (bold, italic, colours) should act sensibly on multiple selected lines; visual typesetting tools should edit all selected lines. -> design 18.b and 22.a for multi-selection from the start.
- Matroska files can carry [Aegisub Extradata] (keep it when reading/writing; we already preserve extradata).

## Design decisions for our port
1. Keyframes: MediaExtractor sync-sample timestamps (all-intra proxies make every frame a keyframe, so offer "scene detection" later instead).
2. Waveform: MediaCodec decode to PCM, downmix, min/max peak pyramid (e.g. 256-sample blocks, then x4 levels) cached on disk, progressive.
3. Touch timing UI: two big draggable handles on the waveform + nudge buttons (+-1 frame, +-10 ms), Snap toggle, tap-to-set from playhead (A/B like text timing in step 15).
4. Shift Times / Post-Processor operate on a list of AssEvent and return a new list (pure functions -> easy undo and unit tests).
5. Still to study in part 3: Kanji Timer, Resolution Resampler, Translation/Styling assistants, Spell Checker, Paste Over, Select Lines are summarised in the roadmap (23.d-23.f, 18.e); their exact option lists will be read when those steps start.
