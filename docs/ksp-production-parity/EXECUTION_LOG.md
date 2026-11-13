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
