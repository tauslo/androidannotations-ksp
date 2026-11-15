# P1 Checklist — Common legacy AA apps

> 前置：[`CHECKLIST_P0b.md`](CHECKLIST_P0b.md) exit。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> **本阶段目标：** 覆盖常见旧工程里除「典型界面」外的高频能力，减少手写迁移。

对照 APT：`SharedPrefHandler`、`PrefHandler`、各类 `*Handler` 监听、`ReceiverHandler`、`EViewHandler`、`EViewGroupHandler`、`IgnoreWhenHandler`、`ViewsByIdHandler`、`FromHtmlHandler`、`NonConfigurationInstanceHandler`。

---

## SharedPreferences（最大单块）

- [x] **BLOCKER** `@SharedPref` 生成 helper 实现（scope：`ACTIVITY` / `ACTIVITY_DEFAULT` / `APPLICATION_DEFAULT` / `UNIQUE`）
- [x] `@DefaultBoolean` `@DefaultInt` `@DefaultLong` `@DefaultFloat` `@DefaultString` `@DefaultStringSet` `@DefaultRes`（无独立 handler，由 SharedPref 消费）
- [x] Field 类型 → `BooleanPrefField` / `IntPrefField` / …（复用 `androidannotations-api` runtime）
- [x] `@Pref` 注入 helper 到 `@E*` 组件
- [x] 嵌套 / 顶层 `@SharedPref` 生成位置对齐 APT（含 static nested）
- [x] Demo + APT vs KSP API spot-check（prefs getter/editor 方法名）

---

## More view listeners

- [x] `@Touch`
- [x] `@FocusChange`
- [x] `@CheckedChange`
- [x] `@TextChange` `@BeforeTextChange` `@AfterTextChange`
- [x] `@EditorAction`
- [x] `@ItemClick` `@ItemLongClick` `@ItemSelect`
- [x] 统一走 listener 基类模式（对照 `AbstractViewListenerHandler` / `AbstractTextWatcherHandler` 等）

---

## Views helpers

- [x] `@ViewsById`（List/数组注入）
- [x] `@FromHtml`
- [ ] （若 P0b 未做）补齐剩余少用 Res：`@MovieRes` 等（P1 跳过，见 KNOWN_DEVIATIONS）

---

## Receiver（动态）

- [x] `@Receiver` on Activity/Fragment/Service/View（动态注册 / 生命周期挂钩）
- [x] 参数级 `@Extra` on receiver methods（对照 `ReceiverHandler.ExtraHandler`）
- [x] 权限 / exported / 注册时机对齐 APT（抽查）

---

## Custom views

- [x] `@EView`
- [x] `@EViewGroup`（layout inflate）
- [x] View 上的 `@ViewById` / `@Click` / `@AfterViews` / `@Bean` 复用

---

## Fragment / Activity extras

- [x] `@IgnoreWhen`（主要 Fragment；对照 `IgnoreWhenHandler` + 与 UiThread/Background 包装顺序）
- [x] `@NonConfigurationInstance`（与 `@Bean` 共存：非 null 跳过注入）
- [x] `@WindowFeature` `@Fullscreen`（若目标工程仍用）

---

## Support / AndroidX Fragment path

- [x] 若 P0b 只做了 framework Fragment：补 AndroidX `Fragment` / `FragmentManager` 分支
- [x] `FragmentById`/`ByTag` 双路径探测

---

## Unimplemented warnings & docs

- [x] 更新 `RECOGNIZED_NOT_IMPLEMENTED`
- [x] PLAYBOOK / README 注明 P1 覆盖范围

---

## Acceptance for P1 exit

- [x] 至少一个 prefs + 一组监听 +（可选）Receiver 的可编译 demo
- [x] APT/KSP 公开 API spot-check（至少 SharedPref + 1 个 listener）
- [x] `EXECUTION_LOG`：P1 done，`Next` = P2 或插件（视业务依赖）
- [ ] Push `ksp/main`（若需要）— 本阶段仅本地 commit，不 push

## Out of scope for P1

- PreferenceActivity 屏、SeekBar/ViewPager/Key 全家桶、WakeLock/Trace/Suppose、DataBinding、EProvider/EIntentService（→ P2）
- REST-Spring / ORMLite（→ 插件）
