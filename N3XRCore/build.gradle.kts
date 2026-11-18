
plugins {
    alias(libs.plugins.android.library)
}

val projectArch = System.getProperty("arch", "all")

val rustAbis = mapOf(
    "arm" to "armv7-linux-androideabi",
    "arm64" to "aarch64-linux-android",
    "x86" to "i686-linux-android",
    "x86_64" to "x86_64-linux-android"
)

val selectedRustAbis = when (projectArch) {
    "all" -> rustAbis.values.toList()
    else -> listOf(
        rustAbis[projectArch]
            ?: throw GradleException("Unsupported architecture: $projectArch")
    )
}

val n3xrJniLibs = layout.buildDirectory.dir("n3xr-jniLibs")

android {
    namespace = "com.n3xr.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val cargoBuild = tasks.register("cargoBuild") {
    group = "build"
    description = "Builds N3XR Rust Core for selected Android ABI."

    inputs.dir("rust/src")
    inputs.file("rust/Cargo.toml")

    outputs.dir(n3xrJniLibs)

    doLast {
        fun runTool(command: List<String>): String? {
            return try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream
                    .bufferedReader()
                    .readText()

                if (process.waitFor() == 0) output else null
            } catch (_: Exception) {
                null
            }
        }

        if (runTool(listOf("cargo", "--version")) == null) {
            throw GradleException("Rust Cargo is not installed.")
        }

        if (runTool(listOf("cargo", "ndk", "--version")) == null) {
            throw GradleException("cargo-ndk is not installed.")
        }

        val installedTargets = runTool(
            listOf("rustup", "target", "list", "--installed")
        ) ?: throw GradleException("Unable to check Rust targets.")

        val missing = selectedRustAbis.filter {
            !installedTargets.lines().any { line ->
                line.trim() == it
            }
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing Rust targets: ${missing.joinToString()}"
            )
        }

        val outputDir = n3xrJniLibs.get().asFile
        outputDir.mkdirs()

        val command = mutableListOf(
            "cargo",
            "ndk",
            "--platform",
            "26",
            "--output-dir",
            outputDir.absolutePath
        )

        selectedRustAbis.forEach {
            command.add("--target")
            command.add(it)
        }

        command.addAll(listOf("build", "--release"))

        val process = ProcessBuilder(command)
            .directory(project.file("rust"))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream
            .bufferedReader()
            .readText()

        val exitCode = process.waitFor()

        println(output)

        if (exitCode != 0) {
            throw GradleException(
                "N3XR Cargo build failed:\n$output"
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(
            n3xrJniLibs.get().asFile.apply { mkdirs() }.absolutePath
        )
    }
}

tasks.named("preBuild") {
    dependsOn(cargoBuild)
}
