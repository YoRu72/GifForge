//! WOFF2 web font -> plain sfnt (TTF/OTF) so Android can load it.
use jni::objects::{JObject, JString};
use jni::sys::jboolean;
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};

#[no_mangle]
pub extern "system" fn Java_com_mediaforge_app_media_GifskiNative_nativeWoff2ToSfnt(
    mut env: JNIEnv,
    _this: JObject,
    in_path: JString,
    out_path: JString,
) -> jboolean {
    let inp: String = match env.get_string(&in_path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let out: String = match env.get_string(&out_path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let res = catch_unwind(AssertUnwindSafe(|| -> Option<()> {
        let data = std::fs::read(&inp).ok()?;
        let sfnt = wuff::decompress_woff2(&data).ok()?;
        std::fs::write(&out, sfnt).ok()
    }));
    matches!(res, Ok(Some(()))) as jboolean
}
