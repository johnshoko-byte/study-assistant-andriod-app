package com.john.studyassistant

import android.graphics.drawable.GradientDrawable
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade

class EventDecorator(
    private val color: Int,
    private val dates: Set<CalendarDay>
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return dates.contains(day)
    }

    override fun decorate(view: DayViewFacade) {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(18, 18)
        }
        view.setSelectionDrawable(dot)
    }
}
