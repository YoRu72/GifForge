//! Decode an existing GIF (with proper frame compositing), trim + crop it, re-encode with gifski.
use gifski::{Repeat, Settings};
use imgref::ImgVec;
use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jbyteArray, jint, jintArray, jlong, jstring};
use jni::JNIEnv;
use rgb::RGBA8;
use std::fs::File;
use std::io::{BufReader, BufWriter};
use std::ptr::null_mut;
use std::sync::atomic::{AtomicBool, AtomicI32, Ordering};

static PROGRESS: AtomicI32 = AtomicI32::new(0);
static CANCEL: AtomicBool = AtomicBool::new(false);

fn gif_info(path: &str) -> Option<[i32; 3]> {
    let file = File::open(path).ok()?;
    let mut dec = gif::DecodeOptions::new().read_info(BufReader::new(file)).ok()?;
    let (w, h) = (dec.width() as i32, dec.height() as i32);
    let mut n = 0;
    while let Ok(Some(_)) = dec.next_frame_info() {
        n += 1;
    }
    Some([w, h, n])
}

/// Per-frame delay in centiseconds (0/1 are treated as 10 like browsers do).
fn gif_delays(path: &str) -> Option<Vec<i32>> {
    let file = File::open(path).ok()?;
    let mut dec = gif::DecodeOptions::new().read_info(BufReader::new(file)).ok()?;
    let mut v: Vec<i32> = Vec::new();
    while let Ok(Some(f)) = dec.next_frame_info() {
        let mut d = f.delay as i32;
        if d <= 1 {
            d = 10;
        }
        v.push(d);
    }
    Some(v)
}

/// Keeps the full-size canvas while frames are played in order (handles disposal methods).
struct Compositor {
    cw: usize,
    ch: usize,
    canvas: Vec<u8>,
    saved: Option<Vec<u8>>,
    prev: Option<(gif::DisposalMethod, usize, usize, usize, usize)>,
}

impl Compositor {
    fn new(cw: usize, ch: usize) -> Self {
        Compositor { cw, ch, canvas: vec![0u8; cw * ch * 4], saved: None, prev: None }
    }

    fn apply(&mut self, frame: &gif::Frame<'_>) {
        let (cw, ch) = (self.cw, self.ch);
        let (fl, ft, fw, fh) = (frame.left as usize, frame.top as usize, frame.width as usize, frame.height as usize);

        // apply the previous frame's disposal
        if let Some((d, pl, pt, pw, ph)) = self.prev.take() {
            match d {
                gif::DisposalMethod::Background => {
                    for row in pt..(pt + ph).min(ch) {
                        for col in pl..(pl + pw).min(cw) {
                            let o = (row * cw + col) * 4;
                            self.canvas[o..o + 4].copy_from_slice(&[0, 0, 0, 0]);
                        }
                    }
                }
                gif::DisposalMethod::Previous => {
                    if let Some(s) = self.saved.take() {
                        self.canvas = s;
                    }
                }
                _ => {}
            }
        }
        if matches!(frame.dispose, gif::DisposalMethod::Previous) {
            self.saved = Some(self.canvas.clone());
        }

        // draw this frame onto the canvas (skip fully transparent pixels)
        let buf = &frame.buffer;
        for row in 0..fh {
            let cy = ft + row;
            if cy >= ch {
                break;
            }
            for col in 0..fw {
                let cx = fl + col;
                if cx >= cw {
                    break;
                }
                let s = (row * fw + col) * 4;
                if s + 3 >= buf.len() {
                    break;
                }
                if buf[s + 3] == 0 {
                    continue;
                }
                let d = (cy * cw + cx) * 4;
                self.canvas[d..d + 4].copy_from_slice(&buf[s..s + 4]);
            }
        }
        self.prev = Some((frame.dispose, fl, ft, fw, fh));
    }
}

/// Sequential reader: each call to `next` returns the fully composited canvas of the next frame.
struct GifReader {
    dec: gif::Decoder<BufReader<File>>,
    comp: Compositor,
}

impl GifReader {
    fn open(path: &str) -> Option<GifReader> {
        let file = File::open(path).ok()?;
        let mut opts = gif::DecodeOptions::new();
        opts.set_color_output(gif::ColorOutput::RGBA);
        let dec = opts.read_info(BufReader::new(file)).ok()?;
        let comp = Compositor::new(dec.width() as usize, dec.height() as usize);
        Some(GifReader { dec, comp })
    }

    fn next(&mut self) -> Option<&[u8]> {
        match self.dec.read_next_frame() {
            Ok(Some(f)) => {
                self.comp.apply(f);
                Some(&self.comp.canvas[..])
            }
            _ => None,
        }
    }
}

/// Full-canvas RGBA of frame `target` (clamped to the last frame).
fn frame_at(path: &str, target: usize) -> Option<Vec<u8>> {
    let file = File::open(path).ok()?;
    let mut opts = gif::DecodeOptions::new();
    opts.set_color_output(gif::ColorOutput::RGBA);
    let mut dec = opts.read_info(BufReader::new(file)).ok()?;
    let mut comp = Compositor::new(dec.width() as usize, dec.height() as usize);
    let mut i = 0usize;
    loop {
        match dec.read_next_frame() {
            Ok(Some(frame)) => {
                comp.apply(frame);
                if i == target {
                    return Some(std::mem::take(&mut comp.canvas));
                }
                i += 1;
            }
            Ok(None) => {
                return if i > 0 { Some(std::mem::take(&mut comp.canvas)) } else { None };
            }
            Err(_) => return None,
        }
    }
}

/// Keeps frames `start..end` (end exclusive, 0 = to the last frame) and the crop area x,y,w,h.
fn crop_gif(
    inp: &str, out: &str, x: usize, y: usize, w: usize, h: usize, quality: u8, fast: bool, start: usize, end: usize,
) -> Result<(), String> {
    PROGRESS.store(0, Ordering::SeqCst);
    CANCEL.store(false, Ordering::SeqCst);
    let total_all = gif_info(inp).map(|i| i[2]).unwrap_or(1).max(1) as usize;
    let end = if end == 0 { total_all } else { end.min(total_all) };
    if start >= end {
        return Err("Nothing to export: the trimmed range is empty".into());
    }
    let span = end - start;

    let file = File::open(inp).map_err(|e| e.to_string())?;
    let mut opts = gif::DecodeOptions::new();
    opts.set_color_output(gif::ColorOutput::RGBA);
    let mut dec = opts.read_info(BufReader::new(file)).map_err(|e| e.to_string())?;
    let (cw, ch) = (dec.width() as usize, dec.height() as usize);
    if w == 0 || h == 0 || x + w > cw || y + h > ch {
        return Err("Crop area is outside the GIF".into());
    }

    let settings = Settings { width: None, height: None, quality, fast, repeat: Repeat::Infinite };
    let (collector, writer) = gifski::new(settings).map_err(|e| e.to_string())?;
    let ofile = File::create(out).map_err(|e| e.to_string())?;
    let wt = std::thread::spawn(move || {
        let mut progress = gifski::progress::NoProgress {};
        writer.write(BufWriter::new(ofile), &mut progress).map_err(|e| e.to_string())
    });

    let mut comp = Compositor::new(cw, ch);
    let mut t_ms = 0f64;
    let mut idx = 0usize; // frames written
    let mut fi = 0usize; // frames read
    let mut err: Option<String> = None;

    loop {
        if CANCEL.load(Ordering::SeqCst) {
            err = Some("Cancelled".into());
            break;
        }
        let frame = match dec.read_next_frame() {
            Ok(Some(f)) => f,
            Ok(None) => break,
            Err(e) => {
                err = Some(e.to_string());
                break;
            }
        };
        let mut cs = frame.delay as u32;
        comp.apply(frame); // every frame must be composited, even the ones before `start`

        if fi >= start {
            if cs <= 1 {
                cs = 10; // browsers treat 0/1 as 100 ms
            }
            let mut pix: Vec<RGBA8> = Vec::with_capacity(w * h);
            for row in 0..h {
                let from = ((y + row) * cw + x) * 4;
                for col in 0..w {
                    let p = &comp.canvas[from + col * 4..from + col * 4 + 4];
                    pix.push(RGBA8::new(p[0], p[1], p[2], p[3]));
                }
            }
            if collector.add_frame_rgba(idx, ImgVec::new(pix, w, h), t_ms / 1000.0).is_err() {
                err = Some("Encoder stopped unexpectedly".into());
                break;
            }
            t_ms += cs as f64 * 10.0;
            idx += 1;
            PROGRESS.store(((idx * 100) / span).min(99) as i32, Ordering::SeqCst);
        }
        fi += 1;
        if fi >= end {
            break;
        }
    }

    drop(collector);
    let wres = wt.join().map_err(|_| "Writer thread panicked".to_string())?;
    if let Some(e) = err {
        return Err(e);
    }
    if idx == 0 {
        return Err("No frames found in this GIF".into());
    }
    wres?;
    PROGRESS.store(100, Ordering::SeqCst);
    Ok(())
}

#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeGifInfo(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
) -> jintArray {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return null_mut(),
    };
    match gif_info(&path) {
        Some(info) => match env.new_int_array(3) {
            Ok(arr) => {
                let _ = env.set_int_array_region(&arr, 0, &info);
                arr.into_raw()
            }
            Err(_) => null_mut(),
        },
        None => null_mut(),
    }
}

/// Per-frame delays in centiseconds, or null if the file isn't a readable GIF.
#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeGifDelays(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
) -> jintArray {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return null_mut(),
    };
    match gif_delays(&path) {
        Some(d) => match env.new_int_array(d.len() as i32) {
            Ok(arr) => {
                let _ = env.set_int_array_region(&arr, 0, &d);
                arr.into_raw()
            }
            Err(_) => null_mut(),
        },
        None => null_mut(),
    }
}

/// Full-canvas RGBA bytes (width*height*4) of one frame, or null.
#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeGifFrame(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
    index: jint,
) -> jbyteArray {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return null_mut(),
    };
    match frame_at(&path, index.max(0) as usize) {
        Some(bytes) => match env.byte_array_from_slice(&bytes) {
            Ok(arr) => arr.into_raw(),
            Err(_) => null_mut(),
        },
        None => null_mut(),
    }
}

/// Returns null on success, otherwise an error message.
#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeCropGif(
    mut env: JNIEnv,
    _this: JObject,
    in_path: JString,
    out_path: JString,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    quality: jint,
    fast: jboolean,
    start_frame: jint,
    end_frame: jint,
) -> jstring {
    let inp: String = match env.get_string(&in_path) {
        Ok(s) => s.into(),
        Err(_) => return null_mut(),
    };
    let out: String = match env.get_string(&out_path) {
        Ok(s) => s.into(),
        Err(_) => return null_mut(),
    };
    let res = crop_gif(
        &inp,
        &out,
        x.max(0) as usize,
        y.max(0) as usize,
        w.max(0) as usize,
        h.max(0) as usize,
        quality.clamp(1, 100) as u8,
        fast != 0,
        start_frame.max(0) as usize,
        end_frame.max(0) as usize,
    );
    match res {
        Ok(()) => null_mut(),
        Err(e) => env.new_string(e).map(|s| s.into_raw()).unwrap_or(null_mut()),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeCropProgress(_env: JNIEnv, _this: JObject) -> jint {
    PROGRESS.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeCancelCrop(_env: JNIEnv, _this: JObject) {
    CANCEL.store(true, Ordering::SeqCst);
}

/// Opens a GIF for sequential reading. Returns 0 on failure; free with nativeGifClose.
#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeGifOpen(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
) -> jlong {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    match GifReader::open(&path) {
        Some(r) => Box::into_raw(Box::new(r)) as jlong,
        None => 0,
    }
}

/// Next frame as full-canvas RGBA bytes (width*height*4), or null at the end.
#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeGifNext(
    env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jbyteArray {
    if handle == 0 {
        return null_mut();
    }
    let reader = unsafe { &mut *(handle as *mut GifReader) };
    match reader.next() {
        Some(bytes) => match env.byte_array_from_slice(bytes) {
            Ok(arr) => arr.into_raw(),
            Err(_) => null_mut(),
        },
        None => null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeGifClose(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { drop(Box::from_raw(handle as *mut GifReader)) };
    }
}
