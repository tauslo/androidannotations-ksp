package org.androidannotations.bench

import android.app.Fragment
import android.view.Menu
import android.view.MenuItem
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.OptionsItem
import org.androidannotations.annotations.OptionsMenu
import org.androidannotations.gradle.R

@EFragment(R.layout.bench_fragment_105)
@OptionsMenu(R.menu.bench_menu_105)
class BenchFragment105 : Fragment() {

    @OptionsItem
    protected fun onShare(item: MenuItem) {
    }
}
