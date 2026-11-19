
use jni::objects::{JClass, JString};
use jni::sys::{jfloat, jint, jstring};
use jni::JNIEnv;

use std::fs;
use std::path::Path;
use std::sync::Mutex;
use std::thread;
use std::time::UNIX_EPOCH;

static CPU_STATE: Mutex<Option<(u64, u64)>> = Mutex::new(None);

fn make_string(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
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

fn memory_info() -> String {
    match (
        read_value("/proc/meminfo", "MemTotal:"),
        read_value("/proc/meminfo", "MemAvailable:"),
    ) {
        (Some(total), Some(available)) => format!(
            "Total: {} MB | Available: {} MB",
            total / 1024,
            available / 1024
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

fn directory_info(path: &str) -> String {
    let directory = Path::new(path);

    if !directory.exists() {
        return "Directory does not exist".to_string();
    }

    if !directory.is_dir() {
        return "Path is not a directory".to_string();
    }

    let entries = match fs::read_dir(directory) {
        Ok(value) => value,
        Err(_) => return "Permission denied or unreadable".to_string(),
    };

    let mut files = 0u64;
    let mut folders = 0u64;
    let mut total_size = 0u64;

    for entry in entries.flatten() {
        let metadata = match entry.metadata() {
            Ok(value) => value,
            Err(_) => continue,
        };

        if metadata.is_dir() {
            folders += 1;
        } else if metadata.is_file() {
            files += 1;
            total_size = total_size.saturating_add(metadata.len());
        }
    }

    format!(
        "Files: {} | Folders: {} | Size: {} MB",
        files,
        folders,
        total_size / (1024 * 1024)
    )
}

fn file_info(path: &str) -> String {
    let metadata = match fs::metadata(path) {
        Ok(value) => value,
        Err(_) => return "File unavailable".to_string(),
    };

    if !metadata.is_file() {
        return "Not a regular file".to_string();
    }

    let modified = metadata
        .modified()
        .ok()
        .and_then(|time| time.duration_since(UNIX_EPOCH).ok())
        .map(|duration| duration.as_secs().to_string())
        .unwrap_or_else(|| "Unknown".to_string());

    format!(
        "Size: {} bytes | Modified: {}",
        metadata.len(),
        modified
    )
}

fn network_info() -> String {
    let content = match fs::read_to_string("/proc/net/dev") {
        Ok(value) => value,
        Err(_) => return "Network statistics unavailable".to_string(),
    };

    let mut report = String::from("NETWORK MONITOR\n");
    let mut count = 0u32;

    for line in content.lines().skip(2) {
        let (name, statistics) = match line.split_once(':') {
            Some(value) => value,
            None => continue,
        };

        let values: Vec<u64> = statistics
            .split_whitespace()
            .filter_map(|value| value.parse().ok())
            .collect();

        if values.len() < 9 {
            continue;
        }

        count += 1;

        report.push_str(&format!(
            "{} | RX: {} bytes | TX: {} bytes\n",
            name.trim(),
            values[0],
            values[8]
        ));
    }

    if count == 0 {
        return "No network interfaces detected".to_string();
    }

    report.push_str(&format!("Interfaces detected: {}", count));
    report
}

// BASIC SYSTEM

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, "N3XR Rust Core 0.8.0".to_string())
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

// MEMORY

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

// CPU

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeCpuUsagePercent(
    _env: JNIEnv,
    _class: JClass,
) -> jfloat {
    cpu_usage()
}

// SYSTEM REPORT

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeSystemReport(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let usage = cpu_usage();

    let cpu_text = if usage < 0.0 {
        "Collecting baseline".to_string()
    } else {
        format!("{:.1}%", usage)
    };

    let report = format!(
        "N3XR SYSTEM REPORT\n\
         Version: 0.8.0\n\
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
        cpu_text,
        memory_info(),
        process_memory_info()
    );

    make_string(&mut env, report)
}

// FILE INFORMATION

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeDirectoryInfo(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    let path: String = match env.get_string(&path) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    make_string(&mut env, directory_info(&path))
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeFileInfo(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    let path: String = match env.get_string(&path) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    make_string(&mut env, file_info(&path))
}

// NETWORK

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeNetworkInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, network_info())
}
