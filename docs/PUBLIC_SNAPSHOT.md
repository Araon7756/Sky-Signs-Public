# 公开源码快照与历史发行边界

首次公开日期：2026-10-14。来源为原 Private 开发仓库提交 `11b5cdbd119a55f071aac48464623e61575cb167`。这是审核后的文件快照，不导入旧 `.git`、分支、标签、提交身份、Issue/PR、Release 或原始证据档案。本仓库为经审核后建立的独立 Public 源码仓库；原 Private 开发仓库继续保存完整历史和原始证据。

## 当前 Public 发行状态

`0.7.0-alpha.2` 已于 2026-10-15 作为首个 Public 二进制 Pre-release 发布，标签为 [`v0.7.0-alpha.2`](https://github.com/Araon7756/Sky-Signs-Public/releases/tag/v0.7.0-alpha.2)。该发行的精确源码目标为 [`e6d6076835e490653f492f50096eeb9149a17ae3`](https://github.com/Araon7756/Sky-Signs-Public/commit/e6d6076835e490653f492f50096eeb9149a17ae3)，不是发布后的 `main` 文档提交。历史 Private `v0.7.0-alpha.1` 保持原样，不重新发布、不覆盖、不删除；不将不同字节的新构建作为同版本历史包发布。

alpha.2 准备阶段已比较历史 Private alpha.1 验收构建 `1cf474050620deefa9719c1eccfa9574f85a04ad` 与版本推进基线的 32 份 main/client Java 文件，Git blob 全部一致。该版本推进未修改生产 Java 或依赖。alpha.2 相较历史 Private alpha.1 没有计划中的运行时功能变更，版本推进用于建立独立、可验证的 Public 发行来源并同步公开法律/来源文档。

该发行从上述精确 Public 提交重新构建、审计，并使用最终普通 JAR 完成隔离单人客户端 smoke test：启动至主菜单、模组加载、进入测试世界、status／forecast 命令、四种仪表放置、气象站界面、一次原版转雨及正常保存退出。构建来源与限定验证结果分别见发行附件 [BUILD_PROVENANCE.md](https://github.com/Araon7756/Sky-Signs-Public/releases/download/v0.7.0-alpha.2/BUILD_PROVENANCE.md) 和 [TEST_REPORT.md](https://github.com/Araon7756/Sky-Signs-Public/releases/download/v0.7.0-alpha.2/TEST_REPORT.md)。这不是完整 P1、多人、专服、光影、性能或长期可靠性验收；`test NO-SOURCE` 不代表功能测试通过。

旧 alpha.1 Public 候选只作为历史审核成果，未改名复用。发布后的 README、版权链接或其他文档修订不改变已发布附件，也不能冒充该发行的构建输入；新构建须另行记录精确提交与摘要。

## 历史 Private 发行事实与来源

2026-10-14 通过 GitHub API 只读核验原发行 `v0.7.0-alpha.1` 于 2026-10-13 发布。附注标签目标为 `b2e9238259b71b0243b659144e5cb4b746ab970c`，标签说明引用 Local 验收构建 `1cf474050620deefa9719c1eccfa9574f85a04ad`。GitHub 记录普通 JAR 为 729609 字节，摘要为 `4f11d32dd5d23365306456566d05e3785b27458f0543be3d9d1501e46829076a`。原 Private 发行入口不作为公开下载链接提供；未迁移或修改附件。此处核验附件元数据与既有来源记录，没有重新下载并审计原发行二进制。

新快照的构建包含修正后的法律说明，结果应使用本次独立摘要，不能冒充旧发行字节。原许可正文、COPYRIGHT、历史 MIT、Apache-2.0 和媒体额外许可保留；公开源码仍采用非商业源码可用许可，不称为 OSI 开源。

## 历史验收摘要

原候选记录 build、compileGametestJava、显式 #49 默认完整客户端回归及独立 P1 专项通过；默认回归为 17 ready、4 epoch、16 截图。普通生产包仅在真实 WEATHER v1 自动世界的新副本完成首次迁移、重开和部分命令／风况检查，两次正常保存退出。以上记录不是本次新游戏运行，也不是 GitHub CI。

真实 v0 自动／手动与 v1 手动世界、旧客户端互通、多人／专用服务器、全部阶段暂停矩阵、逐 tick REBASE、性能回退与长期可靠性仍未验证。#49 是显式测试框架补丁，不进入普通产品包；原生方格区块等待未修复。Photon 雪可见度与历史窗口缩放／闪电黑屏根因未定位，有限时窗未捕获不等于修复。

## 快照内容与省略项

包含 main/client/gametest 源码、资源、脚本、测试框架源码修复及来源记录、Gradle Wrapper、必要技术与法律文档，以及两张经过目视检查的历史 beta.7 展示图。原始测试日志、视频、世界、私有启动参数、未审核截图和旧 Git 历史留在 Private 档案。技术文档中的日期和旧候选状态是历史记录；原证据缺省说明为 Private 档案，不提供断开的公开链接。

首次公开前已在独立副本重新执行构建、测试源码编译、隐私与秘密扫描；完整报告保留于仓库外的 Local 审核档案。源码编译不代表游戏、多人或专用服务器验收通过。首次公开提交使用维护者确认的公开署名 Araon7756 和 GitHub noreply 邮箱，以独立根提交建立全新历史；未导入或修改原 Private 仓库历史。
