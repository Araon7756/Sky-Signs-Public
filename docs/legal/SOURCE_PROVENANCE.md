# Sky Signs 来源基线

历史审计记录日期：2026-10-03。该记录报告已识别证据和维护者确认，不保证所有内容完全原创，也不替代逐项第三方权利。

## 精确对象

- 初次主要审计对象：v0.6.0-beta.9，提交 5a05e4ae8880b2fdee7727f67a073f1237965ec7。
- 本迁移草案的独立源码副本基线：d6fef468a5f890e9879a3b533c0746c68ee6089e（审阅时 origin/main）。该基线仍为 MIT，不能代替 beta.9 的审计或发布来源。
- beta.9 的构建来源及正式 JAR 哈希见 [LICENSE_SCOPE.md](LICENSE_SCOPE.md) 及 Release 构建来源附件（原 Private 档案；不随此快照公开）。

## 人与工具

历史审计已由维护者确认相关 Git 作者身份属于同一维护者；具体作者别名及关联证据保留于 Private 档案，公开快照不列出这些个人身份关联。源码与图标由维护者作为个人项目生成，未代表公司、雇主或他人工作。

历史记录中 Codex 是 AI/工程工具署名，不据此把它认定为另一个真人贡献者。基于审计时所有本地引用可达的 104 个提交（beta.9 可达 75 个，main 可达 101 个），未识别其他真人贡献者；这不证明 Git 以外没有贡献、第三方表达或未获取的历史。

## AI 辅助内容

大量项目源码、测试、脚本和文档由维护者指示 Codex 辅助完成。已检查的项目实现未识别出应报告为独特第三方复制的具体来源；对于有工具与历史证据的相应内容，使用分类 **AI-assisted, no identified third-party source**。常规 Java/Fabric/Minecraft API 模式、标准 JSON 或通用算法不因相似而被认定为复制。

项目图标 src/main/resources/assets/skysigns/icon.png 有原始图像生成、未附第三方参考图及后续拷贝的工具记录；审计比对的图标 SHA-256 为 6e405084669c951cf5a81a49bad6888ef94107b477d64b9163f2c9ab9e3ccf23。它属于 AI-assisted, no identified third-party source，不再记录为来源未知。模型、语言文本与资源 JSON 有同一维护者/AI 辅助工作的修订记录；部分最早导入文件的完整导入前制作过程仍为 Unable to verify，后续逐文件来源链尚未全部重建。运行资源没有单独的原版纹理或音频文件，原版纹理由资源位置引用；不能据此承诺每个素材都具有可排他版权。

私人聊天和工具记录由维护者保存；本文件不发布私人会话、邮箱、凭据或账户信息，也不把这些私人记录称为人人可以公开核验的独立证据。未来新增素材应持续登记，无法确认的来源标为 Unable to verify。

## 已确认第三方材料

历史 FabricMC 诊断框架源码有 10 个路径、9 个不同文件，全部保留原版权头并逐字节匹配来源 source JAR；具体路径和许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。它们在 beta.9 后由提交 14456a4b0972f10b6bd36fd3a62bb372a217e64a 引入，只用于原 Private 诊断证据档案，不是项目原创建模或运行时实现。

2026-10-09 的独立同步器任务新增五份同来源 threading 源码归档，以及一份经既定 Yarn 命名映射、再修复锁内条件等待的 `NetworkSynchronizer.java`。后者是明确修改的 Apache-2.0 第三方实现，只供显式启用的临时测试模块使用；不属于项目原创实现，也不进入普通源集或生产 JAR。具体修改、散列和来源见 `test-infrastructure/fabric-client-gametest/provenance.json`、`atomic-wait.patch` 及第三方通知的对应新增段落。

Gradle Wrapper 文件属于第三方。Minecraft、Yarn、Fabric Loader、Fabric API、Fabric Loom 等是构建/运行依赖，调用接口不能等同于复制源码。上述第三方许可和游戏规则不会被新根 LICENSE 覆盖。

## 证据和限制

快照内可核验材料包括原文件版权头、Gradle 配置及上游正式文本；旧标签、提交和私人来源证据保留于原 Private 档案，不将它们称为公开可独立核验的证据。历史 MIT 范围、Apache 源码复制及 AI 输出的权利边界分别处理。

审计不覆盖不可达/已删除/未获取的 Git 对象、Git 之前全部创作过程、第三方所有私有作品、所有 AI 训练来源、所有依赖的传递闭包、全球法域版权成立问题。未获得的信息为 Unable to verify，不能用“未发现”证明绝无第三方来源。

## 2026-10-14 公开快照范围

本快照来自原 Private 开发仓库提交 `11b5cdbd119a55f071aac48464623e61575cb167`，不包含旧 Git 历史。上述 104 提交统计及 beta.9 来源审计仅是 2026-10-03 的历史范围，不能视为本次全部历史或新增内容的完整来源审计。本次逐文件核对快照、构建配置和第三方通知；没有重新重建每项素材制作链或扩大历史来源结论。

`0.7.0-alpha.2` 已于 2026-10-15 作为首个 Public 二进制 Pre-release 发布，标签为 `v0.7.0-alpha.2`，精确发行源码目标为 `e6d6076835e490653f492f50096eeb9149a17ae3`，不是最初 Private 文件快照提交，也不是发布后的 main 文档提交。发行来源、验证边界及历史 Private `v0.7.0-alpha.1` 的原发行记录见 [公开快照说明](../PUBLIC_SNAPSHOT.md)；历史 Private alpha.1 保持原样，不重新发布、不覆盖、不删除。原始私人聊天、旧 Git 提交身份、个人别名关联及测试归档未随初始文件快照迁移；这不表示后续 Public 提交的身份元数据已经由上述历史来源审计覆盖。图标与尚无法确认的来源仍保留上文 AI-assisted / Unable to verify 边界。

运行图标保留原 C2PA 生成来源元数据。已识别的 UUID 是生成清单／图像实例／证书标识，非玩家 UUID；元数据记录 ChatGPT/gpt-image 与 OpenAI 服务证书，没有识别出个人邮箱、用户名或私钥。本次未验证其密码学签名，不把元数据单独视为完整制作链证明。

## 2026-10-19 合成配方示意图

`docs/images/crafting-recipes.png` 由维护者指示 Codex 辅助，以本仓库四个配方 JSON 的 `pattern`、`key` 和 `result` 为输入，通过程序绘制文字、网格、色条及产出框。它是配方示意图，不是 Minecraft 游戏截图；没有引入第三方参考图片、原版物品纹理或游戏字体文件。使用本机系统字体绘制文字，未分发字体文件。该图记录为 **AI-assisted, no identified third-party source**，不据此承诺排他版权。配方与材料说明见 [生存合成指南](../CRAFTING.md)。
