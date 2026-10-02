//! JNI bridge: Kotlin pushes RGBA frames, gifski encodes them to a GIF file on a writer thread.
mod gifcrop;

use gifski::{Collector, Repeat, Settings};
use imgref::ImgVec;
use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jboolean, jdouble, jint, jlong};
use jni::JNIEnv;
use rgb::RGBA8;
use std::fs::File;
use std::io::BufWriter;
use std::thread::JoinHandle;

struct Session {
    collector: Option<Collector>,
    writer: Option<JoinHandle<bool>>,
}

#[no_mangle]
pub extern "system" fn Java_com_gifforge_app_media_GifskiNative_nativeStart(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
    width: jint,
    height: jint,
    quality: jint,
    fast: jboolean,
    repeat_infinite: jboolean,
) -> jlong {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let settings = Settings {
        width: if width > 0 { Some(width as u32) } else { None },
        height: if height > 0 { Some(height as u32) } else { None },
        quality: quality.clamp(1, 100) as u8,
        fast: fast != 0,
        repeat: if repeat_infinite != 0 { Repeat::Infinite } else { Repeat::Finite(0) },
    };
    let (collector, writer) = match gifski::new(settings) {
        Ok(v) => v,
        Err(_) => return 0,
    };
    let file = match File::create(&path) {
        Ok(f) => f,
        Err(_) => return 0,
    };
    let handle = std::thread::spawn(move || {
        let mut progress = gifski::progress::NoProgress {};
        writer.write(BufWriter::new(file), &mut progress).is_ok()
    });
    let session = Box::new(Session { collector: Some(collector), writer: Some(handle) });
    Box::into_raw(session) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_gifforge_app_media_GifskiNative_nativeAddFrame(
    env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
    data: JByteArray,
    w: jint,
    h: jint,
    pts: jdouble,
) -> jboolean {
    if handle == 0 || w <= 0 || h <= 0 {
        return 0;
    }
    let session = unsafe { &*(handle as *const Session) };
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    let (w, h) = (w as usize, h as usize);
    if bytes.len() != w * h * 4 {
        return 0;
    }
    let pixels: Vec<RGBA8> = bytes
        .chunks_exact(4)
        .map(|p| RGBA8::new(p[0], p[1], p[2], p[3]))
        .collect();
    let img = ImgVec::new(pixels, w, h);
    match &session.collector {
        Some(c) => c.add_frame_rgba(index as usize, img, pts).is_ok() as jboolean,
        None => 0,
    }
}

fn close(handle: jlong) -> bool {
    if handle == 0 {
        return false;
    }
    let mut s = unsafe { Box::from_raw(handle as *mut Session) };
    drop(s.collector.take()); // signals end of frames
    s.writer.take().map(|h| h.join().unwrap_or(false)).unwrap_or(false)
}

#[no_mangle]
pub extern "system" fn Java_com_gifforge_app_media_GifskiNative_nativeFinish(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jboolean {
    close(handle) as jboolean
}

#[no_mangle]
pub extern "system" fn Java_com_gifforge_app_media_GifskiNative_nativeCancel(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) {
    close(handle);
}
