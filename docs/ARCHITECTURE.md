> 当前公开快照为 `0.7.0-alpha.1`，来源 `11b5cdbd119a55f071aac48464623e61575cb167`。下文候选、路线和旧日期描述保留其历史语境；当前发行与验证边界见 [PUBLIC_SNAPSHOT.md](PUBLIC_SNAPSHOT.md)。P1 已合入，WEATHER 结构为 2，WIND/METER 为 1；环境风粒子、风声、统一设置页及协议 v3 未交付。

# 当前架构

核对日期：2026-09-30。实现基线为已发布的 beta.7，精确构建提交 `56ed3f5798ac4884ae8f941f7408705ac12b55df`；模型与 UI 改动通过 PR #5（原 Private 档案；不随此快照公开） 合并。新任务仍应核对实际 checkout 与源码，不能把后续文档提交或未来计划当成发布包功能。

本分支包含 2026-10-07 P1 服务端候选；下述天气／存档部分按候选更新。发布历史和客户端模型/UI 部分继续描述原发布基线，不能把候选当成已发布 0.7。验证状态见 P1 交付（原始证据保留于 Private 档案）。

## 源集与入口

| 位置 | 职责 |
| --- | --- |
| `src/main/java/cn/skysigns` | 公共注册、服务端天气／风况、方块、网络协议、命令及存档 |
| `src/client/java/cn/skysigns` | 客户端网络接收、气象站 Screen、动态仪表渲染、雨雪顶点偏移 |
| `src/main/resources` | Fabric 元数据、模型、物品定义、blockstates、翻译、配方、掉落与 Access Widener |
| `src/client/resources` | 客户端 Mixin 配置 |
| `src/gametest` | 单独的客户端测试模组，不进入发布 JAR |

[`SkySigns`](../src/main/java/cn/skysigns/SkySigns.java) 注册方块／物品／方块实体、迁移事件、三类 payload、命令与风更新／发送的 `END_WORLD_TICK`。[`SkySignsClient`](../src/client/java/cn/skysigns/client/SkySignsClient.java) 注册客户端接收器、渲染器及视觉更新。Loom 分离 main/client 源集；运行时元数据为 `environment: "*"`，生产 Mixin 分为服务端 ServerWeatherMixin 与既有客户端降水渲染 Mixin。

## 服务端天气与气候

[`SkyWeatherType`](../src/main/java/cn/skysigns/weather/SkyWeatherType.java) 只有 `CLEAR / RAIN / STORM`。[`WeatherState`](../src/main/java/cn/skysigns/weather/WeatherState.java) 保存当前天气、下一阶段、阶段起点、截止世界 tick、自动模式和初始化状态。

[`WeatherPlan`](../src/main/java/cn/skysigns/weather/WeatherPlan.java) 是不可变的 S/P/T/E 计划及纯曲线求值器。目标只在写入入口选定；NORMAL 共用前兆／过渡权重，REBASE 保留事件前基值，LEGACY 兼容旧气压曲线。`WeatherState` 保存计划、逻辑 type、模式和暂停状态，WEATHER 结构 2。

[`WeatherManager`](../src/main/java/cn/skysigns/weather/WeatherManager.java)：

- `get/view/describe` 不初始化计划或消费随机数；加载时的确定性 schema 迁移与曲线求值分开。不可变 view 被读数、命令和站报告共用。
- ServerWeatherMixin 在 tickWeather HEAD 初始化并保护自动计时器，在 ServerWorld.tick 的 tickTime 调用后提交 T/E；仪表采样不再依赖 END_WORLD_TICK 才看到更新。运行冻结不调用该时钟推进位置。
- setWeather/resetWeather 的 HEAD/RETURN 捕获完整外部事件；只注册一条通知路径，自有写入 guard 避免回流。同 tick 独立命令逐条处理，读取实际写入世界与时长，完全同值的直接属性写入只能靠旗标差异补充观察。
- 自动计划每 tick 检查边界，大跳只完成一次并建立一次新计划；doWeatherCycle=false 冻结有效时间，恢复整体平移。暂停中的命令替换承诺，恢复后保留完整剩余时长。
- manual 不维护原版计时器，不阻止自然循环；实际旗标变化采纳为 manual 并 REBASE。睡眠仅采纳真实 resetWeather，不合成 gamerule 关闭时清雨。非适用维度不生成计划。
- 时间使用 getTime，不使用昼夜时钟／墙上时间。同进程回退平移计划，首次加载不推断跨存档回退。只读优先，时间溢出保护已保存计划。

[`WindState`](../src/main/java/cn/skysigns/weather/WindState.java) / [`WindManager`](../src/main/java/cn/skysigns/weather/WindManager.java) 分别维护当前／目标角度与强度、模式和变化截止时间。自动风每 tick 沿最短角度差逐步接近目标；自动目标从当前连续天气区间选取，T 不突变重抽；前兆可提前一次晚于 T 的目标调度。实际风值保留旧平滑和滞后，手动风不被覆盖。风查询也不初始化，写入入口按天气后风的顺序初始化。方位为 0° 北、90° 东、180° 南、270° 西；强度 0～1，无真实 m/s 换算。

[`AtmosphericReadings`](../src/main/java/cn/skysigns/weather/AtmosphericReadings.java) 是读数计算入口：

- 气压：共享计划基值（晴 1020、雨 1005、雷暴 989 hPa）加小幅波动；趋势来自基值曲线，稳定／暂停不受微扰影响。
- 湿度：先插值未取整天气基值，再添加群系 `hasPrecipitation`、`downfall`、温度、海拔和附近水体估算，结果限制 5～100%。不匹配群系名称，`downfall` 限制 0～1。
- 附近水体：7 × 5 × 7 范围，最坏 245 次流体查询，发现水即返回；目前没有缓存。
- 当地降水：原版实际降雨状态与仪表上方 `pos.up()` 的 `world.getPrecipitation`，区分雨／雪／遮挡／无降水群系。原版下雪不是新增的 Sky Signs 雪天气。
- `measure` 同时计算各项；气压单独查询不扫描水体。`forecast` 命令与气象站报告会计算湿度。

## 方块与动态显示

[`ModBlocks`](../src/main/java/cn/skysigns/registry/ModBlocks.java) 注册四个方块及对应 BlockItem，加入原版功能方块组。`ModItems` 只注册内部转子／指针，不能把它们当成四种公开仪器的注册入口。

| 对象 | 状态与采样 | 客户端显示 |
| --- | --- | --- |
| `WeatherVaneBlock` | `angle=0..359`；每 tick 定时更新整数角度，旋转碰撞形状按角度缓存 | 固定模型＋方块实体转子渲染，跨 359°/0° 平滑转向 |
| 气压计／湿度计 | `ObservationInstrumentBlock` 的 Kind；四向 `FACING`；共享 `MeterBlockEntity` | 静态外壳＋动态指针；读数左低右高，表盘朝向随方块旋转 |
| 气象站 | 四向 `FACING`；没有独立方块实体，右键按需生成报告 | 静态方块模型＋客户端 Screen |

仪表每秒一次采样，使用 `(worldTime + pos.hashCode()) % 20` 错峰；右键也会主动采样。只有读数变化时通知客户端；迁移的保存标记另行处理。角度 blockstate 同步与风视觉快照是不同路径，不能把风向标误写成只靠每秒 payload 驱动。

`MeterMigration` 在服务端区块加载时筛选含仪表的 section，补建旧仪表缺失的方块实体；右键亦有恢复路径。它与 NBT 数据版本迁移是两种不同机制。

## 数据流与网络边界

```text
原版天气开关 / Sky Signs 命令
  → WeatherManager + WeatherState → 原版天气
  → WindManager + WindState
      → 风向标 angle blockstate → 转子与旋转碰撞
      → wind_visual 快照 → 客户端雨雪倾斜插值

天气 / 群系 / 位置 / 附近水体
  → AtmosphericReadings
      → MeterBlockEntity → 方块实体更新包 → 指针
      → 气象站报告 → station_report_v2 → WeatherStationScreen
```

协议定义在 [`network/`](../src/main/java/cn/skysigns/network)：

- `station_report_v2`（S2C）：六个可翻译 `Text`、三个数字、位置、阶段／模式标记与剩余秒数。客户端本地翻译，不发送预渲染中文字符串。
- `station_refresh`（C2S）：仅位置。UI 每 20 tick 请求；服务端校验玩家当前世界内的方块类型、距离平方 ≤64，并按玩家限制为至少 10 tick 一次。
- `wind_visual`（S2C）：两个 float，每 20 tick 发给该世界支持通道的玩家；目前不按变化阈值抑制发送，无专门入服即时快照。

接收处理分别切回服务器／客户端执行线程。报告发送前 `canSend` 检查，不支持 v2 的客户端右键得到更新提示；这不等于验证过混合版本联机。现有报告没有维度 ID 或请求序号，跨维度迟到包需真实网络验证。

## 降水视觉与配置

[`WeatherRenderingMixin`](../src/client/java/cn/skysigns/mixin/client/WeatherRenderingMixin.java) 只修改原版 `renderPieces` 的上沿顶点 X/Z，不额外生成粒子，不替换天气判定、闪电或雷声。Mixin 有 `require=1`，不是任意渲染修改都能静默降级。

[`WindPrecipitationVisual`](../src/client/java/cn/skysigns/client/render/WindPrecipitationVisual.java) 对快照进行有限值校验和插值，世界变化／退出时重置。满强度上沿最大偏移 2.4 格，按柱高缩放。可选反射查询 Iris API：光影启用时偏移为零；Iris 查询失败时保守停用偏移；关闭光影可恢复。

配置仅为 `config/skysigns-client.properties`：`wind_precipitation_enabled`、`wind_precipitation_strength`，启动时读取，改文件需重启。没有统一服务端 Config、配置 GUI 或热重载机制。

## 持久化与 Beta7 模型／界面

天气／风使用各维度 PersistentState；仪表使用方块实体 NBT。候选结构版本为 WEATHER 2、WIND/METER 1，迁移入口 `SaveDataMigrations`，详见 [SAVE_DATA.md](SAVE_DATA.md)。气象站没有可迁移的独立存储区域。

beta.7 不改变存档结构或网络协议，主要更新以下呈现层：

- 气压计静态外壳与库存模型分别存放几何，`ModelUiAssertions` 逐元素校验一致性；库存仅增加静态指针。湿度计的世界／库存模型分别继承对应气压计模型并替换材质，不是运行时共享一份生成网格。
- 气象站物品继承放置模型，GUI 展示角度为 45°；脚座、阶梯底座、分层顶盖和顶部横架与碰撞轮廓同步更新。动态风向标和表盘指针路径不变。
- `WeatherStationScreen` 面板宽度不超过 420、也不超过可用宽度减 12；标准布局高 228，在可用高度小于 240 时采用高 172 的紧凑布局。保留原版字号，按翻译标签宽度排列读数，截断文本可悬停查看全文，进度条限制范围。
- `src/gametest` 增加 `ModelUiAssertions`、`InstrumentPreviewScreen` 以及逻辑尺寸 320×180 UI、物品预览、四向模型截图路径，不进入发行 JAR。没有新增环境扫描、方块实体或网络刷新频率。
