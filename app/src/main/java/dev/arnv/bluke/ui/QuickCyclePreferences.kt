package dev.arnv.bluke.ui

internal fun normalizedCycleSelection(
    saved: Set<String>?,
    available: List<String>,
): Set<String> {
    if (available.isEmpty()) return emptySet()
    val availableSet = available.toSet()
    return saved
        ?.filterTo(linkedSetOf()) { it in availableSet }
        ?.ifEmpty { linkedSetOf(available.first()) }
        ?: available.toCollection(linkedSetOf())
}

internal fun toggledCycleSelection(
    current: Set<String>,
    value: String,
): Set<String> {
    if (value in current && current.size == 1) return current
    return current.toMutableSet().apply {
        if (!add(value)) remove(value)
    }
}
