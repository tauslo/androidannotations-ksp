package org.androidannotations.bench

import android.os.Parcel
import android.os.Parcelable

class BenchGeneric<T> : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
}
