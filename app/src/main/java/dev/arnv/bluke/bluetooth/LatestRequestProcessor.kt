package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

internal class LatestRequestProcessor<T : Any>(
    scope: CoroutineScope,
    process: suspend (T) -> Unit,
) {
    private val mutablePending = MutableStateFlow<T?>(null)
    val pending: StateFlow<T?> = mutablePending

    init {
        scope.launch {
            mutablePending.filterNotNull().collectLatest(process)
        }
    }

    fun submit(request: T) {
        mutablePending.value = request
    }
}
