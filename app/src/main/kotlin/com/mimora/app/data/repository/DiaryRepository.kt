package com.mimora.app.data.repository

import com.mimora.app.core.model.DateRules
import com.mimora.app.core.model.DiaryEntry
import com.mimora.app.database.MimoraDatabaseHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import java.time.LocalDate

class DiaryRepository(private val db: MimoraDatabaseHelper) {

    /**
     * Emits entry for the given date, and re-emits whenever any database write occurs.
     */
    fun getEntry(date: LocalDate): Flow<DiaryEntry?> = flow {
        emit(db.getEntryByDate(date))
        db.dbChanges.collect {
            emit(db.getEntryByDate(date))
        }
    }

    suspend fun saveEntry(entry: DiaryEntry) {
        // Enforce future date restriction in repository layer
        require(DateRules.isDateAllowedForWriting(entry.date)) {
            "Diary entries cannot be created or modified for future dates."
        }
        db.saveOrUpdateEntry(entry)
    }

    fun getEntriesForMonth(year: Int, month: Int): Flow<List<DiaryEntry>> = flow {
        emit(db.getEntriesForMonth(year, month))
        db.dbChanges.collect {
            emit(db.getEntriesForMonth(year, month))
        }
    }

    fun getMemoriesOnThisDay(month: Int, day: Int, currentYear: Int): Flow<List<DiaryEntry>> = flow {
        emit(db.getMemoriesOnThisDay(month, day, currentYear))
        db.dbChanges.collect {
            emit(db.getMemoriesOnThisDay(month, day, currentYear))
        }
    }

    fun searchEntries(query: String, filterPhoto: Boolean, filterVoice: Boolean): Flow<List<DiaryEntry>> = flow {
        emit(db.searchEntries(query, filterPhoto, filterVoice))
        db.dbChanges.collect {
            emit(db.searchEntries(query, filterPhoto, filterVoice))
        }
    }

    suspend fun deleteEntry(date: LocalDate) {
        db.deleteEntry(date)
    }
}
