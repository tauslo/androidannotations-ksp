# Performance Checklist — Incremental KSP & compile speed

> **可与 P0b/P1 并行**，不阻塞「能替换线上 AA」功能里程碑。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> 基线数据见根目录 [`BENCHMARK.md`](../../BENCHMARK.md)（冷全量约 −22.6% vs kapt）。

---

## Measurement hygiene

- [ ] 固定工具链：Kotlin 2.1.10 / KSP 2.1.10-1.0.31 / AGP 8.7.3 / Gradle 8.11.1 / JDK 17
- [ ] 场景至少三条：冷全量、热全量、`改 1 个源文件` 增量
- [ ] 每场景 ≥3 次取中位数；`--no-build-cache`；daemon stop + 删 `build/`（冷）
- [ ] 分别记录：总 `assembleDebug`、`:kspDebugKotlin` / `:kaptDebugKotlin`、处理器自打日志耗时
- [ ] 更新 `BENCHMARK.md`（或新增章节）写明环境与结论

---

## Processor efficiency

- [ ] 去掉或合并「第二轮 generated 0 class(es)」空跑
- [ ] `written` / 生成去重与 KSP multi-round 语义核对
- [ ] 避免每轮全量重扫可缓存的 R/Manifest（在正确性前提下）
- [ ] 日志级别：默认 warn；详细诊断可 option 打开（避免刷屏拖慢）

---

## Incremental KSP

- [ ] `Dependencies` 正确声明 originating files（含父类注解、嵌套 bean、@Bean 依赖的生成类）
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
