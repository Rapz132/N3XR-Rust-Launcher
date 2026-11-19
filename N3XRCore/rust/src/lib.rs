
use jni::objects::JClass;
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use std::fs;
use std::thread;

fn to_jstring(env: &mut JNIEnv<'_>, value: &str) -> jstring {
    match env.new_string(value) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn read_memory_value(key: &str) -> Option<u64> {
    let content = fs::read_to_string("/proc/meminfo").ok()?;

    for line in content.lines() {
        if line.starts_with(key) {
            return line
                .split_whitespace()
                .nth(1)?
                .parse::<u64>()
                .ok();
        }
    }

    None
}

fn read_process_memory() -> String {
    let content = match fs::read_to_string("/proc/self/status") {
        Ok(value) => value,
        Err(_) => return "Unavailable".to_string(),
    };

    let mut rss = "Unknown";
    let mut peak = "Unknown";

    for line in content.lines() {
        if line.starts_with("VmRSS:") {
            rss = line.trim_start_matches("VmRSS:").trim();
        } else if line.starts_with("VmHWM:") {
            peak = line.trim_start_matches("VmHWM:").trim();
        }
    }

    format!("RSS: {} | Peak: {}", rss, peak)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeVersion(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    to_jstring(&mut env, "N3XR Rust Core 0.3.0")
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
    to_jstring(&mut env, std::env::consts::ARCH)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeOperatingSystem(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    to_jstring(&mut env, std::env::consts::OS)
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

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeDeviceMemoryInfo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let total = read_memory_value("MemTotal:");
    let available = read_memory_value("MemAvailable:");

    let result = match (total, available) {
        (Some(total), Some(available)) => {
            format!(
                "Total: {} MB | Available: {} MB",
                total / 1024,
                available / 1024
            )
        }
        _ => "Unavailable".to_string(),
    };

    to_jstring(&mut env, &result)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeProcessMemoryInfo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let result = read_process_memory();
    to_jstring(&mut env, &result)
}
