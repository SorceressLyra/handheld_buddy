package dev.handheld.buddy

enum class PermissionRequirement { BUTTON_ACCESS, BRIGHTNESS }

fun requiredPermission(buttonAccess: Boolean, brightnessWrite: Boolean, needsBrightness: Boolean): PermissionRequirement? = when {
    !buttonAccess -> PermissionRequirement.BUTTON_ACCESS
    needsBrightness && !brightnessWrite -> PermissionRequirement.BRIGHTNESS
    else -> null
}
