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

- [ ] `@ViewsById`（List/数组注入）
- [ ] `@FromHtml`
- [ ] （若 P0b 未做）补齐剩余少用 Res：`@MovieRes` 等

---

## Receiver（动态）

- [ ] `@Receiver` on Activity/Fragment/Service/View（动态注册 / 生命周期挂钩）
- [ ] 参数级 `@Extra` on receiver methods（对照 `ReceiverHandler.ExtraHandler`）
- [ ] 权限 / exported / 注册时机对齐 APT（抽查）

---

## Custom views

- [ ] `@EView`
- [ ] `@EViewGroup`（layout inflate）
- [ ] View 上的 `@ViewById` / `@Click` / `@AfterViews` / `@Bean` 复用

---

## Fragment / Activity extras

- [ ] `@IgnoreWhen`（主要 Fragment；对照 `IgnoreWhenHandler` + 与 UiThread/Background 包装顺序）
- [ ] `@NonConfigurationInstance`（与 `@Bean` 共存：非 null 跳过注入）
- [ ] `@WindowFeature` `@Fullscreen`（若目标工程仍用）

---

## Support / AndroidX Fragment path

- [ ] 若 P0b 只做了 framework Fragment：补 AndroidX `Fragment` / `FragmentManager` 分支
- [ ] `FragmentById`/`ByTag` 双路径探测

---

## Unimplemented warnings & docs

- [ ] 更新 `RECOGNIZED_NOT_IMPLEMENTED`
- [ ] PLAYBOOK / README 注明 P1 覆盖范围

---

## Acceptance for P1 exit

- [ ] 至少一个 prefs + 一组监听 +（可选）Receiver 的可编译 demo
- [ ] APT/KSP 公开 API spot-check（至少 SharedPref + 1 个 listener）
- [ ] `EXECUTION_LOG`：P1 done，`Next` = P2 或插件（视业务依赖）
- [ ] Push `ksp/main`（若需要）

## Out of scope for P1

- PreferenceActivity 屏、SeekBar/ViewPager/Key 全家桶、WakeLock/Trace/Suppose、DataBinding、EProvider/EIntentService（→ P2）
- REST-Spring / ORMLite（→ 插件）
