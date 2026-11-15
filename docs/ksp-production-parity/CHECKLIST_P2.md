# P2 Checklist — Niche / on-demand core features

> 前置：按业务需要在 P1 后启动；**不要**为了「完成清单」无差别全做。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> 每开一项前：在目标工程 `grep` 注解用量，确认值得做。

对照 APT：Preference*、SeekBar*、Page*、Key*、WakeLock/Trace/Suppose*、DataBound、EProvider/EReceiver/EIntentService 等。

---

## Preference screens（旧 PreferenceActivity）

未做，本轮明确跳过。`@PreferenceScreen` / `@PreferenceHeaders` / `@PreferenceByKey` / `@PreferenceChange` / `@PreferenceClick` / `@AfterPreferences` 是一整套 PreferenceActivity / PreferenceFragment 生命周期，只做其中一两个注解没有可编译的 coherent fixture。kotlin-bench 也没有 Preference 用例。使用这些注解仍走 `RECOGNIZED_NOT_IMPLEMENTED`（默认 warning，`unimplementedIsError=true` 时 error）。

- [ ] `@PreferenceScreen`
- [ ] `@PreferenceHeaders`
- [ ] `@PreferenceByKey`
- [ ] `@PreferenceChange` `@PreferenceClick`
- [ ] `@AfterPreferences`
- [ ] 仅当工程仍用 PreferenceActivity / PreferenceFragment 时开启

---

## SeekBar / ViewPager / Key

- [x] `@SeekBarProgressChange` `@SeekBarTouchStart` `@SeekBarTouchStop`
- [x] `@PageSelected` `@PageScrolled` `@PageScrollStateChanged`
- [x] `@KeyDown` `@KeyUp` `@KeyLongPress` `@KeyMultiple`

---

## Threading extras / diagnostics

- [x] `@WakeLock`（含 Manifest 权限检查）
- [x] `@Trace`（option `trace`）
- [x] `@SupposeUiThread` `@SupposeBackground`（option `threadControl`；**必须排在包装 handler 之后**）
- [x] 更新 handler 执行顺序文档（对照 `CorePlugin`）

---

## Data binding / misc UI

- [x] `@DataBound` + `@EActivity` content view 路径（Fragment / ViewGroup inflate 一并接上，否则注解无法按目标拆开）
- [x] `@BindingObject`
- [x] `@CustomTitle`
- [ ] `@HierarchyViewerSupport` — 跳过。Hierarchy Viewer 已从平台移除，注解过时；仍留在 `RECOGNIZED_NOT_IMPLEMENTED`
- [x] `@HttpsClient`
- [x] `@Transactional`
- [x] `@RootContext` `@RootFragment`（若 P0/P1 未完全覆盖）

---

## Other generating components

- [x] `@EProvider`（ContentProvider_）
- [x] `@EReceiver` + `@ReceiverAction`（清单 Receiver；参数 `@Extra`）
- [x] `@EIntentService` + `@ServiceAction`
- [x] 各组件 Manifest 注册校验

---

## Hardening（建议穿插）

- [x] 未实现注解 → error（可配置）而不只是 warn（KSP 参数 `unimplementedIsError`，默认 false）
- [ ] 增量 KSP 依赖图正确性回归（与 [`CHECKLIST_PERF.md`](CHECKLIST_PERF.md) 协作）
- [ ] 更多 APT vs KSP golden fixtures（自动对比脚本入库）

---

## Acceptance for P2 exit（按所选子集）

- [x] 所选子集均有 demo 或回归 fixture
- [x] 对应 APT handler 行为抽查通过
- [x] `EXECUTION_LOG` 记录「本仓库宣称的 core 覆盖边界」
- [ ] Push `ksp/main`（本次不 push）

本轮所选子集已勾选。仍开放：Preference 屏（整套才做）、`@HierarchyViewerSupport`（过时）、`@MovieRes` / `@IntArrayRes`、增量依赖图回归、APT/KSP golden 脚本。RoboGuice / Otto 记为 N/A。ORMLite / REST-Spring 继续默认跳过，直到业务确认。

## Default skip unless demanded

- RoboGuice / Otto（见插件清单「不做」）
