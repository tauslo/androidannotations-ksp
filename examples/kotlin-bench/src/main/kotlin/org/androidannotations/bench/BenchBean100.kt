package org.androidannotations.bench

import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EBean

@EBean
class BenchBean100 {
    @Background
    protected fun computeDateBackground() {
    }
}
