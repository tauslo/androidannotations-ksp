# P2 Checklist — Niche / on-demand core features

> 前置：按业务需要在 P1 后启动；**不要**为了「完成清单」无差别全做。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> 每开一项前：在目标工程 `grep` 注解用量，确认值得做。

对照 APT：Preference*、SeekBar*、Page*、Key*、WakeLock/Trace/Suppose*、DataBound、EProvider/EReceiver/EIntentService 等。

---

## Preference screens（旧 PreferenceActivity）

未做。目标工程未要求 PreferenceActivity / PreferenceFragment，留到下一波。

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

- [ ] `@DataBound` + `@EActivity` content view 路径
- [ ] `@BindingObject`
- [ ] `@CustomTitle`
- [ ] `@HierarchyViewerSupport`
- [ ] `@HttpsClient`
- [ ] `@Transactional`
- [x] `@RootContext` `@RootFragment`（若 P0/P1 未完全覆盖）

---

## Other generating components

- [x] `@EProvider`（ContentProvider_）
- [x] `@EReceiver` + `@ReceiverAction`（清单 Receiver；参数 `@Extra`）
- [x] `@EIntentService` + `@ServiceAction`
- [x] 各组件 Manifest 注册校验

---

## Hardening（建议穿插）

- [ ] 未实现注解 → error（可配置）而不只是 warn（生产迁移后期）
- [ ] 增量 KSP 依赖图正确性回归（与 [`CHECKLIST_PERF.md`](CHECKLIST_PERF.md) 协作）
- [ ] 更多 APT vs KSP golden fixtures（自动对比脚本入库）

---

## Acceptance for P2 exit（按所选子集）

- [x] 所选子集均有 demo 或回归 fixture
- [x] 对应 APT handler 行为抽查通过
- [x] `EXECUTION_LOG` 记录「本仓库宣称的 core 覆盖边界」
- [ ] Push `ksp/main`（本次不 push）

Preference 屏、`@DataBound` / `@BindingObject`、`@CustomTitle`、`@HierarchyViewerSupport`、`@HttpsClient`、`@Transactional` 未做（按需第二波，见 EXECUTION_LOG）。RoboGuice / Otto / ORMLite / REST-Spring 继续默认跳过。

## Default skip unless demanded

- RoboGuice / Otto（见插件清单「不做」）
