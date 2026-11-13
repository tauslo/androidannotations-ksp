package org.androidannotations.bench

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.InjectMenu
import org.androidannotations.annotations.OptionsItem
import org.androidannotations.annotations.OptionsMenuItem
import org.androidannotations.annotations.OptionsMenu
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_105)
@OptionsMenu(R.menu.bench_menu_105)
class BenchActivity105 : Activity() {

    @InjectMenu
    protected lateinit var benchMenu: Menu

    @OptionsMenuItem
    protected lateinit var shareItem: MenuItem

    @OptionsMenuItem(R.id.menu_search)
    protected lateinit var searchItem: MenuItem

    @OptionsItem
    protected fun onShare(item: MenuItem) {
    }

    @OptionsItem(R.id.menu_settings)
    protected fun onSettings(): Boolean {
        return false
    }

    @OptionsItem(value = [R.id.menu_share, R.id.menu_refresh])
    protected fun onMulti(): Boolean {
        return true
    }

    @OptionsMenuItem
    protected fun singleInjection(menuRefresh: MenuItem) {
    }

    protected fun multiInjection(@OptionsMenuItem menuRefresh: MenuItem, @OptionsMenuItem(R.id.menu_search) menuItemSearch: MenuItem) {
    }
}
