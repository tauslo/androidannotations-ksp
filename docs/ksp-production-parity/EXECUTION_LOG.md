# Execution Log — KSP Production Parity

> **续跑时先看本文件最新 Session 的 `### Next`。**  
> 完整规则见 [`PLAYBOOK.md`](PLAYBOOK.md)。

---

## Log entry template（复制到文件末尾）

```markdown
## Session YYYY-MM-DD — <short title>

- **Agent**: <name or model>
- **Goal slice**: P0a / P0b / … — <one sentence>
- **Git**: branch=`…` HEAD=`<sha>` remote=`ksp/main` <synced|ahead|pending push>
- **Status**: in_progress | blocked | done

### Done
- …

### Changed paths
- …

### Verify
- [ ] mvn install ksp module
- [ ] examples/kotlin assembleDebug
- [ ] other: …

### Blockers
- none | …

### Next
1. <concrete next step for the following agent>
2. …
```

---

## Session 2026-11-10 — MVP baseline + docs handoff

- **Agent**: Composer (Cursor)
- **Goal slice**: MVP complete; export playbook for production parity handoff
- **Git**: branch=`develop` HEAD=`eb5ae1e6` remote=`ksp/main` synced
- **Status**: done（MVP + handoff docs）；**P0a 未开始**

### Done
- Force-pushed fork baseline to `ksp/main` with upstream attribution (`0d24f1b6`)
- Added Maven module `androidannotations-ksp` (Java SymbolProcessor, Kotlin 2.1.10 / KSP 2.1.10-1.0.31)
- MVP annotations: EActivity, EBean, ViewById, StringRes, Extra, AfterViews, Background, UiThread
- Upgraded `examples/kotlin` to AGP 8.7.3 / Gradle 8.11.1 / KSP; `assembleDebug` OK
- Bench 200 classes: kapt median 129.88s vs KSP 100.55s (−22.6%) → `BENCHMARK.md`
- Pushed KSP MVP commit `584611bd` to `ksp/main`
- Wrote this playbook + checklist for other agents

### Changed paths (MVP, already on main)
- `AndroidAnnotations/androidannotations-core/androidannotations-ksp/**`
- `examples/kotlin/**`, `examples/kotlin-bench/**`
- `BENCHMARK.md`, `README.md`
- `docs/ksp-production-parity/**`（本手交文档，随后续 commit）

### Verify
- [x] mvn install ksp (+ api) with skip flags
- [x] examples/kotlin assembleDebug
- [x] kotlin-bench ksp + kapt assembleDebug
- [x] BENCHMARK.md written

### Blockers
- none for MVP
- Production parity blocked on missing IntentBuilder / Bean / Fragment / Manifest validation（见 PLAYBOOK P0a）

### Next
1. **Start P0a** per [`CHECKLIST_P0a.md`](CHECKLIST_P0a.md) item order
2. First code slice recommended: **Options + Manifest finder + wire into processor**（对照 `AndroidManifestFinder`）
3. Then **Activity IntentBuilder_** so `HelloAndroidActivity_.intent(ctx)` exists
4. After each checklist item: update this log + keep `examples/kotlin` green
5. Do **not** start P0b/P1/plugins until P0a checklist is mostly done

### Notes for resume
- PowerShell: quote all `-D...` Maven args
- KSP module checkstyle skipped in its pom; parent license/javadoc often need `-Dlicense.skip=true` `-Dmaven.javadoc.skip=true`
- AA APT test compile fails on JDK 17 (missing JAXB) → use `-Dmaven.test.skip=true` when installing APT
- Kotlin `@Extra` + kapt stubs: property looks private; bench omitted `@Extra` for kapt; kotlin example still has Extra under KSP
- Generator today is string-based `MvpJavaGenerator`; when adding IntentBuilder prefer extracting `ActivityGenerator` / holder-style types rather than growing one god-class forever
---

## Session 2026-11-10 — P0a: Options + Manifest finder + component validation

- **Agent**: Codex
- **Goal slice**: P0a — Options + Manifest finder + wire into processor（对照 APT `AndroidManifestFinder` / `ValidatorHelper.componentRegistered`）
- **Git**: branch=`ksp/main` HEAD=`2c16aa03`（本 session commit 的 base） remote=`ksp/main` pending push
- **Status**: done

### Done
- `KspOptions`：解析 + 校验 `classSuffix` / `androidManifestFile` / `library` / `useR2` / `resourcePackageName`（名称对齐 APT `Option`）
- `AndroidManifest` model + `AndroidManifestFinder`：显式 option → 源文件锚点向上探测 → `user.dir` 兜底；merged manifest（`build/intermediates/merged_manifests/**`）**在任意层级**都优先于 `src/main/AndroidManifest.xml`；同组内优先带 `package` 属性的文件
- `KspEnvironment` facade：options + manifest + logger；`getResourcePackage()` = option ?? manifest application package；`getRClassName()` = R2|R（对照 APT `ProjectRClassFinder`）
- `ComponentValidator.componentRegistered`：注册了原始类 → **error**；生成类名未注册 → **warning**；abstract/library 跳过（对照 APT）
- Processor 接线：用注解类的 containing files 准备 environment；ACTIVITY 做注册校验；resourcePackage 缺失时给出可操作 error
- `examples/kotlin` `build.gradle` 的 `ksp { }` 上方注释文档化全部 5 个 option

### KSP2 gotchas（实测验证，后续切片注意）
- `KSFile.getFileName()` 只返回**文件名**；`getFilePath()` 才返回绝对 VFS 路径（`D:/...`）——manifest 锚点必须用 `getFilePath()`
- KSP 跑在 Kotlin daemon 里：`user.dir` = `C:\Users\...\AppData\Local\kotlin\daemon`，**不是**工程目录——不能当主探测起点
- `KSPLogger.info(...)` **不会**打印到 Gradle 控制台（只有 warn/error 可见）——诊断日志用 `warn`
- `KSPLogger` 签名：`logging/info/warn/error(String message, KSNode symbol)`，message 在前；没有 `debug()`

### Changed paths
- `androidannotations-ksp/.../ksp/env/KspOptions.java`（新）
- `androidannotations-ksp/.../ksp/env/AndroidManifest.java`（新）
- `androidannotations-ksp/.../ksp/env/AndroidManifestFinder.java`（新）
- `androidannotations-ksp/.../ksp/env/KspEnvironment.java`（新）
- `androidannotations-ksp/.../ksp/validate/ComponentValidator.java`（新）
- `androidannotations-ksp/.../ksp/AndroidAnnotationsSymbolProcessor.java`、`MvpModel.java`、`MvpJavaGenerator.java`、`KspAnnotationUtils.java`
- `examples/kotlin/build.gradle`

### Verify
- [x] mvn install ksp module（skip flags）
- [x] examples/kotlin **clean** assembleDebug — summary 日志显示 `manifest=...build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`，无注册告警
- [x] negative：manifest 注册原始类 `.activity.HelloAndroidActivity` → KSP **error** + BUILD FAILED（"Please register HelloAndroidActivity_ instead of HelloAndroidActivity"）
- [x] negative：manifest 注册不存在的类 → 仅 warning，BUILD SUCCESSFUL
- APT 对照：`AndroidManifestFinder`、`ValidatorHelper.componentRegistered`、`ModelConstants.classSuffix`、`ProjectRClassFinder`（resourcePackageName/useR2）

### Blockers
- none

### Next
1. **Activity `IntentBuilder_`**（BLOCKER）：生成 `public static IntentBuilder_ intent(Context)` + 每个 `@Extra` 的 fluent setter + `startActivity()` / `startActivityForResult(int)`，对照 APT `ActivityIntentBuilder`，让示例能跑 `HelloAndroidActivity_.intent(ctx).myIntExtra(1).start()`；按 MVP notes 优先抽出 `ActivityGenerator`，别再往 `MvpJavaGenerator` 里堆
2. 然后深化 `@Extra`：Bundle restore 全类型（对照 APT `BundleHelper`）+ 预留 `@AfterExtras` hook
3. P0a 剩余项：R 资源缺失 warn/fail、流水线向 handler/generate 包拆分、父类注解收集、嵌套 `@EBean`、未实现注解告警；之后是 EBean scopes + `@Bean` 注入（BLOCKER）
