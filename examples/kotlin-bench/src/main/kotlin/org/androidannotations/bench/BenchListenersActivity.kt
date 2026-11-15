package org.androidannotations.bench

import android.app.Activity
import android.text.Editable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import org.androidannotations.annotations.AfterTextChange
import org.androidannotations.annotations.BeforeTextChange
import org.androidannotations.annotations.CheckedChange
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.EditorAction
import org.androidannotations.annotations.FocusChange
import org.androidannotations.annotations.ItemClick
import org.androidannotations.annotations.ItemLongClick
import org.androidannotations.annotations.ItemSelect
import org.androidannotations.annotations.TextChange
import org.androidannotations.annotations.Touch
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_listeners)
class BenchListenersActivity : Activity() {

    @Touch
    protected fun touchTarget(view: View, event: MotionEvent): Boolean {
        return view.isEnabled && event.action >= 0
    }

    @FocusChange
    protected fun focusTarget(hasFocus: Boolean, view: View) {
    }

    @CheckedChange
    protected fun checkedTarget(isChecked: Boolean, button: CheckBox) {
    }

    @EditorAction
    protected fun editorTarget(textView: TextView, actionId: Int, event: KeyEvent): Boolean {
        return actionId == 0 || event.action == 0 || textView.length() == 0
    }

    @ItemClick
    protected fun itemTarget(item: String) {
    }

    @ItemLongClick(R.id.itemTarget)
    protected fun itemLong(position: Int) {
    }

    @ItemSelect(R.id.itemTarget)
    protected fun itemSelect(selected: Boolean, item: String) {
    }

    @BeforeTextChange
    protected fun textTarget(view: EditText, s: CharSequence, start: Int, after: Int) {
    }

    @TextChange(R.id.textTarget)
    protected fun onText(s: CharSequence, start: Int, before: Int, count: Int) {
    }

    @TextChange(R.id.textTarget)
    protected fun onTextView(view: TextView) {
    }

    @AfterTextChange(R.id.textTarget)
    protected fun afterText(s: Editable) {
    }

    @AfterTextChange
    protected fun textAfterOnly(s: Editable) {
    }
}
