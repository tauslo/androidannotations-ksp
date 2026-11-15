package org.androidannotations.bench

import org.androidannotations.annotations.EBean
import org.androidannotations.annotations.sharedpreferences.DefaultInt
import org.androidannotations.annotations.sharedpreferences.Pref
import org.androidannotations.annotations.sharedpreferences.SharedPref
import org.androidannotations.annotations.sharedpreferences.SharedPref.Scope

@EBean
class BenchPrefsClient {

    @Pref
    lateinit var activityPrefs: BenchPrefsActivity_

    @Pref
    lateinit var activityDefaultPrefs: BenchPrefsActivityDefault_

    @Pref
    lateinit var appPrefs: BenchPrefsApp_

    @Pref
    lateinit var uniquePrefs: BenchPrefsUnique_

    @Pref
    lateinit var javaPrefs: BenchPrefsJava_

    @SharedPref(Scope.UNIQUE)
    interface Nested {
        @DefaultInt(1)
        fun nestedCount(): Int
    }
}
