# AndroidAnnotations → KSP 生产替换 Playbook

> **给后续 Agent 的唯一入口文档。**  
> 目标：用 `androidannotations-ksp` **替换线上 APT/kapt AA**，生成行为对齐，业务调用点尽量零改动。  
> 中断后续跑：先读本文件 → 再读同目录 [`EXECUTION_LOG.md`](EXECUTION_LOG.md) 最后一条「Next」→ 从断点继续。

---

## 0. 30 秒上下文

| 项 | 值 |
|----|-----|
| 仓库 | fork of `androidannotations/androidannotations`，基于 `develop@0d24f1b6` |
| 远程 | `git@gitlab.5fun.com:android-group/androidannotations-ksp.git` remote 名 `ksp`，分支 `main` |
| 本地分支 | 通常在 `develop`（已 push 到 `ksp/main`） |
| 工具链锁定 | Kotlin **2.1.10**，KSP **2.1.10-1.0.31**，AGP 8.7.3，Gradle 8.11.1，JDK 17 |
| APT 模块（保留对比） | `org.androidannotations:androidannotations` |
| KSP 模块（主战场） | `org.androidannotations:androidannotations-ksp` |
| 示例 | `examples/kotlin`（KSP）、`examples/kotlin-bench`（kapt/ksp 测速） |
| 测速结果 | 冷全量约 **−22.6%**（见根目录 `BENCHMARK.md`） |

**成功标准（可替换线上 AA）**

1. 典型 Activity/Fragment 工程可卸 `kapt`，只留 `ksp` + `androidannotations-api`
2. 业务仍写 `Xxx_.intent(ctx).foo(v).startActivity()`、`@Bean`、`@EFragment` 等，**无需改调用点**
3. 未实现的 AA 注解必须 **KSP warning/error**，禁止静默无行为
4. 生成类命名仍为 `Foo_`（`classSuffix` 可配置），Manifest 仍注册生成类
5. Kotlin 继续依赖 `kotlin-allopen` + `@KotlinOpen`

**明确不做（除非用户指定）**

- RoboGuice / Otto（库已死）
- 默认不做 ORMLite / REST-Spring（旧工程按需另开一期）
- 不删除 APT 模块（用于对照与回退）
- 不把生成物改成 Kotlin 源（继续生成 **Java 子类**）

---

## 1. 架构原则（必须遵守，效果才不会差太多）

1. **行为规格 = APT 源码，不是 MVP 字符串模板**  
   对照路径：
   - 入口：`.../internal/AndroidAnnotationProcessor.java`
   - 流水线：`ModelExtractor` → Manifest/R → `ModelValidator` → `ModelProcessor` → `CodeModelGenerator`
   - Handler：`.../internal/core/handler/*`
   - Holder：`.../holder/*`
2. **KSP 是新模块并行实现**，不要在 APT 上硬改 `javax.lang.model`
3. **生成 Java 子类**（jcodemodel / JavaPoet / 结构化 writer 均可；优先可维护结构，避免巨型字符串蔓延）
4. **先补基础设施，再堆注解**；没有 IntentBuilder / `@Bean` / Manifest / R，后面 handler 都会歪
5. **每完成一个可交付切片就写 EXECUTION_LOG**，并保证 `examples/kotlin`（及新增 fixture）可编译
6. **Windows PowerShell**：Maven 参数必须加引号，例如 `"-DskipTests"`；不要用未加引号的 `-Dfoo=bar` 串

### 推荐模块内包结构（演进目标）

```
org.androidannotations.ksp/
  AndroidAnnotationsProcessorProvider
  AndroidAnnotationsSymbolProcessor
  env/          # options, logger, R, manifest
  model/        # extracted elements
  validate/
  generate/     # writers / holders
  handler/      # one handler per annotation (or small group)
```

当前 MVP 仍是扁平的 `MvpJavaGenerator` 等，P0a 应开始拆分，但**不要一次大爆炸重写**；按切片迁移。

---

## 2. 当前已完成（MVP）

**已支持注解（浅实现）：**

`@EActivity` `@EBean` `@ViewById` `@StringRes` `@Extra` `@AfterViews` `@Background` `@UiThread`

**已具备：**

- Maven 模块 + `SymbolProcessorProvider` SPI
- `examples/kotlin` 升工具链并切 KSP，`assembleDebug` 通过
- `examples/kotlin-bench` 200 类冷构建对比写入 `BENCHMARK.md`
- README 标明基于上游仓库

**明确缺口（相对 APT）：**

| 能力 | 状态 |
|------|------|
| `IntentBuilder_` / `Xxx_.intent(...)` | 无 |
| `@Bean` + EBean scope 缓存 | 无 |
| `@AfterInject` | 无 |
| `@EFragment` / FragmentBuilder / `@FragmentArg` | 无 |
| Manifest 查找与组件注册校验 | 无（仅 options 默认包名） |
| R 符号校验 / `useR2` | 无（int 字面量或 `R.x.name` 拼接） |
| 父类注解传播 | 无 |
| 嵌套类 → `Outer_.Inner_` | 无（会错误顶层生成） |
| 未实现注解告警 | 无 |
| Bundle 全类型（Parcelable 等） | Extra 仅基本类型+String |
| 增量 KSP | 无 |

关键源码：

- `AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/`

---

## 3. 分期路线（按序执行）

### P0a — 基础设施 + 补齐现有 8 注解（优先，15–20 人日）

**没有本阶段，无法谈替换。**

交付清单：

1. Options：`resourcePackageName`、`androidManifestFile`、`library`、`classSuffix`、`useR2`（对齐 APT Option 名）
2. Manifest 解析 + `@E*` 组件注册校验（library 模式放宽）
3. R / R2 解析；注解 res 用字段引用而非盲写 int（library 非 final R）
4. `ActivityIntentBuilder`：生成 `intent()` + fluent extra + `startActivity` / `startActivityForResult`
5. `@Extra`：BundleHelper 级类型；builder 同步 putExtra
6. `@EBean`：Default / Activity / Fragment / Singleton scope + `rebind`（对照 `EBeanHolder`）
7. `@Bean` + Activity 上 `BeanHolder` put/get 真正生效
8. `@AfterInject` 调用点
9. 父类注解收集（对照 `getAncestorAnnotatedElements`）
10. 嵌套类生成到外层生成类的 static 内部类
11. 对**扫描到但未实现**的 AA 注解打 KSP warning
12. 验收：扩展 `examples/kotlin` 覆盖 `intent()`、`@Bean`；与 APT 生成抽查 diff 关键路径

### P0b — 典型界面可卸 kapt（20–30 人日）

- `@EFragment` + `FragmentBuilder_` + `@FragmentArg` + `@FragmentById`/`@FragmentByTag`
- `@Click` `@LongClick`
- 常用 Res：`DefaultResHandler` 族 + Color/Drawable（ContextCompat 分支）
- `@OptionsMenu` `@OptionsItem` `@OptionsMenuItem`
- `@InstanceState`
- `@OnActivityResult`（含参数 `@Extra`）
- `@SystemService` `@AfterExtras`
- `@EApplication`/`@App` `@EService` + Service IntentBuilder

**验收：** 新建 `examples/kotlin-parity`（或扩 kotlin），用 kapt 与 ksp 各编一次，对比生成类公开 API（intent/builder/关键方法）一致；样例 App 可卸 kapt。

### P1 — 常见旧工程（20–30 人日）

`@SharedPref`/`@Pref`，更多 View 监听，`@Receiver`，`@EView`/`@EViewGroup`，`@IgnoreWhen`，`@ViewsById`，`@FromHtml`，`@NonConfigurationInstance` 等。

### P2 — 偏门按需（15–25 人日）

Preference 屏、SeekBar/ViewPager/Key、WakeLock/Trace/Suppose*、DataBinding、Provider/Receiver/IntentService 等。

### 插件（按依赖存在与否）

| 插件 | 默认 |
|------|------|
| RoboGuice / Otto | 不做 |
| ORMLite | 仅当业务依赖存在 |
| REST-Spring | 单独大期（约 15–25 人日） |

### 并行可做（不阻塞 P0）

- KSP 增量处理、去掉空第二轮 → 提升改 1 文件时的体感速度  
- 详见历史结论：冷全量再抠有限；增量收益更大

---

## 4. APT 对照速查

| 主题 | APT 路径 |
|------|----------|
| 处理器入口 | `androidannotations/.../internal/AndroidAnnotationProcessor.java` |
| Core 注册表 | `.../internal/core/CorePlugin.java` |
| Activity holder | `.../holder/EActivityHolder.java` |
| Bean holder | `.../holder/EBeanHolder.java` |
| Fragment holder | `.../holder/EFragmentHolder.java` |
| IntentBuilder | `.../internal/core/helper/ActivityIntentBuilder.java` |
| Manifest | `.../internal/helper/AndroidManifestFinder.java` |
| R | `.../helper/ProjectRClassFinder.java` 等 |
| Extra | `.../internal/core/handler/ExtraHandler.java` + `BundleHelper` |
| Bean 注入 | `.../internal/core/handler/BeanHandler.java` |
| 注解 API | `androidannotations-api/.../annotations/**` |

生成类后缀：`ModelConstants.classSuffix()` 默认 `_`。

---

## 5. 日常命令

```powershell
# 安装 API + KSP（PowerShell 务必给 -D 参数加引号）
cd AndroidAnnotations
.\mvnw.cmd -pl androidannotations-core/androidannotations-api,androidannotations-core/androidannotations-ksp -am install "-DskipTests" "-Dmaven.test.skip=true" "-Dcheckstyle.skip=true" "-Dlicense.skip=true" "-Dmaven.javadoc.skip=true"

# 如需 kapt 对照，再装 APT
.\mvnw.cmd -pl androidannotations-core/androidannotations -am install "-DskipTests" "-Dmaven.test.skip=true" "-Dcheckstyle.skip=true" "-Dlicense.skip=true" "-Dmaven.javadoc.skip=true"

# 示例
cd ..\examples\kotlin
.\gradlew.bat assembleDebug --no-build-cache

# 测速（可选）
cd ..\kotlin-bench
.\gradlew.bat assembleDebug "-Paa.processor=ksp"
.\gradlew.bat assembleDebug "-Paa.processor=kapt"
```

推远程（用户曾要求写 main；注意 protected main 可能需审批）：

```powershell
git push ksp HEAD:main
```

---

## 6. 中断恢复协议（强制）

每个工作会话：

1. 读 `PLAYBOOK.md`（本文件）确认目标未变  
2. 读 `EXECUTION_LOG.md` **从底部往上**找到最新 `## Session` 与 `### Next`  
3. 只做 `Next` 指向的切片；做完立刻追加日志（不要攒到最后）  
4. 日志模板见 `EXECUTION_LOG.md` 顶部  
5. 若会话可能中断：优先把「可编译的中间态」commit；日志里写清未提交路径与失败命令  
6. **不要重新发明分期**；不要回退改做插件除非日志/用户要求  
7. 效果对齐手段：对同一 fixture，有条件时用 APT 生成一份 `*_` 与 KSP 输出做公开 API diff

### Agent 自检清单（每切片结束）

- [ ] `androidannotations-ksp` 能 `mvn install`（跳过测试/checkstyle/javadoc 参数同上）
- [ ] `examples/kotlin`（或本切片新增 example）`assembleDebug` 通过
- [ ] `EXECUTION_LOG.md` 已更新 Status / Done / Next
- [ ] 若行为对齐：注明对照了哪个 APT handler/holder

---

## 7. 工作量与预期效果

| 阶段 | 人日 | 效果 |
|------|------|------|
| P0a | 15–20 | 调用链开始对齐（intent/Bean）；尚不能宣称全面替换 |
| P0b | 20–30 | **多数典型界面可卸 kapt** ← 生产替换的第一道里程碑 |
| P1 | 20–30 | 覆盖常见旧工程 |
| P2 | 15–25 | 接近 core 全量 |
| 增量 KSP | 另计 | 改 1 文件体感通常 2–5×+；冷全量再快有限 |

典型无插件路径合计约 **55–80 人日**。

---

## 8. 相关文档

- [`EXECUTION_LOG.md`](EXECUTION_LOG.md) — 执行日记（续跑依据）
- [`CHECKLIST_P0a.md`](CHECKLIST_P0a.md) — P0a 打勾清单
- [`../../BENCHMARK.md`](../../BENCHMARK.md) — 已有 MVP 测速
- [`../../README.md`](../../README.md) — 仓库说明
