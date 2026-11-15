package org.androidannotations.bench

import android.database.sqlite.SQLiteDatabase
import org.androidannotations.annotations.EBean
import org.androidannotations.annotations.Transactional

@EBean
class BenchTxnBean {

    @Transactional
    fun write(db: SQLiteDatabase, value: Int): Int {
        return value
    }
}
