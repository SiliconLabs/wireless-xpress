package com.silabs.bgxcommander.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

class LocationDisabledBar(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    constructor(context: Context) : this(context, null)

    fun show() {
        visibility = View.VISIBLE
    }

    fun hide() {
        visibility = View.GONE
    }

}