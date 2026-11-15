package org.androidannotations.bench

import java.util.Set
import org.androidannotations.annotations.sharedpreferences.DefaultInt
import org.androidannotations.annotations.sharedpreferences.DefaultRes
import org.androidannotations.annotations.sharedpreferences.SharedPref
import org.androidannotations.annotations.sharedpreferences.SharedPref.Scope
import org.androidannotations.gradle.R

@SharedPref(value = Scope.UNIQUE, name = "unique_prefs")
interface BenchPrefsUnique {

    @DefaultInt(value = 5, keyRes = R.string.bench_key)
    fun keyed(): Int

    @DefaultRes(R.bool.benchFlag)
    fun resFlag(): Boolean

    @DefaultRes(R.integer.benchNumber)
    fun resNumber(): Int

    @DefaultRes(R.string.app_name)
    fun resName(): String

    @DefaultRes(R.array.benchStrings)
    fun resStrings(): Set<String>
}
