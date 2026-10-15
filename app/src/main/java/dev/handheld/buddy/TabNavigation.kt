package dev.handheld.buddy

import android.view.KeyEvent

enum class TabLevel { TOP, SUB }
data class TabTarget(val level: TabLevel, val index: Int)

fun bumperNavigation(key: Int, top: Int, sub: Int): TabTarget? = when (key) {
    KeyEvent.KEYCODE_BUTTON_L1 -> TabTarget(TabLevel.TOP, (top + 2) % 3)
    KeyEvent.KEYCODE_BUTTON_R1 -> TabTarget(TabLevel.TOP, (top + 1) % 3)
    KeyEvent.KEYCODE_BUTTON_L2 -> if (top == 1) TabTarget(TabLevel.SUB, (sub + 2) % 3) else null
    KeyEvent.KEYCODE_BUTTON_R2 -> if (top == 1) TabTarget(TabLevel.SUB, (sub + 1) % 3) else null
    else -> null
}
