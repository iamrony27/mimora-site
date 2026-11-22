package com.mimora.app.data.repository

import com.mimora.app.core.model.DiaryStatistics
import com.mimora.app.database.MimoraDatabaseHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StatisticsRepository(private val db: MimoraDatabaseHelper) {
    fun getStatistics(): Flow<DiaryStatistics> = flow {
        emit(db.calculateStatistics())
        db.dbChanges.collect {
            emit(db.calculateStatistics())
        }
    }
}
