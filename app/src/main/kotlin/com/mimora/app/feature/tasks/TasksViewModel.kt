package com.mimora.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimora.app.core.model.TaskItem
import com.mimora.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TasksUiState(
    val pendingTasks: List<TaskItem> = emptyList(),
    val completedTasks: List<TaskItem> = emptyList(),
    val showAddDialog: Boolean = false
)

class TasksViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            taskRepository.getTasks().collectLatest { allTasks ->
                val pending = allTasks.filter { !it.isCompleted }
                val completed = allTasks.filter { it.isCompleted }
                _uiState.value = _uiState.value.copy(
                    pendingTasks = pending,
                    completedTasks = completed
                )
            }
        }
    }

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch {
            taskRepository.toggleTask(task)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
        }
    }

    fun openAddTaskDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun closeAddTaskDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addTask(title: String, description: String, dueDate: LocalDate?, reminderTime: LocalTime?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = TaskItem(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                dueDate = dueDate,
                reminderTime = reminderTime,
                isCompleted = false
            )
            taskRepository.saveTask(task)
            closeAddTaskDialog()
        }
    }
}
