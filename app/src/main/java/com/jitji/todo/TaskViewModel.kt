package com.jitji.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository(app)
    val tasks: LiveData<List<Task>> = repo.observeAll()

    fun save(task: Task, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = if (task.id == 0L) repo.insert(task) else {
                repo.update(task); task.id
            }
            val saved = task.copy(id = id)
            if (saved.dueAt != null && !saved.isDone) {
                ReminderScheduler.schedule(getApplication(), saved)
            } else {
                ReminderScheduler.cancel(getApplication(), id)
            }
            onSaved(id)
        }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(isDone = !task.isDone)
            repo.update(updated)
            if (updated.isDone) {
                ReminderScheduler.cancel(getApplication(), updated.id)
            } else if (updated.dueAt != null) {
                ReminderScheduler.schedule(getApplication(), updated)
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(task)
            ReminderScheduler.cancel(getApplication(), task.id)
        }
    }

    fun deleteCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteCompleted()
        }
    }
}
