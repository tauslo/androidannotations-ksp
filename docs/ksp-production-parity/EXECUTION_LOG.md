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

## Session 2026-11-11 — P0a: Activity IntentBuilder_

- **Agent**: Codex
- **Goal slice**: P0a — Generate Activity `IntentBuilder_` (BLOCKER), parity with APT `ActivityIntentBuilder`
- **Git**: branch=`ksp/main` HEAD=`93a363db` remote=`ksp/main` ahead 1（local only, not pushed）
- **Status**: done

### Done
- Each `@EActivity` now generates nested `public static class IntentBuilder_ extends ActivityIntentBuilder<IntentBuilder_>` + `public static IntentBuilder_ intent(Context)`（对照 APT `IntentBuilder.createIntentMethod` / `createContextConstructor`，ctor 传 generated class）
- Fluent setter per `@Extra`: `myIntExtra(int)` → `return super.extra(MY_INT_EXTRA, arg);`（chainable，返回 builder）
- `startForResult(int)` override: Activity context → `startActivityForResult(intent, requestCode)`, else `context.startActivity(intent)`; returns `PostActivityStarter`（final `start()` inherited from API base class，calls `startForResult(-1)`）
- APT parity fix for extra constant naming — mirrors `CaseHelper.camelCaseToUpperSnakeCase(null, name, "Extra")`: `_EXTRA` suffix only when field name does not already end with `extra` (case-insensitive); `myIntExtra` → `MY_INT_EXTRA`（was wrongly `MY_INT_EXTRA_EXTRA`）
- Extra arg casts mirror APT `IntentBuilder.getSuperPutExtraInvocation`: standard bundle types (primitives/wrappers/String/CharSequence/Bundle + arrays) no cast; Parcelable subtype → `(android.os.Parcelable)`; else Serializable → `(java.io.Serializable)`. Custom parcelable via `Parcels.wrap` deferred to Bundle-types slice.
- Compile fix: `KSClassDeclaration.getSuperTypes()` returns `KSTypeReference` — resolve() to `KSType` before walking super types
- examples/kotlin: added "Start with extra" button in main.xml; `afterViews()` click handler runs `HelloAndroidActivity_.intent(this).myIntExtra(42).start()`（acceptance demo）
- Fragment-based `intent(Fragment)` overloads + activity options (`withOptions`) deferred to P0b（classpath-dependent in APT）

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AndroidAnnotationsSymbolProcessor.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/KspAnnotationUtils.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpJavaGenerator.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpModel.java
- examples/kotlin/src/main/res/layout/main.xml
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidActivity.kt

### Verify
- [x] mvn install ksp module（MVN_EXIT=0）
- [x] examples/kotlin `kspDebugKotlin` — generated file contains nested `IntentBuilder_`, static `intent(Context)`, constant `MY_INT_EXTRA = "myIntExtra"`（no double suffix）, fluent `myIntExtra(int)`, `startForResult` override
- [x] examples/kotlin `clean assembleDebug` BUILD SUCCESSFUL（Kotlin demo + Java builder compile）
- [x] APT mirrored: `IntentBuilder`/`ActivityIntentBuilder` ctor passes generated class; extra key name/value per `ExtraHandler.getOrCreateStaticExtraField`; cast rules per `getSuperPutExtraInvocation`

### Blockers
- none

### Next
1. 深化 `@Extra`: Bundle restore 全类型（对照 APT `BundleHelper`）+ 预留 `@AfterExtras` hook
2. P0a 剩余项: R 资源缺失 warn/fail、流水线向 handler/generate 包拆分、父类注解收集、嵌套 `@EBean`、未实现注解告警；之后是 EBean scopes + `@Bean` 注入（BLOCKER）

## Session 2026-11-11 — P0a: @Extra Bundle restore all types + @AfterExtras

- **Agent**: Codex
- **Goal slice**: P0a — Deepen `@Extra`: Bundle restore all types (mirrors APT `BundleHelper`) + `@AfterExtras` hook
- **Git**: branch=`ksp/main` HEAD=`d3865657` remote=`ksp/main` ahead 1（local only, not pushed）
- **Status**: done

### Done
- @Extra restore all types per APT `BundleHelper.METHOD_SUFFIX_BY_TYPE_NAME`: primitives/primitive arrays/String/CharSequence/Bundle → typed getters; Parcelable subtype → `getParcelable` (no cast); Serializable → `(T) getSerializable`; `ArrayList<Parcelable>` → `getParcelableArrayList`; other ArrayLists → `(T) getSerializable` + `@SuppressWarnings("unchecked")`; Parcelable array → API `BundleHelper.getParcelableArray(bundle, key, T[].class)`; SparseArray → `getSparseParcelableArray`; `@org.parceler.Parcel`-annotated or LinkedList/LinkedHashMap/SortedMap/SortedSet/LinkedHashSet → `Parcels.unwrap(getParcelable(...))`
- Write side (IntentBuilder_ fluent) per APT `IntentBuilder.getSuperPutExtraInvocation`: primitives/String/CharSequence no cast; Parcelable → `(android.os.Parcelable)`; parceler types → `Parcels.wrap(arg)`; else Serializable → `(java.io.Serializable)`
- `injectExtras_()` restructured to APT shape: `if (extras_ != null) {…}` instead of early return, so `@AfterExtras` methods run even when the bundle is empty（matches APT test semantics）; called from onCreate and setIntent
- `@AfterExtras` support: no-arg void methods on `@EActivity` collected and invoked at end of `injectExtras_()`
- Type-name mapping: kotlin.IntArray/LongArray/… → int[]/long[]/…; kotlin.collections.ArrayList/List/Map/Set typealiases → java.util.*; new `fullTypeNameWithArgs` keeps generic args（e.g. `java.util.ArrayList<android.graphics.Point>`）
- KSP2 API note: `KSType.getArguments()` returns `List<KSTypeArgument>` (no `KSTypeProjection` in 2.1.10)
- examples/kotlin: added @Extra String / IntArray / Point (Parcelable) / Date (Serializable) / ArrayList<Point> + `@AfterExtras onExtrasInjected()`; button chain now runs `.myIntExtra(42).myStringExtra("…").myPointExtra(Point(3, 7)).start()`

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AndroidAnnotationsSymbolProcessor.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/KspAnnotationUtils.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpJavaGenerator.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpModel.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AnnotationNames.java
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidActivity.kt

### Verify
- [x] mvn install ksp module（MVN_EXIT=0）
- [x] examples/kotlin `kspDebugKotlin` — generated restore: getInt / getString / getIntArray / getParcelable / `(java.util.Date) getSerializable` / `getParcelableArrayList`; write-side casts match APT; `onExtrasInjected()` emitted after the if-block
- [x] examples/kotlin `clean assembleDebug` BUILD SUCCESSFUL

### Deviations from APT（intentional）
- CharSequence extra: no cast (APT would cast to Serializable → runtime CCE risk); uses typed `extra(String, CharSequence)` overload
- Non-Serializable, non-Parcelable custom types: left uncast so the builder method fails to compile early (APT emits a broken Serializable cast that only fails at runtime)

### Blockers
- none

### Next
1. P0a 剩余项: R 资源缺失 warn/fail、流水线向 handler/generate 包拆分、父类注解收集、嵌套 `@EBean`、未实现注解告警；之后是 EBean scopes + `@Bean` 注入（BLOCKER）
## Session 2026-11-11 — P0a: R/R2 index + resource validation + field-ref emission (APT parity)

- **Agent**: Codex
- **Goal slice**: P0a — Resolve `R`/`R2` by package; fail on missing id/layout/string; prefer `pkg.R.type.name` field refs over raw int
- **Git**: branch=`ksp/main` HEAD=`4a9659eb` remote=`ksp/main` ahead 3（local only, not pushed）
- **Status**: done

### Done
- New `env/RClassIndex.java`: per-resource-package index of the R class. Field names read from the compiled resource class via KSP (`resolver.getClassDeclarationByName` + nested-class walk); int values read from the newest AGP runtime symbol list (`build/intermediates/runtime_symbol_list/<variant>/R.txt`) — KSP2 exposes no constant values for classpath Java fields. Mirrors APT `ProjectRClassFinder` + `RInnerClass`
- `KspEnvironment.prepare()` resolves the index once (requires resourcePackage); exposes `getRClassIndex()`
- Resource validation before generation (mirrors APT `IdValidatorHelper.resIdsExist`): int-valued params checked against R.txt values (`Resource id value not found in R.<type>: <value>`); name-based params checked against declared fields with snake_case fallback (`Resource name not found in R.<type>: <name>`); layout follows APT ALLOW_NO_RES_ID (no element-name check). On error: `logger.error` + skip the class (not generated)
- Emission prefers field refs: `resExpression` now takes the index; int path → `fieldNameForValue` → `pkg.R.type.name`; name path → `resolveFieldName` → `pkg.R.type.name`; falls back to raw int / property name only when unresolved
- `setContentView(<resExpr>)` single call guarded by hasLayoutId || hasLayoutName
- examples/kotlin: added `@ViewById(resName = "launchButton")` + click listener via the injected field (no more findViewById)

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AndroidAnnotationsSymbolProcessor.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/KspAnnotationUtils.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpJavaGenerator.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/env/KspEnvironment.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/env/RClassIndex.java (new)
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidActivity.kt

### Verify
- [x] mvn install ksp module（MVN_EXIT=0）
- [x] examples/kotlin `kspDebugKotlin`: generated `setContentView(org.androidannotations.gradle.R.layout.main)` / `R.string.hello` / `R.id.helloTextView` / `R.id.launchButton` — all field refs, no raw ints
- [x] examples/kotlin `clean assembleDebug` BUILD SUCCESSFUL
- [x] Negative test（local only, reverted）: `@ViewById(resName="nope")` → `e: [ksp] ... Resource name not found in R.id: nope in org.androidannotations.gradle.activity.HelloAndroidActivity`, class not generated

### Blockers
- none

### Next
1. P0a pipeline structure: split MVP god-generator toward `handler` + `generate` packages（渐进）
2. Collect annotations from **superclass** members (ancestor annotations)
3. Nested `@EBean` → static nested class inside outer `*_`; warn on recognized-but-unimplemented AA annotations
4. Then BLOCKERs: EBean scopes (Default/Activity/Fragment/Singleton, mirrors `EBeanHolder.createFactoryMethod`) + rebind; `@Bean` injection via `Generated_.getInstance_(…)` + BeanHolder map + `@AfterInject`
5. P0a exit acceptance: `@Bean` EnhancedBean demo in examples/kotlin; APT vs KSP public-API spot-check; mark P0a done here with Next = first P0b item

## Session 2026-11-11 — handler/generate split + superclass annotation collection

- **Agent**: Codex
- **Goal slice**: P0a — Split MVP god-generator toward `handler` + `generate` packages; collect annotations from superclass members (prev session Next 1+2)
- **Git**: branch=`ksp/main` HEAD=`61d7acb3` remote=`ksp/main` ahead 4（local only, not pushed）
- **Status**: done

### Done
- Package split: new `handler/MvpHandler.java` — `build(Resolver, KSClassDeclaration, String)` + all model-collection methods (collectProperty/collectFunction/ExtraTemplates/isParceler*/topLevelGenericArg/topLevelComma/resolveClassDeclaration/implementsInterface/superclassOf) moved verbatim out of the processor; `MvpJavaGenerator.java` moved to `generate/` package (made public); processor is now a thin driver: options → KspEnvironment → validate → handler.build → generate
- Ancestor walk in handler (mirrors APT `ModelExtractor`): `superclasses()` builds nearest-first chain via `superclassOf`, stops at `java.lang.Object` / any `android.*` qualified name, depth guard 64, reversed to farthest-first; `collectMembers()` iterates each ancestor's declarations. Ancestor class-level annotations intentionally NOT collected (APT parity: "currently do not take into account")
- Made public for cross-package use: `MvpModel` (class + Kind/Param/FieldInjection/MethodInfo + all ctors), `KspAnnotationUtils`, `AnnotationNames`
- APT-parity fix in `KspAnnotationUtils.extraKeyConstant`: mirrors APT `CaseHelper.camelCaseToUpperSnakeCase(null, name, "Extra")` — appends `_Extra` only when the name doesn't already end with "extra" (case-insensitive), via new private `camelCaseToSnake()` using APT's regex tokenization + single-uppercase-acronym logic. Old KSP output was `EXTRA_FROM_BASE__E_X_T_R_A`; now `EXTRA_FROM_BASE_EXTRA`
- Demo: new `HelloAndroidBase.kt` (abstract, extends Activity — required so the activity stays a Context; `@Extra protected var extraFromBase: Int = 0`); `HelloAndroidActivity` now extends it and chains `.extraFromBase(7)`

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AndroidAnnotationsSymbolProcessor.java (slimmed)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/handler/MvpHandler.java (new)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/generate/MvpJavaGenerator.java (moved from ksp/; old file deleted)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpModel.java (made public)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/KspAnnotationUtils.java (made public + extraKeyConstant APT parity + camelCaseToSnake)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AnnotationNames.java (made public)
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidActivity.kt (extends HelloAndroidBase; .extraFromBase(7))
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidBase.kt (new)
- docs/ksp-production-parity/CHECKLIST_P0a.md

### Verify
- [x] mvn install ksp module（MVN_EXIT=0）
- [x] examples/kotlin `kspDebugKotlin`: generated 2 class(es); `HelloAndroidActivity_` has `EXTRA_FROM_BASE_EXTRA` constant, `setExtraFromBase(extras_.getInt(EXTRA_FROM_BASE_EXTRA))` in `injectExtras_`, fluent `IntentBuilder_.extraFromBase(int)`
- [x] Resource refs remain field refs (`R.layout.main`, `R.string.hello`, `R.id.*`); 0 raw ints
- [x] examples/kotlin `clean assembleDebug` BUILD SUCCESSFUL

### Blockers
- none

### Next
1. P0a pipeline structure: nested `@EBean` / future nested types → static nested class inside outer `*_`; warn on AA annotations that are recognized but not implemented yet
2. Then BLOCKERs: EBean scopes (Default/Activity/Fragment/Singleton, mirrors `EBeanHolder.createFactoryMethod`) + rebind for Default scope; `@Bean` injection via `Generated_.getInstance_(…)` + BeanHolder map + `@AfterInject`; root context wiring as needed
3. Optional P0a: `@ViewById` method-injection form; Background/UiThread ordering notes
4. P0a exit acceptance: demo `intent(this)…start()` ✅ in code; `@Bean` EnhancedBean injection demo ❌ (still needed); APT vs KSP public-API spot-check (extraKeyConstant parity done this session — good data point); mark P0a done here with Next = first P0b item

## Session 2026-11-11 — nested @EBean static nesting + recognized-but-unimplemented annotation warnings

- **Agent**: Codex
- **Goal slice**: P0a — nested `@EBean` → static nested class inside outer `*_`; warn on AA annotations that are recognized but not implemented yet (prev session Next 1)
- **Git**: branch=`ksp/main` HEAD=`1e5d060c` remote=`ksp/main` ahead 5（local only, not pushed）
- **Status**: done

### Done
- Nested `@EBean` → static nested class (APT `BaseGeneratedClassHolder` parity): `MvpModel` +`nested` flag & `nestedModels`; `MvpHandler.build` derives `nested` from qualifiedName vs package.simple; processor is now 3-pass — build all models → attach each nested model to its enclosing generated model (validating static non-private non-final, enclosing must be a generated AA class, per-nested resource validation) → generate roots only
- KSP quirk: no `Modifier.STATIC` in KSP 1.0.31 — static detection = `JAVA_STATIC || !INNER` (Kotlin nested classes are static unless `inner`)
- Generator: `generateBean` refactored to `appendBeanClass(out, model, nested)`; new `generateNestedBean` (no package line) + `appendNestedClasses` inlines children re-indented one tab before the outer closing brace — arbitrary nesting depth via recursion; import-safe because the generator emits fully-qualified names only
- Warnings: `AnnotationNames.RECOGNIZED_NOT_IMPLEMENTED` = {Bean, AfterInject, EFragment, FragmentArg, Click, InstanceState, OptionsMenu, SharedPref} (roadmap comments); processor `warnUnimplemented(resolver)` — one warn per annotation type with element count + first location
- Demo: nested `@EBean class Greeting` inside `HelloAndroidActivity` (`@StringRes(resName="hello") var text`) + usage in `afterViews()` via `HelloAndroidActivity_.Greeting_.getInstance_(this)`

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpModel.java (nested flag + nestedModels)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/handler/MvpHandler.java (nested derivation, ctor arg)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/generate/MvpJavaGenerator.java (appendBeanClass / generateNestedBean / appendNestedClasses)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AnnotationNames.java (RECOGNIZED_NOT_IMPLEMENTED)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AndroidAnnotationsSymbolProcessor.java (3-pass loop + warnUnimplemented)
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidActivity.kt (nested @EBean Greeting + usage)
- docs/ksp-production-parity/CHECKLIST_P0a.md

### Verify
- [x] mvn install ksp module (MVN_EXIT=0) — first attempt failed on `Modifier.STATIC` (absent in KSP 1.0.31) + stray `return` in void method; fixed, rebuilt clean
- [x] examples/kotlin `kspDebugKotlin`: generated 2 class(es); `HelloAndroidActivity_` contains `public static final class Greeting_ extends Greeting implements ...OnViewChangedListener` nested one tab deeper with both `getInstance_(Context)` overloads; no top-level `Greeting_.java` in the generated dir
- [x] Nested bean injection uses field-ref resource: `setText(resources_.getString(org.androidannotations.gradle.R.string.hello))`; 0 raw ints
- [x] Warning path (temporary `@Bean` probe, then reverted): `w: [ksp] HelloAndroidActivity.kt:51: AndroidAnnotations KSP: @Bean is recognized but not implemented yet; ignoring 1 annotated element(s)`; clean regen confirmed after revert
- [x] examples/kotlin `clean assembleDebug` BUILD SUCCESSFUL

### Blockers
- none

### Next
1. BLOCKER: EBean scopes (Default/Activity/Fragment/Singleton, mirrors `EBeanHolder.createFactoryMethod`) + rebind for Default scope
2. BLOCKER: `@Bean` injection via `Generated_.getInstance_(…)` + BeanHolder map; `@AfterInject`; root context wiring as needed
3. Optional P0a: `@ViewById` method-injection form; Background/UiThread ordering notes
4. P0a exit acceptance: demo `intent(this)…start()` ✅ in code; `@Bean` EnhancedBean injection demo ❌ (still needed); APT vs KSP public-API spot-check; mark P0a done here with Next = first P0b item
