package com.nofs.desk.data

import kotlinx.coroutines.flow.StateFlow

/**
 * ИНТЕРФЕЙС-ШОВ между UI и источником данных.
 * Реализации: FakeDeskDataSource (демо) и WebSocketDeskDataSource (реальный ПК).
 * UI и ViewModel зависят только от этого интерфейса.
 */
interface DeskDataSource {
    val state: StateFlow<DeskState>

    /** Команда от UI (fire-and-forget; результат придёт обновлением state). */
    fun send(command: DeskCommand)

    /** Остановить фоновые задачи/соединение. */
    fun stop()
}
