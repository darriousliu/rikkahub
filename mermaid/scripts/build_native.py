#!/usr/bin/env python3
"""Build the pinned merman wrapper and optionally package the KMP library (Python 3.11+)."""

from __future__ import annotations

import argparse
import os
import platform
import shlex
import shutil
import subprocess
import sys
import tomllib
from dataclasses import dataclass
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1]
UPSTREAM = MODULE / "merman"
MANIFEST = MODULE / "native/Cargo.toml"
PROFILE = "native-distribution"
LIBRARY = "rikkahub_mermaid"


@dataclass(frozen=True)
class Target:
    triple: str
    directory: str
    filename: str


TARGETS = {
    "android": (
        Target("aarch64-linux-android", "arm64-v8a", f"lib{LIBRARY}.so"),
        Target("x86_64-linux-android", "x86_64", f"lib{LIBRARY}.so"),
    ),
    "ios": (
        Target("aarch64-apple-ios", "ios_arm64", f"lib{LIBRARY}.a"),
        Target("aarch64-apple-ios-sim", "ios_simulator_arm64", f"lib{LIBRARY}.a"),
    ),
    "jvm": (
        Target("aarch64-apple-darwin", "darwin-aarch64", f"lib{LIBRARY}.dylib"),
        Target("x86_64-apple-darwin", "darwin-x86-64", f"lib{LIBRARY}.dylib"),
        Target("aarch64-unknown-linux-gnu", "linux-aarch64", f"lib{LIBRARY}.so"),
        Target("x86_64-unknown-linux-gnu", "linux-x86-64", f"lib{LIBRARY}.so"),
        Target("x86_64-pc-windows-gnu", "win32-x86-64", f"{LIBRARY}.dll"),
    ),
}


def run(command: list[str], *, env: dict[str, str] | None = None, cwd: Path = MODULE) -> None:
    print("+ " + shlex.join(command), flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def read_toml(path: Path) -> dict:
    with path.open("rb") as stream:
        return tomllib.load(stream)


def validate_contract() -> str:
    if not (UPSTREAM / "Cargo.toml").is_file():
        raise RuntimeError("Initialize merman first: git submodule update --init --recursive mermaid/merman")
    manifest = read_toml(MANIFEST)
    pin = manifest["package"]["metadata"]["merman"]
    revision = subprocess.check_output(
        ["git", "-C", str(UPSTREAM), "rev-parse", "HEAD"], text=True,
    ).strip()
    if revision != pin["revision"]:
        raise RuntimeError(f"Expected merman {pin['tag']} ({pin['revision']}), found {revision}")
    dependency = manifest["dependencies"]["merman"]
    if not dependency.get("default-features", True) or dependency.get("features"):
        raise RuntimeError("The wrapper must use merman's Rust crate defaults without feature overrides")
    upstream_profile = read_toml(UPSTREAM / "Cargo.toml")["profile"][PROFILE]
    if manifest["profile"][PROFILE] != upstream_profile:
        raise RuntimeError(f"The wrapper must match upstream's {PROFILE} build profile")
    return read_toml(UPSTREAM / "rust-toolchain.toml")["toolchain"]["channel"]


def host_directory(system: str, machine: str) -> str:
    arch = {"arm64": "aarch64", "aarch64": "aarch64", "amd64": "x86-64", "x86_64": "x86-64"}
    os_prefix = {"Darwin": "darwin", "Linux": "linux", "Windows": "win32"}
    try:
        return f"{os_prefix[system]}-{arch[machine.lower()]}"
    except KeyError as error:
        raise RuntimeError(f"Unsupported host: {system} {machine}") from error


def select_targets(kind: str, names: list[str], all_targets: bool, system: str, machine: str) -> list[Target]:
    if all_targets and names:
        raise RuntimeError("Use either explicit targets or --all")
    if kind == "ios" and system != "Darwin":
        raise RuntimeError("iOS builds require macOS and Xcode")
    if not names and not all_targets and kind == "jvm":
        names = [host_directory(system, machine)]
    aliases = {"windows-x86-64": "win32-x86-64"}
    result = []
    for name in names:
        name = aliases.get(name, name)
        match = next((t for t in TARGETS[kind] if name in (t.triple, t.directory)), None)
        if match is None:
            raise RuntimeError(f"Unknown {kind} target: {name}")
        if match not in result:
            result.append(match)
    if not names:
        result = [t for t in TARGETS[kind] if system == "Darwin" or "apple" not in t.triple]
    if system != "Darwin" and any("apple" in t.triple for t in result):
        raise RuntimeError("Apple targets must be built on macOS")
    return result


def android_ndk(explicit: str | None) -> Path:
    version = read_toml(UPSTREAM / "platforms/android/gradle/libs.versions.toml")["versions"]["ndk"]
    override = explicit or os.environ.get("ANDROID_NDK_HOME")
    if override:
        ndk = Path(override).expanduser()
    else:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        properties = MODULE.parent / "local.properties"
        if not sdk and properties.is_file():
            for line in properties.read_text(encoding="utf-8").splitlines():
                key, separator, value = line.partition("=")
                if separator and key.strip() == "sdk.dir":
                    sdk = value.strip().replace("\\:", ":").replace("\\\\", "\\")
        if not sdk:
            raise RuntimeError("Set ANDROID_HOME, ANDROID_SDK_ROOT, or ANDROID_NDK_HOME")
        ndk = Path(sdk).expanduser() / "ndk" / version
    properties = ndk / "source.properties"
    revision = None
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            key, separator, value = line.partition("=")
            if separator and key.strip() == "Pkg.Revision":
                revision = value.strip()
    if revision != version:
        raise RuntimeError(f"NDK {version} is required at {ndk}; install with sdkmanager 'ndk;{version}'")
    return ndk.resolve()


def build(kind: str, target: Target, toolchain: str, ndk: Path | None) -> None:
    env = os.environ.copy()
    env["RUSTUP_TOOLCHAIN"] = toolchain
    cargo_home = Path(env.get("CARGO_HOME", str(Path.home() / ".cargo")))
    env["PATH"] = str(cargo_home / "bin") + os.pathsep + env.get("PATH", "")
    env["CARGO_TARGET_DIR"] = str(MODULE / "build/cargo")
    env.setdefault("IPHONEOS_DEPLOYMENT_TARGET", "15.0")
    env.setdefault("MACOSX_DEPLOYMENT_TARGET", "11.0")
    run(["rustup", "target", "add", "--toolchain", toolchain, target.triple], env=env)
    host = subprocess.check_output(["rustc", "-vV"], env=env, text=True)
    host_triple = next(line.removeprefix("host: ") for line in host.splitlines() if line.startswith("host: "))
    cargo_command = "build"
    if kind == "jvm" and target.triple != host_triple and "apple" not in target.triple:
        for tool in ("cargo-zigbuild", "zig"):
            if not shutil.which(tool, path=env["PATH"]):
                raise RuntimeError(f"Cross-compiling {target.triple} requires {tool}")
        cargo_command = "zigbuild"
    if ndk:
        ndk_host = {"Darwin": "darwin-x86_64", "Linux": "linux-x86_64", "Windows": "windows-x86_64"}
        bin_dir = ndk / "toolchains/llvm/prebuilt" / ndk_host[platform.system()] / "bin"
        suffix = ".cmd" if os.name == "nt" else ""
        clang = str(bin_dir / f"{target.triple}26-clang{suffix}")
        key = target.triple.replace("-", "_")
        env[f"CARGO_TARGET_{key.upper()}_LINKER"] = clang
        env[f"CC_{key}"] = clang
        env[f"CXX_{key}"] = str(bin_dir / f"{target.triple}26-clang++{suffix}")
        env[f"AR_{key}"] = str(bin_dir / ("llvm-ar.exe" if os.name == "nt" else "llvm-ar"))
    run([
        "cargo", cargo_command, "--locked", "--manifest-path", str(MANIFEST),
        "--lib", "--profile", PROFILE, "--target", target.triple,
    ], env=env)
    source = MODULE / "build/cargo" / target.triple / PROFILE / target.filename
    destination = MODULE / "build/native" / kind / target.directory / target.filename
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    print(f"Packaged {target.triple}: {destination}", flush=True)


def package(kind: str, targets: list[Target]) -> None:
    command = [str(MODULE.parent / ("gradlew.bat" if os.name == "nt" else "gradlew"))]
    if kind == "android":
        command += [":mermaid:assembleAndroidMain", "-Pmermaid.android.targets=" + ",".join(t.directory for t in targets)]
    elif kind == "jvm":
        command += [":mermaid:jvmJar", "-Pmermaid.jvm.targets=" + ",".join(t.directory for t in targets)]
    else:
        for target in targets:
            suffix = "IosArm64" if target.directory == "ios_arm64" else "IosSimulatorArm64"
            command.append(f":mermaid:linkReleaseFramework{suffix}")
    run(command, cwd=MODULE.parent)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=TARGETS)
    parser.add_argument("targets", nargs="*", help="Target aliases or Rust triples; omit for platform defaults")
    parser.add_argument("--all", action="store_true", help="Build every target supported on this host")
    parser.add_argument("--check", action="store_true", help="Validate the pinned version/features and list outputs without building")
    parser.add_argument("--package", action="store_true", help="Also assemble the AAR, JVM JAR, or iOS release frameworks")
    parser.add_argument("--ndk-home", help="Path to merman's pinned Android NDK")
    args = parser.parse_intermixed_args()
    toolchain = validate_contract()
    targets = select_targets(args.platform, args.targets, args.all, platform.system(), platform.machine())
    version = read_toml(MANIFEST)["package"]["metadata"]["merman"]["tag"]
    print(f"merman {version}; Rust {toolchain}; features: merman/default")
    if args.check or os.environ.get("MERMAN_CHECK_RECIPE_ONLY") == "true":
        for target in targets:
            print(f"{target.triple} -> build/native/{args.platform}/{target.directory}/{target.filename}")
        return
    ndk = android_ndk(args.ndk_home) if args.platform == "android" else None
    for target in targets:
        build(args.platform, target, toolchain, ndk)
    if args.package:
        package(args.platform, targets)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
