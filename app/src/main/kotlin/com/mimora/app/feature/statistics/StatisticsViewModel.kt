package com.mimora.app.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimora.app.core.model.DiaryStatistics
import com.mimora.app.data.repository.StatisticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _statistics = MutableStateFlow(DiaryStatistics())
    val statistics: StateFlow<DiaryStatistics> = _statistics.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            statisticsRepository.getStatistics().collectLatest { stats ->
                _statistics.value = stats
            }
        }
    }
}
