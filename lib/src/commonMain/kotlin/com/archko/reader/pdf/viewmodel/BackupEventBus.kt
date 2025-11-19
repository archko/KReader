import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// EventBus.kt
public object BackupEventBus {
    private val _restoreCompleted = MutableSharedFlow<Boolean>(replay = 0)
    public val restoreCompleted: SharedFlow<Boolean> = _restoreCompleted.asSharedFlow()

    public suspend fun emitRestoreCompleted(success: Boolean) {
        _restoreCompleted.emit(success)
    }
}