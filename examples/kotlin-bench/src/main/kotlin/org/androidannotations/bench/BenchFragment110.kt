package org.androidannotations.bench

import android.app.Fragment
import android.content.Intent
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.OnActivityResult
import org.androidannotations.gradle.R

@EFragment(R.layout.bench_fragment_110)
class BenchFragment110 : Fragment() {

    @OnActivityResult(41)
    protected fun onFragmentResult(intentData: Intent?) {
    }

    @OnActivityResult(42)
    protected fun onFragmentExtra(@OnActivityResult.Extra("name") name: String?, @OnActivityResult.Extra count: Int) {
    }
}
