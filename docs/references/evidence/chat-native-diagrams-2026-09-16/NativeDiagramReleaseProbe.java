import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.hashsequence.coilresvg.RustBufferStruct;
import com.hashsequence.coilresvg.UniffiLib;
import com.hashsequence.coilresvg.UniffiRustCallStatusStruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class NativeDiagramReleaseProbe {
    @SuppressWarnings("unchecked")
    private static Object load(String library, Class<?> type) {
        return Native.load(library, (Class<? extends Library>) type);
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("me.rerere.mermaid.MermaidNativeLibrary");
        Object library = load("rikkahub_mermaid", type);
        byte[] source = "flowchart LR\nA[你好] --> B[\"$$x^2$$\"]".getBytes(StandardCharsets.UTF_8);
        byte[] config = "{\"theme\":\"dark\"}".getBytes(StandardCharsets.UTF_8);
        Pointer result = (Pointer) type.getMethod("rikkahub_mermaid_render_svg_with_config",
            byte[].class, long.class, byte[].class, long.class)
            .invoke(library, source, (long) source.length, config, (long) config.length);
        try {
            Pointer error = (Pointer) type.getMethod("rikkahub_mermaid_result_error", Pointer.class)
                .invoke(library, result);
            if (error != null) throw new AssertionError(error.getString(0, "UTF-8"));
            Pointer output = (Pointer) type.getMethod("rikkahub_mermaid_result_svg", Pointer.class)
                .invoke(library, result);
            String svg = output.getString(0, "UTF-8");
            if (!svg.contains("<svg") || !svg.contains("你好") || svg.contains("$$x^2$$")) {
                throw new AssertionError("Invalid rendered output after ProGuard");
            }
            System.out.println("PASS: ProGuard Mermaid JNA render, Unicode and default math; SVG chars=" + svg.length());
        } finally {
            type.getMethod("rikkahub_mermaid_result_free", Pointer.class).invoke(library, result);
        }

        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\">" +
            "<rect width=\"100\" height=\"50\" fill=\"#e6141e\"/></svg>").getBytes(StandardCharsets.UTF_8);
        byte[] serialized = ByteBuffer.allocate(svg.length + 4).putInt(svg.length).put(svg).array();
        UniffiRustCallStatusStruct.ByReference status = new UniffiRustCallStatusStruct.ByReference();
        RustBufferStruct.ByValue input = UniffiLib.ffi_resvg_core_rustbuffer_alloc(serialized.length, status);
        if (status.code != 0) throw new AssertionError("Rust allocation failed: " + status.code);
        input.data.write(0, serialized, 0, serialized.length);
        input.len = serialized.length;
        RustBufferStruct.ByValue pixels = UniffiLib.uniffi_resvg_core_fn_func_render_svg(input, 100, 50, status);
        if (status.code != 0) throw new AssertionError("Resvg render failed: " + status.code);
        try {
            ByteBuffer decoded = ByteBuffer.wrap(pixels.data.getByteArray(0, (int) pixels.len));
            int width = decoded.getInt(), height = decoded.getInt(), length = decoded.getInt();
            int rgba = decoded.getInt();
            if (width != 100 || height != 50 || length != 20_000 || rgba != 0xe6141eff) {
                throw new AssertionError("Unexpected Resvg pixel output after ProGuard");
            }
            System.out.println("PASS: ProGuard coil-resvg native rasterization 100x50; exact RGBA=e6141eff");
        } finally {
            UniffiLib.ffi_resvg_core_rustbuffer_free(pixels, new UniffiRustCallStatusStruct.ByReference());
        }
    }
}
