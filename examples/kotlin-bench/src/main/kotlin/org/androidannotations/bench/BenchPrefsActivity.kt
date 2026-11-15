package org.androidannotations.bench

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean
import org.androidannotations.annotations.sharedpreferences.DefaultFloat
import org.androidannotations.annotations.sharedpreferences.DefaultInt
import org.androidannotations.annotations.sharedpreferences.DefaultLong
import org.androidannotations.annotations.sharedpreferences.DefaultString
import org.androidannotations.annotations.sharedpreferences.SharedPref
import org.androidannotations.annotations.sharedpreferences.SharedPref.Scope

@SharedPref(value = Scope.ACTIVITY, name = "activity_prefs")
interface BenchPrefsActivity {

    fun flag(): Boolean

    @DefaultBoolean(true)
    fun enabled(): Boolean

    @DefaultInt(42)
    fun age(): Int

    @DefaultLong(9006L)
    fun big(): Long

    @DefaultFloat(1.5f)
    fun ratio(): Float

    @DefaultString("hello")
    fun greeting(): String

    fun name(): String
}
