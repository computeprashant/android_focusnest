package com.example.focusnest

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/** Spinner adapter that uses the theme's colours, so text stays readable on dark themes. */
class ThemedAdapter(
    context: Context,
    items: List<String>,
    private val textColor: Int,
    private val popupColor: Int,
) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {

    // The selected value on the closed dropdown: bigger and bold
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        (super.getView(position, convertView, parent) as TextView).apply {
            setTextColor(textColor)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }

    // Rows in the open list: roomier, with more padding
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val density = parent.resources.displayMetrics.density
        return (super.getDropDownView(position, convertView, parent) as TextView).apply {
            setTextColor(textColor)
            setBackgroundColor(popupColor)
            minHeight = (52 * density).toInt()
            setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
        }
    }
}
