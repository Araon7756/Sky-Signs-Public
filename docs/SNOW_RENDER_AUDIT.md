> 公开快照说明：本页保留历史设计、候选或验收记录；日期、提交和当时的“尚未发布”等状态仅适用于对应历史阶段。当前发行事实与公开快照验证见 [PUBLIC_SNAPSHOT.md](PUBLIC_SNAPSHOT.md)。原始证据、旧 PR/Issue 和提交历史保留于 Private 档案，未随快照迁移。

# 降水渲染路径与雪花反馈对应关系：Cloud 只读核查

结论：**最小环境可复现、根因未定位；本轮远窗口雪花可见度降低与原始“遮挡不正常”反馈的同一性未验证。**

核对日期：2026-10-10。本报告通过独立文档 PR 共享，原始 Local 证据及散列清单不改写。

## 核查来源

Cloud 已成功 fetch；核查时 main 为 `28dbe0cf4da10bd9f039094b7ce1d0c160cd6ea8`，即 PR #53 的合并提交。读取此提交中的 `Private 证据档案中的对应材料`。它仍固定实际 Local 被测提交为 `3c60a8b3a2e378b1076c0a6a89335de5c839433b`；合并文档／证据没有新增实机运行。

核查 main 与 PR #53 原 head `0c684d7428c213fd68ccb7e12f338d7879ff216a` 的 Git tree 均为 `79c9a8dc3186fee88c09f34641b6903f585b7664`。74 个生产 main/client 和 Gradle 输入逐项与上轮被测源码摘要一致，降水渲染路径没有改变。回交文件中“从 PR 分支获取”的措辞来自合入前；本次按用户指示从核查 main 取得相同共享字节。

## 证据验证

原始来源：Cloud 回交范围（原始证据保留于 Private 档案）、七组 Local 报告（原始证据保留于 Private 档案）、来源映射（原始证据保留于 Private 档案）和 SHA256SUMS（原始证据保留于 Private 档案）。

从核查 main 导出证据，运行其未改动的只读 `verify.cjs`：230 项清单文件、63 张原图及七组既有运行记录通过，清单摘要仍为 `a2a4b80be0b1531d5b7806b84809a85cfb902a11a6bedffd2355d90f76d18f47`。另用 Python/Git 逐项比对全部 231 个 Git 证据文件（包括清单自身）与导出字节，结果一致。

验证器的 `--git-ref` 模式尝试因 Cloud 环境 Node `spawnSync git` 报 `EPERM` 未完成；未修改验证器，也未将此模式写为通过。改用 working-tree 模式与独立 Python/Git 逐字节比较完成检查，包含清单自身的全部 231 个 Git 文件与导出字节一致。230 与 231 是是否包含清单自身的区别。共享核查收据（原始审计收据保留于 Private 档案） 保留执行模式及状态；本次文件检查不等于新的 Minecraft 运行或视觉问题通过。

## 降水渲染介入路径

- 生产客户端唯一 Mixin 为 `WeatherRenderingMixin`，在原版`WeatherRendering.renderPieces`中，对`VertexConsumer.vertex(FFF)`的ordinal 0/1、index 0/2 作四个 `ModifyArg`。这对应上沿两个顶点的 X/Z，雨和雪共用此路径；不改 Y、UV、颜色、光照或渲染状态。Cloud 使用已有 Minecraft 1.21.10／Yarn build.3 named 缓存执行只读 `javap -p -c`，确认雨和雪列表共用此方法，前两次 vertex 使用 topY。缓存条目 `net/minecraft/client/render/WeatherRendering.class` SHA-256 为 `5bc4133002ac48fd4370435026b349bde937e547c91a7d2053fc2184a0a2f417`；不共享 Minecraft 源码或 class，也不把原版字节码参考称为 Iris 变换后的运行时观测。
- `WindPrecipitationVisual` 从 `WindVisualPayload` 接收风况并平滑顶点偏移。`topOffsetX/Z` 在 `enabled && !shaderPackActive` 时才产生偏移；Iris API 报告光影启用后返回零。反射／链接失败时保守设置 `shaderPackActive=true`并记录警告。
- `SkySignsClient` 在 `END_CLIENT_TICK` 调用 tick 更新 Iris 状态。Mixin 仍注册，不是撤销注入；API 实际返回、`shaderPackActive` 及顶点偏移的逐帧值没有本轮记录。静态绕过不扩大为每个渲染帧已观测为零。
- 其他生产介入为服务端天气计划/时长及风包输入。未发现此提交中自定义深度测试／写入、framebuffer、混合、雾、雪透明度、额外雪粒子、天气 shader或原版雨雪贴图覆盖。源码阴性结果不排除第三方变换、间接天气输入或所有模组交互。

精确源码依据：

- WeatherRenderingMixin（原 Private 档案；不随此快照公开）
- WindPrecipitationVisual偏移与Iris检查（原 Private 档案；不随此快照公开）
- 客户端 tick 注册（原 Private 档案；不随此快照公开）
- 风况接收与倾斜输入（原 Private 档案；不随此快照公开）
- 服务端天气注入（原 Private 档案；不随此快照公开）
- 原版天气写入（原 Private 档案；不随此快照公开）
- 风况包发送（原 Private 档案；不随此快照公开）

## 可见度降低与遮挡关系错误的区别

**可见度降低**是画面中雪花更少或更难辨认的观察结果。**遮挡关系错误**需要明确具体雪花相对遮挡物的预期前后关系，以及实际可见／被遮挡的关系是否违反预期。前者本身不能证明后者。

以下结论分别记账；不能用新窗口可见度结果替代原始遮挡反馈复现。

| 判断 | 状态与依据 |
| --- | --- |
| Photon 开启时降雪显示发生明显差异 | 已确认于本轮窗口场景。开前/再开远窗口雪几乎不可见，关闭后明显可见；近窗口开启时上方仍可见雪。先前 Cloud查看过最小组远窗口三张原图及两张综合图；本次共享字节不变，没有冒称重新目视全部 63 张。 |
| 与原反馈处于相同问题范围 | 可作为相关对照：都涉及Photon 开启时降雪显示。但相同描述范围不证明同一几何现象或机制。 |
| 原反馈中的遮挡前后关系错误已复现 | 未验证。本轮主要结果是窗口画面中的雪花可见度降低，并未确认哪些雪花应在遮挡物前/后或被错误遮挡；有限实心墙/手持物场景的Local 记录也没有明确穿墙/覆盖。 |
| 原始山体反馈与窗口可见度降低完全同一 | 未验证。共享材料没有原始山体场景的匹配相机/遮挡边界对照，也未明确原反馈的具体期望-实际关系。不能用新窗口结果代替原反馈复现或关闭原反馈。 |
| Sky Signs 是否为此最小复现的必要条件 | 不成立为必要条件判断：无 Sky Signs 最小 Fabric API／Sodium／Iris／Photon 组仍复现。但不能推导所有降雪遮挡问题均与 Sky Signs 无关。 |
| Photon／Iris／Sodium／驱动或深度机制归因 | 未定位。它们仍同时存在，只有参数/加模组对照，没有完整渲染路径或GPU 机制证据。 |

“窗口内”在本记录中指画面窗口区域：实际观察的是窗口外的降雪，而非确认屋内生成雪粒子。可见度降低可以作为遮挡反馈调查的候选对应现象，但证据尚不足以区分几何遮挡、透明度/雾/背景对比或其他路径。不得写成深度缓冲根因已确认。

下一步如需确认同一性，应由 Local在原始山体场景的隔离副本中固定相机与天气，记录哪些位置预期可见/应遮挡，再进行相同开→关→开采集；把它与远/近窗口对照并列。再分别控制兼容 Photon 版本、其他光影包及Iris／Sodium 版本，必要时在独立诊断实例获取实际渲染状态。仅提出范围，本次不执行实验、不改变代码。

本次核查及文档共享未运行构建、GameTest、Minecraft 或性能测试，未改候选源码、配置或玩家文件，不合并、不发布、不关闭 Issue 或修改 Project。既有失败／复现与未验证项保持。

## Local 同步与只读复核

本报告和核查收据已纳入独立文档 PR；合并前应 fetch 该 PR 分支，在独立、干净工作树中核对 PR 完整 head。不要依赖 Cloud 的忽略目录。遵守 AGENTS.md 的工作区保护规则。

```sh
# 原证据校验：不启动游戏。
node Private 证据档案中的对应材料

# 被测来源与 Cloud 核查 main 的生产及构建输入差异，预期无输出。
git diff --name-only 3c60a8b3a2e378b1076c0a6a89335de5c839433b \
  28dbe0cf4da10bd9f039094b7ce1d0c160cd6ea8 \
  -- src/main src/client build.gradle gradle.properties
```
