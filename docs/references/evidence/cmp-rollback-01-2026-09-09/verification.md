# 第 01 项回退验证：ConversationEntityMapper

日期：2026-09-09。生产代码起点：`006ee55dbbcd0366b490c774b7c8e195ba7043bd`。
原始行为基线：tag `2.4.5`（`5f39f1c1d2298cd88ce908a5a0858d4830885a6b`）。

本项只改动一个生产文件：`composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/repository/ConversationRepository.kt`。
删除一个 mapper 类、一个属性和三个转发；将三个方法体放回原位置。生产文件净减少 15 行。
测试使用真实 Room 内存数据库、BundledSQLiteDriver、现有 DAO 和 FTS，不增加任何生产测试接口。

## 运行结果

先添加七项 Repository 契约测试，在**未回退生产代码**时运行：

```sh
./gradlew :composeApp:jvmTest \
  --tests me.rerere.rikkahub.data.repository.ConversationRepositoryPersistenceTest \
  --console=plain
```

结果：退出码 0，`BUILD SUCCESSFUL in 16s`，七项均通过。

方法归位后，运行相同测试所在模块的完整 JVM 测试和受影响目标编译：

```sh
./gradlew :composeApp:jvmTest \
  :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileKotlinIosArm64 \
  :composeApp:compileKotlinIosSimulatorArm64 \
  :app:compileDebugKotlin --console=plain
```

结果：退出码 0，`BUILD SUCCESSFUL in 42s`。

| 验证 | 实际结果 |
|---|---|
| 新增 ConversationRepositoryPersistenceTest | 7 tests，0 failures，0 errors，0 skipped |
| composeApp 完整 jvmTest | 27 个测试类，180 tests，0 failures，0 errors，0 skipped |
| common metadata | compileCommonMainKotlinMetadata 成功 |
| JVM 生产代码 | compileKotlinJvm 成功，由 jvmTest 依赖执行 |
| Android 共享代码和 app | compileAndroidMain、app:compileDebugKotlin 成功 |
| iOS 真机目标 | compileKotlinIosArm64 成功 |
| iOS 模拟器目标 | compileKotlinIosSimulatorArm64 成功 |

计数来自 `composeApp/build/test-results/jvmTest/TEST-*.xml`，新增测试 XML 时间戳为
`2026-09-09T14:07:53.308Z`。本项没有运行 iOS/Android 数据库集成测试；其结果只有上述编译通过。
编译仍有弃用 API、experimental API、未解析 opt-in marker 等告警，本项没有顺带修改相关代码。

七项测试逐一覆盖：全字段和分支插入/更新、手工旧格式记录和空节点、null/空串编码、epoch 前与纳秒时间的
毫秒存储、Base64 插入/更新拒绝且原数据不变、非法 JSON 异常传播、分页/搜索/文件夹摘要。
具体输入与预期见 `ConversationRepositoryPersistenceTest.kt` 及分项回退计划中的场景表。

## 与 tag 的源码比较

除测试外，还对三个方法完整文本做精确比较，允许的替换仅为：

- `.toEpochMilli()` → `.toEpochMilliseconds()`。
- `Instant.ofEpochMilli(` → `Instant.fromEpochMilliseconds(`。

结果：`conversationToConversationEntity`、`conversationEntityToConversation`、
`conversationSummaryToConversation` 三者全部一致；没有仅按去空白后的相似度推断等价。
移除 mapper/属性并替换这三个方法后，文件其余内容与回退起点字节一致。
生产/测试 source set 中 `ConversationEntityMapper` / `entityMapper` 引用为 0。

可在仓库根目录复核：

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess

path = Path('composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/repository/ConversationRepository.kt')
current = path.read_text()
baseline = subprocess.check_output([
    'git', 'show',
    '2.4.5:app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt',
], text=True)
before = subprocess.check_output([
    'git', 'show', '006ee55dbbcd0366b490c774b7c8e195ba7043bd:' + str(path),
], text=True)

def method(source, name):
    pos = source.index('fun ' + name + '(')
    start = source.rfind('\n', 0, pos) + 1
    end = source.index('\n    }\n', pos) + len('\n    }\n')
    return source[start:end]

names = ['conversationToConversationEntity', 'conversationEntityToConversation',
         'conversationSummaryToConversation']
for name in names:
    expected = method(baseline, name)
    expected = expected.replace('.toEpochMilli()', '.toEpochMilliseconds()')
    expected = expected.replace('Instant.ofEpochMilli(', 'Instant.fromEpochMilliseconds(')
    assert method(current, name) == expected, name
    print(name, 'PASS')

rest = before.replace('    private val entityMapper = ConversationEntityMapper()\n\n', '', 1)
start = rest.index('internal class ConversationEntityMapper {')
end = rest.index('data class ConversationPageResult(', start)
rest = rest[:start] + rest[end:]
for name in names:
    rest = rest.replace(method(rest, name), method(current, name), 1)
assert rest == current
print('Unchanged remainder PASS')
PY
```

## GUI 判断与清理

本项免 GUI：纯转换方法原样归位，Repository 构造、调用方、DAO SQL/schema、实际文件操作、数据库平台构建、
UI 状态/导航、资源与生命周期均未修改；代码测试覆盖移动逻辑，编译覆盖共享方法的受影响目标。
因此本项未启动 GUI 子 agent、未操作真实用户会话、未读取钥匙串或发送真实模型请求。
本结论不覆盖其余审计项，尤其不代表 SharedChatRuntime 的已知行为分叉已经修复。

未加入生产临时日志或测试开关。运行时数据库为内存库，每个测试后关闭。
原始 Gradle 输出仅用于本轮核对，摘要形成后移除临时日志；保留本记录和永久回归测试。
提交前检查 `git diff --check`，暂存范围明确排除用户既有的两项 iOS Xcode 工程配置改动。
