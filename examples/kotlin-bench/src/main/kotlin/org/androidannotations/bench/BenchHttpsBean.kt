package org.androidannotations.bench

import org.androidannotations.annotations.EBean
import org.androidannotations.annotations.HttpsClient
import org.apache.http.client.HttpClient

@EBean
class BenchHttpsBean {

    @HttpsClient(allowAllHostnames = false)
    lateinit var plain: HttpClient

    @HttpsClient(trustStoreResName = "bench_trust", allowAllHostnames = true)
    lateinit var trusted: HttpClient
}
