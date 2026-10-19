# Motion tracking notes (MOT0, s18w)

## What Aegisub users do today [VERIFY details against the Aegisub-Motion source before MOT5]
- Track in Mocha (or Blender / After Effects), export "After Effects Transform Data" (keyframes per frame: Position, Scale, Rotation, Anchor Point, at the source frame rate) and paste it into the Aegisub-Motion macro.
- Aegisub-Motion splits the line into one line per video frame, writes \pos (and \fscx \fscy \frz \org \clip when chosen) relative to the first frame, and shifts \t, \move and \fad times so animations continue across the pieces. Its later "linear" mode writes fewer lines with \move and \t.

## What MediaForge does (s18w)
- Frames: MediaMetadataRetriever, scaled to 480 px wide, converted to luma. Android 9+ decodes in order with getFramesAtIndex (no seek per frame); older phones seek per frame with OPTION_CLOSEST (slower, same result).
- Tracker (subs/Motion.kt PointTracker): template of (2h+1)^2 pixels around the tapped point; in each new frame an exhaustive normalised cross-correlation search in +-24 px; sub-pixel peak from a parabola; template refreshed 15 % per frame while the match is good; below 0.55 the object counts as lost and tracking stops there. NCC is used instead of Lucas-Kanade: it copes with fast moves inside the window, needs no gradients and is easy to test; LK can come later for rotation/scale (MOT3).
- Output (MotionApply): relative to the first tracked point, so the text keeps its distance from the object.
  (a) one line per frame with \pos (frame-exact);
  (b) fewer lines: path simplified by Ramer-Douglas-Peucker with an error limit in pixels, each piece one \move.
  Effect keyframes (MF1 block) are sliced per piece (Effects.slice), so fades and blur keep running across the pieces, like Aegisub-Motion's time shifting.
- Manual path (MOT1): keypoints tapped at chosen moments; straight moves between them.

## Next
MOT3 two-point track (scale + rotation, \org at the tracked point), MOT5 import of AE Transform Data / CSV, MOT6 the same for GIF/video layers, backward tracking and "correct this frame and track again".
