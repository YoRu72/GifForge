#!/usr/bin/env bash
# Local native build (needs Rust, cargo-ndk, Android NDK + ANDROID_NDK_HOME)
set -e
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
cd native
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
