package org.androidannotations.bench

import android.content.Context
import android.content.res.Resources
import org.androidannotations.annotations.EBean
import org.androidannotations.annotations.HttpsClient
import org.androidannotations.annotations.RootContext
import org.apache.http.client.HttpClient

@EBean
class BenchHttpsBean {

    @RootContext
    lateinit var rootContext: Context

    fun getResources(): Resources = rootContext.resources

    @HttpsClient(allowAllHostnames = false)
    lateinit var plain: HttpClient

    @HttpsClient(trustStoreResName = "bench_trust", allowAllHostnames = true)
    lateinit var trusted: HttpClient
}
