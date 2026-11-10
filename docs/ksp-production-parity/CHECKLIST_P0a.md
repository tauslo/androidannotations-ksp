# P0a Checklist — Infrastructure + deepen existing 8 annotations

> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。  
> 顺序建议自上而下；标了 **BLOCKER** 的尽量先做。

## Options & environment

- [x] **BLOCKER** Parse KSP options: `resourcePackageName`, `androidManifestFile`, `library`, `classSuffix`, `useR2`（名称对齐 APT `Option`）
- [x] Pass options through a single `KspEnvironment` / similar（避免各处读 Map）
- [x] Document options in `examples/kotlin` `ksp { arg(...) }`

## Manifest & R

- [x] **BLOCKER** Implement Manifest load（优先显式 `androidManifestFile`；可选向上探测，但勿依赖 `kapt.kotlin.generated`）
- [x] Validate `@EActivity` / 后续 `@E*` 组件已在 Manifest 注册（`library=true` 时放宽）
- [ ] Resolve `R` / `R2` by package；fail/warn if id/layout/string missing
- [ ] Prefer emitting `pkg.R.type.name` field refs over raw int when possible

## Processor pipeline structure

- [ ] Split MVP god-generator toward `handler` + `generate` packages（渐进，勿一次性重写）
- [ ] Collect annotations from **superclass** members（ancestor annotations）
- [ ] Nested `@EBean` / future nested types → static nested class inside outer `*_`
- [ ] Warn on AA annotations that are recognized but **not implemented** yet

## Deepen `@EActivity` / `@Extra`

- [x] **BLOCKER** Generate `IntentBuilder_` + `public static IntentBuilder_ intent(Context)`（对照 `ActivityIntentBuilder`）
- [x] Fluent `putExtra` per `@Extra`；`startActivity` / `startActivityForResult`
- [ ] Expand Bundle restore types（Parcelable, Serializable, arrays, ArrayList… via APT `BundleHelper` 行为）
- [ ] `@AfterExtras` hook if extras injected（可与 P0b 合并，但 Extra 深化时预留）

## Deepen `@EBean` / `@Bean` / `@AfterInject`

- [ ] **BLOCKER** EBean scopes: Default / Activity / Fragment / Singleton（对照 `EBeanHolder.createFactoryMethod`）
- [ ] `rebind` for Default scope if APT has it
- [ ] **BLOCKER** `@Bean` injection calling `Generated_.getInstance_(…)` + BeanHolder map
- [ ] `@AfterInject` invoke after injection block
- [ ] Root context / root fragment wiring as needed for Fragment-scoped beans

## Deepen views / res / threads（质量）

- [ ] `@ViewById` method-injection form（optional in P0a if time；否则记入 P0b）
- [ ] Keep `@Background` / `@UiThread` ordering notes compatible with future Trace/IgnoreWhen
- [ ] Kotlin property → Java setter/field mapping remains correct（含 primitive）

## Acceptance for P0a exit

- [ ] `examples/kotlin` demonstrates `Xxx_.intent(this).myIntExtra(1).start()`（或等价 Extra）
- [ ] `examples/kotlin` demonstrates `@Bean` EnhancedBean injection into Activity
- [ ] Spot-check: APT vs KSP generated public API for sample Activity（method names）
- [ ] `EXECUTION_LOG` marks P0a done and sets Next = first P0b item
- [ ] Push to `ksp/main` when user wants remote updated

## Out of scope for P0a（不要偷跑）

- `@EFragment` / menus / InstanceState / Click（→ P0b）
- SharedPref（→ P1）
- REST / ORMLite（→ 插件期）
- Incremental KSP performance（可另开，但勿阻塞 P0a 完成定义）
