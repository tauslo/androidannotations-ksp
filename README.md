# AndroidAnnotations KSP Fork

This repository is a fork of [androidannotations/androidannotations](https://github.com/androidannotations/androidannotations),
based on branch `develop` at commit `0d24f1b6`. The goal of this fork is to migrate
the annotation processor from javac APT / kapt to **KSP**.

## KSP MVP status

- New module: `org.androidannotations:androidannotations-ksp` (Kotlin **2.1.10**, KSP **2.1.10-1.0.31**)
- Supported annotations (MVP): `@EActivity`, `@EBean`, `@ViewById`, `@StringRes`, `@Extra`, `@AfterViews`, `@Background`, `@UiThread`
- Example: [`examples/kotlin`](examples/kotlin) uses KSP
- Compile speed comparison: [`BENCHMARK.md`](BENCHMARK.md)

## Production parity (for agents)

Goal: replace production APT/kapt AA. Long-running work — **resume from the diary**:

- Playbook: [`docs/ksp-production-parity/PLAYBOOK.md`](docs/ksp-production-parity/PLAYBOOK.md)
- Execution log (read `Next` first): [`docs/ksp-production-parity/EXECUTION_LOG.md`](docs/ksp-production-parity/EXECUTION_LOG.md)
- Checklists: [P0a](docs/ksp-production-parity/CHECKLIST_P0a.md) (done) · [P0b](docs/ksp-production-parity/CHECKLIST_P0b.md) (done) · [P1](docs/ksp-production-parity/CHECKLIST_P1.md) (prefs, listeners, ViewsById, FromHtml, dynamic Receiver, EView/EViewGroup, IgnoreWhen, NonConfigurationInstance, WindowFeature/Fullscreen, AndroidX fragment lookup; MovieRes still open) · [P2](docs/ksp-production-parity/CHECKLIST_P2.md) (current) · [Plugins](docs/ksp-production-parity/CHECKLIST_PLUGINS.md) · [Perf](docs/ksp-production-parity/CHECKLIST_PERF.md)

Upstream project status (unchanged from original):

# DEPRECATED

AndroidAnnotations is deprecated. No more development will be taking place.

Thanks for all your support!

# Fast Android Development. Easy maintenance.

[![Android Annotations Logo](https://github.com/androidannotations/androidannotations/wiki/img/aa-logo.png)](https://github.com/androidannotations/androidannotations/wiki/Home) 

**AndroidAnnotations** is an Open Source framework that **speeds up** Android development.
It takes care of the **plumbing**, and lets you concentrate on what's really important. By **simplifying** your code, it facilitates its **maintenance**.

### [**Documentation**](https://github.com/androidannotations/androidannotations/wiki/Home)

[![Build Status](https://travis-ci.org/androidannotations/androidannotations.svg?branch=develop)](https://travis-ci.org/androidannotations/androidannotations/builds) [![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-AndroidAnnotations-brightgreen.svg?style=flat)](http://android-arsenal.com/details/1/128)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/androidannotations/androidannotations?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
