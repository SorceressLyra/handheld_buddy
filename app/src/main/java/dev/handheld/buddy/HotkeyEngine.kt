package dev.handheld.buddy

enum class Control(val id: String, val title: String) {
    BRIGHTNESS("brightness", "Brightness"), VOLUME("volume", "Volume")
}

data class KeyBinding(val control: Control, val modifiers: Set<Int>, val decrease: Int, val increase: Int)

fun isDpadKey(key: Int) = key in 19..23 || key in 268..271

fun validBindings(bindings: List<KeyBinding>): Boolean {
    val modifiers = bindings.flatMap { it.modifiers }.toSet()
    if (modifiers.any { !isHotkeyKeyAllowed(it) } || bindings.any { !isHotkeyKeyAllowed(it.decrease) || !isHotkeyKeyAllowed(it.increase) }) return false
    if (bindings.any { it.modifiers.isEmpty() || it.decrease == it.increase || it.decrease in modifiers || it.increase in modifiers }) return false
    return bindings.indices.all { i ->
        bindings.drop(i + 1).none { other ->
            bindings[i].modifiers == other.modifiers &&
                setOf(bindings[i].decrease, bindings[i].increase).intersect(setOf(other.decrease, other.increase)).isNotEmpty()
        }
    }
}

/** Android-free matching keeps Back navigation shared across all active controls. */
class HotkeyEngine(bindings: List<KeyBinding>) {
    private val bindings = bindings.sortedByDescending { it.modifiers.size }
    private val modifiers = bindings.flatMap { it.modifiers }.toSet()
    data class Result(val consumed: Boolean = false, val delta: Int = 0, val control: Control? = null, val back: Boolean = false)
    private val held = mutableSetOf<Pair<Int, Int>>()
    private val consumed = mutableSetOf<Pair<Int, Int>>()
    private var used = false

    fun reset() { held.clear(); consumed.clear(); used = false }

    fun event(key: Int, down: Boolean, deviceId: Int = 0): Result {
        val input = deviceId to key
        if (!down) {
            val wasHeld = held.remove(input)
            val handled = consumed.remove(input)
            val back = key == 4 && key in modifiers && wasHeld && !used
            if (held.none { it.second in modifiers }) used = false
            return Result(handled, back = back)
        }
        held.add(input)
        if (key in modifiers) { consumed.add(input); return Result(true) }
        // Built-in Back and shoulder buttons may belong to separate Android input devices.
        val heldKeys = held.map { it.second }.toSet()
        val binding = bindings.firstOrNull {
            (key == it.decrease || key == it.increase) && it.modifiers.isNotEmpty() && heldKeys.containsAll(it.modifiers)
        } ?: return Result()
        consumed.add(input)
        used = true
        return Result(true, if (key == binding.increase) 1 else -1, binding.control)
    }
}
