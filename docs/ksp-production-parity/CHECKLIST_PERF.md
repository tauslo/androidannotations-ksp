# Performance Checklist — Incremental KSP & compile speed

> **可与 P0b/P1 并行**，不阻塞「能替换线上 AA」功能里程碑。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> 基线数据见根目录 [`BENCHMARK.md`](../../BENCHMARK.md)（冷全量约 −22.6% vs kapt）。

---

## Measurement hygiene

- [x] 固定工具链：Kotlin 2.1.10 / KSP 2.1.10-1.0.31 / AGP 8.7.3 / Gradle 8.11.1 / JDK 17（本轮 `java -version` 为 Corretto 17.0.21，未改版本）
- [ ] 场景至少三条：冷全量、热全量、`改 1 个源文件` 增量。冷全量 assemble 仍是既有 3 次中位数，本轮没有重跑。另有 1 次冷 `:kspDebugKotlin`（墙钟 110.9 s）和 1 次热全量 `--rerun-tasks`（墙钟 20.48 s）。改 1 个 Activity 已有 3 次中位数（墙钟 8.14 s），见 `BENCHMARK.md`
- [ ] 每场景 ≥3 次取中位数；`--no-build-cache`；daemon stop + 删 `build/`（冷）。冷 assemble 仍满足。本轮增量 3 次用了 `--no-build-cache`，但 daemon 保持运行（增量需要它）。冷 `:kspDebugKotlin` 与热全量都只跑了 1 次
- [ ] 分别记录：总 `assembleDebug`、`:kspDebugKotlin` / `:kaptDebugKotlin`、处理器自打日志耗时。冷 assemble 有总数。本轮记下了冷/热/增量的 `:kspDebugKotlin` 墙钟和 `generated N` 日志。没有新的 assemble 拆分，也没有 kapt 对照
- [x] 更新 `BENCHMARK.md`（冷全量结论保留，并写入 2026-11-16 的 3 次增量中位数、1 次热全量和 1 次冷 `:kspDebugKotlin`）

---

## Processor efficiency

- [ ] 去掉或合并「第二轮 generated 0 class(es)」空跑
- [ ] `written` / 生成去重与 KSP multi-round 语义核对
- [ ] 避免每轮全量重扫可缓存的 R/Manifest（在正确性前提下）
- [ ] 日志级别：默认 warn；详细诊断可 option 打开（避免刷屏拖慢）

---

## Incremental KSP

- [x] `Dependencies` 声明本轮能廉价拿到的 originating files。非聚合。包含：注解类自己的源文件；当前带有 AndroidAnnotations 成员/参数注解的父类源文件（有 `KSFile` 时）；本编译里已经解析到的 `@Bean` / `@App` / `@Pref` 目标源文件；写进外层生成类的嵌套 `@EBean` / `@SharedPref` 源文件（同文件会去重）。仍不包含：`R` / `AndroidManifest.xml`、没有 `KSFile` 的跨模块字节码、当前没有 AA 成员注解的父类。见 `KNOWN_DEVIATIONS.md` 与 `BENCHMARK.md`
- [ ] 跨模块 `@Bean`：依赖已编译的 `*_` 字节码时的增量失效策略。classpath 类型 `getContainingFile()` 为 null，本轮故意不登记
- [x] 改 1 个 Activity：只重生相关 `*_`，不触碰无关类。3 次改 `BenchActivity001.kt` 都是 `generated 1`，`BenchActivity001_` 更新，`BenchActivity002_` 不变
- [x] 负例：改父类注解成员 → 子类生成类失效并重生。临时两个文件（未入库）：`BenchInheritBase` 的 `@Extra` 被子类 `BenchInheritChild` 收进 `IntentBuilder_`；只改父类后 `generated 1`，只重写 `BenchInheritChild_`
- [x] 负例：改 `@EBean` → 注入方 Activity 生成类失效。只改 `BenchBean001.kt` 后 `BenchActivity112_` 重写（`generated 7`）。同一次还重写了该 Activity 的其他 `@Bean` 目标（002/003/004/101）以及注入 `BenchBean101` 的 `BenchProvider_`。KSP 会把失效输出的全部 originating files 再处理一遍，所以会连带这些类；无关 Activity 不在变更列表里

---

## Corpus

- [ ] 保持 / 扩展 `examples/kotlin-bench`（≥200 注解类型）。2026-11-16 加了 Preference 与 `@IntArrayRes` fixture，但没有入库生成脚本，也没有把注解种类做成可重复清点，所以本项仍开放
- [ ] 可选：生成脚本入库，避免手改 200 文件
- [ ] Fragment / Click 进入 P0b 后，bench 可按比例加入（勿只测 Activity）

---

## Acceptance

- [x] 增量「改 1 文件」相对当前全量 KSP 有稳定数量级改善（预期常见 **2–5×+** 体感；以实测为准）。对比同一次热全量 `:kspDebugKotlin`：墙钟 20.48 s → 中位数 8.14 s（约 2.5×），处理器 6319 ms → 中位数 470 ms（约 13×）。3 次增量都是 `generated 1`
- [ ] 冷全量不显著回退（允许小波动）。本轮没有重跑 3 次冷 `assembleDebug`。1 次冷 `:kspDebugKotlin` 不能代替那条基线
- [x] `EXECUTION_LOG` 记录数字与复现命令
