package com.jitji.todo

import android.content.Context
import androidx.lifecycle.LiveData

class TaskRepository(context: Context) {
    private val dao = TaskDatabase.get(context).taskDao()

    fun observeAll(): LiveData<List<Task>> = dao.observeAll()

    suspend fun find(id: Long): Task? = dao.findById(id)

    suspend fun allPending(): List<Task> = dao.allPending()

    suspend fun insert(task: Task): Long = dao.insert(task)

    suspend fun update(task: Task) = dao.update(task)

    suspend fun delete(task: Task) = dao.delete(task)

    suspend fun deleteCompleted() = dao.deleteCompleted()
}
