import unittest

from build_native import host_directory, select_targets, validate_contract


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


if __name__ == "__main__":
    unittest.main()
