# Performance Checklist — Incremental KSP & compile speed

> **可与 P0b/P1 并行**，不阻塞「能替换线上 AA」功能里程碑。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> 基线数据见根目录 [`BENCHMARK.md`](../../BENCHMARK.md)（冷全量约 −22.6% vs kapt）。

---

## Measurement hygiene

- [x] 固定工具链：Kotlin 2.1.10 / KSP 2.1.10-1.0.31 / AGP 8.7.3 / Gradle 8.11.1 / JDK 17（与 `BENCHMARK.md` / PLAYBOOK 一致，本轮未改版本）
- [ ] 场景至少三条：冷全量、热全量、`改 1 个源文件` 增量。冷全量 assemble 已有 3 次中位数。热全量未测。改 1 文件只有单次 spot（见 `BENCHMARK.md`），未做 3 次中位数
- [ ] 每场景 ≥3 次取中位数；`--no-build-cache`；daemon stop + 删 `build/`（冷）。仅冷 assemble 满足；增量 spot 是单次、daemon 未停
- [ ] 分别记录：总 `assembleDebug`、`:kspDebugKotlin` / `:kaptDebugKotlin`、处理器自打日志耗时。冷 assemble 有总数；处理器耗时有单次日志。三条场景的拆分计时仍缺
- [x] 更新 `BENCHMARK.md`（冷全量结论保留，并写入 2026-11-16 的单次增量 spot）

---

## Processor efficiency

- [ ] 去掉或合并「第二轮 generated 0 class(es)」空跑
- [ ] `written` / 生成去重与 KSP multi-round 语义核对
- [ ] 避免每轮全量重扫可缓存的 R/Manifest（在正确性前提下）
- [ ] 日志级别：默认 warn；详细诊断可 option 打开（避免刷屏拖慢）

---

## Incremental KSP

- [ ] `Dependencies` 正确声明 originating files（含父类注解、嵌套 bean、@Bean 依赖的生成类）。2026-11-16 复核：`writeJavaSource` 仍是 `new Dependencies(false, containingFile)`，只挂注解类自己的源文件。同文件嵌套类因此会一起失效；另一个文件里的父类、`@Bean` / `@App` / `@Pref` 目标、R / Manifest 都没有声明。负例仍开放，代码处有注释
- [ ] 跨模块 `@Bean`：依赖已编译的 `*_` 字节码时的增量失效策略
- [ ] 改 1 个 Activity：只重生相关 `*_`，不触碰无关类
- [ ] 负例：改父类注解成员 → 子类生成类失效并重生
- [ ] 负例：改 `@EBean` → 注入方 Activity 生成类失效

---

## Corpus

- [ ] 保持 / 扩展 `examples/kotlin-bench`（≥200 注解类型）
- [ ] 可选：生成脚本入库，避免手改 200 文件
- [ ] Fragment / Click 进入 P0b 后，bench 可按比例加入（勿只测 Activity）

---

## Acceptance

- [ ] 增量「改 1 文件」相对当前全量 KSP 有稳定数量级改善（预期常见 **2–5×+** 体感；以实测为准）
- [ ] 冷全量不显著回退（允许小波动）
- [ ] `EXECUTION_LOG` 记录数字与复现命令
