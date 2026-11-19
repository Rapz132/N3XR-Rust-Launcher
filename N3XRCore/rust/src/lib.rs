
use jni::objects::JClass;
use jni::sys::{jint, jstring};
use jni::JNIEnv;

use std::thread;

fn string_to_jni(
    env: &mut JNIEnv<'_>,
    value: &str,
) -> jstring {
    match env.new_string(value) {
        Ok(string) => string.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeVersion(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    string_to_jni(&mut env, "N3XR Rust Core 0.2.0")
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeAdd(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    a: jint,
    b: jint,
) -> jint {
    a.saturating_add(b)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeArchitecture(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    string_to_jni(&mut env, std::env::consts::ARCH)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeOperatingSystem(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    string_to_jni(&mut env, std::env::consts::OS)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeCpuThreads(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jint {
    thread::available_parallelism()
        .map(|count| count.get() as jint)
        .unwrap_or(1)
}
