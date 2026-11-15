package org.androidannotations.bench

import android.app.Activity
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.SupposeBackground
import org.androidannotations.annotations.SupposeUiThread
import org.androidannotations.annotations.Trace
import org.androidannotations.annotations.UiThread
import org.androidannotations.annotations.WakeLock

@EActivity
class BenchThreadsActivity : Activity() {

    @WakeLock
    fun hold() {
    }

    @WakeLock(level = WakeLock.Level.FULL_WAKE_LOCK, flags = [WakeLock.Flag.ACQUIRE_CAUSES_WAKEUP], tag = "bench")
    @UiThread
    fun holdOnUi() {
    }

    @Trace
    @Background
    fun tracedBg() {
    }

    @SupposeUiThread
    fun mustUi() {
    }

    @SupposeBackground(serial = ["bench"])
    @Background(serial = "bench")
    fun mustBg() {
    }
}
