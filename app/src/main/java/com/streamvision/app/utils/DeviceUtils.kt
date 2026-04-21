package com.streamvision.app.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

object DeviceUtils {

    /**
     * Returns true if running on Android TV / Fire TV Stick
     */
    fun isTV(ctx: Context): Boolean {
        val uiModeManager = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Returns true if running on a tablet (screen >= 7 inch)
     */
    fun isTablet(ctx: Context): Boolean {
        return ctx.resources.configuration.smallestScreenWidthDp >= 600
    }

    /**
     * Grid columns based on device type
     * TV: 5 columns, Tablet: 4 columns, Phone: 3 columns
     */
    fun gridColumns(ctx: Context): Int = when {
        isTV(ctx)     -> 5
        isTablet(ctx) -> 4
        else          -> 3
    }

    /**
     * Category list width based on device
     */
    fun categoryWidth(ctx: Context): Int {
        val density = ctx.resources.displayMetrics.density
        return when {
            isTV(ctx)     -> (200 * density).toInt()
            isTablet(ctx) -> (180 * density).toInt()
            else          -> (155 * density).toInt()
        }
    }

    /**
     * Font scale multiplier for TV
     */
    fun fontScale(ctx: Context): Float = if (isTV(ctx)) 1.3f else 1.0f

    /**
     * Device name for logging
     */
    fun deviceType(ctx: Context): String = when {
        isTV(ctx)     -> "TV"
        isTablet(ctx) -> "Tablet"
        else          -> "Phone"
    }
}
