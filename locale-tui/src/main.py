#!/usr/bin/env python3
"""Android Locale Manager TUI Application."""

import sys
import asyncio
from pathlib import Path

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent))

import click
from config import Config
from app import LocaleTuiApp
from services.xml_parser import StringsXmlParser
from services.translator import AITranslator
from services.resource_inventory import ResourceInventoryError, ResourceInventoryService
from services.resource_compatibility_overlay import (
    ResourceCompatibilityOverlayError,
    ResourceCompatibilityOverlayService,
)
from services.kotlin_resource_migrator import KotlinResourceCallMigrator
from models.entry import TranslationEntry

TOOL_ROOT = Path(__file__).parent.parent
DEFAULT_MIGRATION_MAP = TOOL_ROOT / "resource-migration.yml"
DEFAULT_RESOURCE_BASELINE = TOOL_ROOT / "baselines" / "compose-resources-v1.json"


def load_config(*, warn_missing_api_key: bool = True) -> Config:
    """Load configuration from file."""
    config_path = Path(__file__).parent.parent / "config.yml"

    if not config_path.exists():
        click.echo(f"错误：未找到配置文件 {config_path}", err=True)
        click.echo("请基于模板创建 config.yml 文件。", err=True)
        sys.exit(1)

    try:
        config = Config.load(config_path)
    except Exception as e:
        click.echo(f"错误：加载配置失败 - {e}", err=True)
        sys.exit(1)

    # Validate configuration
    if warn_missing_api_key and not config.openai_api_key:
        click.echo("警告：未设置 OPENAI_API_KEY。AI 翻译功能将无法使用。", err=True)

    return config


@click.group(invoke_without_command=True)
@click.pass_context
def cli(ctx):
    """Android Locale Manager - 管理和翻译 Android 字符串资源

    不带参数启动 TUI 界面，使用子命令进行命令行操作。
    """
    if ctx.invoked_subcommand is None:
        # No command provided, launch TUI
        config = load_config()
        app = LocaleTuiApp(config)
        app.run()


@cli.command("test-connection")
def test_connection():
    """测试 AI 服务连接

    \b
    示例：
        locale-tui test-connection
    """
    config = load_config()

    if not config.openai_api_key:
        click.echo("错误：未设置 OPENAI_API_KEY，无法测试连接。", err=True)
        sys.exit(1)

    click.echo("AI 服务配置：")
    click.echo(f"  Base URL: {config.openai_base_url}")
    click.echo(f"  Model: {config.translation_model}")
    click.echo("正在测试连接...")

    async def test_async():
        translator = AITranslator(config)
        return await translator.test_connection()

    try:
        content = asyncio.run(test_async())
        click.echo("✓ 连接成功")
        if content:
            click.echo(f"响应: {content}")
        else:
            click.echo("响应为空，但 API 已返回有效结果。")
    except Exception as e:
        click.echo(f"✗ 连接失败: {e}", err=True)
        sys.exit(1)


@cli.command()
@click.argument("key")
@click.argument("value")
@click.option(
    "--module",
    "-m",
    default=None,
    help="模块名称（默认使用配置文件中的第一个模块）",
)
@click.option("--skip-translate", is_flag=True, help="跳过自动翻译，仅添加源语言条目")
def add(key: str, value: str, module: str, skip_translate: bool):
    """添加新的语言条目并自动翻译

    \b
    示例：
        locale-tui add hello_world "Hello, World!"
        locale-tui add greeting "Welcome" -m app
        locale-tui add test_key "Test" --skip-translate
    """
    config = load_config()

    # Select module
    if module:
        selected_module = next((m for m in config.modules if m.name == module), None)
        if not selected_module:
            click.echo(f"错误：未找到模块 '{module}'", err=True)
            click.echo(f"可用模块：{', '.join(m.name for m in config.modules)}", err=True)
            sys.exit(1)
    else:
        if not config.modules:
            click.echo("错误：配置文件中未定义模块", err=True)
            sys.exit(1)
        selected_module = config.modules[0]

    click.echo(f"使用模块: {selected_module.name}")

    # Get source language
    source_lang = config.get_source_language()
    if not source_lang:
        click.echo("错误：未配置源语言", err=True)
        sys.exit(1)

    # Resolve res directory
    res_dir = config.project_root / selected_module.res_path
    if not res_dir.exists():
        click.echo(f"错误：资源目录不存在 {res_dir}", err=True)
        sys.exit(1)

    # Add entry to source language file
    source_file = res_dir / "values" / "strings.xml"
    click.echo(f"添加条目到 {source_file.relative_to(config.project_root)}...")

    try:
        StringsXmlParser.update_entry(source_file, key, value)
        click.echo(f"✓ 已添加条目: {key} = {value}")
    except Exception as e:
        click.echo(f"错误：添加条目失败 - {e}", err=True)
        sys.exit(1)

    # Translate to other languages
    if not skip_translate:
        target_languages = [lang.code for lang in config.languages if not lang.is_source]

        if not target_languages:
            click.echo("未配置目标语言，跳过翻译。")
            return

        click.echo(f"开始翻译到 {len(target_languages)} 种语言...")

        # Create entry for translation
        entry = TranslationEntry(key=key, translations={"values": value})

        async def translate_async():
            translator = AITranslator(config)

            async def translate_one(lang_code: str):
                lang_name = config.get_language_name(lang_code)

                try:
                    translations = await translator.translate_batch(
                        {key: value}, lang_name
                    )

                    if key in translations:
                        return lang_code, lang_name, translations[key], None
                    return lang_code, lang_name, None, "翻译失败（未返回结果）"
                except Exception as e:
                    return lang_code, lang_name, None, str(e)

            tasks = [translate_one(lang_code) for lang_code in target_languages]
            results = await asyncio.gather(*tasks)

            for lang_code, lang_name, translated_value, error in results:
                click.echo(f"翻译到 {lang_name}...", nl=False)

                if error:
                    click.echo(f" ✗ 错误: {error}", err=True)
                    continue

                entry.set_translation(lang_code, translated_value)

                # Save to file
                target_file = res_dir / lang_code / "strings.xml"
                StringsXmlParser.update_entry(target_file, key, translated_value)

                click.echo(f" ✓ {translated_value}")

        asyncio.run(translate_async())
        click.echo("完成！")


@cli.command()
@click.argument("key")
@click.argument("value")
@click.option(
    "--lang",
    "-l",
    default=None,
    help="语言代码（例如：values, values-zh, values-ja），默认为源语言",
)
@click.option(
    "--module",
    "-m",
    default=None,
    help="模块名称（默认使用配置文件中的第一个模块）",
)
def set(key: str, value: str, lang: str, module: str):
    """手动设置指定语言的条目值

    \b
    示例：
        locale-tui set hello_world "你好，世界！" -l values-zh
        locale-tui set greeting "Welcome" -l values
        locale-tui set test_key "テスト" -l values-ja -m app
    """
    config = load_config()

    # Select module
    if module:
        selected_module = next((m for m in config.modules if m.name == module), None)
        if not selected_module:
            click.echo(f"错误：未找到模块 '{module}'", err=True)
            click.echo(f"可用模块：{', '.join(m.name for m in config.modules)}", err=True)
            sys.exit(1)
    else:
        if not config.modules:
            click.echo("错误：配置文件中未定义模块", err=True)
            sys.exit(1)
        selected_module = config.modules[0]

    # Resolve language directory
    if lang is None:
        lang = "values"  # Default to source language

    # Resolve res directory
    res_dir = config.project_root / selected_module.res_path
    if not res_dir.exists():
        click.echo(f"错误：资源目录不存在 {res_dir}", err=True)
        sys.exit(1)

    # Target file
    target_file = res_dir / lang / "strings.xml"
    lang_name = config.get_language_name(lang) if lang != "values" else "源语言"

    click.echo(f"设置 {lang_name} 的条目: {key} = {value}")
    click.echo(f"目标文件: {target_file.relative_to(config.project_root)}")

    try:
        StringsXmlParser.update_entry(target_file, key, value)
        click.echo(f"✓ 设置成功")
    except Exception as e:
        click.echo(f"错误：设置失败 - {e}", err=True)
        sys.exit(1)


@cli.command()
@click.option(
    "--module",
    "-m",
    default=None,
    help="模块名称（默认使用配置文件中的第一个模块）",
)
def list_keys(module: str):
    """列出所有语言条目的键

    \b
    示例：
        locale-tui list-keys
        locale-tui list-keys -m app
    """
    config = load_config()

    # Select module
    if module:
        selected_module = next((m for m in config.modules if m.name == module), None)
        if not selected_module:
            click.echo(f"错误：未找到模块 '{module}'", err=True)
            sys.exit(1)
    else:
        if not config.modules:
            click.echo("错误：配置文件中未定义模块", err=True)
            sys.exit(1)
        selected_module = config.modules[0]

    # Resolve res directory
    res_dir = config.project_root / selected_module.res_path
    source_file = res_dir / "values" / "strings.xml"

    if not source_file.exists():
        click.echo(f"错误：源文件不存在 {source_file}", err=True)
        sys.exit(1)

    # Parse and display
    entries = StringsXmlParser.parse(source_file)

    click.echo(f"模块 '{selected_module.name}' 共有 {len(entries)} 个条目：")
    click.echo()

    for key in sorted(entries.keys()):
        value = entries[key]
        # Truncate long values
        if len(value) > 60:
            value = value[:57] + "..."
        click.echo(f"  {key:40} {value}")


@cli.command("resource-snapshot")
@click.option(
    "--migration-map",
    type=click.Path(path_type=Path, dir_okay=False),
    default=DEFAULT_MIGRATION_MAP,
    show_default=True,
)
@click.option(
    "--output",
    type=click.Path(path_type=Path, dir_okay=False),
    default=DEFAULT_RESOURCE_BASELINE,
    show_default=True,
)
def resource_snapshot(migration_map: Path, output: Path):
    """生成确定性的 Compose Resources 迁移前清单。"""
    config = load_config(warn_missing_api_key=False)
    try:
        service = ResourceInventoryService.from_config(config, migration_map)
        snapshot = service.snapshot()
        service.write_snapshot(snapshot, output)
    except ResourceInventoryError as error:
        raise click.ClickException(str(error)) from error

    summary = snapshot["summary"]
    click.echo(
        f"✓ 已生成 {output}: {summary['locale_file_count']} 个 locale 文件, "
        f"{sum(summary['resource_counts'].values())} 个值资源, "
        f"{summary['binary_resource_count']} 个文件资源"
    )


@cli.command("resource-verify")
@click.option(
    "--migration-map",
    type=click.Path(path_type=Path, dir_okay=False),
    default=DEFAULT_MIGRATION_MAP,
    show_default=True,
)
@click.option(
    "--baseline",
    type=click.Path(path_type=Path, dir_okay=False),
    default=DEFAULT_RESOURCE_BASELINE,
    show_default=True,
)
def resource_verify(migration_map: Path, baseline: Path):
    """验证当前资源与已提交的迁移前清单完全一致。"""
    config = load_config(warn_missing_api_key=False)
    try:
        service = ResourceInventoryService.from_config(config, migration_map)
        summary = service.verify(baseline)
    except ResourceInventoryError as error:
        raise click.ClickException(str(error)) from error

    click.echo(
        f"✓ 资源清单验证通过: {summary['locale_file_count']} 个 locale 文件, "
        f"{sum(summary['resource_counts'].values())} 个值资源, "
        f"{summary['binary_resource_count']} 个文件资源"
    )


@cli.command("resource-overlay-sync")
@click.option(
    "--migration-map",
    type=click.Path(path_type=Path, dir_okay=False),
    default=DEFAULT_MIGRATION_MAP,
    show_default=True,
)
@click.option(
    "--project-root",
    type=click.Path(path_type=Path, file_okay=False),
    default=None,
    help="项目根目录（默认读取 locale-tui 配置）。",
)
@click.option(
    "--mode",
    type=click.Choice(["dry-run", "check", "apply"], case_sensitive=True),
    default="dry-run",
    show_default=True,
    help="预览差异、CI 检查或写入兼容覆盖文件。",
)
@click.pass_context
def resource_overlay_sync(
    ctx: click.Context,
    migration_map: Path,
    project_root: Path | None,
    mode: str,
):
    """从声明的源语言确定性同步 Compose locale 兼容覆盖层。"""
    if project_root is None:
        project_root = load_config(warn_missing_api_key=False).project_root
    elif not project_root.is_absolute():
        project_root = Path.cwd() / project_root

    try:
        service = ResourceCompatibilityOverlayService(project_root, migration_map)
        report = service.sync(mode=mode)
    except (ResourceCompatibilityOverlayError, ValueError) as error:
        raise click.ClickException(str(error)) from error

    if mode == "dry-run":
        if report.files_changed:
            click.echo(report.unified_diff(service.project_root), nl=False)
        click.echo(
            f"预览完成：扫描 {report.overlays_scanned} 个兼容覆盖层，"
            f"需要同步 {report.files_changed} 个。"
        )
        return

    if mode == "check":
        if report.files_changed:
            click.echo(
                f"需要同步 {report.files_changed} 个兼容覆盖层；"
                "请使用 --mode apply 写入结果。",
                err=True,
            )
            ctx.exit(1)
        click.echo(f"✓ {report.overlays_scanned} 个兼容覆盖层均为最新。")
        return

    click.echo(f"✓ 已同步 {report.files_changed} 个兼容覆盖层。")


@cli.command("migrate-kotlin-resource-calls")
@click.option(
    "--source-root",
    type=click.Path(path_type=Path, file_okay=False),
    default=None,
    help="Kotlin 源码根目录（默认 app/src/main/java）。",
)
@click.option(
    "--mode",
    type=click.Choice(["dry-run", "check", "apply"], case_sensitive=True),
    default="dry-run",
    show_default=True,
    help="预览差异、CI 检查或写入迁移结果。",
)
@click.pass_context
def migrate_kotlin_resource_calls(
    ctx: click.Context,
    source_root: Path | None,
    mode: str,
):
    """迁移 app 中 Compose stringResource 的 Android R 调用。"""
    if source_root is None:
        config = load_config(warn_missing_api_key=False)
        source_root = config.project_root / "app/src/main/java"
    elif not source_root.is_absolute():
        source_root = Path.cwd() / source_root

    migrator = KotlinResourceCallMigrator()
    try:
        report = migrator.migrate_tree(source_root, mode=mode)
    except ValueError as error:
        raise click.ClickException(str(error)) from error

    if mode == "dry-run":
        if report.files_changed:
            click.echo(report.unified_diff(source_root), nl=False)
        click.echo(
            f"预览完成：扫描 {report.files_scanned} 个 Kotlin 文件，"
            f"需要迁移 {report.files_changed} 个。"
        )
        return

    if mode == "check":
        if report.files_changed:
            click.echo(
                f"需要迁移 {report.files_changed} 个 Kotlin 文件；"
                "请使用 --mode apply 写入结果。",
                err=True,
            )
            ctx.exit(1)
        click.echo(f"✓ {report.files_scanned} 个 Kotlin 文件无需迁移。")
        return

    click.echo(
        f"✓ 已迁移 {report.files_changed} 个 Kotlin 文件："
        f"{report.app_reference_count} 个 app 字符串引用，"
        f"{report.android_call_count} 个 Android 系统字符串调用。"
    )


def main():
    """Main entry point."""
    cli()


if __name__ == "__main__":
    main()
