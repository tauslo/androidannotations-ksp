package org.androidannotations.bench

import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import org.androidannotations.annotations.AfterPreferences
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.PreferenceByKey
import org.androidannotations.annotations.PreferenceChange
import org.androidannotations.annotations.PreferenceClick
import org.androidannotations.annotations.PreferenceScreen

@Suppress("DEPRECATION")
@PreferenceScreen(resName = "bench_prefs")
@EActivity
class BenchPreferenceActivity : PreferenceActivity() {

    @PreferenceByKey(resName = "benchListKey")
    lateinit var listPref: Preference

    @PreferenceByKey
    lateinit var benchCheckKey: CheckBoxPreference

    @PreferenceChange(resName = ["benchListKey"])
    fun listChanged(preference: Preference, value: Int) {
    }

    @PreferenceChange(resName = ["benchCheckKey"])
    fun checkChanged(preference: CheckBoxPreference, newValue: java.lang.Boolean) {
    }

    @PreferenceClick
    fun benchCheckKeyPreferenceClicked(preference: CheckBoxPreference): Boolean {
        return false
    }

    @PreferenceClick(resName = ["benchListKey"])
    fun listClicked() {
    }

    @PreferenceByKey(resName = "benchEditKey")
    fun injectEdit(pref: EditTextPreference) {
    }

    @AfterPreferences
    fun afterPreferences() {
    }
}
