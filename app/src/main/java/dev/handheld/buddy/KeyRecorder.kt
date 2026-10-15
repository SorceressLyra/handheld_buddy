package dev.handheld.buddy

class KeyRecorder(private val multiple: Boolean) {
    private val captured = mutableSetOf<Int>()
    private val held = mutableSetOf<Int>()
    val keys: Set<Int> get() = captured.toSet()
    var complete = false
        private set

    fun event(key: Int, down: Boolean) {
        if (complete) return
        if (down) {
            held.add(key)
            if (multiple || captured.isEmpty()) captured.add(key)
        } else {
            held.remove(key)
            complete = captured.isNotEmpty() && held.isEmpty()
        }
    }
    fun reset() { captured.clear(); held.clear(); complete = false }
}
