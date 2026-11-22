package com.mimora.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimora.app.core.model.DateRules
import com.mimora.app.core.model.DiaryEntry
import com.mimora.app.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayIndicator(
    val date: LocalDate,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val hasEntry: Boolean = false,
    val hasText: Boolean = false,
    val hasPhoto: Boolean = false,
    val hasVoice: Boolean = false
)

data class CalendarUiState(
    val selectedYearMonth: YearMonth = YearMonth.now(),
    val daysInGrid: List<DayIndicator> = emptyList(),
    val memoriesOnThisDay: List<DiaryEntry> = emptyList(),
    val entriesInMonth: List<DiaryEntry> = emptyList()
)

class CalendarViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadMonth(YearMonth.now())
        loadMemoriesOnThisDay()
    }

    fun selectMonth(yearMonth: YearMonth) {
        loadMonth(yearMonth)
    }

    fun previousMonth() {
        loadMonth(_uiState.value.selectedYearMonth.minusMonths(1))
    }

    fun nextMonth() {
        val next = _uiState.value.selectedYearMonth.plusMonths(1)
        // Allow browsing months up to current month or beyond for viewing calendar structure,
        // but individual future day cells will be disabled.
        loadMonth(next)
    }

    private fun loadMonth(ym: YearMonth) {
        viewModelScope.launch {
            diaryRepository.getEntriesForMonth(ym.year, ym.monthValue).collectLatest { entries ->
                val entryMap = entries.associateBy { it.date }
                val today = LocalDate.now()

                val firstDayOfMonth = ym.atDay(1)
                val daysInMonth = ym.lengthOfMonth()
                val startDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0

                val list = mutableListOf<DayIndicator>()

                // Leading empty days
                val prevMonth = ym.minusMonths(1)
                val prevMonthLength = prevMonth.lengthOfMonth()
                for (i in startDayOfWeek - 1 downTo 0) {
                    val d = prevMonth.atDay(prevMonthLength - i)
                    list.add(
                        DayIndicator(
                            date = d,
                            dayNumber = d.dayOfMonth,
                            isCurrentMonth = false,
                            isToday = d == today,
                            isFuture = d.isAfter(today)
                        )
                    )
                }

                // Days of current month
                for (day in 1..daysInMonth) {
                    val d = ym.atDay(day)
                    val entry = entryMap[d]
                    val isFuture = d.isAfter(today)
                    list.add(
                        DayIndicator(
                            date = d,
                            dayNumber = day,
                            isCurrentMonth = true,
                            isToday = d == today,
                            isFuture = isFuture,
                            hasEntry = entry != null && !entry.isEmpty,
                            hasText = entry?.hasText == true,
                            hasPhoto = entry?.hasPhoto == true,
                            hasVoice = entry?.hasVoice == true
                        )
                    )
                }

                _uiState.value = _uiState.value.copy(
                    selectedYearMonth = ym,
                    daysInGrid = list,
                    entriesInMonth = entries
                )
            }
        }
    }

    private fun loadMemoriesOnThisDay() {
        val today = LocalDate.now()
        viewModelScope.launch {
            diaryRepository.getMemoriesOnThisDay(today.monthValue, today.dayOfMonth, today.year)
                .collectLatest { memories ->
                    _uiState.value = _uiState.value.copy(memoriesOnThisDay = memories)
                }
        }
    }
}
