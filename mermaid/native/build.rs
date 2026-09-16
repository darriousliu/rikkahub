use std::{env, path::PathBuf};

fn main() {
    println!("cargo:rerun-if-changed=exports.def");
    if env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows")
        && env::var("CARGO_CFG_TARGET_ENV").as_deref() == Ok("gnu")
    {
        // cargo-zigbuild drops rustc's -Wl,.../list.def. Pass our C ABI exports as
        // a positional input so Zig keeps them even when libunwind exports symbols.
        let exports = PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap()).join("exports.def");
        println!("cargo:rustc-cdylib-link-arg={}", exports.display());
    }
}
