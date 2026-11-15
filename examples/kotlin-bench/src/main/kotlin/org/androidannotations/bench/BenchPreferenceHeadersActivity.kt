package org.androidannotations.bench

import android.preference.PreferenceActivity
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.PreferenceHeaders

@Suppress("DEPRECATION")
@PreferenceHeaders(resName = "bench_headers")
@EActivity
class BenchPreferenceHeadersActivity : PreferenceActivity()
