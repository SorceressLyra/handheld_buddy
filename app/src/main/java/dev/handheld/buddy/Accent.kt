package dev.handheld.buddy

enum class Accent(val title: String, val themeStyle: Int, val overlayColor: Int) {
    PURPLE("Purple", R.style.Accent_Purple, 0xFFD0BCFF.toInt()),
    BLUE("Blue", R.style.Accent_Blue, 0xFFB3C5FF.toInt()),
    TEAL("Teal", R.style.Accent_Teal, 0xFF80D5C9.toInt()),
    GREEN("Green", R.style.Accent_Green, 0xFFA8D5A2.toInt()),
    AMBER("Amber", R.style.Accent_Amber, 0xFFF6C85F.toInt()),
    ROSE("Rose", R.style.Accent_Rose, 0xFFF5B5CD.toInt());
}
