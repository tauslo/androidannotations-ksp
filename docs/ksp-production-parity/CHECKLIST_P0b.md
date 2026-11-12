# P0b Checklist — Typical screens can drop kapt

> 前置：[`CHECKLIST_P0a.md`](CHECKLIST_P0a.md) 主路径已完成（exit acceptance done）。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> **本阶段里程碑：** 多数典型 Activity/Fragment 界面可卸 `kapt`，只留 `ksp`。

对照 APT：`EFragmentHolder`、`FragmentBy*Handler`、`ClickHandler`、`*ResHandler`、`Options*Handler`、`InstanceStateHandler`、`OnActivityResultHandler`、`SystemServiceHandler`、`EApplicationHandler`、`EServiceHandler`。

---

## Carry-over from P0a（可选，建议穿插）

- [ ] `@ViewById` **method-injection** form（单参数方法；对照 `ViewByIdHandler` + `MethodInjectionHandler`）
- [ ] Document / enforce `@Background` / `@UiThread` ordering relative to future `@Trace` / `@IgnoreWhen` / `@WakeLock`（对照 `CorePlugin` 注册顺序）
- [ ] Kotlin property → Java field/setter mapping review（含 primitive / `@JvmField`）；bench 里 kapt stub 直接写字段 vs KSP setter 的已知偏差是否可接受或需对齐
- [ ] Optional：emit `@NotNull`（或等价）on generated override params if APT does（CLASS retention；非公开 API 阻塞项）

---

## Fragment（BLOCKER 块）

- [ ] **BLOCKER** `@EFragment` generating class（layout inflate / `forceLayoutInjection` / `onCreateView` / `onViewCreated` / `onDestroyView` 清 view 字段）
- [ ] **BLOCKER** `FragmentBuilder_` + `Xxx_.builder()` / `fragment(...)` fluent API（对照 APT Fragment builder）
- [ ] **BLOCKER** `@FragmentArg`：arguments Bundle restore + builder fluent put
- [ ] `@FragmentById` / `@FragmentByTag`（framework Fragment；若工程用 AndroidX 再加 support/AndroidX 路径）
- [ ] Fragment 上 `@Bean` / `@AfterInject` / `@AfterViews` / `@ViewById` / 线程注解复用 Activity 路径
- [ ] Manifest / 组件注册：Fragment 一般不进 Manifest；确认 validator 不误伤
- [ ] Demo：`examples/kotlin` 或 `examples/kotlin-parity` 含 Fragment + Arg + builder

---

## View listeners（高频）

- [ ] **BLOCKER** `@Click`
- [ ] **BLOCKER** `@LongClick`
- [ ] Method signatures：0 参 / View 参；多 id / `resName`；对照 `AbstractViewListenerHandler`
- [ ] Demo：点击启动 `Xxx_.intent(...).start()`（可扩现有 launchButton）

---

## Resources（常用集）

- [ ] `@ColorRes`（ContextCompat 分支，对照 `ColorResHandler` / `ContextCompatAwareResHandler`）
- [ ] `@DrawableRes`（同上）
- [ ] `@ColorStateListRes`
- [ ] `@BooleanRes` `@IntegerRes` `@StringArrayRes` `@TextRes` `@TextArrayRes`
- [ ] `@DimensionRes` `@DimensionPixelOffsetRes` `@DimensionPixelSizeRes`
- [ ] `@AnimationRes` `@HtmlRes` `@LayoutRes`（按样例需要；可先 DefaultRes 通用路径）
- [ ] 全部走 `RClassIndex` 字段引用 + 缺失报错（复用 P0a）

---

## Menu

- [ ] `@OptionsMenu`（inflate）
- [ ] `@OptionsItem`（选中回调）
- [ ] `@OptionsMenuItem`（注入 MenuItem）
- [ ] `@InjectMenu`（若样例需要）

---

## State / results / system

- [ ] `@InstanceState`（onSaveInstanceState / restore；BundleHelper 类型复用 Extra）
- [ ] `@OnActivityResult` + 参数级 `@Extra`（对照内部 `ExtraHandler`）
- [ ] `@SystemService`
- [ ] 确认 `@AfterExtras` 在 P0a 已有；Fragment/Service 若需要则补齐调用点

---

## Application / Service

- [ ] `@EApplication` 生成类 + Manifest application 名校验
- [ ] `@App` 注入
- [ ] `@EService` 生成类 + Service `IntentBuilder_`
- [ ] Service 组件 Manifest 注册校验（复用 `ComponentValidator`）

---

## IntentBuilder Fragment overloads（P0a 曾 defer）

- [ ] `intent(Fragment)` / AndroidX Fragment 重载（classpath 探测，对照 APT）
- [ ] Activity options / `withOptions`（若 APT 有且业务需要）

---

## Unimplemented warning set

- [ ] 从 `RECOGNIZED_NOT_IMPLEMENTED` 移除本阶段已实现注解
- [ ] 为本阶段将做、尚未做的注解保持 warn（避免静默无行为）

---

## Acceptance for P0b exit

- [ ] `examples/kotlin` 或新建 `examples/kotlin-parity`：Activity + Fragment + Click + 常用 Res + Menu 或 InstanceState 至少覆盖一条主路径
- [ ] 同 fixture：`kapt` vs `ksp` 生成公开 API spot-check（DECL/SIG 对齐；记录可接受 body 偏差）
- [ ] 文档声明：典型界面可卸 kapt 的前提与已知偏差
- [ ] `EXECUTION_LOG` 标记 P0b done，`Next` = P1 首项
- [ ] Push `ksp/main`（若用户要求远程同步）

## Out of scope for P0b

- `@SharedPref` / 大量次级监听 / `@Receiver` / `@EView*`（→ P1）
- Preference 屏、WakeLock、DataBinding、Provider/IntentService（→ P2）
- REST / ORMLite / RoboGuice / Otto（→ 插件）
