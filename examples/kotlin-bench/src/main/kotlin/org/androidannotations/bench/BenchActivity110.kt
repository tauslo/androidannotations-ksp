package org.androidannotations.bench

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.util.ArrayList
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.OnActivityResult
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_110)
class BenchActivity110 : Activity() {

    @OnActivityResult(11)
    protected fun onResult() {
    }

    @OnActivityResult(22)
    protected fun onResultWithData(intentData: Intent?) {
    }

    @OnActivityResult(22)
    protected fun onResultWithDataAndCode(intentData: Intent?, result: Int) {
    }

    @OnActivityResult(23)
    protected fun onActivityResultWithResultCodeAndData(result: Int, intentData: Intent?) {
    }

    @OnActivityResult(24)
    protected fun onActivityResultWithDataAndResultCode(intentData: Intent?, result: Int) {
    }

    @OnActivityResult(25)
    protected fun onResultWithIntResultCode(resultCode: Int) {
    }

    @OnActivityResult(26)
    protected fun onResultWithIntegerResultCode(resultCodeInteger: Int?) {
    }

    @OnActivityResult(27)
    protected fun onResultWithResultExtra(resultCode: Int, @OnActivityResult.Extra("value") i: Int,
            @OnActivityResult.Extra s: String?, @OnActivityResult.Extra uri: Uri?,
            @OnActivityResult.Extra uris: ArrayList<Uri>?, @OnActivityResult.Extra strings: Array<String>?) {
    }

    @OnActivityResult(28)
    protected fun onResultWithIntentExtras(originalIntent: Intent?, @OnActivityResult.Extra extraIntent: Intent?) {
    }

    @OnActivityResult(29)
    protected fun onResultWithByteArrays(@OnActivityResult.Extra bytee: Byte, @OnActivityResult.Extra bytes: ByteArray?) {
    }

    @OnActivityResult(30)
    protected fun onResultWithObjects(@OnActivityResult.Extra parcelable: BenchParcelable?,
            @OnActivityResult.Extra serializable: BenchSerializable?,
            @OnActivityResult.Extra genericList: ArrayList<BenchGeneric<String>>?) {
    }
    @OnActivityResult(31)
    protected fun onMixedFirst() {
    }

    @OnActivityResult(31)
    protected fun onMixedSecond(@OnActivityResult.Extra flag: Boolean) {
    }

    @OnActivityResult(32)
    protected fun onResultWithGenericSerializable(@OnActivityResult.Extra serList: ArrayList<BenchSerGeneric<String>>?) {
    }
}
