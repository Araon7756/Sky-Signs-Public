# Sky Signs 许可范围与历史版本

## 当前材料的范围

根 LICENSE 是未经修改的 PolyForm Noncommercial 1.0.0 官方正文。它是本版本项目自有软件内容的主要许可，包括维护者有权许可的源码、测试、脚本、构建配置及随软件提供的项目自有文档和资源。此声明仅适用于许可方实际拥有或已获授权的权利，不制造 AI 输出的排他版权，也不覆盖第三方材料。

项目自有内容中，只有从本许可正式采用之后新增、且未曾另行授权的表达，才可以据此讨论新的使用限制。此前相同表达若已获得 MIT 或其他有效许可，原许可权利继续存在；给同一代码换文件头、元数据、版本号或根 LICENSE，不会消灭这些权利。

[MEDIA_PERMISSION.md](MEDIA_PERMISSION.md) 是独立的额外媒体授权，限定正常视频、直播及制作这些媒体所合理必需的操作。它不改写 PolyForm 正文，不扩大一般商业软件使用、收费服务器、软件销售、收费整合包或商业修改的权限，也不缩减其他有效许可或法定权利。

第三方材料依 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 及原文件许可处理。根 LICENSE 和 fabric.mod.json 的主要许可证字段不把这些材料改成 PolyForm。

## 历史 MIT 边界

截至实施草案基线提交 d6fef468a5f890e9879a3b533c0746c68ee6089e，仓库根 LICENSE 和主模组元数据仍为 MIT。该提交之前按 MIT 提供的项目自有内容及全部既有发布标签继续适用当时的 MIT；第三方材料仍保留自身许可。旧许可证原文存于 [LICENSES/MIT-legacy.txt](../../LICENSES/MIT-legacy.txt)，保存原来的版权行，不作回溯改写。

明确包括以下既有标签：

- testpack-0.5.0-alpha.6、testpack-0.6.0-alpha.6、testpack-0.6.0-beta.4；
- v0.6.0-beta.5、v0.6.0-beta.6、v0.6.0-beta.7、v0.6.0-beta.8、v0.6.0-beta.9。

beta.9 标签解析到提交 5a05e4ae8880b2fdee7727f67a073f1237965ec7。正式发布 JAR 的构建来源为 96bf49a888afb44b41c85d66c27e68651b69c7bd；正式 JAR SHA-256 为 29282f19c52af49fb766fe6ae8f6d574e04f48277ee26b0bb86bfacdcd59a83e。标签提交与构建提交的差别及构建记录见该 Release 附件 BUILD_PROVENANCE.md（原 Private 档案；不随此快照公开）。它们不因未来迁移而变更。

MIT 允许在保留规定通知的条件下使用、修改、分发和销售旧内容。维护者不能收回别人已获授的 MIT 权利；别人仍可继续开发和商业使用符合旧 MIT 的旧版分支。未来新增的表达不因此自动获得 MIT。

## 新许可采用与当前发行记录

许可迁移实施提交为 `3abc8550f6ca775448169f7e18a61133b8c36ef1`，PR #36 合并提交为 `23ccdc301651c2a6f91ec6a97e1502ddef87c581`。2026-10-14 已只读核验原 GitHub 仓库的合并状态。

早期草案曾暂定 `v0.7.0-beta.1`，并使用 beta.9 名称审阅构建；这些是历史候选状态，不能当作当前发行事实。实际首个新许可发行采用 `v0.7.0-alpha.1`，于 2026-10-13 发布。附注标签目标为 `b2e9238259b71b0243b659144e5cb4b746ab970c`；Local 验收构建来源为 `1cf474050620deefa9719c1eccfa9574f85a04ad`。普通生产 JAR 为 729609 字节，SHA-256 `4f11d32dd5d23365306456566d05e3785b27458f0543be3d9d1501e46829076a`。本次核验 GitHub 标签与附件元数据及现存构建来源记录，没有重新上传或迁移旧发行附件。

本公开快照是独立文件快照，不包含旧标签、旧提交对象或 Release；快照构建不是原发行 JAR，必须使用本次另行计算的摘要。详见 [公开快照说明](../PUBLIC_SNAPSHOT.md)。

包含本文件和 MEDIA_PERMISSION.md 的版本提供其中写明的额外权限；未包含它们的旧版、未授权的第三方新增内容或其他分支，不能只根据版本号推定取得此额外授权。

## 正式文本与解释文件

权利按适用的正式许可、有效额外授权和法律判断。README、FAQ、本文及联系维护者的建议，不增加正式许可中的限制，也不要求使用已经获准的权利时另获批准。特别是 PolyForm 的 Distribution License 与用途限制如何作用于具体收费分发，不在解释文件中作绝对禁止结论。

许可依据：[PolyForm 正式文本](https://polyformproject.org/licenses/noncommercial/1.0.0)；[MIT 正式文本](https://opensource.org/license/mit)。
