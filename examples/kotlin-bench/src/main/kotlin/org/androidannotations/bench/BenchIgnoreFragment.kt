package org.androidannotations.bench

import android.app.Fragment
import android.content.Intent
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.IgnoreWhen
import org.androidannotations.annotations.Receiver
import org.androidannotations.annotations.UiThread
import org.androidannotations.gradle.R

@EFragment(R.layout.fragment_simple)
class BenchIgnoreFragment : Fragment() {

    @IgnoreWhen(IgnoreWhen.State.DETACHED)
    protected fun whenDetached() {
    }

    @UiThread
    @IgnoreWhen(IgnoreWhen.State.VIEW_DESTROYED)
    protected fun whenViewsGone(title: String) {
    }

    @Background
    @IgnoreWhen(IgnoreWhen.State.DETACHED)
    protected fun whenBackground() {
    }

    @Receiver(actions = ["org.androidannotations.bench.FRAG"], registerAt = Receiver.RegisterAt.OnAttachOnDetach)
    protected fun onFragBroadcast(intent: Intent) {
    }
}
