# Sky Signs 第三方通知

根 LICENSE 仅覆盖有权许可的项目内容。以下材料不被 PolyForm 或媒体额外许可重许可。依赖关系和实际源码拷贝分开列明；本文不是上游新增的 NOTICE，也不省略随来源提供的适用 NOTICE。

## FabricMC Apache-2.0 测试框架源码副本

版权所有：Copyright (c) 2016, 2017, 2018, 2019 FabricMC。具体年份及表述以文件保留的原版权头为准。

来源坐标：`net.fabricmc.fabric-api:fabric-client-gametest-api-v1:4.2.13+14b92d896f`，来自 [Fabric 官方 Maven source JAR](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-client-gametest-api-v1/4.2.13+14b92d896f/fabric-client-gametest-api-v1-4.2.13+14b92d896f-sources.jar)。source JAR SHA-256：`595a6b794090325a214ba8f28c6da2277f2bd7e0720de32fb54a4bb3ec67f8d2`。

此快照包含 [NetworkSynchronizer.java](../../test-infrastructure/fabric-client-gametest/net/fabricmc/fabric/impl/client/gametest/threading/NetworkSynchronizer.java)，是同来源经 Yarn 命名映射后的已修改副本：将等待条件移入锁内，以原 10 秒预算的条件循环修复丢失通知。Apache-2.0 原版权头和 test-framework source repair 修改说明保留。来源摘要和差异见 [provenance.json](../../test-infrastructure/fabric-client-gametest/provenance.json) 与 [atomic-wait.patch](../../test-infrastructure/fabric-client-gametest/atomic-wait.patch)。它继续适用 [Apache License 2.0](../../LICENSES/Apache-2.0.txt)，不转为 PolyForm。

只有显式加载 [network-synchronizer-patch.gradle](../../scripts/network-synchronizer-patch.gradle) 并启用该测试入口才使用副本；临时模块保留上游原条目和 LICENSE，仅替换同步器外部类并校验其他条目。普通 build 不加载此测试框架源码补丁；不得把临时测试模块 JAR 当作产品发布。

旧 Private 诊断归档另外保存过 2026-10-02 的 10 个路径／9 个不同文件及 2026-10-09 的五份 threading 源码。这些原件未随公开快照迁移，历史产物检查也仅适用于其当时对象。当前公开快照制品须独立核查，不能沿用历史 beta.9 或许可迁移候选的检查结果。

历史来源检查记录 source JAR 内有 LICENSE-fabric-client-gametest-api-v1、没有单独 NOTICE；不推定其他版本相同。更新或增加材料时仍需核查适用 NOTICE 并按 Apache 第 4 条保留通知。

## Gradle Wrapper

gradlew、gradlew.bat、gradle/wrapper/gradle-wrapper.jar 属于 Gradle 项目材料，按 Apache-2.0 处理。脚本保留原 Copyright © 2015 the original authors 等头部；实际声明以文件为准。Wrapper JAR SHA-256：423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9；本迁移不修改其字节、类或原通知。本次检查的 Wrapper JAR 内含 META-INF/LICENSE，没有单独 NOTICE，原嵌入许可继续保留。

Wrapper 指向 Gradle 9.2.0。完整 Gradle 分发包是构建依赖，不随本模组整体重新发布，其内其他依赖仍受各自许可和通知约束。[Gradle v9.2.0 LICENSE](https://github.com/gradle/gradle/blob/v9.2.0/LICENSE)；Apache-2.0 全文随本仓库保存。未核实完整 Gradle 分发包的全部适用通知，不以不存在的仓库根 NOTICE 链接代替核对。

## 主要构建及运行依赖

基线版本来自 gradle.properties、build.gradle 和 Wrapper 配置。除上列明确拷贝的框架源码及 Wrapper 外，本表记录依赖，不声明复制整个上游源码，也不将其称为由本模组拥有。

| 依赖 | 配置版本 | 上游许可/规则 | 本仓库关系 |
| --- | --- | --- | --- |
| Minecraft | 1.21.10 | Mojang/Microsoft 专有游戏规则 | 构建/运行依赖，不随本模组授予游戏本体权利 |
| Yarn mappings | 1.21.10+build.3 | CC0-1.0 | 构建映射依赖 |
| Fabric Loader | 0.19.5 | Apache-2.0 | 运行依赖 |
| Fabric API | 0.138.4+1.21.10 | Apache-2.0 | 模组依赖；上列诊断源码拷贝单独列明 |
| Fabric Loom | 1.14.10 | MIT | 构建插件依赖 |
| Gradle | 9.2.0 | Apache-2.0 及其分发包各组件规则 | 构建工具，Wrapper 材料另列 |

正式来源：[Yarn LICENSE](https://github.com/FabricMC/yarn/blob/1.21.10/LICENSE)、[Fabric Loader LICENSE](https://github.com/FabricMC/fabric-loader/blob/0.19.5/LICENSE)、[Fabric API LICENSE](https://github.com/FabricMC/fabric-api/blob/0.138.4%2B1.21.10/LICENSE)、[Fabric Loom 1.14 分支的许可记录](https://github.com/FabricMC/fabric-loom/blob/9e3837eedd03935321f4ee99aeb1ac481b76d846/LICENSE)。本表不声称已完成每个传递依赖版本及发布包的独立完整审计；Loom 1.14.10 发布源码与该许可记录的精确对应关系为 Unable to verify。Mixin、MixinExtras 等通过依赖链获得的组件继续依各自随附许可处理。

build.gradle 未声明 include/shading 把这些依赖整体嵌入运行时 JAR。若未来打包第三方库、复制代码或资源，应重新登记具体版本、路径、许可证和适用 NOTICE，不能只沿用“仅依赖”结论。

## Minecraft 与其他素材

使用 Minecraft 资源位置、API、方块名或数据格式，不等同于复制其原版纹理、模型、音频。审计未识别需要报告为直接提取原版资产的项目资源；这不是对所有历史、所有素材或媒体音乐的无侵权保证。

Minecraft 游戏代码、原版资源、Mojang/Microsoft 标识及商标不被 Sky Signs 的任何许可覆盖。[Minecraft EULA](https://www.minecraft.net/en-us/eula) 与 [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) 独立适用。媒体额外许可只提供 Sky Signs 一侧可授的权利。

本快照 docs/images/ 中的项目展示截图是项目采集的图像，但画面包含 Minecraft、可能的其他模组、字体和界面等第三方表现；第一方采集不等于拥有全部画面版权。诊断日志、堆栈和依赖清单也包含第三方生成的文本、类名及输出。根许可不取得这些第三方表达的独占权利；历史证据内的 MIT 或上游许可说明作为当时记录保留，不回溯改写。

## 历史 MIT

[LICENSES/MIT-legacy.txt](../../LICENSES/MIT-legacy.txt) 保存旧根许可证，旧版边界见 [LICENSE_SCOPE.md](LICENSE_SCOPE.md)。此历史文本不是把未来全部新增内容授予 MIT；新根许可也不撤回已经授予的旧 MIT 权利。
