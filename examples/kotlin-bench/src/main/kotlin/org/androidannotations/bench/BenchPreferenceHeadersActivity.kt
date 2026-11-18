package org.androidannotations.bench

import android.preference.PreferenceActivity
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.PreferenceHeaders

@PreferenceHeaders(resName = "bench_headers")
@EActivity
class BenchPreferenceHeadersActivity : PreferenceActivity()
