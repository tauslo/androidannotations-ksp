package org.androidannotations.bench

import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EBean

@EBean(scope = EBean.Scope.Singleton)
class BenchBean101 {
    @Background
    protected fun computeDateBackground() {
    }
}
