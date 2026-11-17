# KNOWN DEVIATIONS — KSP vs APT(kapt)

> 适用范围：`examples/kotlin-bench` 全量比对门禁（GT = kapt live 输出，KSP = KSP live 输出）。
> 本文档为 P0b exit acceptance 的「文档声明」项；逐切片细节见 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。

## 典型界面可卸 kapt 的前提

当界面（Activity/Fragment）代码只使用以下能力时，可直接切换 `aa.processor=ksp`、卸掉 kapt：

- 组件：`@EActivity` / `@EFragment`（framework）/ `@EService` / `@EApplication` / `@EBean`
- View 注入：`@ViewById`（field form / 单参数 method form / param form）、`@Click`、`@LongClick`
- 资源：`@StringRes` `@TextRes` `@ColorRes` `@DrawableRes` `@LayoutRes` `@DimensionRes`(+`PixelSize`/`Offset`) `@BooleanRes` `@IntegerRes` `@IntArrayRes` `@HtmlRes` `@ColorStateListRes` `@StringArrayRes` `@AnimationRes`
- Menu：`@InjectMenu` + `@OptionsMenu` / `@OptionsItems` / `@OptionMenuItem`
- 状态与生命周期：`@InstanceState`、`@AfterViews` / `@AfterInject`、`@Bean`、intent overloads（含 Fragment options）
- 线程注解：`@Background` / `@UiThread`

依据（P0b exit，2026-11-14）：kotlin-bench 228 fixtures → 224 生成文件；全量门禁 PASS（missing/KSP-only 均空）；DECL/SIG spot-check：顶层类声明 224/224 + 方法签名 2113/2113 对齐。

## 已知偏差与例外

1. **`BenchActivity102_` 门禁 MISMATCH — 已解决（2026-11-17）**：原 fixture 无 `@JvmField` 的已初始化属性使 kapt stub 编译按设计失败（kapt stub 将 Kotlin `var` backing field 暴露为 private，APT 拒绝在 private 元素上使用 res 注解），GT 目录残留陈旧产物。解决：res 注解字段移入新增 Java fixture `BenchActivity113.java`（无 stub 问题）、`BenchActivity102.kt` 瘦身、GT 重生成 — 两文件与 KSP 输出逐字一致，全量门禁转绿。
2. **Kotlin property → field/setter 映射**：kapt 经 stub、KSP 直读符号。KSP 遵循 `@JvmField || lateinit` → 直接 `this.x = …`，否则发 setter 调用（与 APT stub 可见性语义一致）。门禁比较器吸收「直接字段写 vs setter 调用」形态差与 init_ 语句顺序；bench 无 API 级可观察偏差。
3. **FIELD 先行注入顺序**：kapt 将 Kotlin 属性 backing field 全部置于 stub 类体顶部，APT 按 stub 成员序迭代 ⇒ FIELD form 先于 METHOD/PARAM form。KSP 以「按 form 稳定分区」复现（FIELD 全部先行，组内保持声明序）。
4. **cast 渲染**：jcodemodel cast 为双括号 `((T) x)`；KSP 与 GT 逐字符一致（门禁不忽略括号数量）。
5. **既有 fixture 编译错误**：kotlin-bench 存在 9 个既有 Kotlin 错误（BenchActivity107×8、BenchFragment109×1）；kapt/KSP 构建均 EXIT=1 但代码生成完整、门禁不受影响——fixture 问题，非 processor 偏差。
6. **AndroidX Fragment 查找**：Activity / View 宿主上，字段类型是 `android.app.Fragment`（或其子类）时走 `getFragmentManager()`；否则走 `getSupportFragmentManager()`（View 宿主先把 `getContext()` cast 成 `androidx.fragment.app.FragmentActivity`）。Fragment 宿主仍用 `getFragmentManager()` / `getChildFragmentManager()`。kotlin-bench 为这条路径打开了 `android.useAndroidX=true` 并依赖 `androidx.fragment:fragment`。
7. **core 里仍未实现的只有过时的 `@HierarchyViewerSupport`**。使用它默认得到 KSP warning；把 KSP 参数 `unimplementedIsError` 设为 `true` 时改为 error。Preference 六注解、`@IntArrayRes`、`@MovieRes` 已实现（边界见第 9 条）。`@DataBound` / `@BindingObject` / `@CustomTitle` / `@HttpsClient` / `@Transactional` 已实现。`@Transactional` 不与 `@UiThread` / `@Background` / `@WakeLock` / `@Trace` / `@Suppose*` / `@IgnoreWhen` 组合（组合会报错）。`@HttpsClient` 的 keystore 路径用外层组件的 context（bean 上是 `context_`）取 raw 资源。`@Trace` 已实现，但与 APT 一样默认关闭（KSP 参数 `trace=false`）。`@Suppose*` 默认开启（`threadControl=true`）。插件 REST / ORMLite / RoboGuice / Otto 不在 core 处理器里。
8. **动态 `@Receiver` 与 APT 一样不写 permission / `RECEIVER_EXPORTED`**：生成的是两参数 `registerReceiver(receiver, filter)`。`targetSdk` 33+ 时平台要求导出标志，这是 APT 与 KSP 共同的限制。`androidx.fragment.app.Fragment` 上的 `@Receiver(registerAt = OnAttachOnDetach)` 仍生成 `onAttach(android.app.Activity)`，不会覆盖 AndroidX 的 `onAttach(Context)`；bench 这条路径用的是 framework `android.app.Fragment`。
9. **Preference 与 `@MovieRes` 的 API 边界**：
   - `@PreferenceScreen` / `@PreferenceByKey` / `@PreferenceChange` / `@PreferenceClick` / `@AfterPreferences` 可用于 `PreferenceActivity`，以及 framework / support / AndroidX 的 `PreferenceFragment(Compat)`。宿主类型决定生成的 Preference 类：AndroidX fragment 优先，其次 support-v7 / v14，否则 framework `android.preference.Preference`。
   - `@PreferenceHeaders` 只接受 `android.preference.PreferenceActivity`，并且 `android.preference.PreferenceFragment` 必须在编译类路径上（APT 的 API 11 检查）。
   - ByKey、Click、Change、AfterPreferences 写在重写的 `addPreferencesFromResource` 里。只调用 `setPreferencesFromResource` 的 `PreferenceFragmentCompat` 不会触发这些注入。`@PreferenceScreen` 生成的调用是 `addPreferencesFromResource`；Compat 实现了这个方法，所以这条路径在 androidx.preference 在类路径上时可用。kotlin-bench 用的是 framework `PreferenceActivity`，没有加 preference 依赖。
   - 无 res 值时，Click / Change 的 R.string 名会去掉后缀 `PreferenceClicked` / `PreferenceChanged`（与 APT `extractElementName` 相同）。
   - `@IntArrayRes` 生成 `resources_.getIntArray`。`@MovieRes` 生成 `resources_.getMovie(R.movie.*)`。当前 AGP / aapt2 没有 `res/movie`，编不出 `R.movie`，所以 `@MovieRes` 没有可编译的 bench fixture。

## 增量 originating files

`Dependencies` 仍是非聚合。生成类会登记这些源文件（能拿到 `KSFile` 时）：

- 注解类型自己的源文件
- 父类源文件，且该父类当前声明了 AndroidAnnotations 的成员或参数注解
- 本轮已经解析到的 `@Bean` / `@App` / `@Pref` 目标声明所在文件
- 被内联进外层生成类的嵌套 `@EBean` / `@SharedPref` 所在文件（同一文件会去重）

仍然不会失效、或只在更宽的一次编译里才会跟上的情况：

1. **`R` 与 `AndroidManifest.xml`。** 它们由环境直接读文件，不是符号上的 `KSFile`。改资源或 manifest 不会单靠这条依赖让 `*_` 重生。
2. **跨模块 / classpath 字节码。** `getContainingFile()` 为 null。已编译的 `@EBean` / `@EApplication` / `@SharedPref` 被别的模块注入时，注入方生成类不会因此失效。
3. **当前没有 AA 成员注解的父类。** 抽取器会走过它，但不登记。之后在这个父类上第一次加上注解，子类生成类不会失效，直到一次能重新看到该注解的编译。
4. **同一输出的多个 originating files 会连带重处理。** 只改 `BenchBean001.kt` 时，`BenchActivity112_` 会重生；KSP 还会再处理该 Activity 登记的其他 bean 文件，于是 `BenchBean002_` / `003_` / `004_` / `101_` 以及注入 `BenchBean101` 的 `BenchProvider_` 一并重生。无关 Activity 不会。

手工负例（临时文件，未入库）和 3 次增量数字见根目录 `BENCHMARK.md`。

## 流程约束

- `ksp/main` 分支**仅本地 commit，禁止 push**（用户未要求远程同步；exit acceptance 的 Push 项按 N/A 处理）。
