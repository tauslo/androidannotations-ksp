package org.androidannotations.bench

import android.app.Fragment
import android.view.MenuItem
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.OptionsItem
import org.androidannotations.gradle.R

@EFragment(R.layout.bench_fragment_106)
class BenchFragment106 : Fragment() {

    @OptionsItem
    protected fun onShare(item: MenuItem) {
    }
}
