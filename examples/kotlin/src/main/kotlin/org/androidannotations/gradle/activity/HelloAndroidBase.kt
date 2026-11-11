package org.androidannotations.gradle.activity

import android.app.Activity
import org.androidannotations.annotations.Extra

/**
 * Base Activity carrying an @Extra that subclasses inherit. Mirrors APT ModelExtractor's ancestor-member
 * collection: superclass members are processed in a second phase with the root element's holder.
 */
abstract class HelloAndroidBase : Activity() {

    @Extra
    protected var extraFromBase: Int = 0
}
