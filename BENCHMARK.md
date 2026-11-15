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

`Dependencies` in `AndroidAnnotationsSymbolProcessor.writeJavaSource` is non-aggregating and lists only the annotated type's source file. A same-file edit invalidates that generated class, which matches the one-file spot. A superclass in another file, or an `@Bean` / `@App` / `@Pref` target, is not an originating file, so those edits do not invalidate the generated subclass. That negative case is still open.
