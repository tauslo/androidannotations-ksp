package org.androidannotations.bench

import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EBean

@EBean(scope = EBean.Scope.Activity)
class BenchBean102 {
    @Background
    protected fun computeDateBackground() {
    }
}
