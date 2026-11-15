package org.androidannotations.bench

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.androidannotations.annotations.EService
import org.androidannotations.annotations.Receiver

@EService
class BenchReceiverService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @Receiver(actions = ["org.androidannotations.bench.PING"])
    protected fun onPing(intent: Intent) {
    }
}
