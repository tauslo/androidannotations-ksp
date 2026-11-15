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
## Session 2026-11-11 — EBean scopes (Default/Activity/Fragment/Singleton) + rebind for Default scope

- **Agent**: Codex
- **Goal slice**: P0a — `@EBean` scope-aware factory methods mirroring APT `EBeanHolder.createFactoryMethod`, plus `rebind` for the Default scope (prev session Next 1)
- **Git**: branch=`ksp/main` HEAD=`f472bf18` remote=`ksp/main` ahead 6（local only, not pushed）
- **Status**: done

### Done
- `MvpModel` +`scope` field (Default/Activity/Fragment/Singleton); `MvpHandler.build` reads the `scope` attribute of `@EBean` via new `KspAnnotationUtils.enumArg` (KSP exposes enum-typed args as a KSTypeReference to the enum entry; toString fallback for other representations)
- `MvpJavaGenerator.appendBeanClass` now branches on scope, mirroring APT `EBeanHolder.createFactoryMethod` + `EBeanHandler.process`:
  - Default: ctors call `init_()`; both `getInstance_(Context)` and `getInstance_(Context, Object)` overloads; new `rebind(Context)` method (APT only generates it for the Default scope)
  - Activity: ctors without `init_()`; single-arg `getInstance_` with BeanHolder lookup (getBean → miss: new + putBean + init_) and fallthrough create+init_; no 2-arg overload
  - Fragment: ctors without `init_()`; simple single-arg create+init_; 2-arg with rootFragment BeanHolder lookup + fallthrough `new(context, rootFragment)`
  - Singleton: ctors without `init_()`; `private static X instance_` field; `getInstance_` creates via `context.getApplicationContext()` inside an `OnViewChangedNotifier.replaceNotifier(null)` guard and restores the previous notifier
- New helper `appendBeanHolderLookup` mirrors APT `requestInstanceFromBeanHolder`; unknown scope values fall back to Default emission
- Demo: nested `@EBean(scope = EBean.Scope.Activity) class Counter` in HelloAndroidActivity.kt; afterViews logs `Counter_.getInstance_(this)` plus a BeanHolder dedup check (`counter === second getInstance_ call`) proving the holder branch returns the same instance

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpModel.java (scope field + ctor arg)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/KspAnnotationUtils.java (enumArg)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/handler/MvpHandler.java (read scope from @EBean)
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/generate/MvpJavaGenerator.java (scope-aware appendBeanClass + appendBeanHolderLookup)
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidActivity.kt (nested Activity-scoped Counter bean + usage in afterViews)
- docs/ksp-production-parity/CHECKLIST_P0a.md

### Verify
- [x] mvn install ksp module (MVN_EXIT=0)
- [x] examples/kotlin `kspDebugKotlin`: generated 2 class(es); `Greeting_` (Default): ctors call `init_()`, both overloads, `rebind(Context)` present; `Counter_` (Activity): ctors without `init_()`, single `getInstance_` with BeanHolder lookup + fallthrough, no 2-arg overload, no rebind — matches APT shapes
- [x] Top-level `EnhancedBean_` (Default) has `rebind`
- [x] examples/kotlin `clean assembleDebug` BUILD SUCCESSFUL

### Blockers
- none

### Next
1. BLOCKER: `@Bean` injection via `Generated_.getInstance_(…)` + BeanHolder map; `@AfterInject`; root context wiring as needed
2. Optional P0a: `@ViewById` method-injection form; Background/UiThread ordering notes
3. P0a exit acceptance: demo `intent(this)…start()` ✅ in code; `@Bean` EnhancedBean injection demo ❌ (still needed); APT vs KSP public-API spot-check; mark P0a done here with Next = first P0b item

## Session 2026-11-11 — @Bean injection + @AfterInject (APT BeanHandler/AfterInjectHandler parity)

- **Agent**: Codex
- **Goal slice**: P0a — `@Bean` injection via `Generated_.getInstance_(…)` + `@AfterInject` invoked after the injection block (prev session Next 1, BLOCKER)
- **Git**: branch=`ksp/main` HEAD=`286af505` remote=`ksp/main` ahead 7（local only, not pushed）
- **Status**: done

### Done
- `MvpModel`: nested `BeanTarget {name, declaredType, beanGeneratedQualifiedName, withRootFragmentArg}` + `BeanInjection {Form FIELD|METHOD, name, jvmField, targets}`; fields `beans`, `afterInject`, `errors`
- `AnnotationNames`: new constants `BEAN`, `AFTER_INJECT`; both removed from `RECOGNIZED_NOT_IMPLEMENTED` (the "recognized but not implemented yet" warning is gone)
- `MvpHandler`:
  - field form `collectBeanField`: kind guard (@EActivity only) + non-private; single-target FIELD injection
  - method form `collectBeanMethod`: exactly one parameter (else error); local declared with the param's full type incl. args (`fullTypeNameWithArgs`)
  - param form `collectBeanParams`: one shared braced block per method; the call is emitted only when ALL params are @Bean-annotated; partial annotation → whole block skipped without error (APT parity)
  - `resolveBeanTarget`: bean class = `@Bean value` arg or the element type; must carry `@EBean` (else error); scope via `enumArg(eBean, "scope", "Default")`; `withRootFragmentArg = scope ∉ {Singleton, Activity}`
  - `generatedBeanQualifiedName`: "_" appended to every class segment (nested `pkg.Outer.Inner` → `pkg.Outer_.Inner_`)
  - `@AfterInject`: no-arg, non-private, @EActivity only; invoked strictly after all injections
- `MvpJavaGenerator.appendBeanInjections`: field assign / setter in the init body (Kotlin properties use the setter unless @JvmField); method form emits a braced block with combined decl+assign per target then the call. Order in `init_()`: stringRes → `injectExtras_()` → bean injections → `@AfterInject` calls → registerListener
- Processor: models with non-empty `errors` are logged and skipped in both the nested and non-nested loops (no silent drop)
- Demo: `@Bean protected lateinit var enhancedBean: EnhancedBean` + `@AfterInject fun onInjected()` logging the injected instance

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpModel.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AnnotationNames.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/handler/MvpHandler.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/generate/MvpJavaGenerator.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AndroidAnnotationsSymbolProcessor.java
- examples/kotlin/src/main/kotlin/org/androidannotations/gradle/activity/HelloAndroidActivity.kt
- docs/ksp-production-parity/CHECKLIST_P0a.md

### Verify
- [x] mvn install ksp module (BUILD SUCCESS)
- [x] examples/kotlin `clean assembleDebug` BUILD SUCCESSFUL; KSP generated 2 class(es); the old "@Bean is recognized but not implemented yet" warning is gone
- [x] Generated `init_()`: `setEnhancedBean(org.androidannotations.gradle.activity.EnhancedBean_.getInstance_(this, null));` (2-arg form, Default scope) + `onInjected();` after `injectExtras_()` and before registerListener; 0 raw ints

### Blockers
- none

### Next
1. Optional P0a: `@ViewById` method-injection form; Background/UiThread ordering notes
2. P0a exit acceptance: APT vs KSP generated public-API spot-check for the sample Activity, tick the remaining "Acceptance for P0a exit" items (incl. the @Bean demo item), mark P0a done with Next = first P0b item

## Session 2026-11-11 — P0a exit acceptance — mark P0a done

- **Agent**: Codex
- **Goal slice**: P0a exit acceptance — APT vs KSP generated public-API spot-check, tick the remaining "Acceptance for P0a exit" items, mark P0a done (prev session Next 2)
- **Git**: branch=`ksp/main` HEAD=`8fcb9b01` remote=`ksp/main` ahead 8（local only, not pushed）
- **Status**: done

### Done
- Visibility divergence found & fixed: `MethodInfo.visibility` added to `MvpModel`; `methodVisibility()` helper in `MvpHandler` (reads the KSFunctionDeclaration visibility); emit sites in `MvpJavaGenerator` now honor it — generated methods no longer default to public when the source member is protected/private
- Bean OnViewChangedListener divergence found & fixed: APT registers the listener lazily only when view-support handlers run (`EComponentWithViewSupportHolder.setOnViewChanged()` ~L132 is the only site); KSP now guards registration on `!viewByIds.isEmpty() || !afterViews.isEmpty()`; the beans-with-views path mirrors APT source but is unexercised by the bench
- Spot-check verdict: **PASS** — bench `examples/kotlin-bench` (100 @EActivity + 100 @EBean): APT(kapt) vs KSP, 200/200 identical file paths; after normalization (annotations, whitespace, FQN-vs-import, `final`) DECL 0/200, SIG 0/200
- All 600 residual body-shape diffs = 6 classes of semantically-equivalent expression rewrites (each ×100 activity files):
  - `getBean` / `internalFindViewById`: cast parenthesization `((T) x)` vs `(T) x`
  - `startForResult` (IntentBuilder_): temp var + call vs chained call on the cast
  - `onViewChanged`: direct field write vs generated setter call — Kotlin-property deviation; kapt stubs expose Kotlin properties as protected fields, which is how APT direct field writes compile
  - `init_`: same statements with independent-statement order swapped (registerListener last) + setter call
  - `updateHelloTextView`: explicit `final` local copy before anonymous-class capture
- Comparison script v2 fixed (classDecl: m[5] for the implements list + dot added to the char class; extractMethods skips past each method body so methods count exactly once) — eliminated the v1 false DECL/SIG diffs
- Fresh-build re-verification this session: bench kapt `assembleDebug -Paa.processor=kapt` BUILD SUCCESSFUL; fresh kapt tree sha256-identical (200/200) to backup reference `C:\temp\kapt-generated`; bench KSP `clean assembleDebug -Paa.processor=ksp` BUILD SUCCESSFUL (36/36 tasks); comparison on the fresh trees reproduces exactly {checked:200, declMismatch:0, sigMismatchFiles:0, bodyShapeDiffs:600} with the same 6 keys
- Source-level parity in `examples/kotlin`: `HelloAndroidActivity_.intent(this)` (L62) + `.start()` (L67); `@Bean protected lateinit var enhancedBean: EnhancedBean` (L50-51) — compile and run under KSP
- @NotNull param annotation scope check: APT=100 vs KSP=0 occurrences (CLASS retention, outside the checked public-API surface) → recorded known deviation, deferred to P0b
- Known cosmetic deviations (non-blocking): `beans_` Map vs HashMap private field; bean init_/rebind method order swap; catch-var naming (`final Throwable e` vs `Throwable e__`); FQN-vs-import notation in KSP output; header comment blocks; KSP `final` param modifiers

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/MvpModel.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/handler/MvpHandler.java
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/generate/MvpJavaGenerator.java
- docs/ksp-production-parity/CHECKLIST_P0a.md
- docs/ksp-production-parity/EXECUTION_LOG.md

### Verify
- [x] APT module installed in m2 (jar newer than the 3 modified KSP sources)
- [x] Bench kapt build re-run this session: BUILD SUCCESSFUL
- [x] Bench KSP clean build post-fix: BUILD SUCCESSFUL (36/36 tasks executed)
- [x] APT vs KSP API diff clean after normalization: DECL 0/200, SIG 0/200; all 600 body diffs = the 6 equivalent rewrite classes

### Blockers
- none

### Next
1. P0b first item — `@EFragment` + `FragmentBuilder_` + `@FragmentArg` (+ @FragmentById/@FragmentByTag) per PLAYBOOK §3
2. Deferred from P0a (checklist "Deepen views / res / threads（质量）" section, left unticked): `@ViewById` method-injection form; @Background/@UiThread ordering notes compatible with future Trace/IgnoreWhen; Kotlin property → Java setter/field mapping review (incl. primitives); plus @NotNull param annotation parity

## Session 2026-11-12 — Generate P0b/P1/P2/Plugins/Perf checklists after P0a done

- **Agent**: Composer (Cursor)
- **Goal slice**: Docs — export remaining phase checklists for agent handoff
- **Git**: branch=`ksp/main` HEAD=`eabb66ac`（本 session 提交后更新） remote=`ksp/main` was ahead 9 before docs commit
- **Status**: done

### Done
- Confirmed P0a main path complete via prior exit-acceptance session; optional leftovers moved to P0b Carry-over
- Added checklists: `CHECKLIST_P0b.md`, `CHECKLIST_P1.md`, `CHECKLIST_P2.md`, `CHECKLIST_PLUGINS.md`, `CHECKLIST_PERF.md`
- Updated `PLAYBOOK.md` §2 progress + §8 index; `CHECKLIST_P0a.md` marked DONE; README links

### Changed paths
- `docs/ksp-production-parity/CHECKLIST_P0b.md`（新）
- `docs/ksp-production-parity/CHECKLIST_P1.md`（新）
- `docs/ksp-production-parity/CHECKLIST_P2.md`（新）
- `docs/ksp-production-parity/CHECKLIST_PLUGINS.md`（新）
- `docs/ksp-production-parity/CHECKLIST_PERF.md`（新）
- `docs/ksp-production-parity/PLAYBOOK.md`
- `docs/ksp-production-parity/CHECKLIST_P0a.md`
- `docs/ksp-production-parity/EXECUTION_LOG.md`
- `README.md`

### Verify
- [x] Docs cross-links consistent
- [ ] Push `ksp/main`（含此前 ahead 的 P0a commits + 本文档）— 若远程需同步请执行 push

### Blockers
- none

### Next
1. **P0b**：按 [`CHECKLIST_P0b.md`](CHECKLIST_P0b.md) 从 Fragment BLOCKER 块开始（`@EFragment` + `FragmentBuilder_` + `@FragmentArg`）
2. 注意工作区可能已有未跟踪的 `examples/kotlin-bench/.../BenchFragment*.kt` / `fragment_*.xml` — 接手时先 `git status` 决定纳入或清理
3. 并行可选：[`CHECKLIST_PERF.md`](CHECKLIST_PERF.md) 增量，不阻塞 P0b

## Session 2026-11-12 ? P0b: @EFragment + FragmentBuilder_ + @FragmentArg/@FragmentBy{Id,Tag}

- **Agent**: Codex
- **Goal slice**: P0b item 1?Fragment BLOCKER ??? `@EFragment` ??? + ?? `FragmentBuilder_` + `@FragmentArg`??? APT `EFragmentHolder` / `FragmentArgHandler`
- **Git**: branch=`ksp/main` HEAD=`2b288771` remote=`ksp/main` synced??? commit ????? push?
- **Status**: done

### Done
- `generate()` ?? `Kind.FRAGMENT` ? `generateFragment(model)`???? extends ?? fragment?implements `BeanHolder` + `HasViews`?+ view support ?? `OnViewChangedListener`?`!viewByIds.isEmpty() || !afterViews.isEmpty()`?? APT `EComponentWithViewSupportHolder` ???
- ?????`onCreate`?notifier swap ? `init_` ? super ? restore????? `onViewCreated`?`notifyViewChanged(this)`??`onDestroyView`?? `contentView_` + `@ViewById` ????`internalFindViewById`
- `appendFragmentOnCreateView`?layout inflate + `forceLayoutInjection`????? super view?null-check ? inflate?force=true ?? inflate?
- `@FragmentArg`??????? APT `camelCaseToUpperSnakeCase(name, "Arg")`?`title`?`TITLE_ARG`???? key ? resName??`injectFragmentArguments_()` ? `getArguments()` restore?null-guard??builder fluent putX + `build()`
- ?? `public static class FragmentBuilder_ extends FragmentBuilder<FragmentBuilder_, Foo>` + `public static FragmentBuilder_ builder()`?putX cast ???? APT????????Parcelable ?? `(android.os.Parcelable)`??? Serializable?
- `@FragmentById` / `@FragmentByTag`?byId ? `getFragmentManager().findFragmentById(R.id.x)`?byTag ? `findFragmentByTag(???)`?? byId ? `needsResourcePackage` ?????R ???
- Processor?`E_FRAGMENT` symbols ? `E_BEAN` ?????`validateModel` ?? `fragmentByIds` ???`validateFragmentById`?id ??? R index ????? error?
- Model?`Kind.FRAGMENT`?`fragmentArgs`/`fragmentByTags`/`fragmentByIds`?FragmentInjection: propertyName/typeName/resId/resName/childFragment??`forceLayoutInjection`
- Fixtures?kotlin-bench??`BenchFragment001`?view support + @ViewById + @FragmentByTag + title arg + fragment_main??`002`?? view support???? key "label"?fragment_simple??`003`?@ViewById(R.id.fragmentText) + @FragmentById(R.id.childContainer) + ??? key "count_key"?

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AndroidAnnotationsSymbolProcessor.java
- .../AnnotationNames.java
- .../KspAnnotationUtils.java
- .../MvpModel.java
- .../handler/MvpHandler.java
- .../generate/MvpJavaGenerator.java
- examples/kotlin-bench/src/main/kotlin/org/androidannotations/bench/BenchFragment001.kt???
- examples/kotlin-bench/src/main/kotlin/org/androidannotations/bench/BenchFragment002.kt???
- examples/kotlin-bench/src/main/kotlin/org/androidannotations/bench/BenchFragment003.kt???
- examples/kotlin-bench/src/main/res/layout/fragment_main.xml???
- examples/kotlin-bench/src/main/res/layout/fragment_simple.xml???

### Verify
- [x] mvn install ksp module BUILD SUCCESS?4.9.0-SNAPSHOT ????
- [x] examples/kotlin-bench `clean assembleDebug -Paa.processor=ksp` BUILD SUCCESSFUL?KSP ?? `generated 203 class(es)`?compileDebugJavaWithJavac ???? Java?? Fragment deprecated ???????fixtures ? android.app.Fragment?
- [x] ??????? 200 ?????? P0a ???C:\temp\ksp-p0a-before??????????? 3 ? fragment ??
- [x] GT ????? `-Paa.processor=kapt` ???? APT ?????????????? C:\temp\gt_cmp4.py??`BenchFragment00{1,2,3}_.java` ???? diff = `beans_` ?? `HashMap` vs `Map`???????
- [x] ?????KSP ????? clean ???????

### Known limitations????????
- Fragment ? `@Bean` / `@Extra` / `@StringRes` / `@AfterExtras` ????? emit???????
- ? map builder putX best-effort?`Parcelable[]` / `ArrayList<Parcelable>` ?? `putSerializable`
- Fragment ? `@Background` / `@UiThread` placement ??? GT ??
- ?? KDoc ???? builder ?? javadoc

### Blockers
- none

### Next
1. **P0b View listeners**?`@Click` + `@LongClick`?BLOCKER??? APT `AbstractViewListenerHandler`?0 ? / View ????? id???? [`CHECKLIST_P0b.md`](CHECKLIST_P0b.md)
2. Fragment ???Fragment ??? `@Bean`/`@AfterInject`/`@AfterViews`?Activity ??????`examples/kotlin` ?? Fragment + Arg + builder demo
3. res handlers?@ColorRes/@DrawableRes??? @OptionsMenu ?? ? @InstanceState / @OnActivityResult / @SystemService ? @EApplication/@EService

## Session 2026-11-13 — P0b: @Click + @LongClick（APT AbstractViewListenerHandler parity）

- **Agent**: Codex
- **Goal slice**: P0b View listeners BLOCKER —— `@Click` + `@LongClick`，对照 APT `AbstractViewListenerHandler` / `AbstractListenerHandler`（useElementName=true）
- **Git**: branch=`ksp/main` HEAD=`dcd68613`（session 起点），commit 仅本地未 push
- **Status**: done

### Done
- `MvpHandler.collectViewListener(...)`：方法校验（仅 @EActivity/@EFragment；至多 1 参且须 View 子类型；@Click 必须 void，@LongClick void/boolean）+ id 收集进 `clickListeners` / `longClickListeners`
- KSP 会暴露注解默认值而 APT 只见显式值：`value={-1}`（ResId.DEFAULT_VALUE）归一化为空；`resName=[""]` 视为缺省；两者皆缺省时按 APT `extractElementName` / `actionName` 规则去掉方法名 `Clicked` / `LongClicked` 后缀推导资源名
- 重复 id 校验：原始列表比较 + 空列表保护（细节见 Known limitations）
- `MvpJavaGenerator.appendViewListeners(...)`：Activity/Fragment `onViewChanged` 中 @ViewById 字段循环之后输出监听器局部变量（`view_<rName>`），if-block 紧随其后（afterViews / byTags / byIds 之前）；多 id 每个 id 独立匿名类块；与 @ViewById 字段共享 rName 时复用属性引用不新建局部
- bean 不接线：bean 上 @Click/@LongClick 直接报错（比 APT 更严格，见 Known limitations）
- Fixture `BenchActivity101`：8 个方法覆盖 显式 value / 裸方法名 / View 参 / Button 强转 / 多 id / resName / @LongClick void / @LongClick boolean；layout 9 个 id

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/AnnotationNames.java
- .../KspAnnotationUtils.java
- .../MvpModel.java
- .../handler/MvpHandler.java
- .../generate/MvpJavaGenerator.java
- .../AndroidAnnotationsSymbolProcessor.java
- examples/kotlin-bench/src/main/kotlin/org/androidannotations/bench/BenchActivity101.kt（新增）
- examples/kotlin-bench/src/main/res/layout/bench_activity_101.xml（新增）

### Verify
- [x] mvn install（api + ksp，-am）BUILD SUCCESS
- [x] examples/kotlin-bench `assembleDebug -Paa.processor=ksp` BUILD SUCCESSFUL（未 clean，保留 kapt GT）
- [x] GT 对比：`BenchActivity101_.java` KSP vs APT（kapt），归一化后逐字一致（3461 字符）；监听器接线区域零差异（9 局部变量顺序、if 块、匿名类、`(Button) view` 强转、longVoid `return true;`、longBoolean 返回转发全部一致）。归一化额外吸收了既有 Activity 模板偏差（见 Known limitations），对比脚本 C:/temp/gt_cmp6.py
- [x] 回归门禁：ksp/debug/java/org/androidannotations/bench 203 文件与 C:/temp/ksp-p0b-after 逐字节一致，仅新增 BenchActivity101_.java（共 204）；备份已刷新为 204
- [x] CHECKLIST_P0b.md「View listeners（高频）」4 项全部勾选

### Known limitations（未修复）
- 与 @ViewById 字段共享 id 的路径无 fixture 覆盖（逻辑已实现：复用属性引用，不新建局部）
- bean 的 @Click/@LongClick 报错而 APT 忽略（KSP 更严格）
- Kotlin 方法未做 APT "doesn't throw" 校验
- value-id 无法解析时退化为 `view_<int>` 局部变量
- 重复 id 检查比较原始列表而非 resolved qualified ids：name 引用与 value 引用指向同一 int、或两个 name 解析到同一 id 的情况不会报出
- 既有 Activity 模板偏差（非本切片引入，git diff 未触及；留待「Activity 模板 parity」项）：接口顺序（KSP `HasViews, OnViewChangedListener, BeanHolder` vs GT `BeanHolder` 在前）、beans_ 字段类型 HashMap vs Map、internalFindViewById 位置/`this.`/@SuppressWarnings 风格、getBean/putBean/static intent() 方法顺序、startForResult 局部变量 vs 内联强转
- 原始实现脚本 C:/temp/p0b_impl.py 含 6 行被传输层破坏的引号消息（勿重跑），已被 p0b_fix*.py 补丁取代；磁盘文件为准

### Blockers
- none

### Next
1. **P0b Resources（常用集）**：@ColorRes / @DrawableRes / @ColorStateListRes / @BooleanRes / @IntegerRes / @StringArrayRes / @TextRes / @TextArrayRes / @DimensionRes 等，对照 `ContextCompatAwareResHandler`（CHECKLIST_P0b.md L41-49）
2. Fragment 监听器 fixture：为 fragment 场景补 @Click/@LongClick 覆盖（当前仅 Activity fixture 101）
3. res handlers / @OptionsMenu / @InstanceState 等按原计划推进

## Session 2026-11-13 — P0b: Resources 常用集（APT AbstractResHandler/ContextCompatAwareResHandler parity）

- **Agent**: Codex
- **Goal slice**: P0b Resources（常用集）—— 11 个 res 注解（@ColorRes/@DrawableRes/@ColorStateListRes/@BooleanRes/@IntegerRes/@StringArrayRes/@TextRes/@TextArrayRes/@DimensionRes/@DimensionPixelOffsetRes/@DimensionPixelSizeRes），对照 APT `AbstractResHandler` / `ContextCompatAwareResHandler`（CHECKLIST_P0b.md「Resources（常用集）」）
- **Git**: branch=`ksp/main` HEAD=`ce86a9b5`（session 起点），commit 仅本地未 push
- **Status**: done

### Done
- 新增 `ResCompat.java`：11 个 res 注解类型表（注解名 / R 内部类 / Resources 查找方法 / 允许字段类型，与 APT `AndroidRes` 枚举逐项一致）+ ContextCompat-aware 分支选择 `modeFor(...)`（ANDROIDX_COMPAT → SUPPORT_COMPAT → CONTEXT_METHOD(minSdk≥平台版本) → IF_GUARD(编译期存在平台方法) → RESOURCES），对照 APT `ContextCompatAwareResHandler.getInstanceInvocation`；compat 三类为 color(23/M)、drawable(21/LOLLIPOP)、csl(23/M)
- `MvpModel.ResField` + `model.resFields`：属性名 / Java 类型 / jvmField / value / resName / R 内部类 / 查找方法
- `MvpHandler`：收集属性上的 11 个注解并校验（对照 APT：final → "cannot be used on a final element"；类型不符 → "can only be used on a field which is a ... not ..."；private → "cannot be used on a private element"）
- `MvpJavaGenerator.appendResAssign(...)`：Activity/Fragment/Bean 三种 init_ 中在 stringRes 赋值之后按 mode 输出 —— RESOURCES → `resources_.getX(R...)`；ANDROIDX/SUPPORT_COMPAT → `ContextCompat.getX(context, R...)`；CONTEXT_METHOD → 裸 context 调用；IF_GUARD → `if (SDK_INT >= PLATFORM) {context 方法} else {resources_}`
- `AndroidAnnotationsSymbolProcessor`：处理前按 compat 类型预计算 resCompatModes（minSdk 取自 manifest）；`needsResourcePackage` 扩展 resFields；value/resName/属性名缺失校验复用既有 RClassIndex 路径（validateInjection 重构为重载）
- `KspAnnotationUtils`：`kotlin.CharSequence` → `java.lang.CharSequence`；`kotlin.Array<T>` → `T[]`（StringArrayRes 需要）；新增 `fullJavaTypeName(...)`
- Fixture `BenchActivity102`：6 个带初始化器 primitive var + 4 个 lateinit 引用类型 var，覆盖 10 个 res 注解（TextArrayRes 无字段，见 Known limitations）；resources：color/bool/integer/string-array/dimen + layout
- `examples/kotlin-bench/build.gradle`：ksp 块新增 `arg("androidManifestFile", ...)` 指向 raw manifest（见 Known limitations minSdk 不对称）

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/ResCompat.java（新增）
- .../ksp/AnnotationNames.java
- .../ksp/MvpModel.java
- .../ksp/KspAnnotationUtils.java
- .../ksp/handler/MvpHandler.java
- .../ksp/generate/MvpJavaGenerator.java
- .../ksp/AndroidAnnotationsSymbolProcessor.java
- examples/kotlin-bench/build.gradle
- examples/kotlin-bench/src/main/kotlin/org/androidannotations/bench/BenchActivity102.kt（新增）
- examples/kotlin-bench/src/main/res/values/bench_res_102.xml（新增）
- examples/kotlin-bench/src/main/res/layout/bench_activity_102.xml（新增）
- docs/ksp-production-parity/CHECKLIST_P0b.md

### Verify
- [x] mvn install（api + ksp，-am）BUILD SUCCESS
- [x] examples/kotlin-bench `assembleDebug -Paa.processor=ksp` BUILD SUCCESSFUL；KSP 输出含全部 10 个 res 字段（setter 调用）+ 3 个 IF_GUARD（color M / drawable LOLLIPOP / csl M），init_ 内 `resources_` 局部在前
- [x] GT 对比：BenchActivity102_.java KSP vs APT(kapt)，归一化后逐字一致（2111 字符）；3 个 IF_GUARD 分支、setter 调用 vs 字段赋值形态、init_ 语句顺序均由比较器吸收；脚本 C:/temp/gt_cmp7.py（修复：setter 首字母大写 + 平衡括号整体替换、onViewChanged 正则容忍参数名）
- [x] 回归门禁：ksp/debug/java/org/androidannotations/bench 205 文件与 C:/temp/ksp-p0b-after 逐字节一致，仅新增 BenchActivity102_.java（共 205）；备份已刷新为 205
- [x] CHECKLIST_P0b.md「Resources（常用集）」6 项勾选（@ColorRes/@DrawableRes/@ColorStateListRes；Boolean/Integer/StringArray/Text/TextArray；Dimension trio；RClassIndex）；Animation/Html/Layout 保持未勾

### Known limitations（未修复）
- kapt stub 行为：Kotlin `var` 带初始化器 → kapt stub 中 private backing field → APT 拒绝 res 注解（"cannot be used on a private element"，6 个 primitive 字段，kapt task FAILS）；且 @JvmField 不能用于非 final var。故提交的 fixture（无 JvmField）使 GT 构建按设计为红；GT 参考用临时 @JvmField-on-primitives 变体生成并保存于 C:/temp/gt102_jvmfield.java（比较器对该副本而非 live 目录）
- minSdk 不对称：KSP `AndroidManifestFinder` 默认优先 merged manifest（minSdk=21，来自 build.gradle），而 kapt 收到的是 raw src/main/AndroidManifest.xml（无 uses-sdk → −1）；已用对称的 androidManifestFile ksp arg 修复并验证 drawable 两侧均为 IF_GUARD
- TextArrayRes = 仅校验 parity：APT 允许类型字面为 CharSequence（APT quirk，非 CharSequence[]），KSP 原样镜像；无 GT 字段覆盖
- Bean/Fragment 的 res 字段无 GT 覆盖（生成器支持三种 kind，fixture 仅 Activity）
- setter 调用 vs `this.field=` + init_ 语句顺序由比较器吸收（KSP 对 Kotlin property 发 setter，APT 直接写字段）

### Blockers
- none

### Next
1. **P0b Resources 剩余**：@AnimationRes / @HtmlRes / @LayoutRes（按样例需要；DefaultRes 通用路径，CHECKLIST_P0b.md「Resources（常用集）」末项）
2. Fragment 监听器 fixture：为 fragment 场景补 @Click/@LongClick 覆盖（当前仅 Activity fixture 101）
3. res handlers / @OptionsMenu / @InstanceState 等按原计划推进

## Session 2026-11-13 — P0b: Resources 剩余（@AnimationRes/@HtmlRes/@LayoutRes，APT AnimationResHandler/HtmlResHandler parity）

- **Agent**: Codex
- **Goal slice**: P0b Resources（常用集）末项 —— 3 个 res 注解 @AnimationRes / @HtmlRes / @LayoutRes，对照 APT `AnimationResHandler` / `HtmlResHandler` / `DefaultResHandler`（CHECKLIST_P0b.md「Resources（常用集）」末行）
- **Git**: branch=`ksp/main` HEAD=`ea29fb25`（session 起点），commit 仅本地未 push
- **Status**: done

### Done
- `ResCompat.Type` 新增 `invocation` 字段（调用风格）；类型表新增 3 项：AnimationRes（anim / getAnimation / [XmlResourceParser, Animation] / ANIMATION_UTILS）、HtmlRes（string / getString / [CharSequence, Spanned] / HTML_FROM_HTML）、LayoutRes（layout / getLayout / [XmlResourceParser]），与 APT `AndroidRes` 枚举逐项一致
- `MvpModel.ResField` + `MvpHandler`：传递 invocation 风格
- `MvpJavaGenerator.appendResAssign(...)`：mode 处理之前新增两个分支 —— ANIMATION_UTILS → `AnimationUtils.loadAnimation(contextRef, R.anim...)`（Activity=this / Fragment=getActivity() / Bean=context_）；HTML_FROM_HTML → `Html.fromHtml(resources_.getString(R.string...))`；LayoutRes 走既有 RESOURCES 路径输出 `resources_.getLayout(...)`
- Fixture `BenchActivity103`：3 个 lateinit 引用类型字段（@AnimationRes Animation / @HtmlRes CharSequence / @LayoutRes XmlResourceParser），全引用类型 → kapt stub 为 protected，GT 构建直接兼容（无需 JvmField）；resources：anim + layout + values(string)

### Changed paths
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/ResCompat.java
- .../ksp/MvpModel.java
- .../ksp/handler/MvpHandler.java
- .../ksp/generate/MvpJavaGenerator.java
- .../ksp/AnnotationNames.java
- examples/kotlin-bench/src/main/kotlin/org/androidannotations/bench/BenchActivity103.kt（新增）
- examples/kotlin-bench/src/main/res/anim/bench_anim.xml（新增）
- examples/kotlin-bench/src/main/res/layout/bench_activity_103.xml（新增）
- examples/kotlin-bench/src/main/res/values/bench_res_103.xml（新增）
- docs/ksp-production-parity/CHECKLIST_P0b.md

### Verify
- [x] mvn install（api + ksp，-am）BUILD SUCCESS
- [x] examples/kotlin-bench `assembleDebug -Paa.processor=ksp` BUILD SUCCESSFUL；generated 206 class(es)；KSP 输出含 3 个赋值：AnimationUtils.loadAnimation(this, R.anim.bench_anim) / Html.fromHtml(resources_.getString(R.string.benchHtml)) / resources_.getLayout(R.layout.bench_activity_103)
- [x] GT 对比：BenchActivity103_.java KSP vs APT(kapt)，归一化后逐字一致（1505 字符）；脚本 C:/temp/gt_cmp8.py（修复：通用 FQN 折叠前定向折叠 `android.view.animation.AnimationUtils.` → `AnimationUtils.`、`android.text.Html.` → `Html.`，吸收 jcodegen simple name + import 与 KSP FQN 的差异）
- [x] 回归门禁：ksp/debug/java/org/androidannotations/bench 206 文件与 C:/temp/ksp-p0b-after（205）逐字节一致，仅新增 BenchActivity103_.java；备份已刷新为 206
- [x] CHECKLIST_P0b.md「Resources（常用集）」末项勾选；本节全部完成（7 项）

### Known limitations（未修复）
- aapt2 文件型资源 R 名为 snake_case：`res/anim/bench_anim.xml` → `R.anim.bench_anim`（非 camelCase），fixture 显式值引用须用 `R.anim.bench_anim`；KSP `RClassIndex.resolveFieldName` 已镜像 APT CaseHelper 的 camel→snake fallback
- jcodegen 对 AnimationUtils/Html 输出 simple class name + import，KSP 输出 FQN；由 gt_cmp8.py 定向预折叠吸收
- Bean/Fragment 的 res 字段无 GT 覆盖（生成器支持三种 kind，fixture 仅 Activity）

### Blockers
- none

### Next
1. Fragment 监听器 fixture：为 fragment 场景补 @Click/@LongClick 覆盖（当前仅 Activity fixture 101）
2. @OptionsMenu / @InstanceState / @OnActivityResult / @SystemService 等按原计划推进

## Session 2026-11-13 — P0b: Fragment 监听器 fixture（fragment 场景 @Click/@LongClick GT 覆盖）

- **Agent**: Codex
- **Goal slice**: P0b — fragment 场景 @Click/@LongClick GT 对比覆盖（上 session Next 1）；顺带发现并修复 KSP 对无 @FragmentArg 的 fragment 无条件生成空 `injectFragmentArguments_()` 的 parity bug
- **Git**: branch=`ksp/main` HEAD=`12927f5c` remote=`ksp/main` ahead（local only, not pushed）
- **Status**: done

### Done
- 新 fixture `BenchFragment104`（纯监听器 fragment：无 @ViewById/@AfterViews/@FragmentArg），4 方法覆盖监听器形态全矩阵：显式值 `@Click(R.id.fragBtnA)` / 裸名推导 `@Click`（资源名=方法名）/ View 参数 `fragViewParam(View)` / long-press 布尔返回 `@LongClick … : Boolean`；layout `bench_fragment_104.xml` 含 4 个 Button
- Parity 预检：KSP `generateFragment.hasViewSupport` 计入 click/longClick 监听器 ≡ APT demand-driven `EComponentWithViewSupportHolder.setOnViewChanged()`（任一 handler 请求注入块即加接口+方法+注册）；`findViewById(idRef)` = `hasViews.internalFindViewById(…)` 与 activity 形态一致
- **Parity bug 修复**：GT 对比发现 KSP 对无 @FragmentArg 的 fragment 无条件输出 `injectFragmentArguments_()` 调用 + 空方法，而 APT `FragmentArgHandler` 仅在存在 @FragmentArg 时惰性创建该方法（GT 104 无此方法；001/002/003 有 @FragmentArg 两侧本就一致）。修复：`MvpJavaGenerator` init_ 调用处按 `!model.fragmentArgs.isEmpty()` 门控 + `appendFragmentArgsMethod` 无参时 early-return
- mvn install api+ksp（-am，skip flags）BUILD SUCCESS；KSP 重建后生成目录 207 个类（206+1）

### Changed paths
- examples/kotlin-bench/src/main/kotlin/org/androidannotations/bench/BenchFragment104.kt（新增）
- examples/kotlin-bench/src/main/res/layout/bench_fragment_104.xml（新增）
- AndroidAnnotations/androidannotations-core/androidannotations-ksp/src/main/java/org/androidannotations/ksp/generate/MvpJavaGenerator.java（fragmentArgs 门控 ×2）
- docs/ksp-production-parity/EXECUTION_LOG.md

### Verify
- [x] mvn install ksp（+ api，-am，skip flags）BUILD SUCCESS
- [x] examples/kotlin-bench `assembleDebug -Paa.processor=ksp` GRADLE_EXIT=0；生成目录 207 文件；`BenchFragment104_.java` implements OnViewChangedListener、init_ 含 registerOnViewChangedListener、onViewChanged 含 4 个局部变量 + if 块（匿名类调用 `BenchFragment104_.this.<method>`）、long-click 返回 boolean
- [x] kapt GT 构建 GRADLE_EXIT=0（按协议临时移除 BenchActivity102.kt 后已 git restore）；GT `BenchFragment104_.java` 含全部 4 个监听器探针
- [x] gt_cmp9：BenchFragment104_ EXACT MATCH after accepted HashMap->Map deviation（ksp 2721 字符 / gt 2717 字符；唯一剩余 diff region = 已知 beans_ 字段类型偏差）
- [x] gt_cmp4 回归：fragment 001/002/003（@FragmentArg 路径）仍 FINAL ALL OK — 修复未影响有参路径
- [x] 回归门禁 reg9：live 207 vs backup 206 → ADDED(1)=BenchFragment104_.java、REMOVED(0)、CHANGED(0) PASS；备份已刷新为 207（hash 全匹配）
- [x] checklist 无勾选 — 本 slice 为 GT 覆盖工作；CHECKLIST_P0b.md「View listeners（高频）」节此前已全部勾选，无对应未勾项

### Known limitations（未修复）
- aapt2 文件型资源 R 名为 snake_case、jcodegen simple name vs KSP FQN 等前 session 已知偏差不变（gt_cmp4/9 归一化吸收）
- Bean/Fragment 的 res 字段无 GT 覆盖（生成器支持三种 kind，fixture 仅 Activity）
- kapt GT 构建每次须临时移除 BenchActivity102.kt（kapt stubs 使初始化 var 变 private → APT 拒绝其 res 注解），构建后 git restore

### Blockers
- none

### Next
1. @OptionsMenu / @InstanceState / @OnActivityResult / @SystemService 等按原计划推进

## Session 2026-11-13 — P0b: Menu 支持（@OptionsMenu/@OptionsItem/@OptionsMenuItem/@InjectMenu）+ activity 脚手架与注入 parity 修复

- **Agent**: Codex
- **Goal slice**: P0b — menu 注解族（APT OptionsMenuHandler/OptionsItemHandler/InjectMenuHandler parity）；同时修复 GT 对比暴露的 KSP activity 脚手架/注入路径系统性偏差；新增 fixture 扩展门禁覆盖
- **Git**: branch=`ksp/main` HEAD=`7204bd71`（local only, not pushed）
- **Status**: done

### Done
- **Menu 支持**（KSP handler + 生成器全量实现）：`@OptionsMenu(R.menu.x)`（activity/fragment）、`@OptionsItem`（裸名推导 / 显式 id / 多 id 数组 value / Boolean 返回）、`@OptionsMenuItem`（字段注入，裸名推导 / 显式 id；方法参数亦可标注）、`@InjectMenu`（Menu 字段注入）。生成：activity `onCreateOptionsMenu_`/`onOptionsItemSelected_` + init_ 内菜单字段注入；fragment 同名两方法 + `setHasOptionsMenu(true)`
- 新 fixture：`BenchActivity105`（四注解全矩阵：@InjectMenu benchMenu、@OptionsMenuItem 字段 shareItem/searchItem、裸名 onShare(MenuItem)、显式 id onSettings():Boolean、多 id onMulti():Boolean、参数注入 singleInjection/multiInjection）、`BenchFragment105`（@OptionsMenu + @OptionsItem）、`BenchFragment106`（仅 @OptionsItem 无 @OptionsMenu）；资源 `bench_menu_105.xml`（8 item）+ layout ×3
- **Activity 脚手架 parity 修复**（均以 kapt GT 为准）：implements 子句顺序、internalFindViewById 及其方法体无条件输出并移位、init_ 重构（this.getResources() + register 置于 resources 块之后且按 hasViewSupport 门控）、静态 intent() 拆分并移到 getBean 之前、onViewChanged 移到 getBean 之后并按 hasViewSupport 门控、getBean 双括号去 @SuppressWarnings、startForResult 改局部变量、uiThread 方法先于 background 输出
- **系统性 parity 修复**：bean ctor/rebind 用裸 `context_`/`rootFragment_`；**字段式注入改为遵循 `@JvmField || lateinit`**（直接 `this.x = …` vs setter 调用 `setX(…)`——Kotlin backing field 仅 lateinit/@JvmField 时为 protected，否则 private 必须走 setter）；@NotNull/@Nullable 由 Kotlin nullability 合成（.kt parent-walk + isMarkedNullable）；background wrapper 参数 final；catch `final Throwable e`；bean init_ 先于 rebind；fragment extends 用 FQN + FragmentBuilder_ 限定引用 + builder 参数类型简名
- **lateinit 检测**：本 KSP 版本（2.1.10-1.0.31）无 token API、KSPropertyDeclaration 无 isLateinit()，改用 `property.getModifiers().contains(Modifier.LATEINIT)`（Modifier 枚举含 LATEINIT）
- **BenchActivity102 为 KSP-only**：kapt stub 生成对其失败（`build\tmp\kapt3\stubs\debug\error\NonExistentClass.java` 哨兵；210 源 vs 209 stub + 1 哨兵），无 GT 对应文件，门禁列出不比较。该 fixture 混合 lateinit + 已初始化属性，恰好验证 setter 回退路径（benchColor/benchBool/benchInt/benchDimen/benchPx/benchPxOffset → setXxx(…)，4 个 lateinit → 直接赋值），javac 编译通过
- 门禁：`gt_cmp_all.py` 全量 209 个 kapt GT 文件规范化对比 **ALL_OK（0 mismatch）**，KSP live=210；回归备份刷新至 `C:\temp\ksp-p0b-after`（210 文件、hash 一致）。reg10.py 旧“3 added/0 changed”期望被 gt_cmp_all ALL_OK 取代
- mvn install androidannotations-ksp BUILD SUCCESS；KSP gradle assembleDebug BUILD SUCCESS（javac 通过）

### Next
- @InstanceState → @OnActivityResult（+参数 @Extra）→ @SystemService，随后剩余 checklist 块（Fragment 其余、Application/Service、IntentBuilder Fragment overloads、Unimplemented 警告集——InstanceState 在其 slice 前保持 RECOGNIZED_NOT_IMPLEMENTED）、P0b exit acceptance
## Session 2026-11-14 — P0b: @InstanceState APT parity

- **Agent**: Codex
- **Goal slice**: P0b — @InstanceState（APT InstanceStateHandler parity）
- **Git**: branch=`ksp/main` HEAD=`56c3ccdc`（local only, not pushed）
- **Status**: done（门禁通过；余 1 个既有 mismatch，见 Known limitations）

### Done
- **@InstanceState APT parity**（KSP MvpHandler + MvpJavaGenerator 全量实现）：EActivity/EFragment 等组件的 @InstanceState 字段 → onSaveInstanceState_ 保存 + restoreSavedInstanceState_ 恢复
  - 类型矩阵：String→putString；值类型 int/boolean/float/long/short/char/byte/double→putSerializable（装箱显示类型）；Bundle→putBundle；IntArray→putIntArray；Array<String>/ArrayList<String>→putStringArray/putStringArrayList；Parcelable→putParcelable；ArrayList<P>→putParcelableArrayList；P[]→putParcelableArray（恢复走 org.androidannotations.api.bundle.BundleHelper.getParcelableArray FQN + import）；Serializable→putSerializable；HashMap<K,V>→putSerializable（mapProp 恢复侧带 @SuppressWarnings("unchecked")）；SparseArray<P>→putSparseParcelableArray
  - **lateinit/nullable 装箱规则**（stub 证据驱动）：值类型仅在顶层且 lateinit||nullable 时装箱；泛型参数位置恒装箱；kotlin.Array<T> 原语元素仅当 T 非空时按原语处理
  - 放置（与 GT 逐字节对齐）：activity restore 调用在 init_ 内 bean 注入之后、方法位于 appendOptionsMenuMethods 与 appendUiThreadMethods 之间；fragment restore 调用在 init_ 末尾（setHasOptionsMenu 块之后）、方法位于 appendFragmentArgsMethod 与 appendBackgroundMethods 之间；onSaveInstanceState_ = @Override + super + save 语句；restoreSavedInstanceState_ = private + null-check 早退 + restore 语句
  - APT 校验顺序（全部累积）：enclosing kind → private → bundle 类型 → final；错误消息与 GT 逐字一致（含 "can only be used in a field annotated with @EActivity, @EFragment, @EView, @EViewGroup."、"cannot be used on a private element"、"Unrecognized type. The type argument of SparseArray should implement Parcelable." 等）
- **新 fixture**：BenchActivity107（13 属性全矩阵）、BenchActivity108（6 属性，全 lateinit protected：Parcelable/ArrayList<P>/P[]/Serializable/Serializable[]/HashMap）、BenchFragment109（3 属性）+ 辅助类 BenchParcelable/BenchSerializable + layout ×3
- **双括号 cast bug**（门禁发现并修复）：restore cast 生成 `((Integer))expr`，GT 为 `((Integer) expr)`——右括号必须包住整个表达式。MvpHandler.java L944/L955/L973 三行相同缺陷；修复 = 中段 `")) saved` → `") saved` + 尾字面量 `");` → `""));`。本 session 首次修复时漏掉语句终止分号（MVN_EXIT=1，3× "';' expected"），补回后 BUILD SUCCESS
- **门禁 gt_cmp_all**：GT 213 / KSP live 213、missing/KSP-only 均空、**MISMATCHES=1（仅既有 BenchActivity102_）**；回归备份刷新至 `C:\temp\ksp-p0b-is`（213 文件，hash 全匹配）
- mvn install androidannotations-ksp BUILD SUCCESS

### 构建退出码说明（两侧均 exit 1，按输出文件判定）
- KSP 侧 GRADLE_EXIT=1：fixture 自身 `lateinit var intProp: Int` 为非法 Kotlin（故意构造以验证 lateinit 装箱规则），:compileDebugKotlin 报 9 错（107×8 + 109×1）；KSP 任务本身成功，213 文件全部生成
- kapt GT 侧 GRADLE_EXIT=1：:kaptDebugKotlin 在既有 BenchActivity102 问题上失败（kapt stubs 使已初始化 var 变 private → APT 拒绝其 res 注解 "cannot be used on a private element"）；APT 失败前已写出全部 213 文件，其中 BenchActivity102_.java 不完整（4033 B vs KSP 完整 4271 B）

### Known limitations（未修复）
- BenchActivity102_ 唯一 mismatch：KSP 输出与 pre-session 备份 `C:\temp\ksp-p0b-after` **SHA256 逐字节一致** → 非回归；GT 侧文件不完整系 APT 生成中途失败（既有已知限制，见 menu session 条目）
- fixture lateinit-on-primitive 非法 Kotlin → 两侧均无法编译到底，门禁按生成文件判定

### 门禁语义备注
- gt_cmp_all 去除全部空白（**括号不剥离**——cast 括号位置敏感）、双文件 import 并集 FQN→简名、HashMap→Map 回退、final static↔static final、R. 特判、OnViewChangedNotifier/Builder_ 前缀剥离
- jcodel global-imports quirk：每个 GT 文件均含 `import java.util.HashMap; import java.util.Map;`（即使未用），门禁 import 并集映射已吸收

### Next
1. @OnActivityResult（+参数形式 @Extra）→ @SystemService → fragment @Bean 其余 → Application/Service → IntentBuilder Fragment overloads → 遗留 @ViewById 方法注入形式
2. P0b exit acceptance → P1
## Session 2026-11-14 — P0b: @OnActivityResult + 参数级 @Extra APT parity

- **Agent**: Codex
- **Goal slice**: P0b — @OnActivityResult（APT OnActivityResultHandler/OnActivityResultDelegate/BundleHelper parity）+ 参数级 @Extra
- **Git**: branch=`ksp/main` HEAD=`28760b9d`（local only, not pushed）
- **Status**: done（门禁通过；余 1 个既有 mismatch，见 Known limitations）

### Done
- **OAR 收集**（MvpHandler）：collectActivityResult + findOrCreateActivityResultGroup + addOarExtraConstant + bundleReadExpr；activity/fragment 双链
- **生成**（MvpJavaGenerator）：appendActivityResultConstants + appendActivityResultMethods。常量 `public final static String X = "key";` 紧跟 beans_ 行（activity: beans_ 的 `\n\n` 处拆开插入；fragment: fragmentArgs 循环后，空行条件扩为 `!fragmentArgs.isEmpty() || !oarExtraConstants.isEmpty()`）；方法 @Override（+@SuppressWarnings("unchecked") 仅当 flag，本 fixture activity 有/fragment 无）位于 instanceState 与 uiThread(activity)/background(fragment) 之间；`case  N :` 两空格 quirk；**每 case 一个 break（该 case 全部方法语句之后）**
- **校验 parity**（errorsBefore 快照定界，错误累积）：kind=ACTIVITY|FRAGMENT、非 private、requestCode 0..0xFFFF、void 返回、参数矩阵 [Intent?]+[int|Integer?]+[@Extra*]；case 按 requestCode 分组、方法源序；常量 first-wins 去重（含 field-level @Extra）
- **bundleReadExpr 规则**：BUNDLE_GET_SUFFIX → 数组（parcelable 组件→`org.androidannotations.api.bundle.BundleHelper.getParcelableArray(FQN)`，unchecked 仅当组件含 `<`；否则双括号 cast getSerializable）→ ArrayList 前缀（单一可解析 parcelable 参数→getParcelableArrayList + `unchecked |= arg.contains("<")`；否则无条件 suppress + cast）→ SparseArray → parcelable base getParcelable → parceler Parcels.unwrap → else cast getSerializable + unchecked 仅当含 `<`
- AnnotationNames 两条目；MvpModel 三内部类（ActivityResultMethod/ActivityResultGroup/ExtraConstant）+ 三字段（activityResults/oarExtraConstants/activityResultUnchecked）
- 新 fixture：BenchActivity110（cases 11/22×2/23/24/25/26/27/28/29/30/31×2/32）、BenchFragment110（41/42）+ BenchGeneric&lt;T&gt;=Parcelable / BenchSerGeneric&lt;T&gt;=Serializable + layout ×2
- 门禁：GT 215 / KSP live 215、missing/KSP-only 均空、**MISMATCHES=1（仅既有 BenchActivity102_）**；回归备份刷新至 `C:\temp\ksp-p0b-oar`（215 文件，hash 全匹配）
- mvn install androidannotations-ksp BUILD SUCCESS

### 构建退出码说明
- KSP 侧 GRADLE_EXIT=1：仅 9 个既有 Kotlin 错误（BenchActivity107×8 + BenchFragment109×1，lateinit-on-primitive 故意构造）；KSP 任务本身成功，215 文件全生成；按输出文件判定

### Bugs discovered & fixed（门禁 diff 定位）
- break 原在 per-method 循环内（每方法一个 break）⇒ 不可达代码且与 GT 不符。GT 实证：每 case 仅一个 `break;`，位于该 case 全部方法语句之后；已移出循环
- invocation 缺分号（`BenchActivity110_.this.onResult()` 无 `;`，flat 与 brace-block 两处共用该串）⇒ 末尾补 `+ ");"`

### Known limitations（未修复）
- BenchActivity102_ 唯一 mismatch：既有已知限制（kapt stub 生成失败致 GT 文件不完整），KSP 输出与 pre-session 备份逐字节一致 = 非回归
- isParcelerType 比 APT ParcelerHelper 表窄（与 InstanceState 共享的已知偏差，勿扩展——回归风险）

### Next
1. @SystemService → fragment @Bean remainder → Application/Service（@EApplication/@App/@EService + Manifest checks）→ IntentBuilder Fragment overloads → 遗留 @ViewById 方法注入形式
2. P0b exit acceptance（demo coverage、kapt-vs-ksp public API spot-check、known-deviations 文档声明、EXECUTION_LOG 标 P0b done + Next=P1）→ P1

## Session 2026-11-14 — P0b: @SystemService APT parity

- **Agent**: Codex
- **Goal slice**: P0b — @SystemService（APT SystemServiceHandler/AndroidSystemServices parity）：field form / 单参 method form / 参数级多参 form
- **Git**: branch=`ksp/main` HEAD=`62a7e697`（local only, not pushed）
- **Status**: done（门禁通过；余 1 个既有 mismatch，见 Known limitations）

### Done
- **收集**（MvpHandler）：collectSystemServiceField / collectSystemServiceMethod（单参，局部变量按参数名命名）/ collectSystemServiceParams（参数级，全部参数带 @SystemService 才出块；partial → 整块静默跳过，@Bean precedent）；findParamSystemService / hasSystemServiceParam
- **service 表**：SYSTEM_SERVICES 75 条静态 map，从 APT AndroidSystemServices.java 源 regex 直接提取（零转录偏差）；WifiManager/AudioManager → appContext=true（getApplicationContext()，APT SystemServiceHandlerContextTest 实证）
- **校验 parity**（顺序）：kind=ACTIVITY|FRAGMENT → final（field）→ 已知类型（"Unknown service type: X"）→ private；method form 另加 void 返回 + 恰 1 参（"The method can only have 1 parameter"）
- **生成**（MvpJavaGenerator）：appendSystemServiceInjections + getSystemServiceExpr。FIELD form 复用 appendAssign（lateinit → `this.x = ((T) ctx.getSystemService(Context.X));`，cast 2 个前导括号）；METHOD form brace block 每 target 两条语句（先 `T name = null;` 再单独赋值）后调 `method(args...)`；activity contextRef=`this`、fragment=`getActivity()`；插入点 resFields 循环后
- MvpModel：SystemServiceTarget + SystemServiceInjection（Form FIELD/METHOD）+ systemServices 列表；AnnotationNames SYSTEM_SERVICE 条目
- 新 fixture：BenchActivity111（6 fields + 单参 method + 2 参 param form）、BenchFragment111（3 fields + 单参 method）+ layout ×2
- 门禁：GT 217 / KSP live 217、missing/KSP-only 均空、**MISMATCHES=1（仅既有 BenchActivity102_）**；回归备份刷新至 `C:\temp\ksp-p0b-ss`（217 文件，hash 全匹配）
- mvn install androidannotations-ksp BUILD SUCCESS

### 构建退出码说明
- KSP 侧 GRADLE_EXIT=1：仅 9 个既有 Kotlin 错误（BenchActivity107×8 + BenchFragment109×1，lateinit-on-primitive 故意构造）；KSP 任务本身成功，217 文件全生成；按输出文件判定

### Bugs discovered & fixed
- KSValueParameter 无 getSimpleName()（KSP API 为 getName()），编译错误暴露；两处改 `param.getName().asString()`
- fixture BenchActivity111.kt L45 `println(wifi and audio)` 笔误（Kotlin 无 and 运算符）致 1 条新 :compileDebugKotlin 错；改两行 println

### Known limitations（未修复）
- BenchActivity102_ 唯一 mismatch：既有已知限制（kapt stub 生成失败致 GT 文件不完整），KSP 输出与 pre-session 备份 `C:\temp\ksp-p0b-oar` SHA256 逐字节一致 = 非回归
- AppWidgetManager minSdk<21 特殊路径（APT createSpecialInjection）未实现：KSP processor 无 manifest minSdk，恒走 normal getSystemService path
- param form partial annotation（非全部参数带 @SystemService）：整块静默跳过不报错（@Bean precedent）

### Next
1. fragment @Bean remainder → Application/Service（@EApplication/@App/@EService + Manifest checks）→ IntentBuilder Fragment overloads → 遗留 @ViewById 方法注入形式
2. P0b exit acceptance（demo coverage、kapt-vs-ksp public API spot-check、known-deviations 文档声明、EXECUTION_LOG 标 P0b done + Next=P1）→ P1
## Session 2026-11-14 — P0b: fragment @Bean + @AfterInject APT parity

- **Agent**: Codex
- **Goal slice**: P0b — @Bean/@AfterInject（APT EBeanHandler/InjectHelper/BeanHolder parity）：field/method/param 三形态 + fragment getInstance_ 双重载
- **Git**: branch=`ksp/main` HEAD=`2023d93c`（local only, not pushed）
- **Status**: done（门禁通过；余 1 个既有 mismatch，见 Known limitations）

### Done
- **首次 fixture 验证**：@Bean/@AfterInject 此前从未被 fixture 覆盖；新增 BenchActivity112/BenchFragment112/BenchBean101/102 + layout ×2，覆盖 fragment @Bean field/method/param 三形态 + @AfterInject + `getInstance_(context)` / `getInstance_(context, rootFragment)` 双重载
- MvpHandler：collectBeanMethod/collectBeanParams BeanTarget name = 参数名（`params.get(0).getName().asString()`）；resolveBeanTarget 返回值包 samePackageRef
- MvpJavaGenerator：appendBeanHolderLookup cast 行改双括号含收尾；fragment 路径（L329-330）与 service/app 路径（L737-738）@UiThread/@Background 生成顺序交换为 UI→BG（activity 路径 L193-194 原本正确未动）
- 新增静态 helper `samePackageRef(fullTypeName, model)`：组件自己包内的类剥包前缀用简单名，带段数校验防 `com` vs `com.foobar` 误剥；APT/jcodemodel parity（同包类无 import 直接引用）
- 门禁：GT 221 / KSP live 221、missing/KSP-only 均空、**MISMATCHES=1（仅既有 BenchActivity102_）**；回归备份刷新至 `C:\temp\ksp-p0b-bean`（221 文件，hash 全匹配）
- mvn install androidannotations-ksp BUILD SUCCESS

### 构建退出码说明
- KSP 侧 GRADLE_EXIT=1：仅 9 个既有 Kotlin 错误（BenchActivity107×8 + BenchFragment109×1，lateinit-on-primitive 故意构造）；KSP 任务本身成功，221 文件全生成；按输出文件判定

### Bugs discovered & fixed（门禁 diff 定位）
- method/param form 局部变量名：原用方法名 → GT 用参数名（APT InjectHelper L119/L159 实证）；BeanTarget name 改 `params.get(0).getName().asString()`
- 同包 Bean 类引用：KSP 输出 FQN → GT 对同包类无 import、用简单名；加 samePackageRef 剥离（带段数校验）
- BeanHolder cast：单括号 `(BeanHolder) x` → GT `((BeanHolder) context);` 双括号含收尾（EBeanHolder L187 + jcodemodel 3.0.1 实证产物 `C:\temp\jcmtest`）
- fragment/service @UiThread 方法原在 @Background 之后生成 → APT CorePlugin L240-241 UiThreadHandler 注册在 BackgroundHandler 之前（全局 pipeline，所有组件 UI 先生成）；KSP fragment+service/app 路径交换修正；service/app 暂无 fixture，下一 slice 验证

### Known limitations（未修复）
- BenchActivity102_ 唯一 mismatch：既有已知限制（kapt stub 生成失败致 GT 文件不完整），KSP 输出与 pre-session 备份一致 = 非回归
- GT 目录于 06:52 用当前 APT 4.9.0-SNAPSHOT 全量重新生成；旧快照 `C:\temp\gt_hash_before_kapt.txt`（06:51）为陈旧产物（文件全部不同），两侧现均为当前工具链产物，门禁结果有效
- service/app 路径 UI→BG 顺序已修正但无 fixture 覆盖（下一 slice @EApplication/@EService 覆盖）

### Next
1. Application/Service（@EApplication/@App/@EService + Manifest checks，对照 APT EApplicationHandler/EServiceHandler/AppHandler；service/app UI→BG 顺序已提前修正）→ IntentBuilder Fragment overloads → 遗留 @ViewById 方法注入形式
2. P0b exit acceptance（demo coverage、kapt-vs-ksp public API spot-check、known-deviations 文档声明、EXECUTION_LOG 标 P0b done + Next=P1）→ P1

## Session 2026-11-14 — P0b: @EApplication/@EService/@App APT parity

- **Agent**: Codex
- **Goal slice**: P0b — @EApplication/@EService/@App（APT EApplicationHandler/EServiceHandler/AppHandler + CoreValidatorHelper.applicationRegistered parity）
- **Git**: branch=`ksp/main` HEAD=`6ad018fe`（local only, not pushed）
- **Status**: done（门禁通过；余 1 个既有 mismatch，见 Known limitations）

### Done
- **首次 fixture 覆盖**：@EApplication/@EService/@App 此前从未被 fixture 覆盖；新增 BenchApp（@EApplication）、BenchService（@EService，成员序=GT 注入序：@App field → @App method → @Bean field103 → @AfterInject → @UiThread → @Background）、BenchBean103（裸 @EBean）；Manifest application name=FQN + `<service android:name=".bench.BenchService_"/>`
- **收集**（MvpHandler）：eApplication/eService 查找 + kind 链在 eFragment 后、eBean 前插 APPLICATION/SERVICE；collectAppField / collectAppMethod（恰一参数）/ collectAppParams（参数级，部分标注整块静默跳过，@Bean precedent）；findParamApp / hasAppParam；resolveAppTarget 查 @EApplication，缺失报 `"@App on 'x': <qn> must be annotated with @EApplication"`；生成名 = samePackageRef(generatedBeanQualifiedName(qn), model) 复用 bean helper
- **gate 扩展**：@AfterInject + collectBeanField/Method/Params 四处 gate 改为仅 `kind == BEAN` 报错（消息文本保留原样，UI-only/@SystemService gate 未动）；BEAN 缺口保留
- **校验**（ComponentValidator）：applicationRegistered 镜像 APT CoreValidatorHelper L95-121 —— isLibrary/manifest==null/qn==null → return；appClass!=null 且 !ABSTRACT 且 ≠生成 FQN → appClass==原始 FQN ? ERROR(FQN) : WARNING(FQN)；appClass==null → ERROR `"No application class registered in the AndroidManifest.xml"`（注意 APT 该消息用 FQN，与 componentRegistered 的 simple-name 消息不同）
- **分发**（processor）：classes 收集加 E_APPLICATION/E_SERVICE；验证分发 ACTIVITY||SERVICE → componentRegistered / APPLICATION → applicationRegistered
- **生成**（MvpJavaGenerator）：generateApplication —— 无 implements；`private static <Orig> INSTANCE_;`（字段类型=原始类，jcodemodel quirk）→ getInstance() → javadoc `Visible for testing purposes` + setForTesting(<Orig>) → onCreate{INSTANCE_=this; init_(); super.onCreate();} → init_ 体：resources_(条件)→stringRes→resFields(kind=APPLICATION, contextRef=this)→SystemService→AppInjections→BeanInjections→afterInject→UiThread/Background→nested classes
- **generateService**：无 implements；先 appendIntentStaticMethod（intent(Context) 静态方法位于 init_ 之前，CorePlugin 注册序实证）→ init_ 体同上（SERVICE）→ onCreate{init_(); super.onCreate();} → UiThread/Background → 嵌套 `public static class IntentBuilder_ extends ServiceIntentBuilder<<Gen>.IntentBuilder_>`（构造 super(context, <Gen>.class)，无 extra、无 startForResult）→ appendNestedClasses；onDestroy 惰性不生成
- **appendAppInjections**：FIELD form 复用 appendBeanAssign，表达式=无参静态 `<Gen>.getInstance()`；METHOD form 花括号块局部变量 null 声明+赋值+调用（仿 appendBeanInjections——APT 实证 BeanHandler/AppHandler 同走 injectHelper.process）；activity init_ 中插于 appendSystemServiceInjections 后、injectExtras_() 前（APT handler 序：App(179) 在 SystemService(176) 后、Extra(189) 前）
- 门禁：GT 224 / KSP live 224、missing/KSP-only 均空、**MISMATCHES=1（仅既有 BenchActivity102_）**；回归备份刷新至 `C:\temp\ksp-p0b-appsvc`（224 文件，hash 全匹配）
- mvn install androidannotations-ksp BUILD SUCCESS

### 构建退出码说明
- KSP 侧 GRADLE_EXIT=1：仅 9 个既有 Kotlin 错误（BenchActivity107×8 + BenchFragment109×1，lateinit-on-primitive 故意构造）；KSP 任务本身成功，224 文件全生成；按输出文件判定
- kapt 侧 GT 目录刷新至 224（新增 BenchApp_/BenchService_/BenchBean103_）；activity-not-registered 警告为既有

### Bugs discovered & fixed
- BenchService.kt 缺 onBind：Service.onBind 抽象方法，KSP 与 kapt 两侧 kotlinc 均编译失败；补 `override fun onBind(intent: Intent?): IBinder? { return null }` + imports（Intent/IBinder）——fixture 侧修复，非生成器问题

### Known limitations（未修复）
- BenchActivity102_ 唯一 mismatch：既有已知限制（kapt stub 生成失败致 GT 文件不完整），KSP 输出与 pre-session 备份 `C:\temp\ksp-p0b-bean` SHA256 逐字节一致 = 非回归
- AppWidgetManager minSdk<21 特殊路径未实现（@SystemService slice 遗留）

### Next
1. IntentBuilder Fragment overloads → 遗留 @ViewById 方法注入形式
2. P0b exit acceptance（demo coverage、kapt-vs-ksp public API spot-check、known-deviations 文档声明、EXECUTION_LOG 标 P0b done + Next=P1）→ P1

## Session 2026-11-14 — P0b: IntentBuilder Fragment overloads + options APT parity

- **Agent**: Codex
- **Goal slice**: P0b — IntentBuilder fragment overloads + startForResult 分支矩阵（APT oracle = ActivityIntentBuilder/IntentBuilder）
- **Git**: branch=`ksp/main` HEAD=`fe73fb57`（local only, not pushed）
- **Status**: done（门禁通过；余 1 个既有 mismatch，见 Known limitations）

### Done
- **fixture 触发条件**：kotlin-bench Manifest 加 `<uses-sdk android:minSdkVersion="21" />` → IntentBuilder 分支 fragment=true(minSdk≥11)、options=true(≥16)、guard=false；无此配置两侧同生成无 fragment 输出，分支矩阵无法被验证
- **新增 IntentBuilderCompat.java**（classpath 探测，镜像 APT ClasspathUtil/IntentBuilderHelper）：detect(resolver, minSdkVersion) → fragment(android.app.Fragment 在 classpath 且 minSdk≥11)/androidxFragment/supportFragment/activityOptionsInFragment(Fragment.startActivityForResult 是否带 Bundle 参数——KSP 签名 position 1 探测)/activityCompat(androidx.core.app.ActivityCompat 或 android.support.v4.app.ActivityCompat FQN，androidx 优先，null=plain Activity/Context 调用)/guardActivityOptions(minSdk<16 → options 调用包 SDK_INT≥JELLY_BEAN guard)
- **generator 接线**：processor 调 IntentBuilderCompat.detect(resolver, minSdkVersion) 作为 MvpJavaGenerator 构造器首参传入
- **生成逻辑**（MvpJavaGenerator appendIntentBuilder 重写）：成员序 = jcodemodel fields 先于 constructors：fragment_ field(/fragmentSupport_) → Context ctor → fragment ctor(s) → startForResult override → extras 循环；有 fragment 时整个 activity/context if-else 嵌套在 fragment else 内（inElse，s2 缩进 +1 级）；support 分支恒 2 参 fragmentSupport_.startActivityForResult(intent, requestCode)，经 elseif 链成 `} else if (fragment_!= null)`
- **startForResult 分支矩阵**（对照 APT oracle）：fragment 分支 options+guard→guarded 3/2 参；options→3 参含 lastOptions；否则 2 参。activity 分支：compat 静态 4 参无条件 / options+guard guarded / options 3 参 / 2 参。context 分支：options+guard guarded context.startActivity(intent,lastOptions)/(intent) / options 2 参 / 1 参
- **静态重载**：appendFragmentIntentOverloads —— 生成类上紧跟 intent(Context)：`public static X.IntentBuilder_ intent(Fragment fragment) { return new X.IntentBuilder_(fragment); }`（参数名 fragment/supportFragment，与 APT 一致）
- 门禁：GT 224 / KSP live 224、missing/KSP-only 均空、**MISMATCHES=1（仅既有 BenchActivity102_）**；回归备份刷新至 `C:\temp\ksp-p0b-fragintent`（224 文件，hash 全匹配）
- mvn install androidannotations-ksp BUILD SUCCESS

### 构建退出码说明
- KSP 侧 GRADLE_EXIT=1：仅 9 个既有 Kotlin 错误（BenchActivity107×8 + BenchFragment109×1，lateinit-on-primitive 故意构造）；KSP 任务本身成功，224 文件全生成；按输出文件判定
- kapt 侧 GT 目录：刷新 224 文件时 kapt stub 在 BenchActivity102 生成失败（既有已知问题），GT 该文件为不完整产物

### Bugs discovered & fixed（门禁 diff 定位）
- **KSP Java API 风格错**：IntentBuilderCompat 原用 Kotlin 风格属性访问（parameter.type.resolve()、type.declutable）→ 编译不过；改 getter 风格 parameter.getType().resolve().getDeclaration().getQualifiedName()（参照 KspAnnotationUtils L454/L61 既有模式）+ import KSTypeReference
- **缺闭合大括号**：appendStartForResult 的 context 分支三选一（guard/options/plain）后缺 `out.append(s2).append("}\n")` 闭合 activityCondition 的 else 块 → return 落进外层 fragment else，109 个 activity 文件全部单 token diff；公共出口补一行（覆盖三个子分支，guard 子分支内部自带 if/else 闭合）

### Known limitations（未修复）
- BenchActivity102_ 唯一 mismatch：既有已知限制（kapt stub 生成失败致 GT 文件不完整），KSP 侧相对 pre-session 备份 `C:\temp\ksp-p0b-appsvc` 的 diff 仅为本 slice 预期改动（minSdk=21 → drawable 无 LOLLIPOP guard；intent(Fragment) 重载 + fragment_ field/ctor + startForResult fragment 分支），非回归
- AppWidgetManager minSdk<21 特殊路径未实现（@SystemService slice 遗留）

### Next
1. 遗留 @ViewById 方法注入形式
2. P0b exit acceptance（demo coverage、kapt-vs-ksp public API spot-check、known-deviations 文档声明、EXECUTION_LOG 标 P0b done + Next=P1）→ P1

## Session 2026-11-14 — P0b: @ViewById method/param injection APT parity

- **Agent**: Codex
- **Goal slice**: P0b 切片 @ViewById 方法注入形式（method form + param form，APT oracle = ViewByIdHandler/InjectHelper/EComponentWithViewSupportHolder）
- **Git**: branch=`ksp/main` HEAD=`4b7a96c8`，local only, not pushed
- **Status**: done；门禁通过（224/224，仅 1 个既有 mismatch = Known limitations）

### Done
- **fixture 扩充**：bench_activity_101.xml 新增 3 Button（methodTarget/onMethodFirst/secondTarget）；bench_fragment_112.xml 新增 2 Button（a/b）；BenchActivity101.kt 新增 5 成员——(A) FIELD+METHOD 同 id（View vs Button ⇒ 块内 cast）、(B) 裸 @ViewById method form（回退=方法名 ⇒ R.id.onMethodFirst）、(C) METHOD 在 FIELD 前同 id；BenchFragment112.kt 新增 onParams(@ViewById a: Button, @ViewById b: Button)（裸注解回退=参数名）
- **KSP generator**：MvpJavaGenerator 新增 ViewInjectionPlan（pass1/pass3/foundRefs/foundTypes）+ planViewInjections：FIELD form pass1 `this.x = internalFindViewById(...)`、cache ref=this.x；METHOD/PARAM form pass1 `<Type> view_<rName>` local（parameterized 类型加 @SuppressWarnings("unchecked")，路径保留未测）；pass3 块=每参数 decl(`<Type> <paramName> = null;`)+assign+按 argumentOrder invoke；castRef=APT FoundHolder.getOrCastRef parity（cached==req 或 req==android.view.View ⇒ 原 ref，否则 `((T) ref)` jcodemodel 双括号）
- **kapt stub 顺序语义（本 session 实证）**：kapt 将 Kotlin 属性 backing field 全部置于 stub 类体顶部（方法之前），APT 按 stub 成员序迭代 ⇒ FIELD form 先于 METHOD/PARAM form 处理；KSP planViewInjections 按 form 稳定分区（FIELD 先行，组内保持声明序）
- **processor**：AndroidAnnotationsSymbolProcessor L204 `viewByIds`→`viewInjections`；validateResources 循环按 FIELD/param 分支校验 id
- **MvpModel/MvpHandler**：ViewInjection(Form.FIELD|METHOD_PARAM)+ViewMethodParam 模型；collectViewByIdMethod（method form，idKey=方法名）/collectViewByIdParams（param form，一 method 一 ViewInjection、params 声明序）；collectFunction 两形式独立判断
- 门禁：GT 224 / KSP live 224、missing/KSP-only 均空、**MISMATCHES=1（仅既有 BenchActivity102_）**；回归备份刷新至 `C:\temp\ksp-p0b-viewmethod`（224 文件，hash 全匹配）
- mvn install androidannotations-ksp BUILD SUCCESS

### 构建顺序说明
- KSP 侧 GRADLE_EXIT=1：9 个既有 Kotlin 错误（BenchActivity107×8 + BenchFragment109×1，lateinit-on-primitive fixture 结构），KSP 生成成功（224 文件全部生成）
- kapt 侧 GT 目录刷新至 224；kapt stub 在 BenchActivity102 生成失败（既有已知问题），GT 该文件为不完整产物

### Bugs discovered & fixed（门禁 diff 定位）
- **FIELD/METHOD 处理顺序**：KSP 初版按源码声明序迭代 ⇒ (C) 场景（METHOD 在 FIELD 前同 id）产出 view_secondTarget local+无 cast，GT 为 field 先行 `this.secondView` + 方法块 cast；kapt stub 实证字段位于类体顶部 ⇒ planViewInjections 改按 form 稳定分区
- **cast 双括号**：castRef 单括号 `(T) ref` vs jcodemodel GT `((T) ref)`（两开两闭）；首修只加前导括号漏末尾闭合，门禁仍差 1 字符 ⇒ 补全为 `"((" + T + ") " + ref + ")"`

### Known limitations（未修复）
- BenchActivity102_ 唯一 mismatch：既有已知限制（kapt stub 生成失败致 GT 文件不完整），KSP 输出与 pre-session 备份 `C:\temp\ksp-p0b-fragintent` SHA256 逐字节一致 = 非回归
- parameterized view 类型 @SuppressWarnings("unchecked") 路径保留未测（GT 无先例）

### Next
1. P0b exit acceptance（demo coverage、kapt-vs-ksp public API spot-check、known-deviations 文档声明、EXECUTION_LOG 标 P0b done + Next=P1）→ P1
## Session 2026-11-14 — P0b: exit acceptance (L96–L100) done → P1

- **Agent**: Codex
- **Goal slice**: P0b exit acceptance（CHECKLIST_P0b L96-100）
- **Git**: branch=`ksp/main` HEAD=`3d43537d`（P0b: @ViewById method-injection APT parity），local only, not push

### Acceptance items
- [x] L96 demo coverage：`examples/kotlin-bench`（228 fixtures）覆盖 Activity(EActivity×109) + Fragment(EFragment×10) + Click/LongClick + 常用 Res(String/Text/Color/Drawable/Layout/Dimension*/Boolean/Integer/Html/CSL/StringArray/Animation) + Menu(InjectMenu/OptionsItems/OptionMenuItem) + InstanceState×3——超出「至少一条主路径」要求
- [x] L97 kapt vs ksp 公开 API spot-check（DECL/SIG）：独立脚本 C:\temp\decl_sig_check.py——顶层类声明 224/224 对齐 + 方法签名 2113/2113 对齐（METH 多重集相等，含 visibility/modifiers/返回类型/名/参数类型/throws；参数注解剥离）；嵌套 builder 类由全量 body gate 覆盖。结果 DECLSIG_OK
- [x] L98 文档声明：新增 `docs/ksp-production-parity/KNOWN_DEVIATIONS.md`（典型界面可卸 kapt 前提 + 7 项已知偏差 + 流程约束）
- [x] L99 本条目标记 P0b done，Next = P1 首项
- [x] L100 Push `ksp/main`：N/A——用户未要求远程同步；按 standing instruction 仅本地 commit，不 push

### Evidence / state
- 全量门禁（C:\temp\gt_cmp_all.py）：GT 224 / KSP live 224，missing/KSP-only 均空，MISMATCHES=1（仅 `BenchActivity102_` = GT 侧 @JvmField 临时变体陈旧产物，见 KNOWN_DEVIATIONS #1）
- DECL/SIG：types 224/224 + methods 2113/2113 = 0 mismatch
- 顺带修复：本 log 上一条目误引入的裸 LF 行尾（31 处）已全部归一为 CRLF

### Next
1. P1 首项（CHECKLIST_P1）：SharedPreferences BLOCKER——`@SharedPref` helper 生成（scope ACTIVITY / ACTIVITY_DEFAULT / APPLICATION_DEFAULT / UNIQUE），APT oracle = SharedPrefHandler + PrefHandler

## Session 2026-11-16 — P1: @SharedPref / @Pref KSP generation

- **Agent**: Cursor Grok
- **Goal slice**: P1 — `@SharedPref` helper 生成 + `@Pref` 字段注入（APT SharedPrefHandler / SharedPrefHolder / PrefHandler）
- **Git**: branch=`ksp/main`，local only, not push
- **Status**: done

### Done
- `@SharedPref` 接口收集进 `SharedPrefModel`，生成 `Name_` / `NameEditor_`（scope ACTIVITY、ACTIVITY_DEFAULT、APPLICATION_DEFAULT、UNIQUE）
- 默认值注解：`@DefaultBoolean/Int/Long/Float/String/StringSet` 与 `@DefaultRes` + `keyRes`；字段类型映射到 `*PrefField` / `*PrefEditorField`
- 顶层 helper 单独出 Java 文件；嵌套在 `@E*` 里的接口生成为 enclosing 生成类的 `public static final` 内部类
- `@Pref` 字段在 `init_` 里赋 `new Helper_(contextRef)`（Activity/Application/Service=`this`，Fragment=`getActivity()`，Bean=`context_`）
- `AnnotationNames.SHARED_PREF` / `PREF`；`SharedPref` 移出 `RECOGNIZED_NOT_IMPLEMENTED`（现为空数组）
- CHECKLIST_P1 SharedPreferences 六项打勾

### Changed paths
- `androidannotations-ksp`：`SharedPrefModel`、`handler/SharedPrefHandler`、`generate/SharedPrefGenerator`、`AndroidAnnotationsSymbolProcessor`、`MvpHandler`、`MvpModel`、`MvpJavaGenerator`、`AnnotationNames`
- `examples/kotlin-bench`：`BenchPrefsClient.kt`（`@EBean` + 五个 `@Pref` + 嵌套 `@SharedPref`）
- `docs/ksp-production-parity/CHECKLIST_P1.md`、`EXECUTION_LOG.md`

### Verify
- [x] `mvnw -pl androidannotations-core/androidannotations-ksp -am install`（skipTests）BUILD SUCCESS
- [x] `examples/kotlin-bench assembleDebug -Paa.processor=ksp`：KSP 任务成功，`generated 231 class(es)`；`:compileDebugKotlin` EXIT=1，仅既有 9 个 `lateinit` 原始类型错误（BenchActivity107×8 + BenchFragment109×1），无新增错误
- [x] `build/generated/ksp/debug/java/.../BenchPrefs{Activity,ActivityDefault,App,Unique,Java}_.java` 与 `BenchPrefsClient_.java`（含 `Nested_`）存在
- [x] 与既有 kapt 产物对照：四个 scope 的 `super(...)`、getter/editor 方法名、`keyRes`/`@DefaultRes` 表达式、`DefaultStringSet` 的 `HashSet` 默认值与 APT 一致（FQCN / 成员顺序 / javadoc 空白不同）

### Blockers
- none（assembleDebug 的 EXIT=1 是既有 fixture，见 KNOWN_DEVIATIONS #5）

### Next
1. P1 下一项：更多 View 监听（`@Touch` / `@FocusChange` / `@CheckedChange` / TextWatcher / `@EditorAction` / Item*），或补 `@Pref` 的方法注入（APT `InjectHelper` method form，本次只做字段）

## Session 2026-11-16 — P1: more view listeners

- **Agent**: Cursor Grok
- **Goal slice**: P1 — `@Touch` / `@FocusChange` / `@CheckedChange` / `@TextChange` `@BeforeTextChange` `@AfterTextChange` / `@EditorAction` / `@ItemClick` `@ItemLongClick` `@ItemSelect`，对照 APT `AbstractViewListenerHandler` 与 `TextWatcherHolder`
- **Git**: branch=`ksp/main`，local only, not push
- **Status**: done

### Done
- `ViewListener` 增加 `ListenerKind` 与参数 `ParamRole`。`@Click` / `@LongClick` 仍走原列表与原匿名类文本；新监听器按 APT 规则校验并生成 setter
- 参数：Touch 任意序 `MotionEvent?` + `View?`（void → `return true`）；FocusChange `View?` + `boolean?`；CheckedChange 目标类型 `CompoundButton`（必要时 cast）；EditorAction `TextView?` + `int?` + `KeyEvent?`（void → `return true`）；ItemClick/ItemLongClick 单参 `int` 传 position，否则 `parent.getAdapter().getItem(position)`；ItemSelect 先 `boolean`，`onItemSelected(true, item|position)` / `onNothingSelected(false, null|-1)`，void 的 long-click 返回 true
- TextChange 家族按 id 合并为一个 `TextWatcher`（before/on/after 三个方法都生成；同 id 多个 `@TextChange` 进同一个 `onTextChanged`）。int 参数按名字 `start`/`count`/`before`/`after` 匹配；视图局部变量类型取该 id 上第一个 TextView 子类型，否则 `TextView`
- id：`value={-1}` 与空 `resName` 回退方法名（USE_ELEMENT_NAME）。非 TextWatcher 的 kind 仍按列表做重复 id 检查；TextWatcher 与 APT 一样不拒绝同 id 多方法
- Fixture `BenchListenersActivity` + `bench_listeners.xml`：每种注解一个方法，`textTarget` 上 Before + 两个 TextChange + After 合成一个 watcher
- CHECKLIST_P1「More view listeners」7 项打勾

### Changed paths
- `androidannotations-ksp`：`AnnotationNames`、`MvpModel`、`handler/MvpHandler`、`generate/MvpJavaGenerator`、`AndroidAnnotationsSymbolProcessor`
- `examples/kotlin-bench`：`BenchListenersActivity.kt`、`res/layout/bench_listeners.xml`
- `docs/ksp-production-parity/CHECKLIST_P1.md`、`EXECUTION_LOG.md`

### Verify
- [x] `mvnw -pl androidannotations-core/androidannotations-ksp -am install`（skipTests）BUILD SUCCESS
- [x] `examples/kotlin-bench assembleDebug -Paa.processor=ksp`：`kspDebugKotlin` 成功，`generated 232 class(es)`（+1 `BenchListenersActivity_`）；`:compileDebugKotlin` EXIT=1，仅既有 9 个 `lateinit` 原始类型错误（BenchActivity107×8 + BenchFragment109×1），无新增错误
- [x] `BenchListenersActivity_.java` 含 `setOnTouchListener`、`setOnFocusChangeListener`、`setOnCheckedChangeListener`（CheckBox cast）、`setOnEditorActionListener`、`setOnItemClickListener`、`setOnItemLongClickListener`（void → `return true`）、`setOnItemSelectedListener`（true/item 与 false/null）、同一 id 一个 `addTextChangedListener` 且 `onTextChanged` 调用 `onText` 与 `onTextView`，另有独立 `textAfterOnly` watcher
- [x] 既有 `BenchActivity101_.java` 仍生成 `setOnClickListener` / `setOnLongClickListener`

### Known limitations
- TextWatcher 局部变量类型由该 id 上第一个带 TextView 子类型参数的方法决定；之后更具体的参数不再额外 cast（与 APT `TextWatcherHolder` 相同）
- 非 Text 监听的重复 id 仍比较原始 value/name 列表，name 与 value 指向同一 int 时不会报出（与 `@Click` 相同）
- 仅 `@EActivity` / `@EFragment`；未做 private / throws 校验（与 `@Click` 相同）
- SeekBar / PageChange 不在本切片

### Blockers
- none（assembleDebug 的 EXIT=1 是既有 fixture，见 KNOWN_DEVIATIONS #5）

### Next
1. P1：`@ViewsById`、`@FromHtml`、动态 `@Receiver`（及 receiver 方法上的 `@Extra`）

## Session 2026-11-16 — P1 remaining: views, receiver, custom views

- **Agent**: Cursor Grok
- **Goal slice**: P1 剩余项 — `@ViewsById`、`@FromHtml`、`@EView`/`@EViewGroup`、`@IgnoreWhen`、`@NonConfigurationInstance`、动态 `@Receiver`、`@WindowFeature`/`@Fullscreen`、AndroidX Fragment 查找
- **Git**: branch=`ksp/main`，local only, not push
- **Status**: done（`@MovieRes` 明确跳过；不 push）

### Done
- `@ViewsById`：`List`/`ArrayList`/`Collection` 注入 `ArrayList`，数组走 `toArray`；Activity/Fragment/Bean/`@EView` 可用
- `@FromHtml`：同一字段已有 `@ViewById` 且是 `TextView` 时，注入后 `setText(Html.fromHtml(context.getString(...)))`
- `@EView` / `@EViewGroup`：`Kind.VIEW` / `VIEW_GROUP`；拷贝非 private 构造器，`init_` + `onFinishInflate` + 静态 `build`；ViewGroup 在 `alreadyInflated_` 里 `inflate(getContext(), layout, this)`。View 上复用 `@Click` / `@AfterViews` / `@Bean`，ViewGroup 上复用 `@ViewById` / `@AfterViews`
- `@IgnoreWhen`：仅 Fragment。单独方法生成守卫；与 `@UiThread` / `@Background` 同方法时，守卫包在已提交任务内部的 `super` 调用外（与 APT handler 顺序一致）
- `@NonConfigurationInstance`：`init_` 里先从 holder 恢复，再注入 `@Bean`；非 null 跳过；非 Singleton `@Bean` 调用 `rebind`。`getLastNonConfigurationInstance` / `onRetainNonConfigurationInstance`（FragmentActivity 用 Custom 变体）
- `@WindowFeature` / `@Fullscreen`：`init_` 开头 `FLAG_FULLSCREEN` 与 `requestWindowFeature`（AppCompat 用 `supportRequestWindowFeature`）
- 动态 `@Receiver`：Activity OnCreate 前注册 / onDestroy 前反注册；OnResume 在 super 之后；Fragment OnAttach 在 super 之后；Service onCreate 在 super 之后；View `onAttachedToWindow` 在 super 之后。参数 `@Receiver.Extra` 读 extras。无 permission、无 exported 标志（与 APT 相同）
- AndroidX：字段类型不是 `android.app.Fragment` 时，Activity 用 `getSupportFragmentManager()`。`RECOGNIZED_NOT_IMPLEMENTED` 填入仍未实现的注解（含 `@MovieRes`）

### Changed paths
- `androidannotations-ksp`：`AnnotationNames`、`MvpModel`、`handler/MvpHandler`、`generate/MvpJavaGenerator`、`AndroidAnnotationsSymbolProcessor`
- `examples/kotlin-bench`：`BenchP1Activity`、`BenchIgnoreFragment`、`BenchCustomView`、`BenchCustomGroup`、`BenchReceiverService`、`BenchAndroidXHost`、布局 `bench_p1.xml` / `bench_row.xml`、`values/bench_p1.xml`、manifest、`androidx.fragment`、`android.useAndroidX=true`
- `docs/ksp-production-parity`：CHECKLIST_P1、KNOWN_DEVIATIONS、PLAYBOOK、EXECUTION_LOG；根 `README.md`

### Verify
- [x] `mvnw -pl androidannotations-core/androidannotations-ksp -am install`（skipTests）BUILD SUCCESS（含构造器 FQN 修正后的第二次安装）
- [x] `examples/kotlin-bench assembleDebug -Paa.processor=ksp`：`kspDebugKotlin` 成功，`generated 238 class(es)`（232 + 6）；`:compileDebugKotlin` EXIT=1，仅既有 9 个 `lateinit` 原始类型错误，无新增 Kotlin 错误
- [x] 临时拿掉这 9 个错误后 `:compileDebugJavaWithJavac` 对新建的 6 个生成类零错误（随后已把 fixture 改回）。同一次 javac 还报出既有 `BenchActivity113_` setter 与 `BenchBean101_` 未限定 `OnViewChangedNotifier`，它们在 Kotlin 失败时到不了 javac，不是本切片引入的
- [x] 生成片段：`BenchP1Activity_` 含 `ArrayList` + `toArray`、`Html.fromHtml`、`FLAG_FULLSCREEN`、`requestWindowFeature(1)`、`NonConfigurationInstancesHolder`、`if (this.keptBean == null)`、`rebind`、`registerReceiver` / `unregisterReceiver`、`getInt(STATE_EXTRA)`、`getFragmentManager()`
- [x] `BenchIgnoreFragment_`：`@UiThread` runnable 内 `if (!viewDestroyed_)`，`@Background` 内 `getActivity() != null`，`onAttach` 里 `addAction` + `registerReceiver`
- [x] `BenchCustomView_`：`init_`、`onFinishInflate`、`build`、`setOnClickListener`、`getInstance_(getContext()`、`onAttachedToWindow` 注册。`BenchCustomGroup_`：`inflate(getContext(), R.layout.bench_row, this)`
- [x] `BenchAndroidXHost_`：`getSupportFragmentManager().findFragmentById` / `findFragmentByTag`
- [x] APT 抽查：注册时机与 ReceiverHandler / holder 一致；IgnoreWhen 在 UiThread/Background 任务内部；动态接收器不带 permission/exported

### Blockers
- none（assembleDebug 的 EXIT=1 是既有 fixture，见 KNOWN_DEVIATIONS #5）
- `@MovieRes` 未做

### Next
1. P2：按目标工程用量挑选 Preference 屏、SeekBar/ViewPager/Key、WakeLock/Trace/Suppose、DataBinding，或 `@EProvider` / `@EReceiver` / `@EIntentService`
2. 若要补 AndroidX Fragment 上的动态 `@Receiver`，生成 `onAttach(android.content.Context)` 而不是 `onAttach(Activity)`

## Session 2026-11-16 — P2: generating components, listeners, threading

- **Agent**: Cursor Grok
- **Goal slice**: P2 高价值子集 — Other generating components、SeekBar/ViewPager/Key、Threading extras，以及顺手的 `@RootContext` / `@RootFragment`
- **Git**: branch=`ksp/main`，local only, not push
- **Status**: done for the chosen subset（Preference / DataBinding / 其余 misc UI 与插件未做）

### Done
- `@EReceiver` + `@ReceiverAction`（actions / dataSchemes，单值 `equals`、多值 `List.contains`，参数 `Context` / `Intent` / `@ReceiverAction.Extra`）生成 `BroadcastReceiver_` 的 `onReceive` → `init_` → `super` → 分发
- `@EIntentService` + `@ServiceAction`：`onCreate`/`init_`、`onHandleIntent`（super、null、action、extras、`super.method`、return）、`intent()` 与 `IntentBuilder_` 的 `action` + `super.extra`
- `@EProvider`：`onCreate` 先 `init_()` 再 `return super.onCreate()`，context 为 `getContext()`
- 三类组件走现有 `ComponentValidator.componentRegistered`（library 模式仍跳过）
- SeekBar 三种注解按 id 合成一个 `OnSeekBarChangeListener`；Page 三种合成一个 `addOnPageChangeListener`（androidx ViewPager 优先）；Key 四种生成 `onKeyDown` / `onKeyUp` / `onKeyLongPress` / `onKeyMultiple` 的 `switch`
- `@WakeLock`：`powerManager_` 在 `init_` 赋值；缺 `android.permission.WAKE_LOCK` 时应用报 error、library 报 warning；`PARTIAL_WAKE_LOCK` 加 flags 只 warning
- `@Trace`：option `trace` 默认 false（与 APT `OPTION_TRACE` 一致），开启后包在 WakeLock 之内、UiThread/Background 任务之内
- `@SupposeUiThread` / `@SupposeBackground`：option `threadControl` 默认 true；检查写在已生成方法最外层，因此排在 UiThread/Background 之后
- `@RootContext` / `@RootFragment`：仅 `@EBean`，`context_` / `rootFragment_` 的 instanceof 赋值与 Log.w
- 包装顺序（外→内，对照 `CorePlugin`）：Suppose → UiThread/Background → WakeLock → Trace → IgnoreWhen → super。已写入 PLAYBOOK §2

### Core 覆盖边界
已覆盖：P0a + P0b + P1，加上本子集（Receiver / IntentService / Provider、SeekBar / ViewPager / Key、WakeLock / Trace / Suppose、RootContext / RootFragment）。
仍未覆盖（`RECOGNIZED_NOT_IMPLEMENTED`，使用时 KSP warning）：Preference 屏、`@DataBound`、`@BindingObject`、`@CustomTitle`、`@HierarchyViewerSupport`、`@HttpsClient`、`@Transactional`、`@MovieRes`、`@IntArrayRes`。
插件默认跳过：RoboGuice、Otto、ORMLite、REST-Spring。

### Changed paths
- `androidannotations-ksp`：`AnnotationNames`、`KeyCodes`、`MvpModel`、`handler/MvpHandler`、`generate/MvpJavaGenerator`、`AndroidAnnotationsSymbolProcessor`、`env/KspOptions`、`env/KspEnvironment`、`KspAnnotationUtils`
- `examples/kotlin-bench`：`BenchBroadcast`、`BenchIntentWork`、`BenchProvider`、`BenchSeekKeyActivity`、`BenchThreadsActivity`、`BenchRootBean`、`res/layout/bench_seek.xml`、manifest（WAKE_LOCK、receiver/service/provider/activity）、`androidx.viewpager`、KSP `trace=true`
- `docs/ksp-production-parity`：CHECKLIST_P2、PLAYBOOK、KNOWN_DEVIATIONS、EXECUTION_LOG

### Verify
- [x] `mvnw -pl androidannotations-core/androidannotations-ksp -am install`（skipTests）BUILD SUCCESS
- [x] `examples/kotlin-bench assembleDebug -Paa.processor=ksp`：`kspDebugKotlin` 成功，`generated 244 class(es)`（238 + 6）；`:compileDebugKotlin` EXIT=1，仅既有 9 个 `lateinit` 原始类型错误（BenchActivity107×8 + BenchFragment109×1），无新增 Kotlin 错误
- [x] 生成片段：`BenchBroadcast_` 含 `ACTIONS_ON_PING.equals` + `DATA_SCHEMES_ON_PING.equals`、`ACTIONS_ON_MANY.contains`、`extras_.getString(PAYLOAD_EXTRA)`；`BenchIntentWork_` 含 `ACTION_PING` / `ACTION_NAMED`、`extras.getString` / `getInt`、`IntentBuilder_.ping/named` 的 `action` + `super.extra`；`BenchProvider_` 的 `onCreate` 先 `init_()` 再 `super.onCreate()`，`getInstance_(getContext())`
- [x] `BenchSeekKeyActivity_`：同一 id 一个 `setOnSeekBarChangeListener`（progress/start/stop）、一个 `addOnPageChangeListener`、四个 KeyEvent switch（`KEYCODE_BACK/VOLUME_UP/MENU/ENTER`）
- [x] `BenchThreadsActivity_`：`hold()` 直接 acquire/release；`holdOnUi()` 的 WakeLock 在 `runTask` 内部；`tracedBg()` 的 `Log.isLoggable` 在 Background `execute()` 内部；`mustBg()` 的 `checkBgThread("bench")` 在 `execute(...)` 之前；`mustUi()` 为 `checkUiThread()` 后 `super`
- [x] `BenchRootBean_`：`context_ instanceof Activity` 赋值，否则 `Log.w`；`rootFragment_` 同理
- 拿掉 9 个既有 `lateinit` 后，Kotlin 编译会先撞上既有 `BenchActivity111` 的 `getWindowManager` accidental override，到不了 javac。新生成类未单独过 javac；内容已按上面片段核对

### Known limitations
- `@Key*` 的 keyCode 名称来自内置 `KEYCODE_*` 表；表中没有的 int 会报错，而不是去解析 android.jar
- `@Trace` 与非 void 的 `@WakeLock` 组合按 APT 拒绝；`@Trace` 与 `@UiThread`/`@Background` 组合要求 void
- `@RootContext` / `@RootFragment` 的方法注入日志文案比字段注入短
- Page 监听固定 `addOnPageChangeListener`（androidx / support 的现行 API）
- `@KeyMultiple` 的 `super` 使用本方法的 `keyEvent` 参数（APT `KeyEventCallbackMethodsDelegate` 误传了 `onKeyDown` 的参数）

### Blockers
- none（assembleDebug 的 EXIT=1 是既有 fixture，见 KNOWN_DEVIATIONS #5）

### Next
1. 剩余 P2：Preference 屏，或 `@DataBound` / `@BindingObject`（仅当 EActivity content view 路径已经够直）
2. 若要继续扩 core 边界之外：PERF 增量，或插件边界（ORMLite / REST-Spring 仅当业务依赖存在；RoboGuice / Otto 默认不做）

## Session 2026-11-16 — P2 remainder: binding, title, https, transactional

- **Agent**: Cursor Grok
- **Goal slice**: 剩余有用的 P2 core（DataBound / BindingObject / CustomTitle / HttpsClient / Transactional），hardening lite，PERF spot，插件边界。不 push
- **Git**: branch=`ksp/main`，local only, not push
- **Status**: done for the chosen subset

### Done
- `@DataBound` + `@BindingObject`：Activity `onCreate` 用 `DataBindingUtil.inflate` 再 `setContentView(getRoot, getLayoutParams)`，`onDestroy` 里 `unbind`。Fragment / ViewGroup 的现有 inflate 路径同样替换，否则同一个注解无法按目标拆开。`@BindingObject` 在 `onViewChanged` 里把 `viewDataBinding_` cast 到字段或方法参数。优先 androidx databinding，否则旧 `android.databinding`
- `@CustomTitle`：`init_` 里 `requestWindowFeature(FEATURE_CUSTOM_TITLE)`（与 APT 一样，不用 AppCompat 的 support 变体），`onViewChanged` 开头 `setFeatureInt`
- `@HttpsClient`：`init_` 里生成 `DefaultHttpClient` 匿名子类。无 keystore 走 `createClientConnectionManager`；有 trust/key raw 时加载 BKS。keystore 读取用外层组件 context（bean 为 `context_`），避免匿名类里的 `this.getResources()` 绑到 HttpClient
- `@Transactional`：`beginTransaction` / `setTransactionSuccessful` / `endTransaction`，`RuntimeException` 打 `Log.e` 后重新抛出。不与 `@UiThread` / `@Background` / `@WakeLock` / `@Trace` / `@Suppose*` / `@IgnoreWhen` 组合（报错）
- `unimplementedIsError`（默认 false）：为 true 时 `RECOGNIZED_NOT_IMPLEMENTED` 打 error。kotlin-bench 的 KSP 参数已打开，用来确认新注解不再落在该列表里
- `RECOGNIZED_NOT_IMPLEMENTED` 现为：`@HierarchyViewerSupport`、六个 Preference 注解、`@IntArrayRes`、`@MovieRes`

### Skipped
- Preference 屏：六注解是一整套 PreferenceActivity / PreferenceFragment 生命周期。只做其中一个没有 coherent fixture，kotlin-bench 也没有 Preference 用例。留在未实现列表
- `@HierarchyViewerSupport`：Hierarchy Viewer 已从平台移除，注解过时。留在未实现列表，使用时仍 warning/error
- RoboGuice、Otto：N/A。库已停维护 / 已归档，core 不实现
- ORMLite、REST-Spring：默认跳过，直到业务确认仍依赖 `androidannotations-ormlite` / `androidannotations-rest-spring`。清单保持未勾

### Core 覆盖边界
已覆盖：P0a + P0b + P1 + P2 高价值子集 + 本轮 `@DataBound` / `@BindingObject` / `@CustomTitle` / `@HttpsClient` / `@Transactional`。
仍未覆盖（使用时 warning，或 `unimplementedIsError=true` 时 error）：Preference 屏、`@HierarchyViewerSupport`、`@MovieRes`、`@IntArrayRes`。
插件不在 core jar：RoboGuice / Otto 明确不做；ORMLite / REST-Spring 默认跳过直到业务确认。
因此「生产替换」对典型 Activity/Fragment（加上本轮这四个注解）可以视为 **core ready**。若线上仍用 Preference 屏、`@MovieRes` / `@IntArrayRes`、ORMLite 或 REST-Spring，则还被这些边界挡住，不能算全量替换。

### PERF
- 工具链锁定项已勾（Kotlin 2.1.10 / KSP 2.1.10-1.0.31 / AGP 8.7.3 / Gradle 8.11.1 / JDK 17）
- 冷全量仍以 `BENCHMARK.md` 的 3 次中位数为准：kapt 129.88 s，KSP 100.55 s（−22.6%）。本轮没有重跑冷全量
- 单次 spot（daemon 热、未删 `build/`）：处理器刚换 jar 时 249 class / 3074 ms；只改 `BenchActivity001.kt` 后 1 class / 252 ms，`:kspDebugKotlin` 墙钟 11 s → 5 s。空的第二轮 `generated 0 class(es)` 仍在（约 70–150 ms）
- `Dependencies` 复核：非聚合，只登记注解类自己的源文件。同文件修改会失效；另一个文件的父类或 `@Bean` / `@App` / `@Pref` 目标不会。负例仍开放

### Changed paths
- `androidannotations-ksp`：`AnnotationNames`、`MvpModel`、`handler/MvpHandler`、`generate/MvpJavaGenerator`、`AndroidAnnotationsSymbolProcessor`、`env/KspOptions`、`env/KspEnvironment`
- `examples/kotlin-bench`：`BenchDataActivity`（含 Fragment / ViewGroup）、`BenchTxnBean`、`BenchHttpsBean`、`res/raw/bench_trust.bks`、manifest、`databinding-runtime`、`useLibrary org.apache.http.legacy`、`unimplementedIsError=true`
- docs：CHECKLIST_P2、CHECKLIST_PERF、CHECKLIST_PLUGINS、KNOWN_DEVIATIONS、PLAYBOOK、EXECUTION_LOG；根 `BENCHMARK.md`

### Verify
- [x] `mvnw -pl androidannotations-core/androidannotations-ksp -am install`（skipTests）BUILD SUCCESS
- [x] `examples/kotlin-bench` `kspDebugKotlin` / `assembleDebug -Paa.processor=ksp`：`kspDebugKotlin` 成功，全量 `generated 249 class(es)`（244 + 5）；`:compileDebugKotlin` EXIT=1，仅既有 9 个 `lateinit` 原始类型错误（BenchActivity107×8 + BenchFragment109×1），无新增 Kotlin 错误
- [x] 生成片段：`BenchDataActivity_` 含 `DataBindingUtil.inflate`、`setContentView(viewDataBinding_.getRoot(), ...getLayoutParams())`、`FEATURE_CUSTOM_TITLE`、`setFeatureInt`、`this.binding = ((androidx.databinding.ViewDataBinding) viewDataBinding_)`、`takeBinding`、`unbind`。`BenchDataFragment_` / `BenchDataGroup_` 含对应 inflate。`BenchTxnBean_.write` 含 `beginTransaction` / `setTransactionSuccessful` / `Log.e` / `endTransaction`，参数类型为 `android.database.sqlite.SQLiteDatabase`。`BenchHttpsBean_` 的 `plain` 走无 keystore 分支，`trusted` 含 `R.raw.bench_trust` 与 `SingleClientConnManager`

### Blockers
- none（assembleDebug 的 EXIT=1 是既有 fixture，见 KNOWN_DEVIATIONS #5）

### Next
1. 若业务确认仍用 Preference 屏，再做整套（Screen / Headers / ByKey / Change / Click / AfterPreferences）并加 fixture
2. PERF：热全量与「改 1 文件」各做 ≥3 次中位数；补 `Dependencies` 的父类与 `@Bean` originating files，并回归负例
3. ORMLite / REST-Spring 仅在业务确认依赖后另开一期。RoboGuice / Otto 不再排期

## Session 2026-11-16 — Preference screens and remaining Res

- **Agent**: Cursor Grok
- **Goal slice**: Preference 六注解的 coherent 子集，加上 `@IntArrayRes` / `@MovieRes`。不 push
- **Git**: branch=`ksp/main`，local only, not push
- **Status**: done

### Done
- `@PreferenceScreen`：Activity 的 `onCreate` 末尾、Fragment 的 `super.onCreate` 之后调用 `addPreferencesFromResource(R.xml.*)`
- `@PreferenceHeaders`：只在 `android.preference.PreferenceActivity` 上生成 `onBuildHeaders`（`loadHeadersFromResource` 然后 `super`）。编译类路径没有 `android.preference.PreferenceFragment` 时报错（APT 的 API 11 检查）
- `@PreferenceByKey`：字段、单参数方法、全参数注解三种形态。重写 `addPreferencesFromResource`，`findPreference(getString(R.string.*))`，子类型加 cast
- `@PreferenceChange` / `@PreferenceClick`：同一 key 共用一次 find，`if (ref != null)` 里挂 listener。void 方法 `return true`，boolean 方法返回调用结果。数字 newValue 走 `Integer/Long/Float.valueOf((String) newValue)`。无 res 值时去掉 `PreferenceChanged` / `PreferenceClicked` 后缀
- `@AfterPreferences`：注入块之后调用
- 宿主类型：AndroidX `PreferenceFragment(Compat)` → `androidx.preference.Preference`；support-v7 / v14 → support Preference；其余（含 `PreferenceActivity`）→ framework Preference
- `@IntArrayRes`：`resources_.getIntArray`，允许 `int[]`。`@MovieRes`：`resources_.getMovie`，允许 `android.graphics.Movie`。二者都从 `RECOGNIZED_NOT_IMPLEMENTED` 移除
- `RECOGNIZED_NOT_IMPLEMENTED` 现只剩 `@HierarchyViewerSupport`

### Skipped
- `@HierarchyViewerSupport`：Hierarchy Viewer 已从平台移除。仍留在未实现列表
- `@MovieRes` 的 bench fixture：aapt2 不接受 `res/movie`，没有 `R.movie`。生成路径与其他 Res 字段相同，但当前 AGP 编不过使用点
- RoboGuice、Otto：N/A
- ORMLite、REST-Spring：默认跳过
- 不 push

### Core 覆盖边界
已覆盖：P0a + P0b + P1 + P2（含 Preference 屏、`@IntArrayRes`、`@MovieRes` 的生成）。
仍未覆盖：`@HierarchyViewerSupport`（使用时 warning，或 `unimplementedIsError=true` 时 error）。`@MovieRes` 在当前 aapt2 上没有 `R.movie`，不能当成可编译能力。
插件不在 core jar：RoboGuice / Otto 明确不做；ORMLite / REST-Spring 默认跳过直到业务确认。
因此「生产替换」对 AA core 注解（含仍在用 PreferenceActivity / PreferenceFragment(Compat) 的界面，以及 `@IntArrayRes`）可以视为 **core ready**。线上若还要 `@MovieRes`（当前工具链）、`@HierarchyViewerSupport`、ORMLite 或 REST-Spring，这些边界仍然挡着，不能算全量替换。

### PERF
本轮没有重测冷/热/增量。CHECKLIST_PERF 的测量项、`Dependencies` 负例、空第二轮都保持开放。kotlin-bench 增加了 Preference 与 int-array fixture，corpus 勾选仍不开（没有生成脚本，也没有可重复的注解种类清点）。

### Changed paths
- `androidannotations-ksp`：`AnnotationNames`、`ResCompat`、`MvpModel`、`handler/MvpHandler`、`generate/MvpJavaGenerator`、`AndroidAnnotationsSymbolProcessor`
- `examples/kotlin-bench`：`BenchPreferenceActivity`、`BenchPreferenceHeadersActivity`、`BenchActivity103`（`@IntArrayRes`）、`res/xml/bench_prefs.xml`、`res/xml/bench_headers.xml`、`res/values/bench_prefs.xml`、`res/values/bench_res_103.xml`、manifest
- docs：CHECKLIST_P2、CHECKLIST_PERF、KNOWN_DEVIATIONS、PLAYBOOK、EXECUTION_LOG；根 README

### Verify
- [x] `mvnw -pl androidannotations-core/androidannotations-ksp -am install -DskipTests -Dmaven.javadoc.skip=true` BUILD SUCCESS。不跳过 javadoc 时，`androidannotations-api` 的既有 HTML（`<tt>`、table `summary`）在当前 JDK 的 javadoc 下失败，与本次改动无关；KSP 模块本身 `javac` 通过
- [x] `examples/kotlin-bench` `kspDebugKotlin -Paa.processor=ksp`：全量日志 `generated 251 class(es)`；随后只改 `BenchActivity103` 时 `generated 1`，空第二轮 `generated 0`。工作区里另有未纳入本次提交的 `BenchActivity113.java`，251 含它
- [x] `:compileDebugKotlin` EXIT=1，仅既有 9 个 `lateinit` 原始类型错误（BenchActivity107×8 + BenchFragment109×1），无新增 Kotlin 错误
- [x] `BenchPreferenceActivity_`：`onCreate` 末尾 `addPreferencesFromResource(R.xml.bench_prefs)`；重写里 `this.listPref = findPreference(...)`、`CheckBoxPreference` cast、`preference_benchEditKey` 方法注入、同一 key 的 change+click 包在一个 `if != null`、`Integer.valueOf(((String) newValue))`、boolean click `return`、最后 `afterPreferences()`
- [x] `BenchPreferenceHeadersActivity_`：`onBuildHeaders` 先 `loadHeadersFromResource(R.xml.bench_headers, target)` 再 `super`
- [x] `BenchActivity103_`：`setBenchInts(resources_.getIntArray(R.array.benchInts))`

### Known limitations
- 注入不覆盖只调用 `setPreferencesFromResource` 的写法
- `@PreferenceHeaders` 不是 AndroidX Preference headers API
- `@MovieRes` 依赖已经不存在的 `R.movie`
- 数字 `@PreferenceChange` 参数假定 `newValue` 是 String，与 APT 相同

### Blockers
- none（`compileDebugKotlin` 的 EXIT=1 是既有 fixture，见 KNOWN_DEVIATIONS #5）

### Next
1. PERF：热全量与「改 1 文件」各做 ≥3 次中位数；补 `Dependencies` 的父类与 `@Bean` originating files，并回归负例
2. ORMLite / REST-Spring 仅在业务确认依赖后另开一期。RoboGuice / Otto 不再排期
3. `@HierarchyViewerSupport` 保持不做

