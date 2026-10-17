# Public 0.7.0-alpha.3 — P3 客户端设置发布准备

状态：2026-10-17，候选提交 `323aaf7cd0cecce4ff06d13fea7b29ac801e8062` 已通过 [PR #8](https://github.com/Araon7756/Sky-Signs-Public/pull/8) 合并为 `135badf461a064b3ce323e0ae9f026b87d4ac901`。尚未创建 alpha.3 标签或 Release；既有 Public alpha.2 保留原标签、附件和验证边界。

## 功能与来源

基于 Public main `f3401d35b07eb18a572dd1695a5a83d201ed77a5`，引入原 Private `afcefa99ee59673d1e8b648eb0f57211f54bc77f` 的 P3 客户端与测试源码、两份翻译，共 12 个路径，仅规范文本换行。版本推进为 `0.7.0-alpha.3`。公开法律说明、历史 MIT 边界、第三方通知、CI 与公开 noreply 身份规则保留，不导入私有历史或原始测试档案。

- 共用客户端风状态与会话身份检查，处理首快照、跨零点、暂停、重连与世界切换；不加入协议序号或新的同步策略。
- 原版风格设置页；默认未绑定的设置键可在原版控制设置绑定，气象站标题栏提供辅助入口。
- 降水倾斜开关及 0～100% 强度即时生效，关闭保留强度；完成／Esc 保存，失败可重试或明确不保存返回，未知键保留。
- Iris 光影开启或 API 查询失败时显示原因并停用倾斜控件，保留偏好；外部编辑 properties 文件仍需重启。
- 设置只影响当前客户端表现；没有环境风粒子、风声、新天气、新协议或存档结构变更。

## 构建与产物

本轮已从 Public 候选独立构建和编译测试源码（exit 0，35 秒），默认完整客户端回归 exit 0（2 分 55 秒，17 ready／4 epoch／24 张自动 PNG），P3 状态／配置、界面和模拟 Iris API 断言通过。实际发送集合每个 ready 为 329 个唯一区块，摘要、视距 8、块状态、中心和渲染检查通过；#49 固定补丁激活核对通过，原生方格等待未修复。没有重跑独立 P1_ONLY。

最终普通 JAR 在最小 Fabric API 隔离实例两次独立启动，中文 GUI2、1280×720 验证关闭保留 50%、完成保存 false/0.5、重启持久化、恢复默认及 Esc 保存 true/1.0，未知键保留；两次正常保存退出 0，3 张 F2 原图实际目视。使用 54 文件核验复制的测试世界，源世界保持不变，无测试模组／#49，不操作原玩家实例。

| 产物 | 字节 | SHA-256 |
| --- | --- | --- |
| skysigns-0.7.0-alpha.3.jar | 745608 | `e5d83192de25b3bf66b701fef4eca70dc6558d018ca22d4c47066f752623519d` |
| skysigns-0.7.0-alpha.3-sources.jar | 678454 | `63eba9ed38cad043630443d768c40445a7a465269008266a6afa1cf68b62976b` |

生产包 102 文件／52 类；52 类与已验收 P3 逐字节相同。8 份法律文件与实际 Public 构建输入逐字节一致，COPYRIGHT 的 Git LF／Windows 检出 CRLF 差异单独记录；不改法律内容。两个 JAR 不含测试模块。提交前源码、两个解包 JAR 和附件的四组 Gitleaks 8.30.1 非零扫描通过，34 个额外隐私规则命中已分类，无未解决项；原始日志、世界、启动参数和截图不公开。

PR #8 的 `Java 21 build`、`Gitleaks`、`Commit identity guard` 三项检查均通过。合并后 main 的构建与 Gitleaks 通过，但 [Commit identity guard 失败](https://github.com/Araon7756/Sky-Signs-Public/actions/runs/39007737898/job/117323544515)：合并命令未显式指定公开作者邮箱，GitHub 使用账号默认邮箱，导致合并提交不符合 noreply 规则；候选提交身份仍符合规则。该失败记录保留，不能写为主线 CI 全通过，也不能靠重跑相同范围令其通过。

后续公开 PR 的合并必须显式传入 `--author-email 291055905+Araon7756@users.noreply.github.com`，并继续以 `--match-head-commit <reviewed-sha>` 锁定候选。身份规则和旧历史保持原样；后续事件按真实 `base..head`／`before..after` 范围检查新增提交，不会改变本次失败结论。

实际命令（JDK 21.0.12.1，既有离线缓存）：

```powershell
.\gradlew.bat build compileGametestJava --offline --no-daemon --max-workers=4
.\gradlew.bat runClientGameTest build compileGametestJava --offline --no-daemon --max-workers=4 `
  -I scripts/inf01-isolated-run.gradle -Pinf01RunId=<fresh-run-id> `
  -I scripts/network-synchronizer-patch.gradle -PnetworkSynchronizerPatch=true `
  '-PnetworkPatchRoot=<candidate-root>'
```

测试运行时源码为 Public 基线加当时未提交差异；随后全部候选输入已提交为 `323aaf7cd0cecce4ff06d13fea7b29ac801e8062`。合并提交与该候选文件树完全一致，GitHub main 的 160 个文件 blob 已逐项核对；6 个本地附件保持已测字节。以上是来源等价核验，没有在合并提交上重新运行游戏。正式发布前仍需核对最终标签、产物来源和主线检查结果。README 保留已发布 alpha.2 下载，待 alpha.3 实际发布后再更新。

拟发布附件：普通 JAR、sources JAR、发布说明、测试报告、构建来源及 SHA256SUMS。发布按 Pre-release、非 Latest 处理；标签须指向最终包含候选的 Public 提交。

## 验证边界

Private P3 和今天 Private alpha.2 的已有回归与限定实机验证属于历史证据，不能直接算作 Public alpha.3 新实测。Private alpha.2 默认完整回归为 17 ready、4 epoch、24 张自动截图；普通包两次独立启动验证了中文 GUI2 设置保存、重开和恢复默认，共 3 张 F2 原图。历史真实 Iris／Photon 设置行为限于原 P3 候选。

完整 P1／GUI／语言／DPI／分辨率矩阵、旁白听感、全部文件权限／I/O 故障、旧客户端、所有旧档、多人／专服、专项性能、长期可靠性和本版真实光影矩阵仍未验证。Photon 雪可见度、动态反馈、历史黑屏根因及原生方格区块等待未修复。#49 只用于显式测试框架，不进入生产包；构建或 `test NO-SOURCE` 不等于游戏或多人通过。
