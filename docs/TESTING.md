> 公开快照说明：本页保留历史设计、候选或验收记录；日期、提交和当时的“尚未发布”等状态仅适用于对应历史阶段。当前发行事实与公开快照验证见 [PUBLIC_SNAPSHOT.md](PUBLIC_SNAPSHOT.md)。原始证据、旧 PR/Issue 和提交历史保留于 Private 档案，未随快照迁移。

# 构建与回归测试

## Public alpha.3 候选（2026-10-17）

本分支引入 Private P3 已验收源码，但 Public 版本号与随包法律说明具有独立来源，必须重新构建和审计，不能将 Private alpha.2 的包摘要或运行直接写为本版通过。当前实际命令、来源、结果与未验证范围见 [alpha.3 发布准备](RELEASE_0_7_0_ALPHA_3.md)。原始 Private 日志／截图及世界不随公开仓库迁移。

## Public 仓库基础 CI

[Public CI](../.github/workflows/public-ci.yml) 使用只读 `contents: read` 权限，在针对 `main` 的 Pull Request 和 `main` 推送上运行两个独立 job：`Java 21 build` 与 `Gitleaks`。前者使用 Java 21 和现有 Wrapper，执行 `bash ./gradlew build compileGametestJava --no-daemon --max-workers=4`；通过 bash 调用不依赖 Wrapper 的 Git 执行位，也不升级构建或游戏依赖。

Gitleaks 固定为 8.30.1，下载官方 Linux 发行并验证固定 SHA-256；完整检出公开 Git 历史，分别检查所有已获取引用的历史和当前提交的跟踪文件。扫描使用默认规则、脱敏输出、无 baseline，并检查实际扫描字节数大于零。checkout 与 Java 官方 Action 固定到精确 commit SHA，不使用私人 token 或 repository secret。

这套 CI 不启动 Minecraft、客户端 GameTest 或专用服务器，不接受 EULA，不上传构建 artifact，不创建 tag、Release 或部署。`compileGametestJava` 只验证源码编译；`test NO-SOURCE` 不代表功能测试通过。游戏、多人、光影、真实旧存档和长期验证仍按下文单独记录。

独立的 [Public Identity Guard](../.github/workflows/identity-guard.yml) 提供 `Commit identity guard` 检查，使用事件中的精确 SHA：PR 检查 `base.sha..head.sha`，main push 检查 `before..after`。完整获取历史后，逐条检查该范围内所有提交的原始 author／committer 邮箱，只允许非空用户名的 `@users.noreply.github.com` 地址或精确的 `noreply@github.com`；不使用 mailmap 替换身份。既有历史中的私人邮箱不作为全历史阻断条件，也不因此被删除。空范围、全零或无法解析的 SHA、浅克隆均明确失败；日志输出本轮范围、数量和各提交身份，不读取或打印凭据。隔离临时 Git 仓库中的正反例由 [身份检查测试](../scripts/test-public-commit-identities.py) 验证，不改真实历史。本检查是否成为 main 必需检查，以实际 ruleset 为准；新增 workflow 本身不修改保护规则。

## 0.7.0-alpha.1 Local 候选验收（2026-10-13，尚未发布）

精确候选 `1cf474050620deefa9719c1eccfa9574f85a04ad` 的 build／compileGametestJava、显式 #49 默认完整回归、独立 P1 专项均退出 0。默认回归 17 ready／4 epoch／16 截图，认证与逐阶段发送集合核验通过；普通 alpha.1 JAR 新隔离实例完成真实 v1 自动世界副本首次迁移／重开及部分暂停、手动风、自动恢复，两次正常保存退出。生产 JAR SHA-256 为 `4f11d32dd5d23365306456566d05e3785b27458f0543be3d9d1501e46829076a`，不含 Sky Signs 测试模组／#49 类；普通组合的 Fabric API 自带 dormant gametest API 不等于测试模组激活。

完整记录与八项矩阵（原始证据保留于 Private 档案） 保留沙箱构建失败、包装入口漏执行认证准备的启动失败、未冻结步进拒绝及 Local 分析误判和更正；成功默认回归使用文档原入口、新实例，不改候选代码或断言。此前 2026-10-11 的自然恢复补测单独归档，仍对应旧 beta.9 名称产物，不能冒充 alpha.1 验收。完整子矩阵、REBASE 逐 tick、其他真实旧档、旧客户端、多人／专服、性能／长期和渲染兼容缺口继续保留。证据随本地候选提交归档，尚未 push、合并或发布。

## Cloud 雪山六段录像复核（2026-10-11）

[独立 Cloud 复核报告](SNOW_DYNAMIC_CLOUD_REVIEW.md)固定 PR #57 head `2499fa9c`，核对两条正式全片的六段、14 行事件表及质量边界：11,483 帧全部解码/PTS/图像摘要与 Local 一致，ROI 均值/标准差独立复算一致；目视采用加密采样及重点邻帧，不是原尺寸全帧或连续播放。**本次有限时窗未捕获，动态反馈仍未验证；原反馈继续保留，根因未定位。** 稳定光影开关可见度差异仍在，三处有效段间隙与一处段外间隙、30 fps 和未校准筛选局限保留；不新增渲染兼容、根因修复或发布验收通过项。报告复核时引用尚未合并的 #57 固定提交；本次文档合并不新增实机运行。

## 原雪山连续录像 Local 采集（2026-10-11）

按 PR #56（原 Private 档案；不随此快照公开） 固定 head `dcc2482be8c911afb0e68b957cac17ee20826dc5` 的方案，在新隔离实例及新世界副本完成露天开→关→开各 60 秒、同机位顶棚各 30 秒；有效合计 270.302 秒，稳定期另计 60.396 秒，两场景各一次。来源 main `4152ddf0`，实际 JAR 构建来源仍为 `3c60a8b3`，生产输入相同，未重新构建或运行 GameTest。完整证据（原始证据保留于 Private 档案） 共享两条全程录像（露天四个字节分卷）、校准失败及更正、状态/帧时间线、质量、事件表和 SHA-256；只读验证命令为 `node Private 证据档案中的对应材料`，提交字节核对加 `--git-ref HEAD`。

正式开始前因 60 fps 校准不可靠固定为 30 fps。A 有四处 >66.667 ms 间隙，B 无；全部正式编码帧完成解码/PTS 与图像代理计算，目视 11 张联系表（270 个每秒采样和 30 个最低值邻帧缩略图），没有原尺寸目视全部帧。稳定开关可见度差异仍在，**本次有限时窗未捕获，动态反馈仍未验证**；不据此宣称兼容通过或根因定位。原玩家/来源文件与受保护工作树未变；副本 Photon 参数仅保存时间注释更新，汉化 ZIP 重封装但全部 1213 条目内容相同，严格字节失败和解释均保留。正常保存退出 0，不代表历史退出根因修复。

## 原雪山近似场景与反馈澄清（2026-10-10）

维护者最新明确原反馈为“雪花忽隐忽现或明显变淡”。三组 Local 雪山对照（原始证据保留于 Private 档案） 基于 main **`3810fd9ea02c8ab96d0fa57e05ad2e3add218b24`**，沿用从 `3c60a8b3` 构建的同一生产 JAR，相关源码与构建输入一致，未重新构建。新世界副本回到原截图雪山坐标区域，近似匹配夜景、山峰轮廓及手持仪表；Photon 开→关→开重复观察到中间远景带的可见度差异。原始精确相机/时间未知、分辨率不同，不称逐像素复现。

本次共享全部 9 张 F2、27 张连续窗口帧、两张原始参考及必要脱敏日志。顶棚、已知实心墙两组分别保留；墙中心/手持物未见明确覆盖，边缘微小亮点未定位。持续动态忽隐忽现、几何遮挡错误、原窗口与山体同一机制及根因仍未验证，不能关闭反馈或宣称完整兼容。正常保存退出与原世界/来源保护通过。`node Private 证据档案中的对应材料`（或加 `--git-ref HEAD`）仅验证共享字节与记录；本次共享不新增游戏运行。Cloud 交接说明（原始证据保留于 Private 档案） 明确后续只读范围，历史七组及 Cloud 静态核查保持独立。

## P1 雪花遮挡／可见度 Local 对照证据（2026-10-10）

实际被测 main 为 **`3c60a8b3a2e378b1076c0a6a89335de5c839433b`**，生产 JAR SHA-256 为 `dd74ab3d443c90e6290d11434ebae67a8f6cc176d189c44f1ce546ed2ef1137b`，文件名中的 beta.9 不代替源码版本。共享报告（原始证据保留于 Private 档案） 记录七个独立单人控制组：全新雪原场景比较有／无 Sky Signs，再分别加入 CoolRain、Distant Horizons，或单独改变 Photon 倾斜、雪透明度、主世界雾强度。环境为 Minecraft 1.21.10、Loader 0.19.3、Iris 1.9.7、Sodium 0.7.3、Photon v1.3b、RTX 5060 Ti／NVIDIA 596.49。

无 Sky Signs 的最小 Fabric API／Sodium／Iris／Photon 环境已复现远窗口外雪花在光影开启时几乎不可见、关闭后明显出现、再开启重复；近窗口开启时上方仍可见雪花。其余六组也保留该差异。Sky Signs、CoolRain、Distant Horizons 不是此最小复现的必要条件，具体根因及与用户原始山体遮挡反馈的同一性仍未验证。实心墙与手持石头的有限场景未见明确白色雪花穿墙或覆盖手持物，不扩大为完整兼容通过。

本机完整采集 252 张场景原图；本 PR 共享每组每场景开→关→开的全部 **63 张 F2 原图**、脱敏完整日志、配置、结构化记录、来源映射、独立 SHA-256 清单及只读验证器，另外 189 张窗口截图保留本机。七组均正常保存退出，初始世界相同、天气与时间固定；原存档／源实例的保护性核对通过。保存命令拒绝、提前生成联系表及跨组辅助类型加载错误与更正均保留。

在仓库根目录运行 `node Private 证据档案中的对应材料` 核对工作树文件，或加 `--git-ref HEAD` 核对提交中的字节；这不启动游戏，也不证明视觉问题已修复。本次共享没有新运行构建、GameTest 或 Minecraft；普通生产实例不加载 #49 测试补丁。其余发布前范围见 [P1_RELEASE_CHECKLIST.md](P1_RELEASE_CHECKLIST.md)，后续只读核查范围见 Cloud 交接说明（原始证据保留于 Private 档案）。

[Cloud 核查报告](SNOW_RENDER_AUDIT.md) 和执行收据（原始审计收据保留于 Private 档案） 共享 `28dbe0cf` 的只读源码与证据核对：230 项清单文件、63 张原图及七组既有记录通过，74 个生产／构建输入与被测来源一致；没有新增实机运行。Iris 绕过为静态路径判断，逐帧实际状态未观测。远窗口雪花可见度降低已复现，具体遮挡前后关系错误及与原始山体反馈的同一性仍未验证，两者分别记账。

## P1 发布前两项 Local 补测证据（2026-10-09）

证据与文档 PR #51 合并后的被测 main 为 **`ad11e50201b7403ecb8c84d9ed85553e652bcbd7`**。本轮共享报告（原始证据保留于 Private 档案） 记录两个独立场景：显式启用 #49 的全新实例单次连续 10 周期生命周期退出 0（110 ready、30 epoch、20 组包集合、40 张截图）；普通生产 JAR 打开的真实 WEATHER v1 自动玩家世界副本完成首次迁移、tick 28000 与既有旧版证据对照、旧 T 交接、400 tick 调整终点和暂停计划保存重开。玩家场景不加载 GameTest／#49；全部 62 张原始截图已核查。完整原世界、JAR、私有启动参数只保存在本机。

真实世界副本由 WEATHER 1 迁移为 2，保留 LEGACY、原 S/T、预报、模式与初始化；旧 T−1/T 气压均为 989.7 hPa。重开前仅在副本关闭天气循环，重开后 WEATHER 全部 20 字段、WIND 数据及身份／位置保持；世界时间波动使显示气压变化，已按实际 tick 复算，不要求跨 tick 显示值相等。旧版同 tick 基线本轮未重跑；重开后自然天气恢复与后续新周期、400 tick 全曲线逐 tick 采样仍未验证。

首个长路径工作树在世界创建前启动失败（exit 1、0 周期），原日志保留；同 SHA 的全新短路径实例十轮通过，不将两次拼接或声称唯一根因已解决。WIND／重开气压误判及 UTF-16 读取问题属于 Local 分析工具，原失败与更正依据同时共享，未改候选。原生方格等待仍未解决（`nativeSquareFixed=false`）。共享子集自带来源映射、独立 SHA-256 清单和只读验证器；验证证据文件不是重新运行 Minecraft。本次文档／证据共享没有新启客户端、构建或性能测试，未发布、不关闭 Issue、不改 Project。其余发布前边界见 [P1_RELEASE_CHECKLIST.md](P1_RELEASE_CHECKLIST.md)。

## P1 最终 main 与 #49 启用方式（2026-10-09）

#48 → #49 → #46 → #50 首次合并后的 main 为 **`b1a052e3d8d5384f7b6666bd18823228333753dd`**。#50 五轮 Local 证据（原始证据保留于 Private 档案） 与最终 main 单次补测（原始证据保留于 Private 档案） 分别记录：前者 178 ready／20 组包集合，后者 `runClientGameTest build compileGametestJava` 退出 0、17 ready／4 epoch／16 截图。最终补测使用一个全新隔离单人实例，显式启用 #49；没有重做五轮或真实旧玩家世界。发布前缺口见 [P1_RELEASE_CHECKLIST.md](P1_RELEASE_CHECKLIST.md)。随后 #51 合并为 ad11e502；新增两项专项见页首。本段 17 ready 默认完整回归仍对应 b1a052e。本节之后按日期保留的候选／失败描述是历史记录，不代表当前合并状态。

#49 是**默认不加载的测试框架源码补丁**。单独运行 `runClientGameTest` 不会自动启用。必须同时传入 `-I scripts/network-synchronizer-patch.gradle`、`-PnetworkSynchronizerPatch=true` 和指向被测工作树绝对路径的 `-PnetworkPatchRoot=…`；隔离目录另由 `scripts/inf01-isolated-run.gradle` 与唯一 runId 指定。补丁只替换固定上游测试模块中的同步器外部类，不写原缓存、不改生产依赖或发行 JAR。

下面是在**已完成 Git 检查／fetch、工作树干净且核对精确 SHA 的独立工作树**中的 Windows PowerShell 模板。模板用于当前公开快照；历史 ad11e502 的结果只适用于当时被测输入。本次不执行客户端入口。先配置已有 JDK 21 并确认 `java -version`；应无其他正在执行的验收客户端。`--offline` 仅适用于缓存完整的环境。模板遇到已有 `SKYSIGNS_*` 模式或重用目录会停止，避免把专项／保护模式当成默认回归；不会清理原有实例。

`command.json` 中 `sourceSnapshot` 仅记录初始 Private 文件快照来源；`publicCommit` 在运行时读取被测 Public 工作树的 `HEAD`，`worktreeDirty` 记录跟踪及未跟踪文件的修改状态（不含被 Git 忽略的构建输出），`branch` 记录当前分支，分离 HEAD 时为 `null`。这些值在创建本轮输出目录前读取；如启动检查发现 dirty，应先停止并报告，不能把带修改的运行写成精确提交验收。历史快照 SHA、发行源码目标与实际被测 Public 提交不能互相替代。

```powershell
$ErrorActionPreference = 'Stop'
$CandidateRoot = (Resolve-Path '.').Path
# Read the actual Public checkout; no old Private Git objects are required.
$PublicCommit = & git -C $CandidateRoot rev-parse --verify HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the Public commit.' }
$PublicCommit = $PublicCommit.Trim()
$PublicStatus = @(& git -C $CandidateRoot status --porcelain=v1 --untracked-files=all)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the Public worktree status.' }
$WorktreeDirty = $PublicStatus.Count -ne 0
$PublicBranch = & git -C $CandidateRoot branch --show-current
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the Public branch.' }
if ([string]::IsNullOrWhiteSpace($PublicBranch)) { $PublicBranch = $null }
# Use a fresh isolated test directory and preserve prior evidence.
if (@(Get-ChildItem Env: | Where-Object Name -Like 'SKYSIGNS_*').Count -ne 0) {
    throw 'Existing SKYSIGNS modes must be reviewed before this default run.'
}
$RunId = 'p1-acceptance-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
$Archive = Join-Path $CandidateRoot "out/$RunId"
$Instance = Join-Path $CandidateRoot "build/run/inf01-$RunId"
if ((Test-Path -LiteralPath $Archive) -or (Test-Path -LiteralPath $Instance)) {
    throw 'Use fresh archive and instance directories.'
}
New-Item -ItemType Directory -Path $Archive | Out-Null
$env:SKYSIGNS_INF01_OUTPUT = Join-Path $Archive 'events'
$RunArgs = @(
    'runClientGameTest', 'build', 'compileGametestJava',
    '--offline', '--no-daemon', '--max-workers=4',
    '-I', 'scripts/inf01-isolated-run.gradle', "-Pinf01RunId=$RunId",
    '-I', 'scripts/network-synchronizer-patch.gradle',
    '-PnetworkSynchronizerPatch=true', "-PnetworkPatchRoot=$CandidateRoot"
)
try {
    @{ sourceSnapshot = '11b5cdbd119a55f071aac48464623e61575cb167';
       publicCommit = $PublicCommit; worktreeDirty = $WorktreeDirty; branch = $PublicBranch;
       root = $CandidateRoot;
       runId = $RunId; instance = $Instance; args = $RunArgs;
       started = (Get-Date).ToString('o'); java = $env:JAVA_HOME;
       environment = @{ SKYSIGNS_INF01_OUTPUT = $env:SKYSIGNS_INF01_OUTPUT }
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $Archive 'command.json') -Encoding utf8
    & .\gradlew.bat @RunArgs *> (Join-Path $Archive 'console.log')
    $RunExit = $LASTEXITCODE
    $RunExit | Set-Content -LiteralPath (Join-Path $Archive 'exit.txt')
    if ($RunExit -ne 0) { throw "GameTest failed: $RunExit; retain the full archive." }
} finally {
    Remove-Item Env:SKYSIGNS_INF01_OUTPUT -ErrorAction SilentlyContinue
}
```

这是供后续复测的默认完整入口模板；本次证据共享没有执行新 GameTest，页首生命周期专项来自已封存的 Local 实测。实际 2026-10-09 补测参数／环境／时间在命令记录（原始证据保留于 Private 档案）。启动日志必须有 `NS_PATCH_ACTIVE`，来源指向该工作树 `build/test-framework-patch/`，`disabled=false oldGuard=false`，并与当轮补丁 manifest 的类摘要匹配；本机已测类 SHA-256 为 `0f8392ce11db6146e0275ffcd4014cfb03e40078a92cb35d9d9debf99ca61394`。缺证书、摘要不匹配、invalid state 或崩溃均不能判通过。退出 0 后仍需核验逐阶段事件、截图及 SHA-256；共享证据的只读验证器见补测 README。

旧 `SKYSIGNS_NETWORK_WAIT_GUARD` 必须完全不存在，即使值为 `0` 也不合格；不关闭同步器、不降低视距、不删断言。#48 就绪实现与 #49 补丁没有修复原生 289 方格四角等待（`nativeSquareFixed=false`）。连续 10 周期生命周期要另开新实例并按 #49 Local 步骤（原始证据保留于 Private 档案） 显式开启指定自测模式，不把它混进上述默认回归。多人／专服、EULA、LAN／公网和原玩家世界不在本模板范围内。

## INF-01 独立基础设施候选（2026-10-08）

[完整发送集合／场景／渲染／重开就绪契约](INF_01_READINESS.md) 在独立分支修正默认完整回归的方格等待，显式视距 8、保留原期限和产品断言。原生方格故障保留为独立对照；自测成功、默认完整回归与 Local 实机验收分别记录。实现不携带 P1 天气修改；与 PR #46 的精确候选组合结果见该文档的验证记录，不代表未经修改候选的原生等待已经通过。

核对日期：2026-09-30；功能与测试基线为已发布 beta.7，精确构建提交 `56ed3f5798ac4884ae8f941f7408705ac12b55df`。测试配置、源集和依赖以当前 `build.gradle` / `gradle.properties` 为准。历史“通过”是对应提交与环境的记录，不是新任务自动取得的结果。

## P1 候选验证入口（2026-10-07）

候选交付及实际结果（原始证据保留于 Private 档案） 区分 build、专项客户端断言、默认完整回归与 Local 旧世界副本验收。默认入口仍运行原完整流程；新增纯曲线检查与 WEATHER 2 迁移预期。专项入口 `SKYSIGNS_P1_ONLY=1` 创建一次性集成单人世界，执行六转移 T/E 观察、压缩 NBT 磁盘 round-trip、查询不消费 RNG／不重复水扫描、真实原版命令／睡眠／暂停／回退；测试观察 Mixin 只在该入口开启，无取消调用或修改等待。默认自动转雨场景显式开启框架默认关闭的 doWeatherCycle；读数精确 stress 对照在长时手动雷暴的短接入完成后采样，保留原断言。

```sh
SKYSIGNS_P1_ONLY=1 bash ./gradlew runClientGameTest --offline --no-daemon
```

Cloud 已有图形依赖时可用 `bash scripts/run-client-tests-xvfb.sh --offline`；Windows 按现有 argfile 入口启动并设置同一环境变量。专项不包含游戏存档重开、真实旧玩家世界、UI／语言／光影或性能，不能替代 Local 验收。取消该变量后才是默认完整回归。原生重开下载等待与网络同步器已知失败保持；本 P1 不修等待条件、不减视距、不启用保护、不删除重开断言。

## 环境与命令

需要 JDK 21、仓库中的 Gradle 9.2.0 Wrapper、Fabric/Minecraft 依赖。先检查 `java -version`、工作树和 Wrapper，不创建替代工程，不删除现有缓存。不要把前一次本机 JDK 路径写入 Cloud 配置。

| 操作 | Windows PowerShell | Linux / Cloud shell |
| --- | --- | --- |
| Gradle/JVM 信息 | `.\gradlew.bat --version` | `bash ./gradlew --version` |
| 构建 | `.\gradlew.bat build --no-daemon` | `bash ./gradlew build --no-daemon` |
| 编译测试源码 | `.\gradlew.bat compileGametestJava --no-daemon` | `bash ./gradlew compileGametestJava --no-daemon` |
| 完整客户端回归＋构建 | `.\gradlew.bat runClientGameTest build --no-daemon` | `bash ./gradlew runClientGameTest build --no-daemon` |
| 手工开发客户端 | `.\gradlew.bat runClient` | `bash ./gradlew runClient` |

`gradlew` 当前 Git 执行位为 100644，Linux 示例用 `bash` 调用，无须为测试擅改文件权限。依赖与分发包已缓存时可加 `--offline`；新的 Cloud 工作环境通常需要先取得依赖。缺网络／缓存应报告具体错误，不通过改动依赖版本“修复”环境问题。

正式安装包为 `build/libs/skysigns-<version>.jar`，不是 `-sources.jar` 或开发中间包。测试类仅位于 `src/gametest`。发布／交付时核对 `fabric.mod.json` 的版本、包内没有测试类、源码提交与 SHA-256；JAR 不提交 Git。

## 哪些任务实际执行测试

气象站降水回归在自动晴转雨后等待反映稳定降雨状态的定时报告，避免将渐变早期的旧报告与稍后世界状态直接比较；另有可控雨强 0→1 的快照断言。2026-10-01 云端修复提交与最新候选 `24ae026` 的 Windows 默认完整回归均已通过，环境、日志与验收边界见 快照回归记录（原始证据保留于 Private 档案）。

`build.gradle` 启用 `enableClientGameTests=true`、`enableGameTests=false`，测试模组入口为 `SkySignsClientGameTest`。没有独立 JUnit 测试套件；纯算法断言也由客户端 GameTest 入口调用。

- `build` 编译、校验 Access Widener 和打包；当前 `test` 可显示 `NO-SOURCE`，不代表游戏行为通过。
- `compileGametestJava` 只验证测试源码可编译，不启动 Minecraft。
- `runClientGameTest` 才实际启动 Minecraft 客户端及临时集成服务器，执行断言、保存／重开与截图。
- 日志写“启动成功”、部分截图存在或某条性能输出出现，不等于任务最终成功；必须检查 Gradle 退出状态和断言错误。

## 图形环境与测试目录

客户端测试需要可用窗口／显示、OpenGL、LWJGL 及系统依赖。无显示的环境不能仅靠 `-Djava.awt.headless=true` 运行 Minecraft。若 Cloud 已提供虚拟显示／软件渲染，可使用环境既有机制；本仓库没有提供已验证的 Cloud 图形 setup 脚本，不能承诺任意 Linux 容器即开即测。

无法运行图形测试时仍可构建／编译测试，并明确列为“客户端回归未执行”，交由具备图形环境的隔离实例验收。不得删减断言或更改源码来制造通过结论。

当前本地客户端 GameTest 运行目录为 `build/run/clientGameTest/`，日志在其 `logs/`，截图在 `screenshots/`。以 Gradle 实际输出为准；`clearRunDirectory=false`，旧截图可能保留，必须核对本次时间与名称。普通 `runClient` 使用的 `run/` 也被 Git 忽略。

### 独立湿度计性能模式

候选分支新增 `SKYSIGNS_PERFORMANCE_TEST=1` 入口：运行五种对照场景、三轮世界 tick／帧间隔采样，以及独立算法批量计时。该环境变量启用时不运行原有完整回归；验收完整行为前须取消它。默认入口不变，生产 JAR 不包含性能测试类。可使用 `bash scripts/run-client-tests-xvfb.sh` 启动已有 JDK／图形依赖；该脚本不提供依赖安装或通用 Cloud setup。

场景、AFK 限帧控制、结果与缓存设计见 [湿度计性能基线](HYGROMETER_PERFORMANCE.md)。这是短时集成单人测量，不是多人、长期容量、硬件 GPU 或性能无回退保证。

同一运行目录的客户端任务顺序执行，等上一任务退出后再启动，避免临时世界文件锁冲突。测试卡住时先定位本任务的进程与新日志，不结束用户正常游戏，不删除整个 `run`／缓存目录。

## 当前自动覆盖

| 测试位置 | 核心检查 |
| --- | --- |
| `SkySignsClientGameTest` | 图标、自动晴转雨、气象站定时刷新与当地降水、实际中英文资源切换、原版命令、两种模式实际睡眠、主世界／下界往返、保存重开、旧仪表恢复 |
| `ModelUiAssertions` | 模型元素边界、表盘世界／库存外壳与 display 一致性、湿度计继承、气象站物品继承和库存角度 |
| UI／模型预览路径 | 中英文标准 UI；Screen 初始化为 320×180 的紧凑布局；`InstrumentPreviewScreen` 实际 GUI 物品截图；四向放置、朝向保留、非空碰撞和世界截图 |
| `SaveMigrationAssertions` | 历史字段构造的 v0 `.dat` 经 PersistentStateManager 保存重开；默认值、幂等性、未知字段、未来／无效版本保护、命令拒绝、非有限风值、仪表 0/50/88/100 |
| `ClimateAssertions` | 群系气候范围、湿润／干旱区分、海拔修正、水体加成；安装 Terralith 时追加注册表与高海拔样本 |
| `PerformanceProbe` | 基线与增加 128 台湿度计，各 120 个主世界 tick 的耗时及帧间隔，中位数/P95；另检查 20 台错峰采样 |

当前 UI 与模型截图使用 `beta7-station-*`、`beta7-inventory-models` 和 `beta7-models-*` 名称。测试语言会恢复，临时世界与 fixture 不应写入玩家世界。320×180 是 Screen 逻辑尺寸，不是完整物理分辨率／GUI 缩放矩阵；四向非空碰撞与截图不是精确碰撞网格或所有读数／视角验收。

气象站、仪表与迁移测试仍不能代替真实玩家历史存档升级；fixture 是按历史字段构造，不是运行所有旧版本生成的完整世界。性能探针不测整个专用服务器 tick，也不验证网络吞吐或长期容量。

### 地形测试可复现边界

历史隔离实例使用 Terralith 2.5.13、Tectonic 3.0.17、Lithostitched 1.5.2+patched（本地文件名为 Fabric 1.21.9），放在被忽略的测试 `mods/` 目录。三者不在 Gradle 测试依赖声明中，也不打入发布包；干净 clone 不会自动获得它们。

基础测试可不安装它们运行，`verifyTerralithRegistry` 在未加载 Terralith 时跳过。若要声明地形兼容回归通过，必须在独立实例取得适用且来源可核验的依赖、记录实际版本，然后运行该路径；不能静默换版本后沿用历史结论。

## 人工客户端回归

只在备份世界副本／隔离实例内操作，客户端与服务端使用相同 Sky Signs 版本，只放一份 JAR。

1. 原版 `/weather clear`、`/weather rain`、`/weather thunder`，分别在 Sky Signs 自动／手动模式检查状态、预报和倒计时，不在视觉渐变期反复重置。
2. `/skysigns weather auto` 保留当前天气；`/skysigns weather set clear|rain|storm` 切手动。命令格式中的 `|` 表示选一项，不是直接输入的字符。
3. `/skysigns wind set 0 0`、`90 100`、`359 100`、`0 100` 等方位／强度，核对平滑转向、0/359 边界、碰撞与报告；结束后恢复 `wind auto`。
4. 四向放置所有仪表；近看顶部、指针、选取框、碰撞，检查 GUI／手持／掉落物模型。对照表盘高低值，不能只看聊天数字。
5. 中文／英文、小窗口／不同 GUI 缩放；长文本、省略与悬停；离开 8 格范围或破坏气象站后界面关闭。
6. 雨林、干旱、高山、冷群系、屋顶下与水体附近，比较湿度和原版当地雨雪；检查地形数据包异常气候范围。
7. 无光影时强风雨雪倾角；Iris 无光影／有光影及运行中切换；Photon 闪电回归。必要时在测试世界执行 `/summon minecraft:lightning_bolt ~10 ~ ~`，注意闪电可能损坏方块／实体，不在正式世界操作。光影启用时没有本模组倾角是预期。
8. 自动／手动状态保存退出、重开；夜间雷暴睡觉；维度切换、仪表区块卸载／重载；旧存档副本升级后再保存重开。

## 专用服务端与多人：待授权的测试计划

维护者已暂缓服务端排查。不要因文档出现以下步骤而启动服务端、接受 EULA、开放端口或连接实际服务器。

将来获授权后，在独立目录用适配的 Fabric 服务端、Java 21、Fabric API 与同版本 Sky Signs 做启动 smoke test，检查公共源集是否错误加载客户端类；按实际环境确认 Loom `runServer` 或独立发行实例的启动方式，不能把命令存在写成已测试。

然后至少两个独立客户端验证：不同语言同时看同一站、快速重复刷新和离站、天气／风同步、跨维度同坐标气象站、断线重连、新玩家首次快照、区块卸载、服务器保存／完整重启及旧存档升级。记录 TPS／tick、包数量与字节量、内存、玩家数、仪表数和测试时长。

当前没有专用服务端自动 GameTest。历史 alpha.3 的第二客户端在 GLFW 原生线程崩溃，未完成联机；原因未确认，不能归因于 Sky Signs，也不能记录为同步通过。

## 每次交付的验证记录

PR #15 与已合并 PR #16 的主线组合、自然湿度采样探针、退出网络诊断和不启用 GameTest 的普通客户端测量入口见 [Issue #9 本地补测](ISSUE9_LOCAL_ACCEPTANCE.md)。普通客户端的 Gradle 退出 0 还必须配合完整 `result.json` 与无 `failure.json` 才能判定测量通过。

记录提交／分支、JDK／Loader／API／依赖版本、命令与退出结果、截图／日志位置、JAR 版本和 SHA-256；分别列已执行、失败后修正、未执行。文档-only 变更以链接、事实、差异检查为主，无须为证明文档有效而启动游戏。

## Beta7 发布验证记录

v0.6.0-beta.7 Pre-release（原 Private 档案；不随此快照公开） 于 2026-09-30 发布，附注标签指向 `56ed3f5798ac4884ae8f941f7408705ac12b55df`。该提交与完成候选回归的 `82d3f2450bda1c597d6a40a1c2df681766ec53a9` 在 src、build.gradle、gradle.properties 和 gradle 目录无差异；发布前仍对实际 main 提交补跑回归。

- 干净构建：现有 Wrapper 9.2.0，`clean build --offline --no-daemon`，初始化脚本仅将输出目录改为起初不存在的 `out/beta7-release-build`，避免清除已有 build 内测试实例和证据；实际重新编译打包，成功，10 秒。
- 完整客户端回归：`.\gradlew.bat runClientGameTest --offline --no-daemon`，成功，1m41s，退出码 0；约 16:37–16:39（Asia/Shanghai）。未运行专用服务器或独立多人。
- 环境：Windows 11 amd64、Microsoft OpenJDK 21.0.12.1+1-LTS、Loom 1.14.10、Loader 0.19.5、API 0.138.4+1.21.10、Yarn 1.21.10+build.3；测试实例额外加载上述三个地形模组，不进入发行 JAR。
- 模型／UI、迁移保护、天气命令、睡眠、跨维度、保存重开和错峰采样断言通过。本轮生成截图，候选阶段已人工查看截图；两者都不能扩大为全部视角验收。
- JAR 元数据为 beta.7，105 项，不含测试类；正式 SHA-256 为 `A56E7129477AC053DBCBDD80C2915D926972F1AEEB9A4D0DCADFCB10D7CC80C5`。四个 Release 附件摘要与本地一致，具体来源与测试说明在 Release 附件中。

发布前短时采样与早先候选采样分开记录：

| 场景 | 世界 tick 中位数 / P95 | 帧间隔中位数 / P95 |
| --- | --- | --- |
| 基线，120 tick / 709 帧 | 0.8390 / 1.4005 ms | 8.3296 / 9.9236 ms |
| 增加 128 台湿度计，120 tick / 612 帧 | 0.8749 / 1.2173 ms | 8.3433 / 31.9139 ms |

增加仪表组帧间隔 P95 上升，原因未确定，需重复／长期测量。没有性能阈值断言，不能把任务成功写成性能无回退。开发账户 Realms 认证和音频设备选项日志没有阻止测试，未来格式警告属于保护 fixture 的预期，不声称没有环境警告。

完整记录见 beta.7 阶段与发布测试说明（原始证据保留于 Private 档案）。本次文档同步只做事实、链接和差异校验，不作为新一轮游戏回归。

## 同步器受控诊断（显式开启）

仅在隔离客户端 GameTest 中使用，固定于当前 Fabric 客户端 GameTest API 4.2.13 的内部字段和等待调用。普通回归默认不开启；升级依赖时应重新核验注入目标与 fixture，不能直接沿用结论。

```sh
SKYSIGNS_NETWORK_SELFTEST=1 \
SKYSIGNS_NETWORK_DIAGNOSTICS=1 \
SKYSIGNS_NETWORK_SELFTEST_OUTPUT=/absolute/existing-parent/new-selftest \
SKYSIGNS_NETWORK_SELFTEST_COMMIT=$(git rev-parse HEAD) \
bash ./gradlew runClientGameTest --offline --no-daemon
```

输出路径必须是父目录已存在、目标尚不存在的绝对路径。每次使用新路径与独立运行目录，顺序启动客户端。此入口创建独立 `NetworkSynchronizer` 对象，不创建世界、不操作两个真实全局同步器，也不执行默认完整回归。未开启保护时，通知早于等待用例必须复现十秒 invalid 断言，才能判定 fixture 通过；真正未收到包与负计数同样必须报原断言。日志中的 `side=isolated-test-instance` 错误属于预期受控故障，必须结合命令退出 0、`result.json` 的 `passed=true` 及没有 `failure.json` 判断。

对照时额外设置 `SKYSIGNS_NETWORK_WAIT_GUARD=1`。这个默认关闭的测试专用 Mixin 在原锁内重新检查等待条件，让已处理完的包无需等十秒；真正未收到包与负计数仍应失败。它不关闭同步器，不修改计数、invalid 标志、排队任务或原有超时长度。诊断与保护均不进入发布 JAR；普通客户端性能入口拒绝这些额外模式。开启诊断或保护的运行只用于机制核验，不能充当无观测开销的正式性能基线。四种受控用例不覆盖真实世界退出、排队任务压力、多人或长期可靠性，历史故障关联仍待确认。

本轮证据见 同步器竞态核验（原始证据保留于 Private 档案）。

## Windows 长 classpath 的开发客户端入口（Issue #11 候选）

若 Windows 上 `runClient` / `runClientGameTest` 在加载世界前出现 Mixin 插件与接口分属 knot/app 的 `ClassCastException`，先保存日志并检查是否使用了 `gradle-javaexec-classpath*.jar`。该临时 JAR 的 Manifest 可把 Loader/Mixin 依赖间接带入游戏类加载器；不能据此认定为模型或 UI 缺陷，也不应先删除缓存或升级依赖。

候选提供一个显式 Java 参数文件入口，保留原任务的 classpath、游戏参数、JVM 参数、环境和依赖任务，不改变常规入口及 Linux/Cloud 配置。使用 JDK 21，以进程级 JAVA_HOME/PATH 选择同一 JDK；参数文件以 `native.encoding` 编码写入（本机为 GBK），无法编码的路径会报错而非替换字符。

```powershell
.\gradlew.bat runWindowsClient build --offline --no-daemon --init-script scripts/windows-client-argfile.gradle
.\gradlew.bat runWindowsClient -PlaunchTask=runClient --offline --no-daemon --init-script scripts/windows-client-argfile.gradle
```

第一个命令运行原客户端 GameTest 参数，包含新增水下观测断言；第二个进入普通开发客户端。缓存不完整时去掉 `--offline`。仅允许三个客户端任务，不能用此脚本启动专用服务端。普通客户端仍需模型/UI 人工验收；自动回归不替代真实分辨率、手持、掉落物等检查。

水下验收：风向标接触水体时不更新角度，右键提示无法观测；排水后恢复。气象站禁止在水源或流水中放置；放置后接触水体停止观测，已有界面关闭，排水后恢复。气压计和湿度计不在本次行为修改范围。执行记录见 水下行为与启动对照（原始证据保留于 Private 档案）。

维护者发现原候选会把流水转为内部水源。除水修正取消这一转换；检查所在格、上方和四个水平邻格的水体接触，流水自然退去后恢复。桶主动注水仍为原版含水状态，用空桶取出；桶交互优先于观测。`SKYSIGNS_WATER_ONLY=1` 可显式只运行独立水下 fixture，保留同步检查；默认不设置，运行完整回归。专项通过不消除已记录的完整回归阻塞。

## Beta8 修复版候选准备（2026-10-02）

当前 PR #25 版本号准备为 0.6.0-beta.8，尚未发布。修复范围、已有证据、最终发行 JAR 验收与阻塞见 beta.8 候选说明（原始证据保留于 Private 档案）。开发客户端的通过结论不直接扩展至最终 JAR。

## beta.8 修复版已发布（2026-10-02）

v0.6.0-beta.8 Pre-release（原 Private 档案；不随此快照公开） 于 09:08:55（Asia/Shanghai）发布，draft=false、prerelease=true，未设为 Latest stable。附注标签指向 PR #25 的合并提交 90db28dca24933db3fa6eb72ad0485b7e0c3e149；构建提交 19ebe28404aef27bfaedfc6fa360f8aa3d3a3843 与该提交的构建输入无差异，发布同一已验收 JAR。

包 SHA-256 d3d2560763ed4b7be8dc4d21c9dea6f1f82e0ec894975ce69e6db5ed4c02b61f。正式发行实例启动与水下行为/报告关闭/本次模型及中英文基本布局人工验收通过；完整 GameTest 同步器阻塞仍未解决，未作新一轮完整回归。四个附件均已下载重新计算 SHA-256，与本地一致；附件 TEST_REPORT.md 使用 ASCII 名，避免 GitHub 去除中文文件名造成校验清单不匹配。

发布测试说明（原始证据保留于 Private 档案） 和 最终包验收（原始证据保留于 Private 档案） 记录范围。Issue #11 剩余指针/全部模型/分辨率与 GUI 缩放/完整截图仍未完成，保持打开；单人结果不代表多人或专用服务端通过。

## beta.8 气象站窗口与 GUI 缩放实测（2026-10-02）

Codex 使用正式 beta.8 JAR 在隔离单人客户端完成中英文、GUI 1/2/3/自动、真实客户端区 1280×720 至 200×220，以及 320×180 和 239/240 高度布局边界检查。已观察的标准/紧凑面板、手动/自然模式及文本省略正常；英文 200/240×220 的长预报悬停提示右侧被裁切，本轮悬停全文验收失败。

43 张原始窗口截图、日志、退出码与 SHA-256 见 实际窗口验收矩阵（原始证据保留于 Private 档案）。客户端正常保存退出，隔离选项已恢复；没有新增生产代码改动或完整 GameTest 回归，不将本轮部分结果写成 #11 整体通过。

2026-10-02 后续授权修复：提交 `9d50f7d` 将气象站长提示按逻辑窗口宽度折行并限制窗口边距。新候选 JAR 的中英文 GUI 1/2/3/自动、200/240×220 长提示及 320×180 紧凑布局专项复测通过，35 张窗口截图与原始构建/客户端日志见 修复复测（原始证据保留于 Private 档案）。候选未发布；初测历史失败保留，未重跑完整 GameTest，不把本专项通过扩大为 #11 整体完成。

## beta.9 长提示修复版已发布（2026-10-02）

v0.6.0-beta.9 Pre-release（原 Private 档案；不随此快照公开） 于 10:13:27（Asia/Shanghai）发布，draft=false、prerelease=true，未设为 Latest stable。PR #29 合并提交 `5a05e4ae8880b2fdee7727f67a073f1237965ec7` 为附注标签目标；构建来源 `96bf49a888afb44b41c85d66c27e68651b69c7bd` 与合并提交的构建输入一致，交付同一最终验收包。

JAR SHA-256 `29282f19c52af49fb766fe6ae8f6d574e04f48277ee26b0bb86bfacdcd59a83e`。构建与 GameTest 源码编译成功，最终 JAR 独立启动及中英文关键窗口确认通过；完整 GameTest 阻塞保持，未重跑。四个附件先下载重新核对摘要一致再公开发布。详见 beta.9 测试说明（原始证据保留于 Private 档案） 和 发布来源与附件核对（原始证据保留于 Private 档案）。

## Issue #11 人工验收收口（2026-10-02）

维护者在正式 beta.9 隔离单人实例按五组清单检查后明确回复“都是通过”。四种仪器四向放置、顶部/侧面、选取框与碰撞、库存/第一及第三人称/掉落物，0/25/50/88/100 合成指针、四主方向及精确 359°→0°，中英文 UI 与 GUI 缩放确认通过。结合已有真实物理/逻辑尺寸矩阵、长提示修复复测及范围/破坏关闭记录，#11 五项人工标准已覆盖；55 张原始截图、日志快照、场景清单与哈希见 最终人工验收（原始证据保留于 Private 档案）。PR #31（原 Private 档案；不随此快照公开） 已合并，Issue #11 已关闭（2026-10-02 GitHub 核对）。此前 beta.8 / beta.9 发布阶段的部分完成记录按当时范围保留；完整 GameTest 同步器阻塞仍未确认解决。

本轮没有构建或完整 GameTest；既有 Network synchronizer in invalid state 未解决。单人人工结果不证明多人/专用服务端、全部字体/DPI 或整合包兼容通过。上文部分完成与失败记录保留为历史，当前人工进度以本节为准。

## 网络同步器诊断记录（2026-10-02）

完整异常、框架源码时序审查、两组最小空测试、水下专项及无保护受控竞态复现见 首次诊断（原始证据保留于 Private 档案）。受控复现的进程退出 0 表示预期的独立对象故障被断言确认，不表示真实全局同步器故障已经解决。

随后按运行前固定计划顺序执行两组 10 周期的创建/等待/退出/重开对照，共 40 次世界会话；再执行 5 个原有水下/模型方法周期，共 15 次世界会话和 20 张方向截图。进程退出码均为 0，两侧首次 invalid 观察器生效，未自然复现目标错误。后者未执行历史完整入口的前置 UI/自动天气流程；完整入口包含性能探针，在本轮禁止采样的边界下没有执行。详见 生命周期对照（原始证据保留于 Private 档案） 与 模型方法专项（原始证据保留于 Private 档案）。

诊断入口和观察器源码仅作为 `outputs/` 附件归档，不接入默认构建/回归入口。没有修改原有断言、启用等待保护、禁用同步器或启动正式性能采样；只记录诊断有时序开销，不能用于性能结论。自然故障仍未解决，证据附件的已提交内容与原始 ignored 运行文件应分别核对。

## Issue #9 的 9 月 30 日历史测量归档

2026-09-30 测量记录（原始证据保留于 Private 档案） 保留当日三轮主世界 tick 基线，以及详细计时在 0 台窗口退出后中止的失败和缺失范围。该记录是历史快照；其中“#9 尚未完成”不代表当前状态。后续补测与测量任务收口见 [Issue #9 本地补测](ISSUE9_LOCAL_ACCEPTANCE.md) 和 [路线图](ROADMAP.md)，同步器自然故障的当前边界见上文诊断记录。本次只补充文档归档与索引，没有重新执行构建、GameTest 或性能采样。

## Issue #12 最新主线渲染兼容矩阵（2026-10-03）

六组记录与完整证据（原始证据保留于 Private 档案） 基于 main 23ccdc3 的未发布测试包：Fabric、Sodium、Iris 无光影限定场景通过；Photon 默认闪光缩放后闪电黑屏。关闭闪光或缩放后重载，在本轮受控闪电序列未出现黑屏；重载组前置降雪场景另有黑屏，不扩大为全程通过。光影运行中开→关→开及降水倾斜恢复/退让有配置与截图记录。未重跑完整 GameTest、性能、多人或专用服务端，Issue #12/#35 保持开放。

## Local 天气渐变与 #13 同步调查（2026-10-07）

完整方法、原始数据与方案（原始证据保留于 Private 档案） 基于 PR #44 合并后的 `a822e09`，只添加独立调查夹具，不改生产代码／协议。九个风况窗口、六条原版天气转换、32 张图及跨零点／reset／维度／重开首快照有逐 tick 记录；491 次发送与 491 次接收配对一致，codec payload 为 8 B，不能写成公网完整包尺寸。

专项客户端最后在存档重开后的 `waitForChunksDownload()` 方形 FULL 区块就绪条件超时，运行失败；离线数据检查通过不改变此结果。首次两次夹具失败／中止同样保留。没有禁用同步器、启用保护或重跑制造通过，不是默认完整回归，也未验证新功能性能、光影、多人／专服。#13／Project 不自动收口。

### 重开区块就绪定向诊断（2026-10-07）

三组原始证据与归因（原始证据保留于 Private 档案） 在产品最小场景、不加载 Sky Signs 的框架对照和原调查存档副本中复现原生等待超时：视距 8 要求 289 个方形区块，而其中四个角不在正常玩家发送范围，客户端稳定收到其余 285 个。服务端四角已 FULL，因此支持等待条件与发送形状不一致的夹具归因；原调查失败和旧同步器问题分别保留。三个运行都在默认 1,200 tick 超时，没有删除等待、降低视距或实现新的就绪检查；诊断证据核验不是回归通过。

## 独立 NetworkSynchronizer 源码修复候选（历史交付）

2026-10-09 的独立候选已捕获一次自然生命周期故障：最后包已完成且通知早于 await，计数／队列均为零，仍原样等待 10 秒后失效。源码修复、未收包／队列／负计数控制和完整验证结果见 诊断及验收记录（原始证据保留于 Private 档案）。该记录对应独立交付时的候选状态；随后 #49 已合并，五轮 Local 与最终 main 补测见本页开头。它与 P1 产品实现及 #48 区块就绪检查的职责仍分开。

仅显式给 `runClientGameTest` 加载 `scripts/network-synchronizer-patch.gradle`、`-PnetworkSynchronizerPatch=true` 和 `-PnetworkPatchRoot=<独立候选根目录>` 时使用补丁。脚本复制固定上游测试模块，仅替换一份外部类，保留原缓存、生产依赖及所有其他模块条目。启动必须出现 `NS_PATCH_ACTIVE`，并核对 `disabled=false oldGuard=false` 和类摘要；缺证书或摘要不匹配则失败。

完整回归仍用 PR #48 的显式视距 8 就绪实现和原断言，在新的隔离实例执行。此源码修复不改变原生下载方格的四角几何；不能把原 `waitForChunksDownload()` 的 289 方格等待写成已经修好。Windows 操作及组合候选精确来源见独立记录中的 Local 回交说明。
