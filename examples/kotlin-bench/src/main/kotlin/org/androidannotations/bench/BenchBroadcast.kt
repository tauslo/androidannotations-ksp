package org.androidannotations.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.androidannotations.annotations.EReceiver
import org.androidannotations.annotations.ReceiverAction

@EReceiver
class BenchBroadcast : BroadcastReceiver() {

    @ReceiverAction(actions = ["org.androidannotations.bench.PING"], dataSchemes = ["bench"])
    fun onPing(context: Context, intent: Intent, @ReceiverAction.Extra("payload") payload: String) {
    }

    @ReceiverAction(actions = ["org.androidannotations.bench.A", "org.androidannotations.bench.B"])
    fun onMany(intent: Intent) {
    }

    override fun onReceive(context: Context, intent: Intent) {
    }
}
