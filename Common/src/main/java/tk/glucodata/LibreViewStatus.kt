package tk.glucodata

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last thing the LibreView uploader had to say. Uploads run on their own threads and outlive
 * the screen that started them, so the status lives in a process wide holder: the Compose settings
 * screen collects it while it is open, and `Libreview` publishes every step of a run to it.
 */
object LibreViewStatus {

    private val _status = MutableStateFlow("")

    /** Ready to show: empty until the uploader ran in this process. */
    val status: StateFlow<String> = _status.asStateFlow()

    @JvmStatic
    fun setStatus(value: String) {
        _status.value = value
    }

    @JvmStatic
    fun getStatus(): String = _status.value
}
