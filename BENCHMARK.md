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
- This is an MVP processor (core sample annotations only), not a full AA feature parity rewrite.
