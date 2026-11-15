# kapt vs KSP Compile Benchmark

Measured on the same toolchain after migrating AndroidAnnotations MVP annotations to KSP.

## Environment

- OS: Windows 10 (10.0.26300)
- JDK: OpenJDK 17 (Corretto 17.0.21)
- Gradle: 8.11.1
- AGP: 8.7.3
- Kotlin: 2.1.10
- KSP: 2.1.10-1.0.31
- Corpus: `examples/kotlin-bench` — 100 `@EActivity` + 100 `@EBean` classes (200 annotated types)

## Method

Cold `assembleDebug` with `--no-build-cache`, deleting `build/` and stopping the Gradle daemon between runs.

Switch processor with `-Paa.processor=kapt|ksp`:

- **Before**: existing APT jar via `kapt` (`org.androidannotations:androidannotations`)
- **After**: KSP MVP jar (`org.androidannotations:androidannotations-ksp`)

Script: `examples/kotlin-bench/run-benchmark.ps1`

## Results (median of 3 cold runs)

| Processor | Runs (seconds) | Median |
|-----------|----------------|--------|
| kapt (APT) | 137.85, 129.88, 123.85 | **129.88 s** |
| KSP MVP | 100.55, 97.41, 102.00 | **100.55 s** |

KSP cold assemble was about **22.6% faster** than kapt on this corpus (`(129.88 - 100.55) / 129.88`).

During one KSP run the processor logged generating 200 classes in ~6.8 s of processor wall time; kapt additionally pays stub generation (`kaptGenerateStubsDebugKotlin`) and non-incremental APT overhead.

## Notes

- Comparison uses the upgraded toolchain on both sides so the delta is processor path, not Kotlin/AGP upgrade.
- Bench corpus omits `@Extra` because kapt stubs expose Kotlin properties as private fields and the legacy APT validator rejects them; `@Extra` is still covered in `examples/kotlin`.
- The cold numbers above are the locked baseline (3 runs, `--no-build-cache`, daemon stopped, `build/` deleted). Later feature work was not re-run as a new cold median.

## Incremental spot-check (2026-11-16, single run)

Same machine and toolchain. Gradle daemon was already warm; `build/` was not deleted. Not a median, and not a substitute for the cold table above.

Command: `examples/kotlin-bench` → `.\gradlew.bat kspDebugKotlin "-Paa.processor=ksp"`.

| Scenario | Processor log | Gradle wall |
|----------|----------------|-------------|
| Processor jar just replaced, resources already up to date | 249 classes in 3074 ms, then an empty round of 131 ms | assembleDebug 15 s (includes `:compileDebugKotlin`, which still fails on the 9 pre-existing `lateinit` errors) |
| Earlier successful `:kspDebugKotlin` only, same 249 classes | 3401 ms + empty round 148 ms | 11 s |
| Touch `BenchActivity001.kt` only, then `:kspDebugKotlin` | 1 class in 252 ms, then an empty round of 70 ms | 5 s |

Processor time for one changed file was about 12× lower than the 249-class round (3074 ms → 252 ms). Gradle wall for `:kspDebugKotlin` was 11 s → 5 s. The empty second round (`generated 0 class(es)`) is still there.

The spot above used a processor that listed only the annotated type's own source file. The next section replaces that dependency behavior and records repeated timings.

## Incremental KSP (2026-11-16, after originating-file fix)

Same machine and toolchain as the cold table. JDK confirmed this run: OpenJDK 17.0.21 (Corretto 17.0.21.0.10). Working tree also had the pre-existing uncommitted `BenchActivity113.java` and a local edit to `BenchActivity102.kt`, so the processor reports **251** classes, matching the previous session's note. Those bench files are not part of this change.

`writeJavaSource` stays non-aggregating. Each generated file now also lists, when a `KSFile` is already in hand:

- the annotated type's own source
- a superclass source that currently declares AndroidAnnotations member or parameter annotations
- `@Bean` / `@App` / `@Pref` target sources resolved from symbols this compilation already holds
- nested `@EBean` / `@SharedPref` sources inlined into that output (same-file nests dedupe)

Not listed: `R` and `AndroidManifest.xml` (read outside the symbol model), classpath / other-module types with no `KSFile`, and a superclass that currently has no AndroidAnnotations member annotations. Adding the first such annotation on a clean superclass does not invalidate subclasses until a later round sees it.

Commands, from `examples/kotlin-bench`:

```
.\gradlew.bat --stop
Remove-Item -Recurse -Force .\build
.\gradlew.bat kspDebugKotlin --no-build-cache --refresh-dependencies "-Paa.processor=ksp"
.\gradlew.bat kspDebugKotlin --rerun-tasks --no-build-cache "-Paa.processor=ksp"
.\gradlew.bat kspDebugKotlin --no-build-cache "-Paa.processor=ksp"
```

| Scenario | Runs | Median | Processor log |
|----------|------|--------|----------------|
| Cold `:kspDebugKotlin` (daemon stopped, `build/` deleted, 1 run) | wall 110.9 s | n/a (single) | 251 classes in 11703 ms, empty round 362 ms |
| Warm full `:kspDebugKotlin --rerun-tasks` (1 run) | wall 20.48 s | n/a (single) | 251 classes in 6319 ms, empty round 210 ms |
| Touch `BenchActivity001.kt` only, 3× | 8.36, 8.14, 6.93 s | **8.14 s** | 756, 470, 366 ms; median **470 ms**. Empty round 292, 189, 74 ms; median 189 ms. Each run `generated 1` |

The cold assemble median in the table above was not re-run. This cold `:kspDebugKotlin` is one task timing, not a new assemble baseline.

Touching `BenchActivity001.kt` rewrote `BenchActivity001_.java` and left `BenchActivity002_.java` untouched. Wall time versus the warm full task was about 2.5× lower (20.48 s → 8.14 s). Processor time was about 13× lower (6319 ms → 470 ms). The empty second round is still there.

### Manual dependency checks

Touch only `BenchBean001.kt` (injected by `BenchActivity112`). `:kspDebugKotlin` logged `generated 7` in 384 ms (wall 6.41 s). Rewritten outputs:

- `BenchBean001_` (its own file)
- `BenchActivity112_` (the injector)
- `BenchBean002_`, `BenchBean003_`, `BenchBean004_`, `BenchBean101_` (the other `@Bean` targets of that same activity)
- `BenchProvider_` (it injects `BenchBean101`)

KSP reprocesses every originating file of an invalidated output. Those sibling beans are then dirty too, so a provider that injects one of them is regenerated. Unrelated activities were not in the changed set.

Superclass check used two temporary files, not committed, so the bench corpus stayed the same:

- `BenchInheritBase.kt`: abstract `Activity` with `@Extra inheritedExtra`
- `BenchInheritChild.kt`: `@EActivity` subclass

The child output contained `inheritedExtra` on `IntentBuilder_`. Touching only the base then logged `generated 1` in 417 ms (wall 6.67 s) and rewrote only `BenchInheritChild_.java`.
