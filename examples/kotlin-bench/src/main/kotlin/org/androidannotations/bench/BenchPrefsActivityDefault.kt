package org.androidannotations.bench

import org.androidannotations.annotations.sharedpreferences.DefaultInt
import org.androidannotations.annotations.sharedpreferences.SharedPref
import org.androidannotations.annotations.sharedpreferences.SharedPref.Scope

@SharedPref(Scope.ACTIVITY_DEFAULT)
interface BenchPrefsActivityDefault {

    /**
     * The age of the user.
     */
    @DefaultInt(42)
    fun age(): Int

    fun visited(): Boolean
}
