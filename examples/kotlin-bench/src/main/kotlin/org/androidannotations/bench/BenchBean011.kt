package org.androidannotations.bench

import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EBean

@EBean
class BenchBean011 {
    @Background
    protected fun computeDateBackground() {
    }
}
