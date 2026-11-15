package org.androidannotations.bench

import android.content.Intent
import org.androidannotations.annotations.EIntentService
import org.androidannotations.annotations.ServiceAction
import org.androidannotations.api.support.app.AbstractIntentService

@EIntentService
class BenchIntentWork : AbstractIntentService("bench-intent") {

    @ServiceAction
    fun ping(value: String) {
    }

    @ServiceAction("custom.action")
    fun named(count: Int) {
    }

    override fun onHandleIntent(intent: Intent?) {
    }
}
