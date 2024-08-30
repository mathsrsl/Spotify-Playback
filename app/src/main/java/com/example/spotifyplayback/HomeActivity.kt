package com.example.spotifyplayback


import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.spotify.android.appremote.api.SpotifyAppRemote

/**
 * HomePage activity of the SpotifyPlayback app.
 * This activity is the home page of the app.
 * It displays the home page with the client_id input and submit button.
 * It also explains how to get the client_id.
 * If a client_id is already saved in the shared preferences, it redirects to the Player activity.
 * If Spotify is not installed, it shows a dialog to prompt the user to install it.
 */
class HomeActivity : AppCompatActivity() {
    // Init the client_id with an empty string
    private var clientId: String = ""

    // Init the SpotifyAppRemote instance
    private lateinit var spotifyAppRemote: SpotifyAppRemote

    /**
     * onCreate method of the HomePage activity.
     * override onCreate.
     * It checks if the client_id is already saved in the shared preferences.
     * If it is, it redirects to the Player activity.
     * If not, it displays the home page and initializes the settings preferences.
     * It also checks if Spotify is installed on the device.
     * @param savedInstanceState: Bundle? the saved instance state.
     * @return Unit: nothing is returned.
     * @see ToastUtil.showCustomToast
     * @see copyToClipboard
     * @see redirectToPlayerPage
     * @see checkClientID
     * @see initAppSettings
     * @see showInstallSpotifyDialog
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the client_id is already saved in the shared preferences
        val sharedPref = getSharedPreferences("spotify", MODE_PRIVATE)
        val clientId = sharedPref.getString("client_id", null)

        if (clientId != null) {
            // If a client_id is already saved, check if Spotify is installed and redirect to the
            // Player activity if it is.
            if (!SpotifyAppRemote.isSpotifyInstalled(this)) {
                // Use false to make the dialog not cancelable: close the app if Spotify is not
                // installed (because the client_id is already saved)
                showInstallSpotifyDialog(false)
            } else {
                // Redirect to the Player activity
                redirectToPlayerPage()
            }
        } else {
            // If no client_id is saved, display the home page
            setContentView(R.layout.layout_homeactivity)

            // Initialize the settings preferences by default
            initAppSettings()

            // Get the elements from the layout
            // Find the copy buttons, client_id input, and submit button
            val copyButtonStep1 = findViewById<ImageButton>(R.id.copy_button_step1)
            val copyButtonStep2 = findViewById<ImageButton>(R.id.copy_button_step2)
            val clientIdInput = findViewById<EditText>(R.id.client_id_input)
            val submitButton = findViewById<Button>(R.id.submit_button)

            // Define the theme color of the copy buttons based on the system theme
            val theme = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (theme == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                // Dark theme
                copyButtonStep1.setImageResource(R.drawable.ic_copy_button_dark)
                copyButtonStep2.setImageResource(R.drawable.ic_copy_button_dark)
            } else {
                // Light theme
                copyButtonStep1.setImageResource(R.drawable.ic_copy_button_light)
                copyButtonStep2.setImageResource(R.drawable.ic_copy_button_light)
            }

            // Show a dialog if Spotify is not installed
            if (!SpotifyAppRemote.isSpotifyInstalled(this)) {
                showInstallSpotifyDialog()
            }

            /* ### Listeners ### */

            // Add a click listener to the copyButtonStep1 button
            copyButtonStep1.setOnClickListener {
                // Copy the text to the clipboard (redirect URI)
                copyToClipboard("com.example.spotifyplayback://callback")
            }

            // Add a click listener to the copyButtonStep2 button
            copyButtonStep2.setOnClickListener {
                // Copy the text to the clipboard (android package name)
                copyToClipboard("com.example.spotifyplayback")
            }

            // Add an editor action listener to the clientIdInput EditText
            clientIdInput.setOnEditorActionListener { _, _, _ ->
                // Simulate a click on the submitButton button of the keyboard
                submitButton.performClick()
                true
            }

            //  Add a click listener to the submitButton button
            submitButton.setOnClickListener {
                // Get the text from the clientIdInput EditText
                val input = clientIdInput.text.toString()

                // Check if the input is empty
                if (input.isEmpty()) {
                    // Return a custom toast message
                    ToastUtil.showCustomToast(this, "Enter a client ID")
                    return@setOnClickListener
                } else {
                    // Check if Spotify is installed, if not, show a dialog and return
                    if (!SpotifyAppRemote.isSpotifyInstalled(this)) {
                        showInstallSpotifyDialog()
                        return@setOnClickListener
                    } else {
                        // Check if the client_id is valid (32 characters, alphanumeric, lowercase)
                        if (checkClientID(input)) {
                            // Save the client_id in the activity variable
                            this@HomeActivity.clientId = input

                            // Save the client_id in the shared preferences
                            with(sharedPref.edit()) {
                                putString("client_id", input)
                                apply()
                            }

                            // Redirect to the Player activity for Spotify connection
                            redirectToPlayerPage()
                        } else {
                            // If the client_id is invalid, show a custom toast message
                            ToastUtil.showCustomToast(this, "Invalid client ID")
                            return@setOnClickListener
                        }
                    }
                }
            }
        }
    }

    /**
     * onStop method of the HomePage activity.
     * override onStop.
     * It disconnects from Spotify when the activity is stopped.
     * @see disconnectFromSpotify
     */
    override fun onStop() {
        super.onStop()

        disconnectFromSpotify()
    }

    /**
     * disconnectFromSpotify method of the HomePage activity.
     * It disconnects from the Spotify app remote.
     * If the Spotify app remote is initialized, it disconnects from it.
     * @see SpotifyAppRemote.disconnect
     */
    private fun disconnectFromSpotify() {
        // Check if the Spotify app remote is initialized
        if (::spotifyAppRemote.isInitialized) {
            SpotifyAppRemote.disconnect(spotifyAppRemote)
        }
    }

    /**
     * initAppSettings method of the HomePage activity.
     * It initializes the settings preferences with default values.
     */
    private fun initAppSettings() {
        // Initialize the settings preferences with default values
        // Use the "settings" shared preferences file
        getSharedPreferences("settings", MODE_PRIVATE).edit().apply {
            putBoolean("full_screen", true)
            putInt("theme", 0)
            putBoolean("hide_controls", true)
            putInt("display_time", 10)
            putBoolean("blur_background", true)
            putInt("blur_value", 15)
            putInt("darken_value", 60)
            putBoolean("vertical_swipe", true)
            putBoolean("horizontal_swipe", true)
            putBoolean("double_tap", true)
            apply()
        }
    }

    /**
     * checkClientID method of the HomePage activity.
     * It checks if a client_id is valid.
     * A client_id is valid if it is 32 characters long and contains only lowercase alphanumeric
     * characters.
     * @param clientId: String the client_id to check.
     * @return Boolean: true if the client_id is valid, false otherwise.
     */
    private fun checkClientID(clientId: String): Boolean {
        // Check if the client_id is 32 characters long
        if (clientId.length != 32) {
            return false
        }

        // Check if the client_id contains only lowercase characters and no spaces
        for (char in clientId) {
            if (!char.isLetterOrDigit() || char.isUpperCase() || char == ' ') {
                return false
            }
        }

        // Everything is valid, return true
        return true
    }

    /**
     * redirectToPlayerPage method of the HomePage activity.
     * It redirects to the Player activity.
     * It creates an intent to start the Player activity and finishes the current activity.
     */
    private fun redirectToPlayerPage() {
        // Load the intent to start the Player activity
        val intent = Intent(this, PlayerActivity::class.java)
        startActivity(intent)
        finish() // Finish the current activity for a clean navigation
    }

    /**
     * copyToClipboard method of the HomePage activity.
     * It copies a text to the clipboard.
     * It creates a new ClipData object with the text and sets it as the primary clip.
     * It also shows a custom toast message to confirm the copy.
     * @param text: String the text to copy to the clipboard.
     * @see ToastUtil.showCustomToast
     */
    private fun copyToClipboard(text: String) {
        // Get the clipboard manager
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        // Create a new ClipData object with the text
        val clip = ClipData.newPlainText("text", text)
        // Set the clip as the primary clip
        clipboard.setPrimaryClip(clip)

        // Show a custom toast message to confirm the copy
        ToastUtil.showCustomToast(this, "Copied to clipboard")
    }

    /**
     * showInstallSpotifyDialog method of the HomePage activity.
     * It displays a dialog to prompt the user to install Spotify.
     * The dialog has a message, an install button, and a cancel button.
     * The install button opens the Spotify app in the Play Store.
     * The cancel button closes the dialog.
     * @param cancelable: Boolean whether the dialog is cancelable or not.
     * @see DialogUtil.displayCustomDialog
     * @see installSpotifyFromPlayStore
     */
    private fun showInstallSpotifyDialog(cancelable: Boolean = true) {
        DialogUtil.displayCustomDialog(
            context = this,
            message = "Spotify not found. You need to install the Spotify app to use this app.",
            positiveButtonText = "Install",
            negativeButtonText = "Cancel",
            onPositiveClick = { installSpotifyFromPlayStore() },
            onNegativeClick = { if (!cancelable) finish() }, // Close the app if not cancelable
            isCancelable = cancelable
        )
    }

    /**
     * installSpotifyFromPlayStore method of the HomePage activity.
     * It opens the Spotify app in the Play Store.
     * It creates a Play Store URI with the Spotify package name and referrer.
     * It tries to open the Play Store app with the URI.
     * If it fails, it opens the Play Store website with the URI.
     */
    private fun installSpotifyFromPlayStore() {
        // Define the package name of the Spotify app and the referrer
        val branchLink =
            Uri.encode("https://spotify.link/content_linking?~campaign=${packageName}")
        val appPackageName = "com.spotify.music"
        val referrer = "_branch_link=${branchLink}"

        // Try to open the Play Store app with the URI
        try {
            // Create the URI with the package name and referrer
            val uri = Uri.parse("market://details")
                .buildUpon()
                .appendQueryParameter("id", appPackageName)
                .appendQueryParameter("referrer", referrer)
                .build()

            // Start the Play Store activity with the URI
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (ignored: ActivityNotFoundException) {
            // If the Play Store app is not found, open the Play Store website
            // Create the URI with the package name and referrer
            val uri = Uri.parse("https://play.google.com/store/apps/details")
                .buildUpon()
                .appendQueryParameter("id", appPackageName)
                .appendQueryParameter("referrer", referrer)
                .build()

            // Open the Play Store website with the URI
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}

