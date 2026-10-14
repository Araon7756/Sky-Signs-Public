> 公开快照说明：本页保留历史设计、候选或验收记录；日期、提交和当时的“尚未发布”等状态仅适用于对应历史阶段。当前发行事实与公开快照验证见 [PUBLIC_SNAPSHOT.md](PUBLIC_SNAPSHOT.md)。原始证据、旧 PR/Issue 和提交历史保留于 Private 档案，未随快照迁移。

# Sky Signs 0.7：分阶段天气与可配置环境风设计

日期：2026-10-07（Asia/Shanghai）。状态：P0 设计稿，供维护者审阅；不是功能实现、性能验收或发布记录。

P1 候选状态（2026-10-07）：维护者已授权服务端实现与 PR，manual 保留原版时长和自然循环。候选固定 A=1200、B=400、REBASE=400 tick，并按本稿规则裁剪；不实现环境风、设置 UI 或协议 v3。实现、实际验证和 Local 未验项目见 P1 交付（原始证据保留于 Private 档案）。本文其余 P0 推荐及历史未测试记录保留，不能据候选认定全部 0.7 功能完成。

实际基线：开始前成功执行 `git fetch origin`，`origin/main` 与工作区 HEAD 均为 `0dc91a7ce10ee33bd66c845cf1b8fcf5831b1a86`。文档分支为 `codex/0-7-design`。本稿关联 #37（原 Private 档案；不随此快照公开），测量依赖 #13（原 Private 档案；不随此快照公开），兼容风险 #12（原 Private 档案；不随此快照公开）、#35（原 Private 档案；不随此快照公开）。Project #1 当前字段未读取、未验证；Issue 中的历史字段说明不替代本次 Project 读取。

本文用三种标记区分依据：**源码事实**指上述提交或明确版本的 API／映射可核对内容；**设计推荐**指后续实施契约；**待 Local 验证**指真实客户端、视觉、声音、游戏行为或性能尚无本任务证据。代码事实不能由设计推荐替代。

## 1. 范围与产品行为

**设计推荐：**首个 `0.7.0-beta.1` 保留 `CLEAR / RAIN / STORM` 三种逻辑天气，增加前兆、过渡、发生与缓和的显示语义。预报、气压、湿度天气基值与自动风区间读取同一服务端计划。环境风采用风声和可选的稀疏粒子；不新增风向袋、玩家推力或运动物理。

每名玩家独立控制粒子开关、粒子表现强度和风声。粒子强度主要调密度，不改变速度所代表的真实风况、仪表或服务端天气。设置集中到原版风格的 Sky Signs 页面：beta.1 首先开放环境风，评估纳入既有降水倾斜选项，后续沿同一入口扩展。

前兆 60～120 秒、过渡／缓和 20～40 秒、默认粒子开启且密度 50% 均为**待 Local 验证的原型参数**，不是性能保证。零星小雨和天空变阴先调查；多云、完整服务端配置、多平台支持不进入 beta.1 本设计核心。保留现有 Iris 光影启用时的降水倾斜退让，不承诺全部光影支持。

本 P0 只新增本文件并在 [路线图](ROADMAP.md) 增加入口。运行代码、资源、依赖、协议、结构版本和 `mod_version` 均未修改；不创建 tag／Release，不修改 Issue／Project，不合并 PR。后续运行代码任务须经设计审阅并确认具体范围。

## 2. 基线事实与设计接入位置

| 位置 | 源码事实 | 设计推荐 |
| --- | --- | --- |
| [WeatherState](../src/main/java/cn/skysigns/weather/WeatherState.java) | WEATHER 结构 1；保存 `type / forecast / phase_started_at / next_change / automatic / initialized`；原始 NBT 副本保留未知字段 | 结构 2 保存完整计划与连续曲线锚点；阶段由时刻推导 |
| [WeatherManager](../src/main/java/cn/skysigns/weather/WeatherManager.java) | 每 20 tick 推进；初始化延迟 1200 tick；晴 4～7、雨 3～5、雷暴 2～4 分钟；`get` 初始化并采纳不同原版天气；查询可能选取随机新计划 | 把初始化／采纳／推进收敛到服务端更新入口；查询只读取已提交的计划视图 |
| [AtmosphericReadings](../src/main/java/cn/skysigns/weather/AtmosphericReadings.java) | 气压整阶段 smoothstep 插值，加 `sin((getTime + dimensionOffset)/360) * 1.2 hPa`；湿度按当前 type 切换；气压专用路径不扫描水 | 用共享阶段曲线替换天气基值，不增加水体扫描 |
| [WindState](../src/main/java/cn/skysigns/weather/WindState.java)、[WindManager](../src/main/java/cn/skysigns/weather/WindManager.java) | 风结构 1；逐 tick 最短角度转向、强度每 tick 最多变化 0.0025；自动目标每 20～45 秒选取，按当前天气区间；手动风保留 | 按计划插值后的区间选目标；继续保留现有风目标存档与渐变 |
| [SkySigns](../src/main/java/cn/skysigns/SkySigns.java)、[WindVisualPayload](../src/main/java/cn/skysigns/network/WindVisualPayload.java) | END_WORLD_TICK 先天气后风；每 20 tick 发送方向／强度两个 float，无维度或序号 | 先保持同步行为，原始快照与客户端效果分离；优化依赖 #13 |
| [StationReportPayload](../src/main/java/cn/skysigns/network/StationReportPayload.java)、[ObservationInstrumentBlock](../src/main/java/cn/skysigns/block/ObservationInstrumentBlock.java) | v2 六个 Text、三项数值、坐标、flags、剩余秒；进度是整阶段到 `next_change`；仪表按位置错峰秒采样 | 保持仪表采样边界；阶段协议单独决策 |
| [SkySignsClient](../src/client/java/cn/skysigns/client/SkySignsClient.java)、[WindPrecipitationVisual](../src/client/java/cn/skysigns/client/render/WindPrecipitationVisual.java) | 接收风后直接转为倾斜目标；倾斜平滑系数 0.2；两个 properties 启动读取；退出／世界对象变化清零 | 共用未乘配置的客户端风状态，倾斜／粒子／声音各自消费 |
| [WeatherStationScreen](../src/client/java/cn/skysigns/client/screen/WeatherStationScreen.java) | 自绘气象站面板，每 20 tick 请求刷新；以坐标匹配报告；不是设置页，无统一设置入口 | 新建独立原版控件设置页，气象站可提供辅助入口 |

依据还包括 [SAVE_DATA](SAVE_DATA.md)、[KNOWN_ISSUES](KNOWN_ISSUES.md)、[TESTING](TESTING.md)、[build.gradle](../build.gradle)、[gradle.properties](../gradle.properties)、fabric.mod.json（原始审计收据保留于 Private 档案）、客户端 mixin 声明（原始审计收据保留于 Private 档案）、[降水渲染注入](../src/client/java/cn/skysigns/mixin/client/WeatherRenderingMixin.java)。当前 Minecraft 1.21.10、Yarn `1.21.10+build.3`、Fabric API `0.138.4+1.21.10`、Java 21；仅有天气降水客户端 Mixin，没有服务端天气中断钩子。

相关断言为 [SkySignsClientGameTest](../src/gametest/java/cn/skysigns/test/SkySignsClientGameTest.java)、[SaveMigrationAssertions](../src/gametest/java/cn/skysigns/test/SaveMigrationAssertions.java)、[ClimateAssertions](../src/gametest/java/cn/skysigns/weather/ClimateAssertions.java)、[水下断言](../src/gametest/java/cn/skysigns/test/WaterObservationAssertions.java) 和 [湿度计性能入口](../src/gametest/java/cn/skysigns/test/HygrometerPerformanceTest.java)。现有原版命令、睡眠、重开、维度断言不覆盖新的阶段契约或同天气改时长；本任务没有执行这些断言。

## 3. 统一计划与六条转换

### 3.1 时刻与阶段契约

**设计推荐：**每个适用维度拥有一个权威计划。计划只在初始化、前一计划完成或明确中断时生成；随机选取目标天气和时长后保存，查询、站刷新和存档重开不重抽。

定义 `S = phase_started_at`（计划起点）、`P = precursor_at`（前兆起点）、`T = next_change`（逻辑天气切换）、`E = transition_end`（过渡终点）。持有 `from_type` 与 `forecast`（该计划已确定的目标），满足 `S <= P <= T <= E`。

| 时间区间 | 显示阶段 | 逻辑 type | 预报与边界行为 |
| --- | --- | --- | --- |
| `[S,P)` | 发生／稳定 | from_type | 已确定目标 forecast；倒计时到 T，稳定期不会提前改原版旗标 |
| `[P,T)` | 前兆 | from_type | 同一目标逐渐接近；此阶段没有独立持久化天气类型 |
| `[T,E)` | 过渡；湿转晴显示“缓和” | forecast | 在首个不早于 T 的推进点更新逻辑 type 并调用原版 setWeather；显示目标正在发生，倒计时到 E |
| `E` 及以后 | 目标完全发生 | forecast | 完成旧计划后一次性生成新计划，新 S=实际完成 tick；先前目标不再作为“下一天气”展示 |

服务端每 tick 检查边界，避免保留当前 20 tick 推進带来的最多 19 tick 切换误差；昂贵仪表采样与网络发送仍维持原节奏。对一个 tick 内跨过 T、E 的情况，先确定最终逻辑状态、最多提交一次旧目标并生成一次新计划，不追赶生成大量遗漏周期。纯查询按快照推导阶段，不执行这些提交。

正常新计划在稳定开始 S 选取原有天气时长范围 L，令 T=S+L；前兆长度 A、过渡长度 B 同时选定。初始化可沿用 L=1200 tick 的短首周期。L 是稳定开始到逻辑切换的长度；加上上一段过渡后，逻辑切换间隔可能比旧版本长 B，此体验变化须 Local 评估。T 时原版天气持续时间要覆盖到计划结束和下一次目标切换，不沿用可能早于计划截止的原版独立随机倒计时。

短周期采用确定性裁剪：`L=max(0,T-S)`，`A'=min(A,floor(L/2))`、`B'=min(B,floor(L/4))`，`P=T-A'`、`E=T+B'`。L 为 0／1 时允许零长度阶段，直接完成；无条件最低时长不得把有效旧 T 推迟。P=T 表示无前兆，E=T 表示无过渡。这些界限同时用于新计划、旧计划接入及命令重排，不能查询时重新抽 A、B 或目标。

### 3.2 六条不同三态转移表

下表均遵守 P 开始前兆、T 切换逻辑旗标、E 完成；“雨雪”由当地群系／温度／遮挡决定。

| 转移 | `[P,T)` 前兆与读数方向 | T 原版旗标 `(raining, thundering)` | `[T,E)` 表现与 E 结果 |
| --- | --- | --- | --- |
| CLEAR→RAIN | 气压下降、湿度与自动风区间上升；阴天可行性待调查 | `(true,false)` | 进入雨雪的过渡，E 后逻辑雨稳定 |
| CLEAR→STORM | 更低气压、更高湿度与风区间；不把雷声／闪电提前伪造为已发生 | `(true,true)` | 进入雷暴；雷电实际触发遵守原版，E 不保证已出现闪电 |
| RAIN→STORM | 降水逻辑维持，气压再降、湿度／风区间提高 | `(true,true)` | 雷暴增强过渡，不经历晴或重复启动降雨 |
| STORM→RAIN | 气压回升、湿度／风区间回落，仍为湿天气 | `(true,false)` | 雷暴减弱为雨；显示“过渡”，不另建缓和段 |
| RAIN→CLEAR | 气压回升、湿度／风区间回落；T 前仍允许当地降水 | `(false,false)` | 同一过渡显示“缓和”，E 后晴；没有额外结束计时 |
| STORM→CLEAR | 气压回升、湿度／风区间回落；不强制经过 RAIN | `(false,false)` | 雷暴直接缓和为晴，E 后晴；不叠加雨→晴阶段 |

**待 Local 验证：**原版逻辑旗标与雨／雷渐变不同步，B=20～40 秒不自动改变原版渐变速度。beta.1 首选原版渐变＋连续仪表／风反馈，E 是计划完成时刻，不声称原版画面持续整个 B。如果需要控制 rain/thunder gradient 的速度，必须核查 1.21.10 更新路径、同步和 Iris 行为，并另行确认实现范围；P0 不承诺真实降雨强度系统。

## 4. 气压、湿度、自动风与预报连续性

### 4.1 共用连续曲线

**设计推荐：**令 `h(x)=x²(3-2x)`，x 限制在 0～1。当两段都有长度时，统一权重 w：稳定期 0；前兆为 `0.5*h((t-P)/(T-P))`；过渡为 `0.5+0.5*h((t-T)/(E-T))`；E 后为 1。只有过渡时用 `h((t-T)/(E-T))`；只有前兆时用 `h((t-P)/(T-P))`，不用半程系数。两段均为零时逻辑立即完成，读数锚点交给下一计划的 REBASE 短接入，不瞬间跳变或除零。LEGACY 仍按第 6 节的独立兼容求值。

正常新计划气压基值从 1020／1005／989 hPa 插值到目标；保留原来的维度偏移和 ±1.2 hPa 微扰。趋势由基值曲线当前斜率／剩余目标差解释，稳定期与暂停显示稳定；不因微扰反复切换趋势，不在已经完成目标的过渡末端继续称“下降”。气压计与站报告共用同一个求值入口。

湿度先算天气基值：有降水群系 CLEAR/RAIN/STORM 为 38/82/93，无降水群系为 38/46/52。按同一 w 插值后，加现有 downfall、温度、海拔、水体修正，再一次性取整并 clamp 5～100。不要分别调用两次旧 humidity 方法插值最终值：那会重复最坏 245 次流体查询，且提前截断产生不正确曲线。一次湿度测量只取一次气候样本和一次 `hasNearbyWater`，气压专用路径仍不触及水体；本设计不实现缓存。

当地降水仍取仪表上方 `pos.up()` 的原版降水／群系／遮挡结果，并使用当前原版视觉状态区分无降水、雨、雪、遮挡与无降水群系；不能仅凭 w 或逻辑 type 报告“这里正在下雨”。湿度与当地降水是两个不同读数。

### 4.2 自动风与手动风

自动风区间沿用晴 `[0.05,0.52]`、雨 `[0.35,0.78]`、雷暴 `[0.72,1.00]`，用同一 w 插值区间端点。每次现有 20～45 秒目标调度从当前区间选目标，方向延续已有有界随机偏移和最短角度转向；前兆开始时若下一风调度超过 T，则把它提前到 P 一次，保证前兆内有目标调度机会，具体可感知程度待 Local 验证。T 不再调用按新 type 突然重抽的旧 adaptToWeather 逻辑。

区间表示**目标选择范围**，并非要求实际风强逐 tick 被夹进区间；现有 WindState 平滑会有滞后。不得夹断实际风值造成仪表跳变。预报承诺目标天气及风范围走势，不承诺某个精确随机风强。切换后续目标只在风调度推进时发生，查询不随机；WIND 只保存现有当前值、目标值、截止 tick 和模式，因此无需新增结构字段，保持 1。若 Local 要求严格跟随整条风曲线，则须另议持久化随机样本／锚点及可能的 WIND 结构变化。

手动风不被天气自动、命令、睡眠或气压迁移覆盖；风声、粒子与风向标如实使用手动风。UI 粒子密度不参与这些运算。WEATHER 只读时自动风不解释未知阶段，保持可读取的既有风状态和自身调度，不利用未来天气字段随机生成新天气；WIND 只读时不写风。

### 4.3 中断的连续接入与预告边界

中断时先取**旧计划在事件 tick 的基值**作为锚点，未混入气候修正或气压微扰。新目标在 400 tick 的短接入期用 h 连续趋近，期间逻辑天气可立即由命令改变，仪表明确显示“调整中”。手动模式的接入完成后固定目标基值，预报“手动”，不伪造自动下一天气。此 400 tick 也是原型参数，需 Local 验证。

正常计划中当前阶段、目标和时间来自同一不可变视图。T 前只承诺当前保存的下一目标；T 后显示“正在进入目标／缓和”，不得把尚未安排的下一轮随机天气当作确定预告。外部命令或睡眠会明确取消旧承诺；远期趋势保持未确定，不扩展为现实概率预报。

## 5. 中断、时间与可用接入点

**源码事实：**当前 `WeatherManager.get` 只通过逻辑 type 不同识别外部变化，漏掉相同天气修改时长；没有读取 `doWeatherCycle`。现有 Sky Signs 命令直接调用 setManual／enableAutomatic，睡眠断言检查了晴旗标和模式保持。getTime 和 getTimeOfDay 是两个不同接口，不应用昼夜时间作为计划时钟。

本次另外核对了 [Yarn 1.21.10+build.3 映射](https://maven.fabricmc.net/net/fabricmc/yarn/1.21.10+build.3/yarn-1.21.10+build.3-v2.jar) 中存在 `ServerWorld.setWeather(int,int,boolean,boolean)`、`resetWeather()`、`tickWeather()`、`setTimeOfDay(long)`，`WeatherCommand.executeClear/executeRain/executeThunder`，`TimeCommand.executeSet/executeAdd` 和 `ServerWorldProperties.getClearWeatherTime/getRainTime/getThunderTime`。这是**方法签名事实**，未反编译或运行核实所有调用路径。后续代码任务须在实际依赖的源码／字节码上确认调用方、注入点和计时行为，不能把签名存在写成钩子已实现。

| 事件 | 设计推荐：服务端规则 | 接入与验证界限 |
| --- | --- | --- |
| `/skysigns weather set …` | 立即逻辑切换，取消旧计划、保留手动模式；短接入读数，手动风不变 | 已有 SkySignsCommands 调用点可直接调整；命令前检查 WEATHER 只读 |
| `/skysigns weather auto` | 从当前读数锚点接入；一次性创建完整新计划，保留当前可见逻辑天气 | 已有调用点；不要先修改原版再发现只读 |
| 原版 `/weather`，包括同天气改时长 | 采纳实际旗标及命令有效时长；保持 Sky Signs auto/manual 选择。自动模式取消旧目标、一次性重排下一目标，T 对齐命令实际有效截止；手动模式不自行到期切换 | 推荐在确认过的 WeatherCommand 成功执行末端采纳，再辅以 setWeather 外部写入通知；自有写入 guard 防止递归。仅比较 type 不够，不能把每 tick 自然递减误判为新命令 |
| 睡眠清天气 | 取消旧计划并采纳实际晴旗标；保持模式，自动模式创建晴的新完整计划，手动模式晴固定 | 推荐 resetWeather 事件＋tick 后旗标核对；睡眠是否在 gamerule 关闭时清雨、哪些维度生效待 Local 实测 |
| `doWeatherCycle=false` | Sky Signs 自动推进暂停；记录 pause_at，冻结阶段与气象基值，不冻结命令写入。manual 模式无需自动循环 | 推荐在每 tick 服务端入口读 gamerule；目前代码未如此实现 |
| `doWeatherCycle=true` | 恢复时把 S/P/T/E、接入锚点时间整体平移 `now-pause_at`，保持剩余时长与目标；不重抽 | 暂停期间发生命令则在暂停时刻建立替代计划，仍保持暂停；再次恢复仅平移替代计划 |
| 暂停期间保存／重开 | pause_at 与锚点持久化，逻辑旗标尽力对齐；读数用冻结的有效 tick | 恢复且已开 gamerule 时只解冻一次；不根据墙上时钟推进 |
| 普通保存／重开 | 恢复原计划与风目标，离线时间不算 tick；首次更新若已经到期则按第 3 节完成一次 | 状态与原版实际不同先按明确中断处理；未来格式不采纳覆盖 |
| 玩家维度切换 | 每维度计划不互相复制；无天光或有顶维度不自动生成降水计划，显示不适用；客户端清风、粒子和声音 | 维度适用判断沿用当前 hasSkyLight／hasCeiling；自定义维度需 Local 核对 |
| `/time set/add` 与睡眠跳昼夜 | 计划使用单调游戏 tick getTime，不随 getTimeOfDay 平移或提前到期 | 签名区分已确认，实际命令／睡眠是否只变昼夜时钟须源码调用链核查与 Local 断言 |
| 异常时钟回退／跳跃 | 回退时用上次有效 tick 与当前 tick 的差平移计划，保留剩余时长；大幅前跳至多完成一次旧计划 | 用运行时 last_tick 检查；加载首次不把离线时间当回退；加减检查 long 溢出，损坏时间进入保护 |
| 未来／无效版本 | 保留原始载荷、只读，停止对应天气更新／迁移／命令／中断采纳；显示“不支持的存档格式” | 原版 `/weather` 与其他模组仍可变天空；保护仅覆盖模组 NBT，不覆盖整个世界 |

暂停冻结的是计划曲线，气压微扰可继续按游戏时间变化，但趋势仍显示暂停／稳定。自动风的独立小幅变化可以继续在冻结区间调度，gamerule 不隐式改变手动风或 WIND 模式；产品说明须清楚。若要连风一起冻结，需要另定规则。

跨模组调用建议用自有写入 guard 包住 applyVanillaWeather，并以事件序号每 tick 合并重复通知；同一次命令只重排一次。更新优先级为：只读保护 → 当 tick 明确命令／睡眠等中断 → gamerule 暂停／恢复 → 到期计划推进 → 未分类的原版状态核对。自动计划持有期间，用自有写入保持原版计时器覆盖 T，T 提交时覆盖 E，在 E 新计划生成后对齐新的 T；所有这类写入均受 guard 保护。不能把自有计划造成的原版自然到期先采纳为外部中断而取消已预告目标。原版时长单位、计时器递减／自动翻转顺序和注入时机须在 P1 核查，必要时采用计划更新前后的明确仲裁，未核查前不视为可靠实现。没有可靠事件来源时，旗标／计时器快照只能识别部分外部写入，无法无损识别“恰好同值”的调用；应声明限制并增加受影响用例，不把计时器比较声称为完整命令钩子。

## 6. WEATHER 1→2 存档设计

**设计推荐：**只将 WEATHER 升为 2；WIND/METER 保持 1。此处是拟议字段，不是当前 SAVE_DATA 的已实现格式。继续使用各维度的 `skysigns_weather.dat` 外层 `data`，版本字段必须为 NBT Int，与发布版本和原版 DataVersion 分开。

### 6.1 字段表与不变量

| 字段 | NBT 类型／单位 | 结构 2 语义与默认／校验 |
| --- | --- | --- |
| skysigns_data_version | Int | 2；未来、负数、非 Int 均保留原始载荷只读 |
| type | String | 当前逻辑 CLEAR/RAIN/STORM；自动计划 T 前=from_type，T 后=forecast；手动固定 |
| forecast | String | 本计划已确定目标；有效旧值不重抽；手动无计划时=type |
| phase_started_at | Long tick | S；保留旧有效值，正常新计划在前一计划完成时设置 |
| next_change | Long tick | T，仍为逻辑切换截止，不改成 E；保留旧有效值 |
| automatic / initialized | Byte boolean | 保留旧模式／初始化语义 |
| from_type | String | 计划源天气；迁移为旧 type，保留至 E |
| precursor_at / transition_end | Long tick | P/E；按第 3 节裁剪，满足顺序不变量 |
| curve_mode | String | NORMAL、LEGACY、REBASE；不认识的模式保护原数据，不按 NORMAL 猜测 |
| anchor_at / anchor_end | Long tick | REBASE 的连续接入起止；其他模式保存 S/S，避免含糊缺省 |
| pressure_anchor | Float hPa | 未加微扰的气压基值；NORMAL 为源基值，REBASE 为事件当时值 |
| humidity_wet_anchor / humidity_dry_anchor | Float 百分点 | 未加气候修正的两类天气湿度锚点；不能保存某位置的最终湿度 |
| wind_min_anchor / wind_max_anchor | Float 0～1 | REBASE 的风区间锚点；NORMAL 为源天气区间，不能替代 WindState 的实际值 |
| paused / pause_at | Byte boolean / Long tick | 是否冻结与冻结时刻；未暂停 pause_at=0；暂停的有效 tick 为 pause_at |

`curve_mode` 决定基值求法：NORMAL 使用第 4 节统一 w；REBASE 在 `[anchor_at,anchor_end]` 对锚点到 **from_type 基值**做 h 插值，随后连接 from_type→forecast 的正常前兆／过渡。不能在 T 后用已经变化的 type 重新解释接入目标。创建时 anchor_at=S、anchor_end=min(S+400,T)、P=max(T-A',anchor_end)，短命令确定性缩短接入／前兆，不推迟 T。LEGACY 使用下节规则。

手动模式 from_type=forecast=type，S/P/T/E 均为命令事件的有效 tick，REBASE anchor_end=S+400；先完成接入再保持手动基值，不运行自动时间表。零长度自动计划完成时，在同 tick 新计划的 REBASE 中承接实际基值，接入目标为该新计划的 from_type，不能用零接入时长跳到目标；第 3 节每 tick 最多生成一次的规则防止递归。NORMAL 的锚点字段只作源基值记录，REBASE 求值必须先于阶段权重。

完整结构 2 重复加载幂等。校验未知 type、字段类型、非有限锚点、区间反序与时间溢出：不能把无效版本当 0，也不能任意重抽计划；结构 2 关键计划损坏时保留原载荷只读并给出诊断，避免 PersistentStateManager 解码异常回落到默认天气。新增可选字段可按表确定性补齐，关键字段无法确定则保护。

### 6.2 逐级迁移与旧曲线接入

继续保留 `SaveDataMigrations.upgrade` 的副本＋逐级循环：0→1 沿用现有步骤，WEATHER 1→2 追加独立分支，不能直接 0→2。每步只补充／转换属于自身的数据，不删除未知键。缺迁移步骤返回原始输入只读，不抛出导致默认重建；版本 2 保存仍从 storedData 副本写回已知字段。

1→2 不使用随机数或当前世界时间。旧有效 type、forecast、S、T、automatic、initialized 原样保留；P 根据旧 L 与固定推荐 A=1200 tick 裁剪，E=T（仅这个剩余旧计划无额外过渡），`curve_mode=LEGACY`，锚点为旧 type 基值。旧 manual 仍固定当前基值；initialized=false 留待常规一次初始化，不能迁移时随机生成。

自动 LEGACY 气压继续使用旧 `h(clamp((t-S)/(T-S)))` 整阶段曲线与旧微扰，确保安装瞬间对任意有效 t 都连续；趋势按旧 source/target 解释。湿度也在该剩余计划保持旧 type 基值。不能简单把旧气压改为前兆窗口 w，否则升级时会跳变。

T 到达时采用旧 forecast，恰好 E=T，气压已在目标基值。湿度从旧 type 基值用 REBASE 400 tick 接到目标，而非瞬间跳变；同步把风区间从源接向目标。建立新目标计划时保持从实际当前基值起步，P 不早于接入结束；第一次新计划后使用 NORMAL。LEGACY 是兼容桥，不承诺旧计划已具有完整新阶段视觉。

有效旧 T 已过期时迁移仍保留它，首次运行按一次完成规则处理，不在迁移重抽。旧时间反序或不可解释的类型／时间字段先保护并诊断；缺字段可沿用 0→1 明确默认，但不能宣称损坏输入已恢复原有效预报。实际实现须加入磁盘 fixture 并验证升级前后同 tick 气压值。

### 6.3 拟议 SNBT fixture

以下均为 `.dat` 外层 `data` 的内容示例，不是本任务生成或跑过的世界。Long 带 L，Float 带 f，boolean 使用 Byte；未知字段 `extension_note` 应保留。

正常自动晴→雨计划（例如有效 tick=14000，S=10000、P=13600、T=16000、E=16800）：

```snbt
{
  skysigns_data_version: 2,
  type: "CLEAR", forecast: "RAIN", from_type: "CLEAR",
  phase_started_at: 10000L, next_change: 16000L,
  automatic: 1b, initialized: 1b,
  precursor_at: 13600L, transition_end: 16800L,
  curve_mode: "NORMAL", anchor_at: 10000L, anchor_end: 10000L,
  pressure_anchor: 1020.0f,
  humidity_wet_anchor: 38.0f, humidity_dry_anchor: 38.0f,
  wind_min_anchor: 0.05f, wind_max_anchor: 0.52f,
  paused: 0b, pause_at: 0L, extension_note: "keep"
}
```

暂停的雨→晴缓和（例如保存世界 tick=22000，有效 tick=20500）：

```snbt
{
  skysigns_data_version: 2,
  type: "CLEAR", forecast: "CLEAR", from_type: "RAIN",
  phase_started_at: 16000L, next_change: 20000L,
  automatic: 1b, initialized: 1b,
  precursor_at: 18800L, transition_end: 20800L,
  curve_mode: "NORMAL", anchor_at: 16000L, anchor_end: 16000L,
  pressure_anchor: 1005.0f,
  humidity_wet_anchor: 82.0f, humidity_dry_anchor: 46.0f,
  wind_min_anchor: 0.35f, wind_max_anchor: 0.78f,
  paused: 1b, pause_at: 20500L, extension_note: "keep"
}
```

第二例在 now=23000 恢复时平移 +2500：S=18500、P=21300、T=22500、E=23300、anchor_at=anchor_end=18500，paused=0、pause_at=0；type/forecast/from_type 不变，仍剩 300 tick 缓和。保存重开若仍暂停，则不做平移。

迁移对照输入为 `{skysigns_data_version:1,type:"STORM",forecast:"RAIN",phase_started_at:4000L,next_change:18000L,automatic:1b,initialized:1b,extension_note:"keep"}`。输出保留全部原字段，新增 from_type=STORM、P=16800、E=18000、LEGACY、anchor_at=anchor_end=4000、pressure_anchor=989、两湿度锚点=93/52、风区间=0.72/1、paused=false、pause_at=0，并只把结构号改为 2。无版本同输入必须先 0→1 再得到相同结果；这与现有迁移测试的历史字段一致。

## 7. 站报告与风况协议选择

**源码事实：**`station_report_v2` 使用六个可翻译 Text，三个 VAR_INT 读数、LONG 坐标和两个 VAR_INT 时间信息。flags 低 7 bit 是 0～100 整阶段进度，`0x100` 为打开、`0x200` 为手动；没有独立阶段字段。`wind_visual` 仅两 float，编码内容为 8 字节；每秒 8 字节／玩家仅是内容计算，不包含通道 ID、帧头、压缩或网络实际流量，更不是 #13 实测。

| 方案 | 编码兼容 | 语义与限制 | 推荐 |
| --- | --- | --- | --- |
| v2 文本复用 | 不改 ID、字段顺序、flags 和 codec | weather／forecast Text 可解释前兆，但新翻译键在旧客户端可能裸显；进度与秒数仍表示 S→T，不能悄悄改成子阶段或 E。过渡期需诚实显示文本，旧计时保持到逻辑切换已完成的 100%／0 秒 | 无结构化阶段 UI 时的保守候选；同版本客户端优先，老客户端使用已有键或 literal fallback，实际语言行为待验证 |
| 新 `station_report_v3` | 新 ID 与独立 codec；通过 canSend 选择，不向 v2 追加数据 | 明确逻辑 currentType、targetType、stage、stageProgress、到边界的剩余 tick／秒、auto/paused/open；原文本与数值仍可用于显示。维度、会话／请求标识、计划序号需要与 #13 一起决定 | 推荐需要阶段进度条时采用；先确认协议范围，再实施 |

不占用 v2 “空闲 bit”来暗改阶段含义。v3 推荐 stage 为版本固定的枚举（稳定／前兆／过渡／缓和／手动／不适用／只读），不使用发布版本号作协议枚举。v2 fallback 只保留其旧计时语义；T 后尚未生成后续预报时不得伪造下一目标倒计时。旧端是否必须同版本更新由维护者决定，编码能解码不等于语义兼容。

风况先保留每秒发送与现有 payload；后续 #13 测量静态手动、自动变化、天气切换的包量，比较角差最短路径阈值、强度阈值、保底周期与首次快照。原型候选可以讨论角差 2°、强度差 0.02、保底 5 秒，但未经测量不固定为实现参数。加入世界／重连／维度切换后应先获得新世界快照，不能仅等变化阈值。

当前两个 payload 无维度／会话标识，客户端收到包时传入“此刻 client.world”不能证明包来自该世界。清状态只消除缓存继承，不能保证拒绝切换前迟到包；同坐标旧站报告也可能误匹配。v3 和可能的新风况 ID 应评估维度键＋连接会话／请求 token＋序号：仅维度键无法拒绝往返同一维度的旧包。机制选定前把迟到包列为待验证风险，不写成已发生 Bug。本 P0 不实施协议或发送行为变化。

## 8. 客户端共用风状态

**设计推荐：**独立客户端风状态持有原始服务端方向／强度、最近快照有效性和 world 身份，以及未乘用户设置的平滑方向／强度。网络接收先检查有限数值，方向归一到 `[0,360)`，强度 clamp `[0,1]`；非法包忽略，不用 NaN 污染旧有效状态。首次合法快照直接初始化共用状态，视觉／音量可另用短淡入，避免从任意 0° 绕远路。

方向使用 `wrapDegrees(target-current)` 最短路径，重点验证 359°→0°；强度有界趋近，不外推天气。平滑时使用客户端有效 tick／帧 delta，不因暂停菜单或异常大 delta 瞬间跳动；原有 0.2 可作为候选，实机决定最终值。原始值可以用于诊断，仪表仍是服务端权威，不显示客户端平滑值为服务端真值。

三个消费者分别控制：降水倾斜保留旧 enabled/strength 与 Iris 判断；粒子密度只影响生成；声音开关／音量独立。关闭倾斜或开光影不清共用风，不连带关闭风声。关闭粒子不停止接收服务器数据，也不破坏声音所需状态。

现有倾斜上沿 `(x,z)=(-sin θ,+cos θ)*tilt` 是**上风侧偏移**。风的运动方向应为 `(sin θ,-cos θ)`：0° 向北（-Z）、90° 向东（+X）、180° 向南（+Z）、270° 向西（-X）。粒子速度不能直接复制上沿偏移，否则反向。四方向与跨零点须以风向标和手动风作对照。

世界对象变化、退出连接或 client.world=null 时清空原始／平滑状态、停止并释放本功能风声实例、清除本功能粒子和局部环境缓存；暂停到恢复不积累无限生成债务。进入新世界暂时无有效快照时静默，不借旧状态生成；若长期未收到包，按 #13 确定的保底时间＋余量淡出而非永久保留。当前周期候选可先用 5 秒失效供测量；最终同步规则改变后需一起调整。

## 9. 粒子、风声与性能预算

### 9.1 一种稀疏视觉原型

**设计推荐：**仅原型一种短寿命、低不透明度的小型风尘／风痕粒子，运动方向随共用风状态。避免每个群系生成不同素材、不做世界级扫描、不对原版全部粒子调用 clear。可先复用原版粒子类型验证方向和密度；但若无法只清除此功能创建的实例，应在后续授权资源任务实现带所有权集合的专用粒子，不能通过清空 ParticleManager 破坏其他效果。

| 项目 | 原型硬上限／规则（均待 Local 验证） |
| --- | --- |
| 生成范围 | 玩家附近水平半径 12 格，垂直 ±3 格；只看已加载区块，不触发区块加载 |
| 生成率 | 最大风强且密度 100% 时最多 20 个／秒；50% 最多 10 个／秒；每 tick 尝试最多 2 次，不补发暂停期间欠量 |
| 活跃数量 | 本功能最多 80 个；达到上限不生成；所有权集合随死亡移除 |
| 寿命 | 20～60 tick，最长 3 秒；超范围或世界变化提前死亡 |
| 环境检查 | 每 10 tick 至多 4 个候选位置；固定数目的天空／高度／水体／区块检查，缓存最多 4 个结果，TTL≤10 tick；生成只用有效缓存 |
| 强度关系 | 生成率乘玩家密度与实际风强的单调函数；风强 0 不生成，密度不改粒子所示方向和真实风强 |
| 原版粒子档位 | All 系数 1、Decreased 系数 0.5、Minimal 停止本功能粒子；不使用 alwaysShow 强行突破档位 |

密度 0% 与开关关闭在粒子成本上等价：同 tick 停止生成，取消粒子专用环境检查和缓存，标记所有本功能粒子死亡并清所有权集合；最迟下一渲染更新不再显示。最高密度也受所有硬上限约束，不能靠滑块绕过性能预算。声音若开启可以保留它自己的固定低频环境检测，不能把这种检测误算为“关闭粒子后仍在粒子扫描”。

室内或头顶遮挡禁止生成户外风尘；水下停止粒子；无天光／有顶维度不适用，关闭所有环境风。候选位置使用固定局部判断，避免逐粒子逐 tick 射线、流体体积扫描或服务端请求。快速移动、飞行、遮挡判定、极寒／无降水群系中风尘是否合理由 Local 决定；不能让风痕被误认成雨前小雨。

### 9.2 独立风声

风声默认开启、原型音量 50%，从共用风强映射音量，平滑淡入／淡出，最多一个本地循环实例，使用环境音分类并尊重原版环境音量与主音量。声音独立于粒子开关和原版粒子档位；手动风同样有效，不向其他玩家或服务器发送声音设置。

室内先降低音量，水下与不适用维度静音；最多每 10 tick 做一次固定局部检查，缓存只服务声音，不重复粒子候选扫描。关闭风声立即开始不超过 10 tick 的淡出，完成后销毁实例并停止声音专用检查；退出世界直接释放。避免每 tick 重启循环或同时叠加多个实例，音频设备异常不能影响服务器状态。音量曲线、听觉疲劳、重复循环缝隙和遮挡效果均待 Local 验证。

素材优先由项目原创或引用已核对可复用的原版游戏内资源 ID，不把原版资源提取再分发。自制音频／纹理记录作者、来源与授权；第三方资源／依赖必须先核对来源许可，再按现有 [许可范围](legal/LICENSE_SCOPE.md)、[第三方声明](legal/THIRD_PARTY_NOTICES.md)、[来源记录](legal/SOURCE_PROVENANCE.md) 补记录。P0 不添加素材，也不默认互联网下载的“免费风声”已获授权。

### 9.3 天空变阴和零星小雨调查

原版旗标、雨强渐变和天空颜色的关系应在晴→雨、晴→雷暴、冷群系和屋顶下分别实测；需要提前修改雨强／天空时，检查当地降水是否被错误报告为已经发生。全局低雨强不等于可控的“几滴小雨”。独立雨粒子会引入第二套降水表现、清理与光影兼容成本，须先给无光影／Iris 启用策略，再确认范围。默认 beta.1 不要求提前变阴或零星小雨，仪表和风反馈可承担前兆。

保留 #12 主线矩阵（原始证据保留于 Private 档案） 的限定结论：基础／Sodium／Iris 无光影场景通过，Photon 默认闪光缩放组仍失败；关闭闪光和缩放后重载仅限定窗口有效，重载前另有降雪黑屏。新增粒子先在这些组合对照；不能把声音正常或倾斜退让当作光影全兼容。

## 10. 原版风格设置 UI 与持久化

### 10.1 入口与真实 API

**源码事实：**本项目无设置 Screen、按键入口或 Mod Menu 依赖。现有 Screen、ClientTickEvents 与 Fabric API 已在工程中使用。本次核对上述 Yarn 的 `Screen.init/addDrawableChild/close`、`ButtonWidget.builder`、`SliderWidget.applyValue/updateMessage` 和 `KeyBinding.wasPressed` 签名；同时核对当前 Fabric API 依赖内 `fabric-screen-api-v1 3.1.0+9c82a7986f` 的 `ScreenEvents.AFTER_INIT`、`Screens.getButtons`，以及 `fabric-key-binding-api-v1 1.1.1+b3ba9c486f` 的 `KeyBindingHelper.registerKeyBinding` 源码。后续编译仍须核对具体构造签名，不抄旧版字符串 category 构造器。

**设计推荐：**beta.1 的可靠入口为“控制”中可绑定的 Sky Signs 设置按键（默认未绑定，避免抢键），通过现有 END_CLIENT_TICK 打开独立 Screen；在气象站页面增加“Sky Signs 设置…”辅助按钮，保留返回父页面。无气象站也能使用按键入口。

原版“选项”或暂停页面按钮作为可选增强：可通过 AFTER_INIT 与 getButtons 加按钮，但 API 不自动提供空槽或布局锚点。必须处理原版现有控件、缩放与其他模组按钮冲突；没有验证布局时不承诺稳定入口。可选 Mod Menu 只增加入口集成，不成为唯一入口；需要其 API 才讨论 compileOnly／可选依赖与声明，beta.1 基础设置不需要新增配置库或强制 Mod Menu。

### 10.2 文字线框与交互

```text
                   Sky Signs 设置
  环境风
  [风粒子：开 / 关]
  粒子表现强度：50%  [────────●────────]
  仅影响粒子密度；实际风况与仪表不变
  [风声：开 / 关]
  风声音量：50%      [────────●────────]

  降水倾斜（评估纳入 beta.1）
  [降水倾斜：开 / 关]
  表现强度：100%     [────────────────●]
  状态说明／保存失败提示

             [恢复默认]   [完成 / 返回]
```

使用原版按钮、滑块、字体与背景，键盘 Tab／方向键操作、旁白和鼠标均可用；滑块显示 0～100%，内部值 0～1。禁用粒子时密度滑块置灰，显示保存值而非改写为 0；Minimal 档位显示“受原版粒子设置限制”，仍允许保存偏好。Iris 光影启用或查询 API 失败时倾斜控件显示当前不可用原因，保留偏好，光影关闭后可恢复。声音不随这些不可用状态禁用。

宽窗口单列居中；窄窗口／高 GUI 缩放采用滚动内容区，底部操作始终可达。中英文长提示折行且限制窗口内边距，参照现有气象站提示修复；需覆盖 200×220、240×220、320×180 与 GUI 1/2/3/自动，不把 Screen 逻辑尺寸当作真实窗口实测。

### 10.3 配置与保存规则

保留现有文件 `config/skysigns-client.properties` 与两个键 `wind_precipitation_enabled`、`wind_precipitation_strength`，默认 true／1.0。建议新增 `wind_particles_enabled=true`、`wind_particles_density=0.5`、`wind_sound_enabled=true`、`wind_sound_volume=0.5`；缺新键时确定性补默认，不改变旧有效降水键。

设置修改与恢复默认立即在内存生效；粒子关闭立即清理。拖动不每帧写磁盘，完成／Esc／返回时统一保存，Esc 与返回同样保留当前修改，没有隐式撤销。恢复默认只重置已知键，不删除未知键；将来新增页面也遵守此契约。

严格解析 boolean 的 true/false（当前 Boolean.parseBoolean 对非法字符串可能得到 false，新实现不沿用该含糊行为）；非数值／NaN／Infinity 用默认，有限越界数值 clamp 后告知，未知键保留。启动时读取一次并校验，UI 可以热更新内存；外部手改文件仍要求重启或显式未来重载，不假设自动监视文件。

写回从原 Properties 副本更新已知键，UTF-8 注释／读取编码保持一致；同目录临时文件写完后尝试原子替换，支持情况不足时保留可诊断的回退，不先截断原文件。保存失败显示“设置已生效，但未保存。重启后将恢复上次保存值”，提供重试与返回；不静默退出页面冒充已保存。创建目录失败、只读文件、损坏内容、文件保存冲突均需验证，日志不包含无关配置或秘密。此配置是每个客户端的偏好，不发往服务端，也不因多个玩家共享服务器而同步。

## 11. 验收矩阵与后续 PR 拆分

### 11.1 必须分别记录的验证

| 类别 | 后续检查 | 通过判据／限制 |
| --- | --- | --- |
| 纯算法 | 六转换、S/P/T/E 边界、零长度／短周期、曲线连续与范围、趋势、359°→0°、非法风值、时钟回退／溢出 | 固定种子与假时钟可重复；查询次数不影响计划或随机序列；游戏断言不能只镜像实现 |
| 迁移 | 0→1→2、1→2、2 幂等、未知键、future／invalid／缺步骤、正常／暂停／LEGACY／REBASE 磁盘 fixture | 有效 forecast/T 不变、同 tick 气压升级前后相等、只读原始载荷保存不变；真实历史世界仍另测副本 |
| 游戏行为 | 自动六转换、两类天气命令、相同天气改时长、睡眠、gamerule 开关、暂停命令、保存重开、主世界／下界往返、/time、气象站读数与当地雨雪／屋顶 | 集成单人逐项证据；实际旗标／渐变快照同 tick 比较；不将旧断言通过当作新契约已覆盖 |
| 客户端风与 UI | 四方向／跨零点、首快照、非法包、退出／世界切换、粒子关的所有权清理、热设置、语言／缩放、保存失败／未知键 | 原始风不乘用户密度，声音与倾斜独立；迟到包需协议机制与 #13 测量验证 |
| 视觉与声音 | 稀疏粒子 50%/100%、手动风／自动前兆、室内、水下、无天光、Minimal、光影开关、声音单独开关／音量／循环 | Local 人工观察并保存截图／音频记录来源；不承诺未测试光影 |
| 性能与同步 | 下表对照＋服务器 tick、客户端帧间隔、粒子活跃数／生成数／检查数、音频实例数、包量 | 实机 GPU／frametime 独立测量，报告中位数/P95/P99、环境、精确提交和原始数据；构建不替代性能 |

最低性能对照，在相同世界副本、天气／风、视角、距离、分辨率、帧率设置和渲染组合下分别执行，固定既有降水倾斜开关，暖机后每组采样 120 秒、重复三轮（候选方法，Local 可先确认）：

| 组 | 粒子 | 风声 | 核对重点 |
| --- | --- | --- | --- |
| A | 关闭 | 关闭 | 本功能粒子检查／活跃数／音频实例为 0，作为基线 |
| B | 关闭 | 开启 | 声音独立，粒子检查仍为 0；音频至多 1 实例 |
| C | 50% | 开启 | 生成率／检查数／活跃数有界，保持可读方向 |
| D | 100% | 开启 | 不超过上限，密度变化不影响仪表与同步 |
| E | 100% | 关闭 | 分离粒子成本，音频实例与声音专用检查为 0 |

先以无新增仪表场景隔离风反馈成本，再以固定仪表数量核对天气读数成本；不要混入旧湿度计性能波动后直接归因。原型候选预算为相对 A 的客户端帧间隔 P95 增量≤1 ms、P99 增量≤2 ms，服务端主世界 tick P95 增量≤0.1 ms；这些需维护者与 Local 按设备噪声确认，尚未达成或实测。帧回调间隔不是独立 GPU 用时，需 GPU 指标时另设测量。超过预算先降低上限／默认密度再复测，不写“无明显影响”替代数据。

保留现有 **GameTest `Network synchronizer in invalid state` 未解决**，不能启用等待保护／删断言来宣称完整回归通过；受控竞态复现与局部成功不证明根因已修复，见 诊断（原始证据保留于 Private 档案）。保留 **Photon 默认组合黑屏失败**与 #12/#35 的限定规避结果。多人、专用服务端、独立服务器重启、不同语言同服和联机包压力未授权，继续未验证，不从集成单人推断通过。

2026-10-07 本次读取 #37：其“实测选定风互动与天气过渡的性能成本”条目为 `[x]`，其余设计／实现项尚未完成。当前源码没有本稿新功能，仓库已共享测量为仪表／既有场景，未找到可支持新风反馈与分阶段天气性能验收的记录。因此该勾选与可核实证据不一致，本稿不据此认定 0.7 性能完成，也不修改 Issue。请后续以实际新功能提交与测量证据核对；本任务没有验证 Project 当前字段。

### 11.2 推荐拆分与决策门槛

| 顺序／位置 | PR／任务内容 | 依赖与交付 |
| --- | --- | --- |
| P0 Cloud（本 PR） | 本设计稿＋路线图入口 | 文档检查，维护者审阅；不表示 beta.1 功能完成 |
| P1 Cloud | 统一天气计划、连续读数、WEATHER 迁移、命令／睡眠／gamerule 接入 | 先确认阶段／暂停／LEGACY 与命令时长规则；算法／迁移检查与编译，不改客户端反馈或网络协议 |
| P2 Local 调查 | 原版雨雪渐变／阴天／零星小雨可行性；#13 现有同步测量 | 隔离单人；给出实际可用钩子、视觉与包量证据；不自动实施协议 |
| P3 Cloud | 客户端风状态分离、properties 热更新、原版设置 Screen | 先确认入口与配置键；保留旧键、Iris 退让，不强制新增 UI 库 |
| P4 Cloud + Local | 一种粒子与独立风声原型，完成清理与硬预算 | 素材来源确认后 Cloud 候选；Local 做方向、声音、关停成本、缩放、渲染与性能对照 |
| P5 可选 Cloud | station_report_v3／新风协议与首次快照 | 必须另获协议范围确认，并依据 #13 选择识别／阈值／保底策略；v2 fallback 明确 |
| P6 Local | 精确候选的游戏行为、旧世界副本、兼容与性能验收 | 保留同步器／Photon 失败，未授权多人／专服不执行；结果决定 beta.1 范围，发布另行确认 |

维护者需要决定：阶段参数与周期长度体验；原版视觉先复用还是另议渐变控制；同天气原版命令时长优先与 gamerule 暂停语义；WEATHER 2 锚点／LEGACY 方案；v2 文本候选还是后续 v3；设置入口、粒子／声音默认和性能预算。推荐下一任务先做 **Local 原版天气渐变及 #13 同步测量调查**，并审阅本 PR 的服务端契约；资料齐备后确认 P1 具体实现范围，避免先承诺未知视觉／协议行为。

## 12. P0 文档交付与检查边界

本 PR 只含 `docs/V0_7_DESIGN.md` 与 `docs/ROADMAP.md`。提交前检查 name-only diff、`git diff --check`、标题、表格列数、相对文件链接、代码围栏，以及阶段表／字段表／fixture／迁移规则一致性；实际检查结果记录在 PR 正文。保持 PR 待维护者审批，不自行合并。

纯设计文档未运行 Gradle、Minecraft、GameTest、GPU／音频／性能或多人／专用服务端测试，未接受 EULA、连接用户服务器或改玩家世界。本稿所有新增行为、原型参数和性能阈值仍是设计推荐或待 Local 验证；本次交付不更新发布状态。

## 13. 后续 Local 调查依据（2026-10-07）

P0 上述未测试边界作为历史保留。PR #44 合并后在 `a822e09` 开展了 原版渐变与 #13 现有同步调查（原始证据保留于 Private 档案）：六条转换显示原版梯度每 tick 变化 0.01，约 5 秒完成；低强度暖群系仍有 441 条带，提前变阴／定量零星雨不能直接等同于低雨强。推荐先复用原版短渐变，让更长的阶段曲线驱动仪表／风；延长实际雨强或独立天空表现需另定范围。

九个现有风况窗口确认每 20 tick 发送 8 B payload。2°／0.01／100 tick 保底离线候选在静态组减少 80%，自动组仅约 2%～27%；首次快照与跨维度旧包契约仍应独立处理。本次没有实现这些候选或新协议。

本调查专项最后在重开存档的区块下载就绪等待超时，运行失败，原始数据与失败记录保留。数据一致性检查不表示完整回归／新功能性能／光影／多人／专服通过，也不改变 P0 参数的原型性质或发布状态。

后续 Local 定向诊断（原始证据保留于 Private 档案） 在最小产品、无产品框架和原调查存档副本中复现：视距 8 的方形等待包含四个正常发送范围之外的角。服务端已 FULL、客户端稳定缺四角，支持夹具条件与发送形状不一致的归因，尚未修正检查或重验。已有主要天气／风测量可供 P1 设计审阅，但不据此认定重开验收、旧同步器问题或 0.7 新功能已通过。
