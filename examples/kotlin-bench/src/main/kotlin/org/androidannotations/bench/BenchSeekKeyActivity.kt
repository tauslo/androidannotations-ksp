package org.androidannotations.bench

import android.app.Activity
import android.view.KeyEvent
import android.widget.SeekBar
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.KeyDown
import org.androidannotations.annotations.KeyLongPress
import org.androidannotations.annotations.KeyMultiple
import org.androidannotations.annotations.KeyUp
import org.androidannotations.annotations.PageScrollStateChanged
import org.androidannotations.annotations.PageScrolled
import org.androidannotations.annotations.PageSelected
import org.androidannotations.annotations.SeekBarProgressChange
import org.androidannotations.annotations.SeekBarTouchStart
import org.androidannotations.annotations.SeekBarTouchStop
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_seek)
class BenchSeekKeyActivity : Activity() {

    @SeekBarProgressChange(R.id.seek)
    fun onProgress(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
    }

    @SeekBarTouchStart(R.id.seek)
    fun onStart(seekBar: SeekBar) {
    }

    @SeekBarTouchStop(R.id.seek)
    fun onStop(seekBar: SeekBar) {
    }

    @PageSelected(R.id.pager)
    fun onPage(position: Int) {
    }

    @PageScrolled(R.id.pager)
    fun onScrolled(position: Int, offset: Float, offsetPixels: Int) {
    }

    @PageScrollStateChanged(R.id.pager)
    fun onState(state: Int) {
    }

    @KeyDown(KeyEvent.KEYCODE_BACK)
    fun onBack(event: KeyEvent): Boolean {
        return true
    }

    @KeyUp(KeyEvent.KEYCODE_VOLUME_UP)
    fun onVolumeUp(event: KeyEvent) {
    }

    @KeyLongPress(KeyEvent.KEYCODE_MENU)
    fun onMenuLong() {
    }

    @KeyMultiple(KeyEvent.KEYCODE_ENTER)
    fun onEnterMultiple(count: Int, event: KeyEvent) {
    }
}
