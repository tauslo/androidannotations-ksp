package org.androidannotations.bench

import org.androidannotations.annotations.sharedpreferences.DefaultInt
import org.androidannotations.annotations.sharedpreferences.SharedPref
import org.androidannotations.annotations.sharedpreferences.SharedPref.Scope

@SharedPref(Scope.APPLICATION_DEFAULT)
interface BenchPrefsApp {

    @DefaultInt(7)
    fun counter(): Int
}
