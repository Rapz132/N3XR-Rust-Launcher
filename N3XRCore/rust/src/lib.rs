
use jni::objects::{JClass, JString};
use jni::sys::{jfloat, jint, jstring};
use jni::JNIEnv;
use std::fs;
use std::path::Path;
use std::sync::Mutex;

static CPU_PREVIOUS: Mutex<Option<(u64, u64)>> = Mutex::new(None);

fn make_string(env: &mut JNIEnv, value: String) -> jstring {
    match env.new_string(value) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn read_cpu() -> Option<(u64, u64)> {
    let content = fs::read_to_string("/proc/stat").ok()?;
    let line = content.lines().next()?;

    let values: Vec<u64> = line
        .split_whitespace()
        .skip(1)
        .filter_map(|v| v.parse().ok())
        .collect();

    if values.len() < 4 {
        return None;
    }

    let total: u64 = values.iter().sum();
    let idle = values[3] + values.get(4).copied().unwrap_or(0);

    Some((total, idle))
}

fn cpu_usage() -> f32 {
    let current = match read_cpu() {
        Some(value) => value,
        None => return -1.0,
    };

    let mut previous = match CPU_PREVIOUS.lock() {
        Ok(value) => value,
        Err(_) => return -1.0,
    };

    let result = if let Some(old) = *previous {
        let total_diff = current.0.saturating_sub(old.0);
        let idle_diff = current.1.saturating_sub(old.1);

        if total_diff == 0 {
            -1.0
        } else {
            ((total_diff.saturating_sub(idle_diff)) as f32
                / total_diff as f32
                * 100.0)
                .clamp(0.0, 100.0)
        }
    } else {
        -1.0
    };

    *previous = Some(current);
    result
}

fn memory_info() -> String {
    match fs::read_to_string("/proc/meminfo") {
        Ok(content) => content
            .lines()
            .filter(|line| {
                line.starts_with("MemTotal:")
                    || line.starts_with("MemAvailable:")
                    || line.starts_with("MemFree:")
            })
            .collect::<Vec<_>>()
            .join("\n"),
        Err(_) => "Memory information unavailable".to_string(),
    }
}

fn process_memory_info() -> String {
    match fs::read_to_string("/proc/self/status") {
        Ok(content) => content
            .lines()
            .filter(|line| {
                line.starts_with("VmRSS:")
                    || line.starts_with("VmHWM:")
                    || line.starts_with("VmSize:")
            })
            .collect::<Vec<_>>()
            .join("\n"),
        Err(_) => "Process memory unavailable".to_string(),
    }
}

fn network_info() -> String {
    match fs::read_to_string("/proc/net/dev") {
        Ok(content) => {
            let mut result = Vec::new();

            for line in content.lines().skip(2) {
                let Some((name, data)) = line.split_once(':') else {
                    continue;
                };

                let values: Vec<&str> = data.split_whitespace().collect();

                if values.len() < 9 {
                    continue;
                }

                result.push(format!(
                    "{} RX={} bytes TX={} bytes",
                    name.trim(),
                    values[0],
                    values[8]
                ));
            }

            if result.is_empty() {
                "No network interfaces found".to_string()
            } else {
                result.join("\n")
            }
        }
        Err(_) => "Network information unavailable".to_string(),
    }
}

fn directory_info(path: &str) -> String {
    let target = Path::new(path);

    if !target.is_dir() {
        return "Directory unavailable or inaccessible".to_string();
    }

    match fs::read_dir(target) {
        Ok(entries) => {
            let mut count = 0usize;

            for entry in entries {
                if entry.is_ok() {
                    count += 1;
                }
            }

            format!("Directory: {}\nEntries: {}", path, count)
        }
        Err(_) => "Cannot read directory".to_string(),
    }
}

fn file_info(path: &str) -> String {
    match fs::metadata(path) {
        Ok(metadata) => format!(
            "Path: {}\nSize: {} bytes\nFile: {}\nDirectory: {}",
            path,
            metadata.len(),
            metadata.is_file(),
            metadata.is_dir()
        ),
        Err(_) => "File information unavailable".to_string(),
    }
}

fn system_report() -> String {
    format!(
        "N3XR Rust Core 1.0.0\nArchitecture: {}\nOS: {}\nCPU threads: {}\nCPU usage: {:.2}%\n\nDevice memory:\n{}\n\nProcess memory:\n{}\n\nNetwork:\n{}",
        std::env::consts::ARCH,
        std::env::consts::OS,
        std::thread::available_parallelism()
            .map(|v| v.get())
            .unwrap_or(0),
        cpu_usage(),
        memory_info(),
        process_memory_info(),
        network_info()
    )
}

fn health_check() -> String {
    let proc_stat = Path::new("/proc/stat").exists();
    let proc_mem = Path::new("/proc/meminfo").exists();
    let proc_net = Path::new("/proc/net/dev").exists();

    format!(
        "N3XR Rust Core Health Check\nVersion: 1.0.0\nArchitecture: {}\nOS: {}\n/proc/stat: {}\n/proc/meminfo: {}\n/proc/net/dev: {}\nStatus: {}",
        std::env::consts::ARCH,
        std::env::consts::OS,
        if proc_stat { "OK" } else { "UNAVAILABLE" },
        if proc_mem { "OK" } else { "UNAVAILABLE" },
        if proc_net { "OK" } else { "UNAVAILABLE" },
        if proc_stat && proc_mem { "HEALTHY" } else { "LIMITED" }
    )
}

fn diagnostics() -> String {
    format!("{}\n\n{}", health_check(), system_report())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, "N3XR Rust Core 1.0.0".to_string())
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
    std::thread::available_parallelism()
        .map(|v| v.get() as jint)
        .unwrap_or(0)
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
    make_string(&mut env, system_report())
}

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

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeNetworkInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, network_info())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeHealthCheck(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, health_check())
}

#[no_mangle]
pub extern "system" fn Java_com_n3xr_core_N3XRNative_nativeDiagnostics(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    make_string(&mut env, diagnostics())
}
