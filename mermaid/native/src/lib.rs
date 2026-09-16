use std::ffi::{CString, c_char};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

use merman::{
    Engine, MermaidConfig, OperationControl, RenderOutput, RenderRequest, Renderer, SvgRequest,
};

pub struct MermaidResult {
    svg: Option<CString>,
    error: Option<CString>,
}

fn render(source: &str, config: &str) -> Result<Option<String>, String> {
    let request = SvgRequest {
        pipeline: Some(merman::svg::SvgPipeline::resvg_safe()),
        ..Default::default()
    };
    let engine = if config.is_empty() {
        Engine::new()
    } else {
        let config = serde_json::from_str(config).map_err(|error| error.to_string())?;
        Engine::new().with_site_config(MermaidConfig::from_value(config))
    };
    match Renderer::new()
        .with_engine(engine)
        .render(RenderRequest::svg(source, OperationControl::new(), request))
    {
        Ok(RenderOutput::Svg(svg)) => Ok(svg.map(|output| output.svg().to_owned())),
        Ok(_) => Err("Merman returned an unexpected output type".to_owned()),
        Err(error) => Err(error.to_string()),
    }
}

/// # Safety
/// `source` must point to `length` readable bytes, or may be null when length is zero.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rikkahub_mermaid_render_svg(
    source: *const u8,
    length: u64,
) -> *mut MermaidResult {
    unsafe { rikkahub_mermaid_render_svg_with_config(source, length, ptr::null(), 0) }
}

unsafe fn read_utf8<'a>(source: *const u8, length: u64) -> Result<&'a str, String> {
    let length = usize::try_from(length).map_err(|error| error.to_string())?;
    let bytes = if length == 0 {
        &[][..]
    } else {
        if source.is_null() || length > isize::MAX as usize {
            return Err("Invalid Mermaid input buffer".to_owned());
        }
        unsafe { std::slice::from_raw_parts(source, length) }
    };
    std::str::from_utf8(bytes).map_err(|error| error.to_string())
}

/// # Safety
/// Both inputs must point to their stated number of readable bytes, or be null when empty.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rikkahub_mermaid_render_svg_with_config(
    source: *const u8,
    length: u64,
    config: *const u8,
    config_length: u64,
) -> *mut MermaidResult {
    // A Rust panic must never unwind through JNA or Kotlin/Native.
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let source = unsafe { read_utf8(source, length) }?;
        let config = unsafe { read_utf8(config, config_length) }?;
        render(source, config)?
            .map(|svg| CString::new(svg).map_err(|error| error.to_string()))
            .transpose()
    }))
    .unwrap_or_else(|_| Err("Merman panicked while rendering the diagram".to_owned()));

    let result = match outcome {
        Ok(svg) => MermaidResult { svg, error: None },
        Err(error) => MermaidResult {
            svg: None,
            error: Some(CString::new(error.replace('\0', "\u{fffd}")).expect("NULs were replaced")),
        },
    };
    Box::into_raw(Box::new(result))
}

/// # Safety
/// `result` must be null or an unfreed result returned by `rikkahub_mermaid_render_svg`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rikkahub_mermaid_result_svg(
    result: *const MermaidResult,
) -> *const c_char {
    unsafe { result.as_ref() }
        .and_then(|result| result.svg.as_ref())
        .map_or(ptr::null(), |svg| svg.as_ptr())
}

/// # Safety
/// `result` must be null or an unfreed result returned by `rikkahub_mermaid_render_svg`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rikkahub_mermaid_result_error(
    result: *const MermaidResult,
) -> *const c_char {
    unsafe { result.as_ref() }
        .and_then(|result| result.error.as_ref())
        .map_or(ptr::null(), |error| error.as_ptr())
}

/// # Safety
/// `result` must be null or an unfreed result returned by `rikkahub_mermaid_render_svg`.
/// All borrowed output pointers become invalid after this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rikkahub_mermaid_result_free(result: *mut MermaidResult) {
    if !result.is_null() {
        drop(unsafe { Box::from_raw(result) });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CStr;

    unsafe fn take(result: *mut MermaidResult) -> Result<Option<String>, String> {
        let svg = unsafe { rikkahub_mermaid_result_svg(result) };
        let error = unsafe { rikkahub_mermaid_result_error(result) };
        let value = if !error.is_null() {
            Err(unsafe { CStr::from_ptr(error) }
                .to_str()
                .unwrap()
                .to_owned())
        } else if !svg.is_null() {
            Ok(Some(
                unsafe { CStr::from_ptr(svg) }.to_str().unwrap().to_owned(),
            ))
        } else {
            Ok(None)
        };
        unsafe { rikkahub_mermaid_result_free(result) };
        value
    }

    #[test]
    fn ffi_renders_unicode_and_html_labels_as_resvg_safe_svg() {
        let source = "flowchart LR\n A[你好<br/>Mermaid] --> B[完成]";
        let svg = unsafe {
            take(rikkahub_mermaid_render_svg(
                source.as_ptr(),
                source.len() as u64,
            ))
        }
        .unwrap()
        .unwrap();
        assert!(svg.contains("<svg"));
        assert!(svg.contains("你好"));
        assert!(svg.contains("完成"));
        assert!(!svg.contains("foreignObject"));
    }

    #[test]
    fn ffi_renders_math_with_upstream_default_features() {
        let source = r#"flowchart LR
A["$$x^2$$"]"#;
        let svg = unsafe {
            take(rikkahub_mermaid_render_svg(
                source.as_ptr(),
                source.len() as u64,
            ))
        }
        .unwrap()
        .unwrap();
        assert!(svg.contains("<svg"));
        assert!(svg.contains("<path"));
        assert!(!svg.contains("$$x^2$$"));
        assert!(!svg.contains("foreignObject"));
    }

    #[test]
    fn ffi_accepts_site_config_and_reports_invalid_config_buffers() {
        let source = b"flowchart LR\nA[Theme] --> B[Color]";
        let config = br##"{"theme":"base","themeVariables":{"primaryColor":"#f12345"}}"##;
        let svg = unsafe {
            take(rikkahub_mermaid_render_svg_with_config(
                source.as_ptr(),
                source.len() as u64,
                config.as_ptr(),
                config.len() as u64,
            ))
        }
        .unwrap()
        .unwrap();
        assert!(svg.contains("#f12345"));
        assert!(
            unsafe {
                take(rikkahub_mermaid_render_svg_with_config(
                    source.as_ptr(),
                    source.len() as u64,
                    ptr::null(),
                    1,
                ))
            }
            .is_err()
        );
        assert!(
            unsafe {
                take(rikkahub_mermaid_render_svg_with_config(
                    source.as_ptr(),
                    source.len() as u64,
                    [0xff].as_ptr(),
                    1,
                ))
            }
            .is_err()
        );
    }

    #[test]
    fn ffi_preserves_upstream_errors_for_empty_and_invalid_source() {
        assert!(unsafe { take(rikkahub_mermaid_render_svg(ptr::null(), 0)) }.is_err());
        let invalid = b"not a mermaid diagram";
        assert!(
            unsafe {
                take(rikkahub_mermaid_render_svg(
                    invalid.as_ptr(),
                    invalid.len() as u64,
                ))
            }
            .is_err()
        );
    }

    #[test]
    fn ffi_reports_invalid_utf8_and_invalid_buffer() {
        assert!(unsafe { take(rikkahub_mermaid_render_svg([0xff].as_ptr(), 1)) }.is_err());
        assert!(unsafe { take(rikkahub_mermaid_render_svg(ptr::null(), 1)) }.is_err());
        unsafe { rikkahub_mermaid_result_free(ptr::null_mut()) };
    }
}
