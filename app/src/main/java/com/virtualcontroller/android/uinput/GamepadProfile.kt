package com.virtualcontroller.android.uinput

enum class GamepadProfile(
    val id: Int,
    val displayName: String,
    val vid: Int,
    val pid: Int,
    val isMouseMode: Boolean = false
) {
    // Keep IDs 0 and 3 so existing saved settings continue to select the same profiles.
    XBOX     (0, "xbox",     0x045E, 0x0B12),
    DUALSENSE(3, "dualsense", 0x054C, 0x0CE6),

    // Internal legacy mode retained for compatibility with existing preferences.
    // It is no longer exposed in the profile picker.
    MOUSE    (4, "Desktop (mouse + keyboard)", 0x046D, 0xC077, isMouseMode = true);

    companion object {
        fun fromId(id: Int): GamepadProfile =
            values().firstOrNull { it.id == id } ?: XBOX
    }
}
