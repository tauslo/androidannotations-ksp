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
