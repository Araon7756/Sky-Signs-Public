> 公开快照说明：本页保留历史设计、候选或验收记录；日期、提交和当时的“尚未发布”等状态仅适用于对应历史阶段。当前发行事实与公开快照验证见 [PUBLIC_SNAPSHOT.md](PUBLIC_SNAPSHOT.md)。原始证据、旧 PR/Issue 和提交历史保留于 Private 档案，未随快照迁移。

# INF-01：完整发送集合与场景就绪检查

本实现依据维护者已确认的 独立方案 PR #47（原 Private 档案；不随此快照公开）。基础设施分支基于 `main` 的 `7d9fe54`，不包含 PR #46 的 P1 天气变更。测试类、故障注入与配置均在 `src/gametest`，不进入正式 JAR；生产源码、协议、存档结构、依赖和版本不变。

## 就绪契约

默认完整回归在框架初始化后、首次创建世界前显式设置视距 8。每次就绪检查通过正常 `sendClientSettings()` 同步设置；必须同时观察到 option/clamped、服务端总视距、玩家已接受视距和当前 filter 的有效视距都是 8。

`ChunkReadiness` 在服务器线程枚举当前玩家实际 `ChunkFilter.forEach` 的完整集合 F，生成排序坐标与 SHA-256；再以本版原生 `ChunkFilter.cylindrical` 核对中心和有效范围，不复制几何公式。IGNORE_ALL 只作为未就绪在原期限内等待，不能用空集合宣称完成；未知类型失败。客户端在当前世界逐个检查 `getChunk(x,z,FULL,false)`，同时要求当前场景关键集合 K 在 F 内、预期块／仪表 BE／朝向已收到。加载总数不能代替坐标检查。视距 8 的静止场景 F 通常为 329 个，其中 285 个在原生等待方格中、44 个在方格外；这些数字只在独立机制测试中作为固定版本交叉检查，不是默认等待的成功门槛。

检查绑定当前集成服务器、连接、ClientWorld、玩家 UUID、维度与连接 epoch，并核对服务器玩家、filter 和客户端缓存／玩家中心一致。连续实际 tick 的两次完整快照必须相同；变化在同一个 1,200 tick 下载期限内重取，不能无限续期。回调只交付快照，不在客户端 predicate 中调用服务器。只读服务端 FULL 查询不会增票或强制生成区块。

下载成功后，保留框架独立渲染等待 `waitForChunksRender(false, remaining)`，同时要求当前世界完成实际 render frame、chunk updater 与 builder 队列为空，并再次检查全部 F/K 与身份。这里的 false 只拆开两个阶段，不跳过下载。渲染阶段也有独立 1,200 tick 总期限；目标变化后重新下载／渲染仍受原渲染阶段期限限制。

水场景覆盖外部水流及相邻查询范围；站场景覆盖 legacy meter、stress／错峰仪表、模型平台和床。四向模型截图前另核对实际模型块／朝向。水、站与两次重开额外通过原版传送路径把目标放进镜头，生成带 run/epoch/scene 的新截图，然后恢复位置／朝向并重验就绪。队列完成与客户端状态是自动就绪证据；实际画面仍需要查看本次截图。

维度天气测试保留原 `y=80` 传送和生存玩家，为隔离测试世界的两个落点建立玻璃地板／外壳与净空，并把实际客户端地板加入 K。原夹具在下界落入岩浆、等待期间死亡，导致玩家移除、filter 清空；返回超平坦主世界也有高空跌落风险。这个有限场景布置使玩家能正常维持区块跟踪，不编辑 filter、不添加就绪票或补发区块，也不改变天气断言。

保存退出后检查客户端世界、连接和集成服务器均清空。两次 `save.open()` 保留原服务端天气、legacy meter／schema、阶段时间戳和预报断言的回调顺序，然后检查新连接的完整 F/K、客户端仪表和渲染。新增等待不冻结天气、不改时长／容差，也不更新 PR #46 的候选。详细证据及尚未完成范围见 实施验证记录（原始证据保留于 Private 档案）。

## 隔离运行与证据

需要既有 JDK 21／依赖；Cloud 还需要 Xvfb／OpenGL。使用 `scripts/inf01-isolated-run.gradle` 给每次运行分配尚不存在的 `build/run/inf01-<runId>`，已有目录会拒绝执行，不清理旧证据。`SKYSIGNS_INF01_OUTPUT` 的父目录应存在、目标目录应不存在；事件同时写入该目录的 `events.jsonl` 和标准日志。

先取消 `SKYSIGNS_P1_ONLY`、`SKYSIGNS_WATER_ONLY`、`SKYSIGNS_NETWORK_*`、`SKYSIGNS_ISSUE9_MODE`、`SKYSIGNS_PERFORMANCE_TEST` 等专项变量。禁止旧等待保护；默认完整回归无需设置 `SKYSIGNS_INF01_SELFTEST`。

```sh
# 每轮换新 runId 和输出目录；完整默认入口，显式视距8。
SKYSIGNS_INF01_OUTPUT=/absolute/existing-parent/default-01-events \
bash scripts/run-client-tests-xvfb.sh --offline \
  --init-script scripts/inf01-isolated-run.gradle -Pinf01RunId=default-01 \
  build compileGametestJava

# 独立负向／机制测试；把 CASE 改为 native、negative 或 lifecycle。
SKYSIGNS_INF01_SELFTEST=1 SKYSIGNS_INF01_CASE=negative \
SKYSIGNS_INF01_OUTPUT=/absolute/existing-parent/negative-01-events \
bash scripts/run-client-tests-xvfb.sh --offline \
  --init-script scripts/inf01-isolated-run.gradle -Pinf01RunId=negative-01

# 无产品原生对照：运行 classpath 移除 main/client，metadata 不依赖产品。
SKYSIGNS_INF01_SELFTEST=1 SKYSIGNS_INF01_CASE=native \
SKYSIGNS_INF01_OUTPUT=/absolute/existing-parent/framework-01-events \
bash scripts/run-client-tests-xvfb.sh --offline \
  --init-script scripts/inf01-isolated-run.gradle -Pinf01RunId=framework-01 \
  -Pinf01Framework=true
```

Windows 使用现有 `scripts/windows-client-argfile.gradle`，例如设置同样的进程环境后：

```powershell
.\gradlew.bat runWindowsClient build compileGametestJava --offline --no-daemon `
  --init-script scripts/inf01-isolated-run.gradle `
  --init-script scripts/windows-client-argfile.gradle -Pinf01RunId=local-default-01
```

`native` 用例保留原生下载调用与完整超时，捕获并断言四角超时，记录原错误链为 `nativeExpectedFailure`。该自测退出 0 表示成功核验预期故障，**不表示原生等待通过**；原 Local 失败记录保持。framework 变体只允许 native 用例并验证实际未加载 Sky Signs。

`negative` 包括错误 epoch／中心、未接受视距快照、实际客户端 option 12 未同步（随后恢复目标 8）、K 在 F 外、未收到的块更新、实际 render frame、已关闭会话，以及真实阻止 `(1,1)` 正常区块安装。缺 FULL 必须在原 1,200 tick 超时，render-only 完成不能让它通过。包发送／接收观察与故障注入 Mixin 只在显式自测入口开启，普通回归不启用。

`lifecycle` 预先固定 10 周期；每周期创建、下载／渲染、仪表目标、负中心移动／返回、保存退出、两次同存档重开。任意失败停止并保留当前日志，不增补成功轮次替代失败；重开静止会话逐项比对实际发送、接收与完整 F。

默认水／站／两次重开成功与自测成功分开记账，必须同时检查 Gradle／游戏退出和完整阶段事件。正式 JAR 检查只验证打包边界。INF-01 不修旧同步器竞态、不修改性能专项入口中的历史原生等待；观测运行和默认已有 PerformanceProbe 不构成性能无回退结论。
