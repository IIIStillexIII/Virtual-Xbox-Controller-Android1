package com.virtualcontroller.android.input

import kotlin.math.hypot

// Per-stick calibration applied to raw SC2026 Int16 values (-32768..32767)
// before they go to uinput / GamepadMapper.
data class StickCalibration(
    val centerX: Int = 0,
    val centerY: Int = 0,
    val deadzonePercent: Int = 8,   // 0..100 — radial deadzone as % of full range
    val invertY: Boolean = false,
    val invertX: Boolean = false,
    val sensitivity: Float = 1.0f
) {
    // Returns calibrated (x, y) in the same Int16 range, with deadzone applied radially.
    fun apply(rawX: Int, rawY: Int): Pair<Int, Int> {
        // 1. Subtract center offset
        var x = rawX - centerX
        var y = rawY - centerY

        // 2. Optional axis inversion.
        if (invertX) x = -x
        if (invertY) y = -y

        // 3. Radial deadzone — if magnitude < deadzone, output zero;
        //    otherwise rescale so the deadzone edge becomes the new zero,
        //    keeping max deflection intact.
        val percent = deadzonePercent.coerceIn(0, 100)
        if (percent == 100) return 0 to 0

        val deadzone = 32767.0 * percent / 100.0
        if (deadzone > 0.0) {
            // Convert before multiplication: Int x*x can overflow for offset or
            // malformed calibration values before the result reaches sqrt().
            val magnitude = hypot(x.toDouble(), y.toDouble())
            if (magnitude <= deadzone || magnitude == 0.0) {
                return 0 to 0
            }

            val scale = (magnitude - deadzone) / (32767.0 - deadzone) * 32767.0 / magnitude
            x = (x * scale).toInt()
            y = (y * scale).toInt()
        }

        // Sensitivity is applied after deadzone remapping so the deadzone remains
        // stable. Clamp the multiplier and final axes to the virtual stick range.
        val gain = sensitivity.coerceIn(0.2f, 3.0f)
        x = (x * gain).toInt().coerceIn(-32767, 32767)
        y = (y * gain).toInt().coerceIn(-32767, 32767)
        return x to y
    }

    companion object {
        val DEFAULT = StickCalibration()
    }
}
