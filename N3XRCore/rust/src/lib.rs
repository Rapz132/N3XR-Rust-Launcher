use std::path::Path;
use jni::objects::JClass;
use jni::sys::{jfloat, jint, jstring};
use jni::JNIEnv;
use std::fs;
use std::sync::Mutex;
use std::thread;

static CPU_STATE: Mutex<Option<(u64, u64)>> = Mutex::new(None);

fn make_string(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn read_value(file: &str, key: &str) -> Option<u64> {
    let content = fs::read_to_string(file).ok()?;

    content.lines().find_map(|line| {
        let mut parts = line.split_whitespace();

        if parts.next()? == key {
            parts.next()?.parse().ok()
        } else {
            None
        }
    })
}

fn cpu_counters() -> Option<(u64, u64)> {
    let content = fs::read_to_string("/proc/stat").ok()?;
    let line = content.lines().next()?;
    let mut parts = line.split_whitespace();

    if parts.next()? != "cpu" {
        return None;
    }

    let values: Vec<u64> = parts
        .filter_map(|value| value.parse().ok())
        .collect();

    if values.len() < 4 {
        return None;
    }

    let total: u64 = values.iter().sum();
    let idle = values[3] + values.get(4).copied().unwrap_or(0);

    Some((total, idle))
}

fn cpu_usage() -> f32 {
    let current = match cpu_counters() {
        Some(value) => value,
        None => return -1.0,
    };

    let mut state = match CPU_STATE.lock() {
        Ok(value) => value,
        Err(_) => return -1.0,
    };

    let previous = *state;
    *state = Some(current);

    let (old_total, old_idle) = match previous {
        Some(value) => value,
        None => return -1.0,
    };

    let total_delta = current.0.saturating_sub(old_total);
    let idle_delta = current.1.saturating_sub(old_idle);

    if total_delta == 0 {
        return -1.0;
    }

    let usage =
        100.0 * total_delta.saturating_sub(idle_delta) as f32
        / total_delta as f32;

    usage.clamp(0.0, 100.0)
}

fn memory_info() -> String {
    let total = read_value("/proc/meminfo", "MemTotal:");
    let available = read_value("/proc/meminfo", "MemAvailable:");

    match (total, available) {
        (Some(t), Some(a)) => format!(
            "Total: {} MB | Available: {} MB",
            t / 1024,
            a / 1024
        ),
        _ => "Unavailable".to_string(),
    }
}

fn process_memory_info() -> String {
    let content = match fs::read_to_string("/proc/self/status") {
        Ok(value) => value,
        Err(_) => return "Unavailable".to_string(),
    };

    let mut rss = "Unavailable".to_string();
    let mut peak = "Unavailable".to_string();

    for line in content.lines() {
        if line.starts_with("VmRSS:") {
            rss = line.replace("VmRSS:", "").trim().to_string();
        } else if line.starts_with("VmHWM:") {
            peak = line.replace("VmHWM:", "").trim().to_string();
        }
    }

    format!("RSS: {} | Peak: {}", rss, peak)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, "N3XR Rust Core 0.5.0".to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeAdd(
    _env: JNIEnv,
    _class: JClass,
    a: jint,
    b: jint,
) -> jint {
    a.saturating_add(b)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeArchitecture(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, std::env::consts::ARCH.to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeOperatingSystem(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, std::env::consts::OS.to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeCpuThreads(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    thread::available_parallelism()
        .map(|count| count.get() as jint)
        .unwrap_or(1)
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeDeviceMemoryInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, memory_info())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeProcessMemoryInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, process_memory_info())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeCpuUsagePercent(
    _env: JNIEnv,
    _class: JClass,
) -> jfloat {
    cpu_usage()
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeSystemReport(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let report = format!(
        "N3XR SYSTEM REPORT\n\
         Version: 0.5.0\n\
         OS: {}\n\
         Architecture: {}\n\
         CPU Threads: {}\n\
         CPU Usage: {}\n\
         Device Memory: {}\n\
         Process Memory: {}",
        std::env::consts::OS,
        std::env::consts::ARCH,
        thread::available_parallelism()
            .map(|count| count.get())
            .unwrap_or(1),
        {
            let usage = cpu_usage();
            if usage < 0.0 {
                "Collecting baseline".to_string()
            } else {
                format!("{:.1}%", usage)
            }
        },
        memory_info(),
        process_memory_info()
    );

    make_string(&mut env, report)
}
