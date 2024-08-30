package com.example.spotifyplayback

import android.content.SharedPreferences

/**
 * SettingsManager class to manage the shared preferences of the app.
 * It contains methods to get and set the settings of the app.
 * @param sharedPreferences The shared preferences object
 */
class SettingsManager(private val sharedPreferences: SharedPreferences) {

    // Define the companion object with the shared preferences keys
    companion object {
        const val PREF_FULL_SCREEN = "full_screen"
        const val PREF_THEME = "theme"
        const val PREF_HIDE_CONTROLS = "hide_controls"
        const val PREF_DISPLAY_TIME = "display_time"
        const val PREF_BLUR_BACKGROUND = "blur_background"
        const val PREF_BLUR_VALUE = "blur_value"
        const val PREF_DARKEN_VALUE = "darken_value"
        const val PREF_HORIZONTAL_SWIPE = "horizontal_swipe"
        const val PREF_VERTICAL_SWIPE = "vertical_swipe"
        const val PREF_DOUBLE_TAP = "double_tap"
    }

    /* ##### Full screen settings ##### */

    /**
     * isFullScreen method of the SettingsManager class.
     * It returns the value of the full screen setting (true or false).
     * @return The value of the full screen setting
     */
    fun isFullScreen(): Boolean = sharedPreferences.getBoolean(PREF_FULL_SCREEN, false)

    /**
     * setFullScreen method of the SettingsManager class.
     * It sets the value of the full screen setting.
     * @param enabled The value of the full screen setting (true or false)
     */
    fun setFullScreen(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_FULL_SCREEN, enabled).apply()
    }

    /* ##### Theme settings ##### */

    /**
     * getTheme method of the SettingsManager class.
     * It returns the value of the theme setting (0, 1 or 2).
     * 0: Automatic, 1: Light, 2: Dark
     * @return The value of the theme setting
     */
    fun getTheme(): Int = sharedPreferences.getInt(PREF_THEME, 0)

    /**
     * setTheme method of the SettingsManager class.
     * It sets the value of the theme setting.
     * @param theme The value of the theme setting (0, 1 or 2)
     */
    fun setTheme(theme: Int) {
        sharedPreferences.edit().putInt(PREF_THEME, theme).apply()
    }

    /* ##### Hide controls settings ##### */

    /**
     * isHideControls method of the SettingsManager class.
     * It returns the value of the hide controls setting (true or false).
     * @return The value of the hide controls setting
     */
    fun isHideControls(): Boolean = sharedPreferences.getBoolean(PREF_HIDE_CONTROLS, false)

    /**
     * setHideControls method of the SettingsManager class.
     * It sets the value of the hide controls setting.
     * @param hidden The value of the hide controls setting (true or false)
     */
    fun setHideControls(hidden: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_HIDE_CONTROLS, hidden).apply()
    }

    /* ##### Display time settings ##### */

    /**
     * getDisplayTime method of the SettingsManager class.
     * It returns the value of the display time setting.
     * @return The value of the display time setting
     */
    fun getDisplayTime(): Int = sharedPreferences.getInt(PREF_DISPLAY_TIME, 10)

    /**
     * setDisplayTime method of the SettingsManager class.
     * It sets the value of the display time setting.
     * @param time The value of the display time setting
     */
    fun setDisplayTime(time: Int) {
        sharedPreferences.edit().putInt(PREF_DISPLAY_TIME, time).apply()
    }

    /* ##### Blur background settings ##### */

    /**
     * isBlurBackground method of the SettingsManager class.
     * It returns the value of the blur background setting (true or false).
     * @return The value of the blur background setting
     */
    fun isBlurBackground(): Boolean = sharedPreferences.getBoolean(PREF_BLUR_BACKGROUND, false)

    /**
     * setBlurBackground method of the SettingsManager class.
     * It sets the value of the blur background setting.
     * @param enabled The value of the blur background setting (true or false)
     */
    fun setBlurBackground(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_BLUR_BACKGROUND, enabled).apply()
    }

    /* ##### Blur value settings ##### */

    /**
     * getBlurValue method of the SettingsManager class.
     * It returns the value of the blur value setting.
     * @return The value of the blur value setting
     */
    fun getBlurValue(): Int = sharedPreferences.getInt(PREF_BLUR_VALUE, 15)

    /**
     * setBlurValue method of the SettingsManager class.
     * It sets the value of the blur value setting.
     * @param value The value of the blur value setting
     */
    fun setBlurValue(value: Int) {
        sharedPreferences.edit().putInt(PREF_BLUR_VALUE, value).apply()
    }

    /* ##### Darken value settings ##### */

    /**
     * getDarkenValue method of the SettingsManager class.
     * It returns the value of the darken value setting.
     * @return The value of the darken value setting
     */
    fun getDarkenValue(): Int = sharedPreferences.getInt(PREF_DARKEN_VALUE, 60)

    /**
     * setDarkenValue method of the SettingsManager class.
     * It sets the value of the darken value setting.
     * @param value The value of the darken value setting
     */
    fun setDarkenValue(value: Int) {
        sharedPreferences.edit().putInt(PREF_DARKEN_VALUE, value).apply()
    }

    /* ##### Horizontal swipe settings ##### */

    /**
     * isHorizontalSwipeEnabled method of the SettingsManager class.
     * It returns the value of the horizontal swipe setting (true or false).
     * @return The value of the horizontal swipe setting
     */
    fun isHorizontalSwipeEnabled():
            Boolean = sharedPreferences.getBoolean(PREF_HORIZONTAL_SWIPE, true)

    /**
     * setHorizontalSwipeEnabled method of the SettingsManager class.
     * It sets the value of the horizontal swipe setting.
     * @param enabled The value of the horizontal swipe setting (true or false)
     */
    fun setHorizontalSwipeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_HORIZONTAL_SWIPE, enabled).apply()
    }

    /* ##### Vertical swipe settings ##### */

    /**
     * isVerticalSwipeEnabled method of the SettingsManager class.
     * It returns the value of the vertical swipe setting (true or false).
     * @return The value of the vertical swipe setting
     */
    fun isVerticalSwipeEnabled(): Boolean = sharedPreferences.getBoolean(PREF_VERTICAL_SWIPE, true)

    /**
     * setVerticalSwipeEnabled method of the SettingsManager class.
     * It sets the value of the vertical swipe setting.
     * @param enabled The value of the vertical swipe setting (true or false)
     */
    fun setVerticalSwipeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_VERTICAL_SWIPE, enabled).apply()
    }

    /* ##### Double tap to like settings ##### */

    /**
     * isDoubleTapEnabled method of the SettingsManager class.
     * It returns the value of the double tap setting (true or false).
     * @return The value of the double tap setting
     */
    fun isDoubleTapEnabled(): Boolean = sharedPreferences.getBoolean(PREF_DOUBLE_TAP, true)

    /**
     * setDoubleTapEnabled method of the SettingsManager class.
     * It sets the value of the double tap setting.
     * @param enabled The value of the double tap setting (true or false)
     */
    fun setDoubleTapEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_DOUBLE_TAP, enabled).apply()
    }
}
