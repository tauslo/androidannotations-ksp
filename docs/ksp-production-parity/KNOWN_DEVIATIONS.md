# KNOWN DEVIATIONS — KSP vs APT(kapt)

> 适用范围：`examples/kotlin-bench` 全量比对门禁（GT = kapt live 输出，KSP = KSP live 输出）。
> 本文档为 P0b exit acceptance 的「文档声明」项；逐切片细节见 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。

## 典型界面可卸 kapt 的前提

当界面（Activity/Fragment）代码只使用以下能力时，可直接切换 `aa.processor=ksp`、卸掉 kapt：

- 组件：`@EActivity` / `@EFragment`（framework）/ `@EService` / `@EApplication` / `@EBean`
- View 注入：`@ViewById`（field form / 单参数 method form / param form）、`@Click`、`@LongClick`
- 资源：`@StringRes` `@TextRes` `@ColorRes` `@DrawableRes` `@LayoutRes` `@DimensionRes`(+`PixelSize`/`Offset`) `@BooleanRes` `@IntegerRes` `@HtmlRes` `@ColorStateListRes` `@StringArrayRes` `@AnimationRes`
- Menu：`@InjectMenu` + `@OptionsMenu` / `@OptionsItems` / `@OptionMenuItem`
- 状态与生命周期：`@InstanceState`、`@AfterViews` / `@AfterInject`、`@Bean`、intent overloads（含 Fragment options）
- 线程注解：`@Background` / `@UiThread`

依据（P0b exit，2026-11-14）：kotlin-bench 228 fixtures → 224 生成文件；全量门禁 PASS（missing/KSP-only 均空）；DECL/SIG spot-check：顶层类声明 224/224 + 方法签名 2113/2113 对齐。

## 已知偏差与例外

1. **`BenchActivity102_` 门禁唯一 MISMATCH（GT 侧陈旧产物，非 KSP 偏差）**：当前提交的 fixture（无 `@JvmField` 的已初始化属性）使 kapt stub 编译按设计失败（kapt stub 将 Kotlin `var` backing field 暴露为 private，APT 拒绝在 private 元素上使用 res 注解），live GT 目录中残留的文件是更早 `@JvmField` 临时变体生成的陈旧产物。KSP 输出对应当前 fixture（setter 形态 + API-level IF_GUARD）且 javac 编译通过。**勿以修改 KSP 行为的方式「修掉」该 mismatch。**
2. **Kotlin property → field/setter 映射**：kapt 经 stub、KSP 直读符号。KSP 遵循 `@JvmField || lateinit` → 直接 `this.x = …`，否则发 setter 调用（与 APT stub 可见性语义一致）。门禁比较器吸收「直接字段写 vs setter 调用」形态差与 init_ 语句顺序；bench 无 API 级可观察偏差。
3. **FIELD 先行注入顺序**：kapt 将 Kotlin 属性 backing field 全部置于 stub 类体顶部，APT 按 stub 成员序迭代 ⇒ FIELD form 先于 METHOD/PARAM form。KSP 以「按 form 稳定分区」复现（FIELD 全部先行，组内保持声明序）。
4. **cast 渲染**：jcodemodel cast 为双括号 `((T) x)`；KSP 与 GT 逐字符一致（门禁不忽略括号数量）。
5. **既有 fixture 编译错误**：kotlin-bench 存在 9 个既有 Kotlin 错误（BenchActivity107×8、BenchFragment109×1）；kapt/KSP 构建均 EXIT=1 但代码生成完整、门禁不受影响——fixture 问题，非 processor 偏差。
6. **AndroidX Fragment 查找**：Activity / View 宿主上，字段类型是 `android.app.Fragment`（或其子类）时走 `getFragmentManager()`；否则走 `getSupportFragmentManager()`（View 宿主先把 `getContext()` cast 成 `androidx.fragment.app.FragmentActivity`）。Fragment 宿主仍用 `getFragmentManager()` / `getChildFragmentManager()`。kotlin-bench 为这条路径打开了 `android.useAndroidX=true` 并依赖 `androidx.fragment:fragment`。
7. **P2 已做子集之外仍未实现**：`@MovieRes`、`@IntArrayRes`、Preference 屏、DataBinding（`@DataBound` / `@BindingObject`）、`@CustomTitle`、`@HierarchyViewerSupport`、`@HttpsClient`、`@Transactional`、REST/ORMLite/RoboGuice/Otto。使用这些注解会得到 KSP warning（`RECOGNIZED_NOT_IMPLEMENTED`）。`@Trace` 已实现，但与 APT 一样默认关闭（KSP 参数 `trace=false`）。`@Suppose*` 默认开启（`threadControl=true`）。
8. **动态 `@Receiver` 与 APT 一样不写 permission / `RECEIVER_EXPORTED`**：生成的是两参数 `registerReceiver(receiver, filter)`。`targetSdk` 33+ 时平台要求导出标志，这是 APT 与 KSP 共同的限制。`androidx.fragment.app.Fragment` 上的 `@Receiver(registerAt = OnAttachOnDetach)` 仍生成 `onAttach(android.app.Activity)`，不会覆盖 AndroidX 的 `onAttach(Context)`；bench 这条路径用的是 framework `android.app.Fragment`。

## 流程约束

- `ksp/main` 分支**仅本地 commit，禁止 push**（用户未要求远程同步；exit acceptance 的 Push 项按 N/A 处理）。
