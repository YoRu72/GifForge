# MediaForge
Android media toolkit: video and GIF editor (Gifski), text and fonts, and an Aegisub-style subtitle workshop with Arabic first.
See ROADMAP.md (source of truth), AEGISUB_NOTES.md, SUBTITLEEDIT_NOTES.md, MOTION_NOTES.md, ARABIC_GRAMMAR_NOTES.md.

Build: push to GitHub; Actions > Build APK runs the Arabic guard, the string gate, the unit tests and the build, then publishes the APK to the release `beta` (direct download) and as the artifact `MediaForge-debug-apk`.

Arabic: Settings > Language. Arabic strings live in app/src/main/res/values-ar and follow tools/AR_GLOSSARY.md (concise classical Arabic). Numbers are always Western digits.

Checks (all in CI before the build): `tools/check_arabic.sh` (no letter spacing / font features that break lam-alef), `tools/check_strings.py` (string keys, Arabic parity, format arguments, plurals, digits), `gradle testDebugUnitTest` (formats, encodings, editing, Fix common errors, styles, Aegisub tools, karaoke, motion tracking).
