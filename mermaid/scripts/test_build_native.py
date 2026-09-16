import struct
import tempfile
import unittest
from pathlib import Path

from build_native import host_directory, select_targets, validate_contract, validate_windows_exports, windows_exports


class NativeBuildTest(unittest.TestCase):
    def test_wrapper_uses_pinned_upstream_rust_defaults(self):
        self.assertEqual(validate_contract(), "1.95.0")

    def test_defaults_select_both_mobile_architectures_and_only_the_desktop_host(self):
        self.assertEqual(len(select_targets("android", [], False, "Darwin", "arm64")), 2)
        self.assertEqual(len(select_targets("ios", [], False, "Darwin", "arm64")), 2)
        desktop = select_targets("jvm", [], False, "Darwin", "arm64")
        self.assertEqual([target.triple for target in desktop], ["aarch64-apple-darwin"])

    def test_windows_uses_jnas_actual_resource_prefix(self):
        self.assertEqual(host_directory("Windows", "AMD64"), "win32-x86-64")
        target = select_targets("jvm", ["windows-x86-64"], False, "Darwin", "arm64")[0]
        self.assertEqual(target.directory, "win32-x86-64")
        self.assertEqual(target.triple, "x86_64-pc-windows-gnu")

    def test_all_excludes_apple_targets_on_non_apple_hosts(self):
        self.assertEqual(len(select_targets("jvm", [], True, "Darwin", "arm64")), 5)
        linux = select_targets("jvm", [], True, "Linux", "x86_64")
        self.assertEqual(len(linux), 3)
        self.assertFalse(any("apple" in target.triple for target in linux))

    def test_invalid_or_unbuildable_targets_fail_before_building(self):
        for kind, names, all_targets, system in (
            ("jvm", ["typo"], False, "Darwin"),
            ("jvm", ["darwin-aarch64"], False, "Linux"),
            ("jvm", ["linux-x86-64"], True, "Linux"),
            ("ios", [], False, "Windows"),
        ):
            with self.subTest(kind=kind, names=names, system=system), self.assertRaises(RuntimeError):
                select_targets(kind, names, all_targets, system, "x86_64")


class WindowsExportsTest(unittest.TestCase):
    exports = (
        "rikkahub_mermaid_render_svg",
        "rikkahub_mermaid_render_svg_with_config",
        "rikkahub_mermaid_result_svg",
        "rikkahub_mermaid_result_error",
        "rikkahub_mermaid_result_free",
    )

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.library = Path(self.directory.name) / "rikkahub_mermaid.dll"

    def write_dll(self, exports, *, export_table=True):
        # Minimal PE32+ image with one section and an export name table.
        data = bytearray(2048)
        data[:2] = b"MZ"
        struct.pack_into("<I", data, 0x3C, 0x80)
        data[0x80:0x84] = b"PE\0\0"
        struct.pack_into("<H", data, 0x86, 1)
        struct.pack_into("<H", data, 0x94, 240)
        struct.pack_into("<H", data, 0x98, 0x20B)
        struct.pack_into("<I", data, 0x98 + 112, 0x1000 if export_table else 0)
        struct.pack_into("<III", data, 0x98 + 240 + 12, 0x1000, 1536, 0x200)
        struct.pack_into("<I", data, 0x200 + 24, len(exports))
        struct.pack_into("<I", data, 0x200 + 32, 0x1040)
        position = 0x300
        for index, name in enumerate(exports):
            struct.pack_into("<I", data, 0x240 + index * 4, position - 0x200 + 0x1000)
            encoded = name.encode("ascii") + b"\0"
            data[position:position + len(encoded)] = encoded
            position += len(encoded)
        self.library.write_bytes(data)

    def test_accepts_all_public_c_abi_exports(self):
        self.write_dll(self.exports)
        self.assertEqual(windows_exports(self.library), set(self.exports))
        validate_windows_exports(self.library)

    def test_rejects_a_missing_config_entry_point(self):
        self.write_dll([name for name in self.exports if not name.endswith("with_config")])
        with self.assertRaisesRegex(RuntimeError, "missing C ABI exports: rikkahub_mermaid_render_svg_with_config"):
            validate_windows_exports(self.library)

    def test_rejects_dll_with_only_unwinder_exports(self):
        self.write_dll(["_Unwind_Resume", "unw_getcontext"])
        # A byte-string search would incorrectly accept names outside the export table.
        with self.library.open("ab") as stream:
            stream.write("\0".join(self.exports).encode("ascii"))
        with self.assertRaisesRegex(RuntimeError, "missing C ABI exports"):
            validate_windows_exports(self.library)

    def test_rejects_absent_or_empty_export_tables(self):
        for export_table in (False, True):
            with self.subTest(export_table=export_table):
                self.write_dll([], export_table=export_table)
                self.assertEqual(windows_exports(self.library), set())
                with self.assertRaisesRegex(RuntimeError, "missing C ABI exports"):
                    validate_windows_exports(self.library)


if __name__ == "__main__":
    unittest.main()
