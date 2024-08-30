package com.example.spotifyplayback


import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.util.Locale

/**
 * Settings activity.
 * This activity is used to display and modify the settings of the application.
 */
class SettingsActivity: AppCompatActivity() {

    // Declare settings manager and cache manager
    private lateinit var cacheManager: CacheManager
    private lateinit var settingsManager: SettingsManager

    // Declare UI elements
    private lateinit var fullScreen: SwitchCompat
    private lateinit var themeSpinner: Spinner
    private lateinit var hideControls: SwitchCompat
    private lateinit var displayTimeView: TextView
    private lateinit var displayTimeSeekBar: SeekBar
    private lateinit var blurBackground: SwitchCompat
    private lateinit var blurValueView: TextView
    private lateinit var blurValueSeekBar: SeekBar
    private lateinit var darkenValueView: TextView
    private lateinit var darkenValueSeekBar: SeekBar
    private lateinit var clearCache: Button
    private lateinit var cacheSizeView: TextView
    private lateinit var disconnectButton: Button
    private lateinit var clientIdView: TextView
    private lateinit var horizontalSwipe: SwitchCompat
    private lateinit var verticalSwipe: SwitchCompat
    private lateinit var doubleTap: SwitchCompat

    /**
     * OnCreate method of the activity.
     * override onCreate.
     * Initialize the settings activity with the settings shared preferences.
     * It starts the listeners and sets the values.
     * @param savedInstanceState: Bundle?
     * @see SettingsManager
     * @see CacheManager
     * @see initUIElements
     * @see setupListeners
     * @see populateUIWithSettings
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        /* Initialize the settings manager with the settings shared preferences */
        val settingsSharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        settingsManager = SettingsManager(settingsSharedPref)

        /* Set the theme based on the preference */
        setThemeBasedOnPreference()

        /* Load the layout */
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_settingsactivity)

        /* Add a back button listener: go back to the player activity */
        onBackPressedDispatcher.addCallback(
            this, object : OnBackPressedCallback(true
        ) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@SettingsActivity, PlayerActivity::class.java)
                startActivity(intent)
                finish()
            }
        })

        /* Initialize the cache manager */
        cacheManager = CacheManager(this)

        /* Get all the interactive elements */
        initUIElements()

        /* Set the values */
        populateUIWithSettings()

        /* Listener */
        setupListeners()
    }

    /**
     * onResume method of the activity.
     * override onResume.
     * Update the cache size when the activity is resumed.
     */
    override fun onResume() {
        super.onResume()
        // Update the cache size when the activity is resumed
        displayCacheSize()
    }

    /**
     * setThemeBasedOnPreference method of the activity.
     * Set the theme of the activity based on the preference.
     * The theme is set to light or dark based on the preference.
     * @see SettingsManager.getTheme
     */
    private fun setThemeBasedOnPreference() {
        /* Set the theme based on the preference */
        // for the 0 value, the default theme is used (no change)
        val themePreference = settingsManager.getTheme()
        when (themePreference) {
            1 -> setTheme(R.style.AppTheme_Light)
            2 -> setTheme(R.style.AppTheme_Dark)
        }
    }

    /**
     * initUIElements method of the activity.
     * Initialize all the UI elements of the activity.
     */
    private fun initUIElements() {
        fullScreen = findViewById(R.id.switch_full_screen)
        themeSpinner = findViewById(R.id.spinner_theme)
        hideControls = findViewById(R.id.switch_hide_controls)
        displayTimeView = findViewById(R.id.textView_time_display)
        displayTimeSeekBar = findViewById(R.id.time_seekBar)
        blurBackground = findViewById(R.id.switch_blur_background)
        blurValueView = findViewById(R.id.textView_blur_display)
        blurValueSeekBar = findViewById(R.id.blur_seekBar)
        darkenValueView = findViewById(R.id.textView_darken_display)
        darkenValueSeekBar = findViewById(R.id.darken_seekBar)
        clearCache = findViewById(R.id.button_clear_cache)
        cacheSizeView = findViewById(R.id.textView_cache_size)
        disconnectButton = findViewById(R.id.button_disconnect)
        clientIdView = findViewById(R.id.textView_client_id)
        horizontalSwipe = findViewById(R.id.switch_horizontal_swipe)
        verticalSwipe = findViewById(R.id.switch_vertical_swipe)
        doubleTap = findViewById(R.id.switch_double_tap_like)
    }

    /**
     * setupListeners method of the activity.
     * Set up all the listeners for the interactive elements of the activity.
     * @see settingsManager
     */
    private fun setupListeners() {
        // full screen
        fullScreen.setOnClickListener {
            // update the full screen value and display the new value
            settingsManager.setFullScreen(!settingsManager.isFullScreen())
        }

        // theme
        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                // Reload the activity if the theme is changed and save the new theme
                if (position != settingsManager.getTheme()) {
                    settingsManager.setTheme(position)
                    recreate()
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }

        // hide controls
        hideControls.setOnClickListener {
            // update the hide controls value and display the new value
            settingsManager.setHideControls(!settingsManager.isHideControls())
            displayHideControls()
        }

        // display time
        displayTimeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Display the new value of the seek bar
                displayTime()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Save the new display time value
                settingsManager.setDisplayTime(seekBar?.progress ?: 10)
            }
        })

        // blur background
        blurBackground.setOnClickListener {
            // update the blur background value and display the new value
            settingsManager.setBlurBackground(!settingsManager.isBlurBackground())
            displayBlurBackground()
        }

        blurValueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Display the new value of the seek bar
                displayBlurValue()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Save the new blur value
                settingsManager.setBlurValue(seekBar?.progress ?: 15)
            }
        })

        // darken background
        darkenValueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Display the new value of the seek bar
                displayDarkenValue()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Save the new darken value
                settingsManager.setDarkenValue(seekBar?.progress ?: 60)
            }
        })

        // horizontal swipe
        horizontalSwipe.setOnClickListener {
            // update the horizontal swipe value
            settingsManager.setHorizontalSwipeEnabled(!settingsManager.isVerticalSwipeEnabled())
        }

        // vertical swipe
        verticalSwipe.setOnClickListener {
            // update the vertical swipe value
            settingsManager.setVerticalSwipeEnabled(!settingsManager.isVerticalSwipeEnabled())
        }

        // double tap to like
        doubleTap.setOnClickListener {
            // update the double tap value
            settingsManager.setDoubleTapEnabled(!settingsManager.isDoubleTapEnabled())
        }

        // clear cache
        clearCache.setOnClickListener {
            // clear the cache and display the new cache size
            cacheManager.clearCache()
            ToastUtil.showCustomToast(this, "Cache cleared")
            displayCacheSize()
        }

        // disconnect
        disconnectButton.setOnClickListener {
            // display a dialog to confirm the disconnection
            displayDisconnectDialog()
        }
    }

    /**
     * populateUIWithSettings method of the activity.
     * Populate the UI elements with the settings values (from the shared preferences).
     */
    private fun populateUIWithSettings() {
        displayFullScreen()
        displayTheme()
        displayTimeSeekBar() // display the progress of the seek bar before the value
        displayTime()
        displayHideControls()
        displayBlurBackground()
        displayDarkenSeekBar() // display the progress of the seek bar before the value
        displayDarkenValue()
        displayCacheSize()
        displayBlurSeekBar() // display the progress of the seek bar before the value
        displayBlurValue()
        displayHorizontalSwipe()
        displayVerticalSwipe()
        displayDoubleTap()
        displayClientId()
    }


    /* #################################### Display functions ################################### */

    /**
     * displayFullScreen method of the activity.
     * Set the full screen switch to the value of the full screen setting.
     * @see settingsManager
     */
    private fun displayFullScreen() {
        fullScreen.isChecked = settingsManager.isFullScreen()
    }

    /**
     * displayTheme method of the activity.
     * Set the theme spinner to the value of the theme setting.
     * @see settingsManager
     */
    private fun displayTheme() {
        themeSpinner.setSelection(settingsManager.getTheme())
    }

    /**
     * displayHideControls method of the activity.
     * Set the hide controls switch to the value of the hide controls setting.
     * Enable or disable the display time seek bar and view based on the hide controls setting.
     * @see settingsManager
     */
    private fun displayHideControls() {
        val hide = settingsManager.isHideControls()
        hideControls.isChecked = hide // set the switch to the value of the setting

        // enable or disable the seek bar and view based on the setting
        if (hide) {
            displayTimeSeekBar.isEnabled = true
            displayTimeView.isEnabled = true
        } else {
            displayTimeSeekBar.isEnabled = false
            displayTimeView.isEnabled = false
        }
    }

    /**
     * displayTime method of the activity.
     * Display the value of the display time seek bar in the display time view.
     * The value is displayed in seconds if it is less than 60, otherwise in minutes and seconds.
     * @see displayTimeSeekBar
     */
    private fun displayTime() {
        val time = displayTimeSeekBar.progress

        // Calculate the time in minutes and seconds if the time is greater than 60 and display it
        if (time < 60) {
            displayTimeView.text = String.format(Locale.getDefault(), "%d sec", time)
        } else {
            val min = time / 60
            val sec = time % 60
            displayTimeView.text =
                String.format(Locale.getDefault(), "%d min %d sec", min, sec)
        }
    }

    /**
     * displayTimeSeekBar method of the activity.
     * Set the progress of the display time seek bar to the value of the display time setting.
     * @see settingsManager
     */
    private fun displayTimeSeekBar() {
        displayTimeSeekBar.progress = settingsManager.getDisplayTime()
    }

    /**
     * displayBlurBackground method of the activity.
     * Set the blur background switch to the value of the blur background setting.
     * Enable or disable the blur value seek bar and view based on the blur background setting.
     * @see settingsManager
     */
    private fun displayBlurBackground() {
        val blur = settingsManager.isBlurBackground()
        blurBackground.isChecked = blur // set the switch to the value of the setting

        // enable or disable the seek bar and view based on the setting
        if (blur) {
            blurValueSeekBar.isEnabled = true
            blurValueView.isEnabled = true
        } else {
            blurValueSeekBar.isEnabled = false
            blurValueView.isEnabled = false
        }
    }

    /**
     * displayBlurValue method of the activity.
     * Display the value of the blur value seek bar in the blur value view.
     * @see blurValueSeekBar
     */
    private fun displayBlurValue() {
        blurValueView.text = blurValueSeekBar.progress.toString()
    }

    /**
     * displayDarkenValue method of the activity.
     * Display the value of the darken value seek bar in the darken value view.
     * @see darkenValueSeekBar
     */
    private fun displayDarkenValue() {
        val value = darkenValueSeekBar.progress.toString()
        darkenValueView.text = String.format(Locale.getDefault(), "%s%%", value)
    }

    /**
     * displayDarkenSeekBar method of the activity.
     * Set the progress of the darken value seek bar to the value of the darken value setting.
     * @see settingsManager
     */
    private fun displayDarkenSeekBar() {
        darkenValueSeekBar.progress = settingsManager.getDarkenValue()
    }

    /**
     * displayBlurSeekBar method of the activity.
     * Set the progress of the blur value seek bar to the value of the blur value setting.
     * @see settingsManager
     */
    private fun displayBlurSeekBar() {
        blurValueSeekBar.progress = settingsManager.getBlurValue()
    }

    /**
     * displayHorizontalSwipe method of the activity.
     * Set the horizontal swipe switch to the value of the horizontal swipe setting.
     * @see settingsManager
     */
    private fun displayHorizontalSwipe() {
        horizontalSwipe.isChecked = settingsManager.isHorizontalSwipeEnabled()
    }

    /**
     * displayVerticalSwipe method of the activity.
     * Set the vertical swipe switch to the value of the vertical swipe setting.
     * @see settingsManager
     */
    private fun displayVerticalSwipe() {
        verticalSwipe.isChecked = settingsManager.isVerticalSwipeEnabled()
    }

    /**
     * displayDoubleTap method of the activity.
     * Set the double tap switch to the value of the double tap setting.
     * @see settingsManager
     */
    private fun displayDoubleTap() {
        doubleTap.isChecked = settingsManager.isDoubleTapEnabled()
    }

    /**
     * displayCacheSize method of the activity.
     * Display the size of the cache in the cache size view.
     * @see cacheManager
     */
    private fun displayCacheSize() {
        cacheSizeView.text = cacheManager.getCacheSize()
    }

    /**
     * displayClientId method of the activity.
     * Display the client ID in the client ID view.
     */
    private fun displayClientId() {
        val spotifySharedPref = getSharedPreferences("spotify", MODE_PRIVATE)
        val clientId = spotifySharedPref.getString("client_id", null)
        // display the client ID in the view
        clientIdView.text = String.format(Locale.getDefault(), "Client ID: %s", clientId)
    }


    /* ################################## Disconnect functions ################################## */

    /**
     * displayDisconnectDialog method of the activity.
     * Display a dialog to confirm the disconnection.
     * @see DialogUtil
     * @see disconnect
     */
    private fun displayDisconnectDialog() {
        DialogUtil.displayCustomDialog(
            context = this,
            message = "Are you sure you want to disconnect?",
            positiveButtonText = "Yes",
            negativeButtonText = "No",
            onPositiveClick = { disconnect() },
            onNegativeClick = {}
        )
    }

    /**
     * disconnect method of the activity.
     * Disconnect the user by clearing the cache and the shared preferences.
     * Start the home page activity.
     * @see cacheManager
     */
    private fun disconnect() {
        /* Clear the cache */
        cacheManager.clearCache()

        /* Clear the shared preferences (to disconnect the user) */
        val spotifySharedPref = getSharedPreferences("spotify", MODE_PRIVATE)

        with(spotifySharedPref.edit()) {
            putString("client_id", null)
            putString("access_token", null)
            putString("refresh_token", null)
            putLong("expiration_time", 0)
            apply()
        }

        /* Start the home page activity */
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
