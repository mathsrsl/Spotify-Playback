package com.example.spotifyplayback


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets.Type
import android.view.WindowInsetsController
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.properties.Delegates


/* Swipe constants */
// The minimum distance to swipe
private const val MIN_SWIPE_DISTANCE = 150
// Padding to avoid swiping from the top or bottom
private const val MAX_SWIPE_OFF_SCREEN_VERTICAL = 100
// Padding to avoid swiping from the left or right
private const val MAX_SWIPE_OFF_SCREEN_HORIZONTAL = 70

/* Settings constants (values loaded from the shared preferences in the onCreate method) */
private var IS_FULL_SCREEN by Delegates.notNull<Boolean>()
private var HIDE_CONTROLS by Delegates.notNull<Boolean>()
private var DISPLAY_TIME by Delegates.notNull<Int>()
private var BLUR_BACKGROUND by Delegates.notNull<Boolean>()
private var BLUR_VALUE by Delegates.notNull<Int>()
private var DARKEN_VALUE by Delegates.notNull<Int>()
private var VERTICAL_SWIPE by Delegates.notNull<Boolean>()
private var HORIZONTAL_SWIPE by Delegates.notNull<Boolean>()
private var DOUBLE_TAP by Delegates.notNull<Boolean>()

/* Authentication constants */
private const val REDIRECT_URI = "com.example.spotifyplayback://callback"
private lateinit var CLIENT_ID: String

/**
 * Data class to store the information about the current track playing on Spotify.
 * This data class is used to store the information about the track and display it on the UI.
 */
data class TrackInfo(
    val id: String,
    val isSameTrack: Boolean,
    val isCurrentlyPlaying: Boolean,
    val title: String,
    val artistsList: List<Pair<String, String>>,
    val durationMs: Long, // in milliseconds
    val currentProgress: Long, // in milliseconds
    val imageUrl: String?,
    val isSameImage: Boolean,
    val isShuffle: Boolean,
    val isSmartShuffle: Boolean,
    val isShuffleAllowed: Boolean,
    val volume: Int?, // percentage
    val isSupportsVolume: Boolean?,
    val deviceName: String?
)


/**
 * Player activity is the main activity of the application.
 * It displays the current track playing on Spotify and allows the user to control the playback.
 * Here, there are all the methods to interact with the Spotify API and control the playback.
 * @author Mathieu R.
 */
class PlayerActivity : ComponentActivity(),
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    /* Authentication attributes */
    private lateinit var spotifyAppRemote: SpotifyAppRemote // Spotify App Remote instance
    private lateinit var authLauncher: ActivityResultLauncher<Intent> // fot the authentication
    private var isAuthInError = false // flag to check if the authentication is in error
    private lateinit var accessToken: String
    private lateinit var expirationTime: String

    /* Backends attributes */
    private lateinit var sharedPref: SharedPreferences
    private val fetchInterval = 1000L // Interval to fetch the track information (1s)
    private lateinit var fetchHandler: Handler
    private var countdownTimer: CountDownTimer? = null // Controls display timer
    private var countdownVolumeTimer: CountDownTimer? = null // Volume bar display timer
    private var volumeTimerSec: Int = 3 // Duration of the volume bar display (3s)

    /* Interface attributes */
    private lateinit var displayMetrics: android.util.DisplayMetrics
    private var screenHeight: Int = 0
    private var screenWidth: Int = 0
    private var noPlaybackTextView: TextView? = null // TextView to display a message
    private var loadProgressBar: ProgressBar? = null // ProgressBar to display the loading message
    private var noInternerDialog: AlertDialog? = null // Dialog to display the no internet message
    private var connectionErrorDialog: AlertDialog? = null
    private var noTrack = false
    private var progressBarAnimator: ObjectAnimator? = null
    private var isMessageVisible = true
    private var isFirstCall = true
    private var isFirstArtistCall = true
    private var isInternet = true
    // Boolean to check if the user interacted with the controls and avoid glitches :
    private var isBarTouched = false
    private var isControlsVisible = true
    private var isImageSwiped = false
    private var isVolumeChanged = false
    private var isPlaybackChanged = false
    private var isFadeAnimationActive = false
    private var isLikeRequestSent = false
    private var isLikeTaskRunning = false

    /* Track attributes */
    private var trackId: String? = null
    private var trackImage: String? = null
    private var totalDuration: Long = 0

    /* Playback attributes */
    private var isPlaying = false
    private var isShuffle = false
    private var isSmartShuffle = false
    private var isShuffleAllowed = false
    private var isLiked = false
    private var percentVolume = 100
    private var isSupportsVolume = false
    private var deviceName: String? = null
    // Previous track information to avoid unnecessary updates
    private var previousTrackId = ""
    private var previousTrackImage = ""
    private var previousArtistImage = ""

    /* Widgets attributes */
    private lateinit var progressBar: SeekBar
    private lateinit var playPauseButton: ImageButton
    private lateinit var skipPreviousButton: ImageButton
    private lateinit var skipNextButton: ImageButton
    private lateinit var imageMusic: ImageView
    private lateinit var imageBackArtist: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var shuffleButton: ImageButton
    private lateinit var likeButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var darkenView: View // The view to darken the background (editable by user)
    private lateinit var progressTimeView: TextView // Current time of the track
    private lateinit var totalTimeView: TextView // Total time of the track
    private lateinit var volumeBar: ProgressBar
    private lateinit var deviceView: TextView // Display the device name
    private lateinit var heartImage: ImageView // load the heart image for the like animation

    /* Swipe attributes */
    // Gesture detector to handle swipe and tap gestures
    private lateinit var mDetector: GestureDetector


    /* #################################### Activity methods #################################### */

    /**
     * OnCreate method of the activity.
     * override onCreate.
     * This method is called when the activity is created.
     * Initialize the UI elements and the attributes of the activity.
     * @param savedInstanceState The saved instance state of the activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout of the activity
        setContentView(R.layout.layout_playeractivity)

        // Get the settings from the shared preferences
        val settingsSharedPref = getSharedPreferences("settings", MODE_PRIVATE)

        IS_FULL_SCREEN = settingsSharedPref.getBoolean("full_screen", false)
        HIDE_CONTROLS = settingsSharedPref.getBoolean("hide_controls", false)
        DISPLAY_TIME = settingsSharedPref.getInt("display_time", 5)
        BLUR_BACKGROUND = settingsSharedPref.getBoolean("blur_background", false)
        BLUR_VALUE = settingsSharedPref.getInt("blur_value", 15)
        DARKEN_VALUE = settingsSharedPref.getInt("darken_value", 60)
        VERTICAL_SWIPE = settingsSharedPref.getBoolean("vertical_swipe", false)
        HORIZONTAL_SWIPE = settingsSharedPref.getBoolean("horizontal_swipe", false)
        DOUBLE_TAP = settingsSharedPref.getBoolean("double_tap", false)

        // Get the UI elements (buttons, image views, text views, etc.) from the layout
        this.progressBar = findViewById(R.id.progressBar)
        this.playPauseButton = findViewById(R.id.imagePlayPauseButton)
        this.skipPreviousButton = findViewById(R.id.imagePreviousButton)
        this.skipNextButton = findViewById(R.id.imageNextButton)
        this.imageMusic = findViewById(R.id.imageMusic)
        this.imageBackArtist = findViewById(R.id.imageBackArtist)
        this.trackTitle = findViewById(R.id.trackTitle)
        this.trackArtist = findViewById(R.id.trackArtist)
        this.shuffleButton = findViewById(R.id.imageShuffleButton)
        this.likeButton = findViewById(R.id.imageLikeButton)
        this.settingsButton = findViewById(R.id.settingsButton)
        this.darkenView = findViewById(R.id.darkenView)
        this.progressTimeView = findViewById(R.id.progressTime)
        this.totalTimeView = findViewById(R.id.totalTime)
        this.volumeBar = findViewById(R.id.volumeBar)
        this.deviceView = findViewById(R.id.deviceName)
        this.heartImage = findViewById(R.id.heart_image)

        // Define the alpha value of the darken view with the value from the shared preferences
        this.darkenView.alpha = DARKEN_VALUE / 100f

        // By default, the controls aren't visible and a message is displayed (initializing)
        showMessage(true, R.string.text_initializing, true)
        showVolume(false)

        // Load the authentication information from the shared preferences
        this.sharedPref = getSharedPreferences("spotify", MODE_PRIVATE)
        CLIENT_ID = sharedPref.getString("client_id", null) ?: ""
        this.accessToken = sharedPref.getString("access_token", null) ?: ""
        this.expirationTime = sharedPref.getLong("expiration_time", 0).toString()

        // Initialize the handler for the fetching task
        this.fetchHandler = Handler(Looper.getMainLooper())

        // Initialize the gesture detector to allow swipe and tap gestures
        this.mDetector = GestureDetector(this, this)
        this.mDetector.setOnDoubleTapListener(this)

        // Allow to the track title and artist to be displayed in the marquee mode
        this.trackTitle.isSelected = true
        this.trackArtist.isSelected = true

        // Init the auth launcher (for the Spotify authentication)
        initAuthLauncher()

        // Init the heart image to avoid a bad first animation
        this.heartImage.apply {
            alpha = 0f // Hide the heart image before
            visibility = View.VISIBLE
            (layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, 0, 0)
            postDelayed({ visibility = View.GONE; alpha = 1f}, 1)
        }

        // Setup the listeners for the controls
        setupListeners()
    }

    /**
     * OnResume method of the activity.
     * override onResume.
     * This method is called when the activity is resumed.
     * It starts the fetching task to get the track information periodically and reload the UI.
     * It also checks if the access token is expired and if the user is connected to the Internet.
     * If the access token is expired, it starts the Spotify authentication process.
     */
    override fun onResume() {
        super.onResume()

        // If the user allows the full screen mode, hide the status and navigation bars
        // with the right method according to the Android version (API 30 and more or less)
        if (IS_FULL_SCREEN) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use WindowInsetsController for API 30 and more

                // Hide the status and navigation bars
                window.insetsController?.let { controller ->
                    controller.hide(Type.statusBars() or Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                // Use the deprecated method for API 29 and less
                // Avoid the deprecated warning and crash
                @Suppress("DEPRECATION")

                // Hide the status and navigation bars
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
        }

        // Check if the access token and start the fetching task
        checkTokenAndStartFetchingTask()

        // Reload the screen size
        this.displayMetrics = resources.displayMetrics
        this.screenHeight = displayMetrics.heightPixels
        this.screenWidth = displayMetrics.widthPixels
    }

    /**
     * OnPause method of the activity.
     * override onPause.
     * This method is called when the activity is paused.
     * It stops the fetching task to get the track information periodically.
     */
    override fun onPause() {
        super.onPause()

        // Stop the fetching task to avoid unnecessary calls
        stopFetchingTask()

        /* Remove dialogs if they are displayed */
        // Remove the no internet dialog if it's displayed
        if (noInternerDialog != null) {
            noInternerDialog?.dismiss()
            noInternerDialog = null
        }

        // Remove the connection error dialog if it's displayed
        if (connectionErrorDialog != null) {
            connectionErrorDialog?.dismiss()
            connectionErrorDialog = null
        }
    }

    /**
     * OnDestroy method of the activity.
     * override onDestroy.
     * This method is called when the activity is destroyed.
     * It stops the fetching task to get the track information periodically and disconnects the
     * Spotify App Remote.
     */
    override fun onDestroy() {
        super.onDestroy()

        // Stop the fetching task
        stopFetchingTask()

        // Disconnect the Spotify App Remote if it's initialized
        if (::spotifyAppRemote.isInitialized) {
            SpotifyAppRemote.disconnect(spotifyAppRemote)
        }

        // Destroy the countdown timer
        resetControlsTimer()
        resetVolumeTimer()
    }

    /**
     * OnStop method of the activity.
     * override onStop.
     * This method is called when the activity is stopped.
     * It stops the fetching task to get the track information periodically and resets the controls
     * timer.
     */
    override fun onStop() {
        super.onStop()

        // Stop the fetching task to avoid unnecessary calls
        stopFetchingTask()

        // Reset the timer of the controls and the volume bar
        resetControlsTimer()
        resetVolumeTimer()
    }

    /**
     * onConfigurationChanged method of the activity.
     * override onConfigurationChanged.
     * This method is called when the configuration of the device changes.
     * For example, when the device is rotated, the configuration changes.
     * It reloads the image of the music and updates the screen size.
     * @param newConfig The new configuration of the device
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // If the orientation of the device changes
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
            || newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {

            // Reload the image of the music (avoid a bad proportion)
            if (this.trackId != null) {
                handleTrackImage(this.trackImage)
            }

            // Update the screen size
            displayMetrics = resources.displayMetrics
            screenHeight = displayMetrics.heightPixels
            screenWidth = displayMetrics.widthPixels
        }
    }

    /**
     * setupListeners method of the activity.
     * This method sets up the listeners for the controls of the activity.
     * It detects the click on the buttons and the seek bar and sends the appropriate requests.
     * @see onCreate
     */
    private fun setupListeners() {
        /* ### Listeners for the controls ### */

        // Detect the click on the play/pause button
        this.playPauseButton.setOnClickListener {
            sendPlaybackRequest(!this.isPlaying)
        }

        // Detect the click on the previous button
        this.skipPreviousButton.setOnClickListener {
            sendSkipRequest(false)
        }

        // Detect the click on the next button
        this.skipNextButton.setOnClickListener {
            sendSkipRequest(true)
        }

        // Detect the click on the shuffle button and verify if the shuffle is allowed
        this.shuffleButton.setOnClickListener {
            if (this.isShuffleAllowed) {
                sendShuffleRequest(!this.isShuffle)
            } else {
                // Display a message if the shuffle is not allowed
                ToastUtil.showCustomToast(
                    this,
                    "Shuffle is not available for this playlist"
                )
            }
        }

        // Detect the click on the like button and send the like request accordingly
        this.likeButton.setOnClickListener {
            if (this.trackId != null) {
                // Send the opposite of the current like status
                sendLikeRequest(this.trackId!!)

                // Change the like status (UI and data)
                handleLikeStatus(!this.isLiked)
                this.isLikeRequestSent = true
            }
        }

        // Detect the click on the settings button and start the settings activity
        this.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish() // Finish the current activity to force reload the activity with the new
            // settings
        }

        // Detect a change in the progress bar and send the seek request accordingly
        this.progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update the progress time view in real-time
                updateTimeProgress(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Display the thumb of the seek bar
                seekBar?.thumb = ResourcesCompat.getDrawable(
                    resources, R.drawable.seekbar_thumb, null)

                // stop the automatic refresh of the track information (fetching task)
                isBarTouched = true

                // Cancel the progress bar animator if it's running
                progressBarAnimator?.cancel()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Hide the thumb of the seek bar
                seekBar?.thumb = ResourcesCompat.getDrawable(
                    resources, R.drawable.transparent_thumb, null)

                // Send the seek request with the new progress to the Spotify API
                val progressMs = (progressBar.progress * totalDuration) / 1000
                sendSeekToPosition(progressMs)
            }
        })
    }


    /* #################################### Gesture methods ##################################### */

    /**
     * OnTouchEvent method of the activity.
     * override onTouchEvent.
     * This method is called when the user interacts with the screen.
     * It handles the touch events and the gestures of the user.
     * @param event The motion event of the user
     * @return Boolean : True if the event is handled, false otherwise
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle the touch event and the gestures
        return if (mDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    /**
     * OnDown method of the activity.
     * override onDown.
     * This method is invoked when the user touches the screen.
     * It returns true to indicate that the gesture detector should continue to detect the gesture.
     * @param event The motion event of the user
     * @return Boolean : True if the event is handled, false otherwise
     */
    override fun onDown(event: MotionEvent): Boolean { return true }

    /**
     * OnFling method of the activity.
     * This method is invoked when the user performs a fling gesture on the screen.
     * It handles the fling gesture and performs the appropriate action.
     * Swipe right, left, up, or down.
     * @param event1 The first motion event
     * @param event2 The second motion event
     * @param velocityX The velocity of the gesture in the x direction
     * @param velocityY The velocity of the gesture in the y direction
     * @return Boolean : True if the event is handled, false otherwise
     * @see onSwipeRight
     * @see onSwipeLeft
     * @see onSwipeUp
     * @see onSwipeDown
     */
    override fun onFling(
        event1: MotionEvent?, event2: MotionEvent,
        velocityX: Float, velocityY: Float
    ): Boolean {
        // Calculate the distance between the two events
        val deltaX = event2.x - event1!!.x
        val deltaY = event2.y - event1.y

        // Check if the swipe is not from the borders of the screen (avoid non-intentional swipes)
        if (event1.y > MAX_SWIPE_OFF_SCREEN_VERTICAL && event1.y
            < screenHeight - MAX_SWIPE_OFF_SCREEN_VERTICAL
            && event1.x > MAX_SWIPE_OFF_SCREEN_HORIZONTAL && event1.x
            < screenWidth - MAX_SWIPE_OFF_SCREEN_HORIZONTAL) {

            if (abs(deltaX) > abs(deltaY)) { // If the swipe is horizontal
                // Check if the swipe distance is enough and swipe right or left accordingly
                if (abs(deltaX) > MIN_SWIPE_DISTANCE) {
                    if (deltaX > 0) {
                        onSwipeRight()
                    } else {
                        onSwipeLeft()
                    }
                    return true
                }
            } else { // If the swipe is vertical
                // Check if the swipe distance is enough and swipe up or down accordingly
                if (abs(deltaY) > MIN_SWIPE_DISTANCE) {
                    if (deltaY > 0) {
                        onSwipeDown()
                    } else {
                        onSwipeUp()
                    }
                    return true
                }
            }
        }

        // If the swipe is not enough or from the borders, return false
        return false
    }

    /**
     * OnLongPress method of the activity.
     * override onLongPress.
     * This method is invoked when the user performs a long press gesture on the screen.
     * It's not used in this activity.
     * @param event The motion event of the user
     */
    override fun onLongPress(event: MotionEvent) { }

    /**
     * OnScroll method of the activity.
     * override onScroll.
     * This method is invoked when the user scrolls on the screen.
     * It's not used in this activity.
     * @param p0 The first motion event
     * @param event1 The second motion event
     * @param distanceX The distance of the scroll in the x direction
     * @param distanceY The distance of the scroll in the y direction
     * @return Boolean : True if the event is handled, false otherwise
     */
    override fun onScroll(
        p0: MotionEvent?, event1: MotionEvent,
        distanceX: Float, distanceY: Float
    ): Boolean {
        return true
    }

    /**
     * OnShowPress method of the activity.
     * override onShowPress.
     * This method is invoked when the user performs a show press gesture on the screen.
     * It's not used in this activity.
     * @param event The motion event of the user
     */
    override fun onShowPress(event: MotionEvent) { }

    /**
     * OnSingleTapUp method of the activity.
     * override onSingleTapUp.
     * This method is invoked when the user performs a single tap gesture on the screen.
     * It handles the single tap gesture and performs the appropriate action.
     * It used only if the double tap is disabled.
     * If the controls are visible, hide them. If the controls are hidden, show them.
     * @param event The motion event of the user
     * @return Boolean : True if the event is handled, false otherwise
     */
    override fun onSingleTapUp(event: MotionEvent): Boolean {
        // First, check if the double tap is allowed or not
        // Use this method only if the double tap is disabled (to avoid conflicts)
        if (!DOUBLE_TAP) {
            // Show or hide the controls according to their visibility
            if (isControlsVisible) {
                resetControlsTimer()
                showControls(false)
            } else {
                resetControlsTimer()
                showControls()
                startControlsTimer()
            }
        }

        return true
    }

    /**
     * OnDoubleTap method of the activity.
     * override onDoubleTap.
     * This method is invoked when the user performs a double tap gesture on the screen.
     * It handles the double tap gesture and performs the appropriate action.
     * If the user double taps on the screen, like the track only if the track is not liked yet.
     * @param event The motion event of the user
     * @return Boolean : True if the event is handled, false otherwise
     * @see startHeartAnimation
     * @see sendLikeRequest
     */
    override fun onDoubleTap(event: MotionEvent): Boolean {
        // If the track is not null and the double tap is allowed
        if (this.trackId != null && DOUBLE_TAP && !noTrack) {
            // Only like the track if it's not liked yet (avoid non-intentional unlikes)
            if (!this.isLiked) {
                // Send the like request to the Spotify API (opposite of the current status)
                sendLikeRequest(this.trackId!!)

                // Change the like status (UI and data)
                handleLikeStatus(true)
                this.isLikeRequestSent = true
            }

            startHeartAnimation(event)
        }

        return true
    }

    /**
     * OnDoubleTapEvent method of the activity.
     * override onDoubleTapEvent.
     * This method is invoked when the user performs a double tap gesture on the screen.
     * It's not used in this activity.
     * @param event The motion event of the user
     * @return Boolean : True if the event is handled, false otherwise
     */
    override fun onDoubleTapEvent(event: MotionEvent): Boolean { return true }

    /**
     * OnSingleTapConfirmed method of the activity.
     * This method is invoked when the user performs a single tap gesture on the screen.
     * It handles the single tap gesture and performs the appropriate action.
     * It used only if the double tap is enabled.
     * If the controls are visible, hide them. If the controls are hidden, show them.
     */
    override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
        // First, check if the double tap is allowed or not
        // Use this method only if the double tap is enabled (to avoid conflicts with the double
        // tap)
        if (DOUBLE_TAP) {
            // Show or hide the controls according to their visibility
            if (isControlsVisible) {
                resetControlsTimer()
                showControls(false)
            } else {
                resetControlsTimer()
                showControls()
                startControlsTimer()
            }
        }

        return true
    }

    /**
     * OnSwipeRight method of the activity.
     * This method is invoked when the user performs a swipe right gesture on the screen.
     * It swipes the image to the right and sends a skip request to the Spotify API.
     * @see swipeImage
     * @see sendSkipRequest
     * @see onFling
     */
    private fun onSwipeRight() {
        // Check if the user is connected to the Internet and the horizontal swipe is allowed
        if (isInternet && HORIZONTAL_SWIPE) {
            // Start the swipe animation and send the skip request to the Spotify API
            swipeImage(false)
            sendSkipRequest(false)
        }
    }

    /**
     * OnSwipeLeft method of the activity.
     * This method is invoked when the user performs a swipe left gesture on the screen.
     * It swipes the image to the left and sends a skip request to the Spotify API.
     * @see swipeImage
     * @see sendSkipRequest
     * @see onFling
     */
    private fun onSwipeLeft() {
        // Check if the user is connected to the Internet and the horizontal swipe is allowed
        if (isInternet && HORIZONTAL_SWIPE) {
            // Start the swipe animation and send the skip request to the Spotify API
            swipeImage(true)
            sendSkipRequest(true)
        }
    }

    /**
     * OnSwipeUp method of the activity.
     * This method is invoked when the user performs a swipe up gesture on the screen.
     * It shakes the image and sends a volume request to the Spotify API (volume up).
     * @see shakeImage
     * @see sendVolumeRequest
     */
    private fun onSwipeUp() {
        // Check if the user is connected to the Internet and the vertical swipe is allowed
        if (isInternet && VERTICAL_SWIPE) {
            // Start the shake animation only if another animation is not active
            if (!this.isImageSwiped && !this.isFadeAnimationActive)
                shakeImage(true)

            // Check if the device supports volume control
            if (this.isSupportsVolume) {
                // Increase the volume by 10% if the volume is less than 90% or set it to 100%
                if (percentVolume <= 90) {
                    sendVolumeRequest(percentVolume + 10)
                } else if (percentVolume in 91..99) {
                    sendVolumeRequest(100)
                }

                // Display the volume bar for a certain time (3s by default)
                showVolume(true)
                resetVolumeTimer()
                startVolumeTimer()
            } else {
                // Display a message if the device does not support volume control
                ToastUtil.showCustomToast(
                    this,
                    String.format(
                        Locale.getDefault(),
                        "%s does not support volume control",
                        deviceName
                    )
                )
            }
        }
    }

    /**
     * OnSwipeDown method of the activity.
     * This method is invoked when the user performs a swipe down gesture on the screen.
     * It shakes the image and sends a volume request to the Spotify API (volume down).
     * @see shakeImage
     * @see sendVolumeRequest
     */
    private fun onSwipeDown() {
        // Check if the user is connected to the Internet and the vertical swipe is allowed
        if (isInternet && VERTICAL_SWIPE) {
            // Start the shake animation only if another animation is not active
            if (!this.isImageSwiped && !this.isFadeAnimationActive)
                shakeImage(false)

            // Check if the device supports volume control
            if (this.isSupportsVolume) {
                // Decrease the volume by 10% if the volume is more than 10% or set it to 0%
                if (percentVolume >= 10) {
                    sendVolumeRequest(percentVolume - 10)
                } else if (percentVolume in 1..9) {
                    sendVolumeRequest(0)
                }

                // Display the volume bar for a certain time (3s by default)
                showVolume(true)
                resetVolumeTimer()
                startVolumeTimer()
            } else {
                // Display a message if the device does not support volume control
                ToastUtil.showCustomToast(
                    this,
                    String.format(
                        Locale.getDefault(),
                        "%s does not support volume control",
                        deviceName
                    )
                )
            }
        }
    }


    /* #################################### Checking methods #################################### */

    /**
     * IsTokenExpired method of the activity.
     * This method checks if the access token is expired.
     * It compares the expiration time of the token with the current time.
     * If the expiration time is earlier than the current time, the token is expired.
     * @return Boolean : True if the token is expired, false otherwise
     */
    private fun isTokenExpired(): Boolean {
        if (this.expirationTime.isNotEmpty()) {
            // Compare the expiration time of the token with the current time
            // If the expiration time is earlier than the current time, the token is expired (with a
            // margin of 5 minutes)
            return System.currentTimeMillis() + 300000 > this.expirationTime.toLong()
        }
        return true
    }

    /**
     * IsInternetAvailable method of the activity.
     * This method checks if the user is connected to the Internet.
     * It uses the ConnectivityManager to check the network capabilities of the device.
     * @param context The context of the activity
     * @return Boolean : True if the user is connected to the Internet, false otherwise
     * @see checkInternetConnectionTask
     */
    private fun isInternetAvailable(context: Context): Boolean {
        // Use the ConnectivityManager to check the network capabilities of the device
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        // Check if the user is connected to the Internet and if the network is active
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        // Try the connection with the different types of transport (Wi-Fi, cellular, Ethernet)
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /**
     * CheckInternetConnectionTask method of the activity.
     * This method checks if the user is connected to the Internet.
     * It displays a message if the user is not connected to the Internet.
     * @return Boolean : True if the user is connected to the Internet, false otherwise
     * @see showMessage
     * @see isInternetAvailable
     */
    private fun checkInternetConnectionTask(): Boolean {
        // Check if the user is connected to the Internet
        if (!isInternetAvailable(this)) {
            // If the old state is true, change it to false and display a message
            if (isInternet) {
                isInternet = false
                runOnUiThread {
                    showMessage(true, R.string.text_noInternet)
                }
            }
            return false
        } else {
            // If the old state is false, change it to true and hide the message
            if (!isInternet) {
                isInternet = true
                runOnUiThread {
                    showMessage(false, 0)
                }
            }
            return true
        }
    }

    /**
     * CheckTrackState method of the activity.
     * This method checks the state of the track playing on Spotify.
     * It checks if the track is not empty and if the track is playing.
     * If the track is empty or not playing, it displays a message to the user.
     * @param track The track information in JSON format
     * @return Boolean : True if the track is playing, false otherwise
     */
    private fun checkTrackState(track: String): Boolean {
        // Check if the track is not empty and if track is a JSON object
        if (track.isNotEmpty() && track[0] == '{') {
            val trackData = JSONObject(track) // Convert the track information to a JSON object

            // if the track is not empty and the item is not null, the track is playing
            if (trackData.optJSONObject("item") == null) {
                // Display a message if the track is empty
                if (!this.noTrack) {
                    this.showMessage(true, R.string.text_noTrackPlaying)
                    this.noTrack = true // Update the state of the track
                }
                return false
            } else {
                // Hide the message if the track is playing
                if (this.noTrack) {
                    this.showMessage(false, 0)
                    this.noTrack = false // Update the state of the track
                }
                return true
            }
        } else {
            // Display a message if the track is empty
            if (!this.noTrack) {
                this.showMessage(true, R.string.text_noTrackPlaying)
                this.noTrack = true // Update the state of the track
            }
            return false
        }
    }


    /* ################################# Authentication methods ################################# */

    /**
     * StartSpotifyAuthentication method of the activity.
     * This method starts the Spotify authentication process.
     * It creates the authentication request and opens the Spotify login activity.
     * @see initAuthLauncher
     */
    private fun startSpotifyAuthentication() {
        // If an error occurred during the past authentication or the user is not connected to the
        // Internet, return
        if (this.isAuthInError || !checkInternetConnectionTask()) {
            return
        }

        // Create the authentication request with the client ID, the redirect URI, ...
        val builder = AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI)

        // Add the scopes required for the application
        builder.setScopes(arrayOf(
            "streaming",
            "app-remote-control",
            "ugc-image-upload",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "user-library-read",
            "user-library-modify"
        ))

        // Build the authentication request
        val request = builder.build()

        // Open the Spotify login activity with the authentication request
        val authIntent = AuthorizationClient.createLoginActivityIntent(this, request)
        authLauncher.launch(authIntent) // Use the authentication launcher to start the activity
    }

    /**
     * InitAuthLauncher method of the activity.
     * This method initializes the authentication launcher.
     * It registers the activity result launcher for the authentication process.
     */
    private fun initAuthLauncher() {
        // Register the activity result launcher for the authentication process
        authLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // First, check if the result is OK
            if (result.resultCode == RESULT_OK) {
                // Extract the response from the result
                val data = result.data
                val response = AuthorizationClient.getResponse(result.resultCode, data)

                when (response?.type) { // Handle the different types of responses
                    // If the response is a token, the authentication is successful
                    AuthorizationResponse.Type.TOKEN -> {
                        // extract the token and the expiration time from the response
                        val token = response.accessToken
                        val expiresIn = response.expiresIn.toLong()

                        // If the token is not null, the authentication is successful
                        if (token != null) {
                            ToastUtil.showCustomToast(
                                this,
                                "Authentication successful"
                            )

                            // Display a loading message, as the track is not loaded yet
                            showMessage(true, R.string.text_loading, true)

                            // Update the state of the first call
                            if (!isFirstCall) {
                                isFirstCall = true
                            }

                            // Save the token and the expiration time in the shared preferences
                            with(sharedPref.edit()) {
                                putString("access_token", token)
                                this@PlayerActivity.accessToken = token

                                // Calculate the expiration time in milliseconds and save it
                                // Current time + expiresIn (*1000 = in ms)
                                val expirationTime = System.currentTimeMillis() + expiresIn * 1000
                                putLong("expiration_time", expirationTime)
                                this@PlayerActivity.expirationTime = expirationTime.toString()

                                apply()
                            }

                            // Start the fetching task withe the new token
                            startFetchingTask()
                        } else {
                            // If the token is null, display the authentication error dialog
                            displayAuthenticationErrorDialog()
                            this.isAuthInError = true // Update the state of the authentication
                        }
                    }
                    // If the response is an error, display the authentication error dialog
                    AuthorizationResponse.Type.ERROR -> {
                        displayAuthenticationErrorDialog()
                        this.isAuthInError = true
                    }
                    // If the response is empty, the user cancelled the authentication
                    AuthorizationResponse.Type.EMPTY -> {
                        ToastUtil.showCustomToast(
                            this,
                            "Authentication cancelled"
                        )
                    }
                    // If the response is unknown, display a message to the user
                    else -> {
                        Toast.makeText(
                            this,
                            "Unexpected error. Please try again",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * CheckTokenAndStartFetchingTask method of the activity.
     * This method checks if the access token is expired and starts the fetching task.
     * If the access token is expired, it checks if the user is connected to the Internet and
     * starts the Spotify authentication process. Otherwise, it starts the fetching task.
     * @see isTokenExpired
     * @see checkInternetConnectionTask
     * @see startSpotifyAuthentication
     * @see startFetchingTask
     * @see onResume
     */
    private fun checkTokenAndStartFetchingTask() {
        // Define the next call as the first one (also after a pause)
        this.isFirstCall = true

        // Check if the access token is expired
        if (!isTokenExpired()) {
            // If the access token is not expired, start the fetching task
            showMessage(true, R.string.text_loading, true) // Display the loading message

            // Start the fetching task to get the track information periodically
            this.fetchHandler = Handler(Looper.getMainLooper())
            startFetchingTask()
        } else {
            // Otherwise, check the Internet connection and start the Spotify authentication process
            if (checkInternetConnectionTask()) {
                startSpotifyAuthentication()
            } else {
                // Display a dialog if the user is not connected to the Internet
                displayNoInternetDialog()
            }
        }
    }

    /* #################################### Request methods ##################################### */

    /**
     * SendPlaybackRequest method of the activity.
     * This method sends a playback request to the Spotify API.
     * It sends a request to play or pause the music.
     * @param isPlay Boolean : True if the music should play, false if the music should pause
     * @see checkInternetConnectionTask
     */
    private fun sendPlaybackRequest(isPlay: Boolean) {
        //  Check if the user is connected to the Internet and show a message if not
        if (!checkInternetConnectionTask()) {
            return
        }

        //  Build the URL of the appropriate endpoint to play or pause the music
        val url = if (isPlay) {
            "https://api.spotify.com/v1/me/player/play"
        } else {
            "https://api.spotify.com/v1/me/player/pause"
        }

        // Create an empty request body, as playing and pausing do not require additional data
        val requestBody = "".toRequestBody("application/json".toMediaType())

        // Build the request with the appropriate URL, access token, and request body
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${this.accessToken}")
            .put(requestBody) // Use the HTTP PUT method for play and pause requests
            .build()

        // Create an HTTP client to send the request
        val client = OkHttpClient()

        // Execute the request asynchronously and handle the response
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    // The request was successful
                    runOnUiThread {
                        this@PlayerActivity.handlePlayButton(isPlay)
                        this@PlayerActivity.isPlaybackChanged = true
                    }
                } else {
                    // The request failed - show an error message (Toast)
                    fetchHandler.post {
                        Toast.makeText(this@PlayerActivity,
                            "Error while sending play/pause request",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle request errors - show an error message (Toast)
                fetchHandler.post {
                    Toast.makeText(this@PlayerActivity,
                        "Error while sending play/pause request",
                        Toast.LENGTH_SHORT).show()
                }
                return
            }
        })
    }

    /**
     * SendSkipRequest method of the activity.
     * This method sends a skip request to the Spotify API.
     * It sends a request to skip to the next or previous track.
     * @param isNext Boolean : True if the music should skip to the next track, false if the music
     * should skip to the previous track
     * @see checkInternetConnectionTask
     * @see onSwipeRight
     * @see onSwipeLeft
     */
    private fun sendSkipRequest(isNext: Boolean) {
        // Check if the user is connected to the Internet
        if (!checkInternetConnectionTask()) {
            return
        }

        // Build the URL of the appropriate endpoint to play or pause the music
        val url = if (isNext) {
            "https://api.spotify.com/v1/me/player/next"
        } else {
            "https://api.spotify.com/v1/me/player/previous"
        }

        // Create an empty request body, as skipping does not require additional data
        val requestBody = "".toRequestBody("application/json".toMediaType())

        // Build the request with the appropriate URL, access token, and request body
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${this.accessToken}")
            .post(requestBody) // Use the HTTP POST method for skip requests
            .build()

        val client = OkHttpClient()

        // Execute the request asynchronously and handle the response
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    // The request was successful
                    fetchCurrentTrack()

                } else {
                    // The request failed - show an error message (Toast)
                    fetchHandler.post {
                        Toast.makeText(this@PlayerActivity,
                            "Error while sending skip request",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle request errors - show an error message (Toast)
                fetchHandler.post {
                    Toast.makeText(this@PlayerActivity,
                        "Error while sending skip request",
                        Toast.LENGTH_SHORT).show()
                }
                return
            }
        })
    }

    /**
     * SendShuffleRequest method of the activity.
     * This method sends a shuffle request to the Spotify API.
     * It sends a request to shuffle the music.
     * @param isShuffleOn Boolean : True if the music should shuffle, false if the music should not
     * shuffle
     * @see checkInternetConnectionTask
     */
    private fun sendShuffleRequest(isShuffleOn: Boolean) {
        // Check if the user is connected to the Internet
        if (!checkInternetConnectionTask()) {
            return
        }

        // Build the URL of the appropriate endpoint to shuffle the music
        val url = if (isShuffleOn) {
            "https://api.spotify.com/v1/me/player/shuffle?state=true"
        } else {
            "https://api.spotify.com/v1/me/player/shuffle?state=false"
        }

        // Create an empty request body, as shuffling does not require additional data
        val requestBody = "".toRequestBody("application/json".toMediaType())

        // Build the request with the appropriate URL, access token, and request body
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${this.accessToken}")
            .put(requestBody) // Use the HTTP PUT method for shuffle requests
            .build()

        val client = OkHttpClient()

        // Execute the request asynchronously and handle the response
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    // The request was successful
                    // Update the shuffle status according to the request
                    if (isShuffleOn) {
                        this@PlayerActivity.isShuffle = true
                    } else {
                        this@PlayerActivity.isShuffle = false
                    }

                    // Update the shuffle status icon
                    runOnUiThread {
                        this@PlayerActivity.handleShuffleStatus(
                            isShuffleOn, false, true
                        )
                    }
                } else {
                    // The request failed - show an error message (Toast)
                    fetchHandler.post {
                        Toast.makeText(this@PlayerActivity,
                            "Error while sending shuffle request",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle request errors - show an error message (Toast)
                fetchHandler.post {
                    Toast.makeText(this@PlayerActivity,
                        "Error while sending shuffle request",
                        Toast.LENGTH_SHORT).show()
                }
                return
            }
        })
    }

    /**
     * SendLikeRequest method of the activity.
     * This method sends a like request to the Spotify API.
     * It sends a request to like or unlike the track.
     * @param trackId The ID of the track to like or unlike
     * @see checkInternetConnectionTask
     * @see onDoubleTap
     */
    private fun sendLikeRequest(trackId: String) {
        // Check if the user is connected to the Internet
        if (!checkInternetConnectionTask()) {
            return
        }

        // Build the URL of the appropriate endpoint to like or unlike the track
        val url = "https://api.spotify.com/v1/me/tracks"

        // Create a request body with the ID of the track to like or unlike
        val requestBody = "{\"ids\": [\"$trackId\"]}"
            .toRequestBody("application/json".toMediaType())

        // Build the request with the appropriate URL according to the current like status
        val request = if (!this.isLiked) {
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${this.accessToken}")
                .put(requestBody) // Use the HTTP PUT method for like requests
                .build()
        } else {
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${this.accessToken}")
                .delete(requestBody) // Use the HTTP DELETE method for unlike requests
                .build()
        }

        // Create an HTTP client to send the request
        val client = OkHttpClient()

        // Execute the request asynchronously and handle the response
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    // The request was successful
                    this@PlayerActivity.isLikeRequestSent = false
                    if (isLiked) {
                        // Update the like status according to the request
                        this@PlayerActivity.isLiked = true
                        handleLikeStatus(true)
                        runOnUiThread {
                            ToastUtil.showCustomToast(this@PlayerActivity, "Track liked")
                        }
                    } else {
                        // Update the like status according to the request
                        this@PlayerActivity.isLiked = false
                        handleLikeStatus(false)
                        runOnUiThread {
                            ToastUtil.showCustomToast(this@PlayerActivity, "Track unliked")
                        }
                    }
                } else {
                    // The request failed - show an error message (Toast)
                    fetchHandler.post {
                        Toast.makeText(this@PlayerActivity,
                            "Error while sending like request",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle request errors - show an error message (Toast)
                fetchHandler.post {
                    Toast.makeText(this@PlayerActivity,
                        "Error while sending like request",
                        Toast.LENGTH_SHORT).show()
                }
                return
            }
        })
    }

    /**
     * SendSeekToPosition method of the activity.
     * This method sends a seek request to the Spotify API.
     * It sends a request to seek to a specific position in the track.
     * @param progressMs The position in milliseconds to seek to
     * @see checkInternetConnectionTask
     */
    private fun sendSeekToPosition(progressMs: Long) {
        // Check if the user is connected to the Internet
        if (!checkInternetConnectionTask()) {
            return
        }

        // Build the URL of the appropriate endpoint to seek to a specific position in the track
        val url = "https://api.spotify.com/v1/me/player/seek?position_ms=$progressMs"

        // Create an empty request body, as seeking does not require additional data
        val requestBody = "".toRequestBody("application/json".toMediaType())

        // Build the request with the appropriate URL, access token, and request body
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${this.accessToken}")
            .put(requestBody) // Use the HTTP PUT method for seek requests
            .build()

        val client = OkHttpClient()

        // Execute the request asynchronously and handle the response
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    // Resume refreshing the progress bar after the seek request
                    fetchHandler.postDelayed({
                        isBarTouched = false
                    }, 750) // 1/4 seconds
                } else {
                    // The request failed - show an error message (Toast)
                    fetchHandler.post {
                        Toast.makeText(this@PlayerActivity,
                            "Error while sending seek request",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle request errors - show an error message (Toast)
                fetchHandler.post {
                    Toast.makeText(this@PlayerActivity,
                        "Error while sending seek request",
                        Toast.LENGTH_SHORT).show()
                }
                return
            }
        })
    }

    /**
     * SendVolumeRequest method of the activity.
     * This method sends a volume request to the Spotify API.
     * It sends a request to change the volume of the music.
     * @param percent The volume percentage to set
     * @see checkInternetConnectionTask
     * @see onSwipeUp
     * @see onSwipeDown
     */
    private fun sendVolumeRequest(percent: Int) {
        // Check if the user is connected to the Internet
        if (!checkInternetConnectionTask()) {
            return
        }

        // Build the URL of the appropriate endpoint to change the volume of the music
        val url = "https://api.spotify.com/v1/me/player/volume?volume_percent=$percent"

        // Create an empty request body, as changing the volume does not require additional data
        val requestBody = "".toRequestBody("application/json".toMediaType())

        // Build the request with the appropriate URL, access token, and request body
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${this.accessToken}")
            .put(requestBody) // Use the HTTP PUT method for volume requests
            .build()

        val client = OkHttpClient()

        // Execute the request asynchronously and handle the response
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                // Check if the request was successful
                if (response.isSuccessful) {
                    // The request was successful : update the volume percentage and the volume bar
                    percentVolume = percent
                    handleVolume(percent, true)
                    isVolumeChanged = true
                } else {
                    // The request failed - show an error message (Toast)
                    fetchHandler.post {
                        Toast.makeText(this@PlayerActivity,
                            "Error while sending volume request",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle request errors - show an error message (Toast)
                fetchHandler.post {
                    Toast.makeText(this@PlayerActivity,
                        "Error while sending volume request",
                        Toast.LENGTH_SHORT).show()
                }
                return
            }
        })
    }

    /**
     * FetchCurrentTrack method of the activity.
     * This method fetches the current track playing on Spotify.
     * It sends a request to the Spotify API to get the current track information.
     * @see fetchCurrentTrack
     */
    private fun fetchTrackFromApi(): Any {
        // If there is no internet connection, return an empty string and show a message
        if (!this.checkInternetConnectionTask()) {
            return false
        }

        // Try to fetch the current track from the Spotify API, if the request fails, show a message
        // (probably no connection)
        try {
            // Create an HTTP client to send the request
            val client = OkHttpClient()

            // Build the request with the appropriate URL and access token
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/player")
                .addHeader("Authorization",  "Bearer ${this.accessToken}")
                .build()

            // Execute the request and get the response
            val response: Response = client.newCall(request).execute()
            return response.body?.string() ?: ""
        } catch (e: Exception) {
            // Show a message if the request fails (probably no connection)
            checkInternetConnectionTask()
            return false
        }
    }

    /**
     * FetchArtistImageFromApi method of the activity.
     * This method fetches the artist image from the Spotify API.
     * It sends a request to the Spotify API to get the artist image (URL).
     * @param artistLink The URL of the artist image
     * @return Any : The response of the request (image URL) or false if the request failed
     * (no internet connection in particular).
     * @see fetchArtistImage
     */
    private fun fetchArtistImageFromApi(artistLink: String): Any {
        // If there is no internet connection, return an empty string and show a message
        if (!this.checkInternetConnectionTask()) {
            return false
        }

        // Try to fetch the artist image from the Spotify API, if the request fails, show a message
        // (probably no connection)
        try {
            // Create an HTTP client to send the request
            val client = OkHttpClient()

            // Build the request with the appropriate URL and access token
            val request = Request.Builder()
                .url(artistLink)
                .addHeader("Authorization",  "Bearer ${this.accessToken}")
                .build()

            // Execute the request and get the response
            val response: Response = client.newCall(request).execute()
            return response.body?.string() ?: ""
        } catch (e: Exception) {
            // Show a message if the request fails (probably no connection)
            checkInternetConnectionTask()
            return false
        }
    }

    /**
     * FetchLikeStatusFromApi method of the activity.
     * This method fetches the like status of the track from the Spotify API.
     * It sends a request to the Spotify API to get the like status of the track.
     * @param trackId The ID of the track to get the like status
     * @return Any : The response of the request (like status) or false if the request failed
     * (no internet connection in particular).
     * @see fetchLikeStatus
     */
    private fun fetchLikeStatusFromApi(trackId: String): Any {
        // If there is no internet connection, return an empty string and show a message
        if (!this.checkInternetConnectionTask()) {
            return false
        }

        // Try to fetch the like status of the track from the Spotify API, if the request fails,
        // show a message (probably no connection)
        try {
            // Create an HTTP client to send the request
            val client = OkHttpClient()

            // Build the request with the appropriate URL and access token
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/tracks/contains?ids=$trackId")
                .addHeader("Authorization",  "Bearer ${this.accessToken}")
                .build()

            // Execute the request and get the response
            val response: Response = client.newCall(request).execute()
            return response.body?.string() ?: ""
        } catch (e: Exception) {
            // Show a message if the request fails (probably no connection)
            checkInternetConnectionTask()
            return false
        }
    }


    /* ################################### Animation methods #################################### */

    /**
     * AnimateProgressBar method of the activity.
     * This method animates the progress bar of the music player.
     * It animates the progress bar from the current progress to the new progress.
     * @param progressTo The new progress value to animate to
     * @see handleTrackInfo
     */
    private fun animateProgressBar(progressTo: Int) {
        // Cancel the previous animation if it is still running
        progressBarAnimator?.cancel()

        // Animate the progress bar from the current progress to the new progress (duration: 1s)
        progressBarAnimator = ObjectAnimator.ofInt(
            progressBar, "progress", progressBar.progress, progressTo).apply {
                duration = 1000
                interpolator = DecelerateInterpolator()
                start()
            }
    }

    /**
     * ShakeImage method of the activity.
     * This method shakes the image to indicate the volume change.
     * It shakes the image up or down to indicate the volume change.
     * @param isUp Boolean : True if the volume is increased, false if the volume is decreased
     * @see onSwipeUp
     * @see onSwipeDown
     */
    private fun shakeImage(isUp: Boolean) {
        // Shake the image (imageMusic) to indicate the volume change up or down
        val shake = AnimationUtils.loadAnimation(this, if (isUp) R.anim.shake_down else R.anim.shake_up)
        imageMusic.startAnimation(shake)
    }

    /**
     * SwipeImage method of the activity.
     * This method swipes the image to indicate the track change.
     * It swipes the image to the left or right to indicate the track change.
     * @param isNext Boolean : True if the track is changed to the next track, false if the track is changed to the previous track
     * @see onSwipeRight
     * @see onSwipeLeft
     */
    private fun swipeImage(isNext: Boolean) {
        // Swipe the image (imageMusic) to indicate the track change to the next or previous track
        val swipe = AnimationUtils.loadAnimation(
            this, if (isNext) R.anim.swipe_to_left else R.anim.swipe_to_right)
        imageMusic.startAnimation(swipe)

        // Hide the image after the animation ends
        swipe.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                isImageSwiped = true
            }

            override fun onAnimationEnd(animation: Animation) {
                imageMusic.visibility = View.INVISIBLE
            }

            override fun onAnimationRepeat(animation: Animation) {
                // Do nothing
            }
        })
    }

    /**
     * FadeInImageTrack method of the activity.
     * This method fades in the track image.
     * It fades in the track image to make it visible.
     * @param imageView The image view to fade in
     * @param duration The duration of the fade in animation
     * @param listener The listener for the fade in animation
     * @see handleTrackImage
     */
    private fun fadeInImageTrack(
        imageView: ImageView, duration: Long = 1000, listener: Animator.AnimatorListener? = null
    ) {
        this.isFadeAnimationActive = true // Update the state of the fade animation

        // Fade in the image view to make it visible
        imageView.alpha = 0f // Set the alpha to 0 (invisible)
        val fadeInAnimator = imageView.animate().alpha(1f).setDuration(duration)
        listener?.let { fadeInAnimator.setListener(it) }
        fadeInAnimator.start() // Start the fade in animation

        // onAnimationEnd: Update the state of the fade animation
        fadeInAnimator.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                runOnUiThread {
                    this@PlayerActivity.isFadeAnimationActive = false
                }
            }
        })
    }

    /**
     * FadeOutImageTrack method of the activity.
     * This method fades out the track image.
     * It fades out the track image to make it invisible.
     * @param imageView The image view to fade out
     * @param duration The duration of the fade out animation
     * @param listener The listener for the fade out animation
     * @see handleTrackImage
     */
    private fun fadeOutImageTrack(
        imageView: ImageView, listener: Animator.AnimatorListener?, duration: Long = 500
    ) {
        this.isFadeAnimationActive = true // Update the state of the fade animation

        val fadeOutAnimator = ObjectAnimator.ofFloat(imageView, "alpha", 1f, 0f)
        fadeOutAnimator.duration = duration
        listener?.let { fadeOutAnimator.addListener(it) }
        fadeOutAnimator.start()
    }

    /**
     * FadeInImageArtiste method of the activity.
     * This method fades in the artist image.
     * It fades in the artist image to make it visible.
     * @param duration The duration of the fade in animation
     * @param onAnimationEnd The listener for the fade in animation
     * @see handleArtistImage
     */
    private fun View.fadeInImageArtiste(duration: Long, onAnimationEnd: (() -> Unit)? = null) {
        // Fade in the artist image to make it visible
        animate()
            .alpha(1f) // Set the alpha to 1 (visible)
            .setDuration(duration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    onAnimationEnd?.invoke()
                }
            })
    }

    /**
     * FadeOutImageArtiste method of the activity.
     * This method fades out the artist image.
     * It fades out the artist image to make it invisible.
     * @param duration The duration of the fade out animation
     * @param onAnimationEnd The listener for the fade out animation
     * @see handleArtistImage
     */
    private fun View.fadeOutImageArtiste(duration: Long, onAnimationEnd: (() -> Unit)? = null) {
        // Fade out the artist image to make it invisible
        animate()
            .alpha(0f) // Set the alpha to 0 (invisible)
            .setDuration(duration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    onAnimationEnd?.invoke()
                }
            })
    }

    /**
     * StartHeartAnimation method of the activity.
     * This method starts the heart animation.
     * It starts the heart animation at the position of the double-tap.
     * @param event The motion event of the double-tap
     * @see onDoubleTap
     */
    private fun startHeartAnimation(event: MotionEvent) {
        // Make the heart visible and position it at the double-tap position
        this.heartImage.visibility = View.VISIBLE

        // Position the heart at the center of the double-tap
        val params = this.heartImage.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = event.x.toInt() - this.heartImage.width / 2
        params.topMargin = event.y.toInt() - this.heartImage.height / 2
        this.heartImage.layoutParams = params

        // Load the animations for the heart
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_heart)
        val zoomIn = AnimationUtils.loadAnimation(this, R.anim.zoom_in_heart)

        // Set a random rotation for the heart [-35, 35]
        val randomRotation = (-35..35).random().toFloat()
        this.heartImage.rotation = randomRotation

        // Create an animation set with the fade-in and zoom-in animations
        val animationSet = AnimationSet(true).apply {
            addAnimation(fadeIn)
            addAnimation(zoomIn)
        }

        // Start the animation set for the heart
        this.heartImage.startAnimation(animationSet)

        // Hide the heart after the animation ends
        this.heartImage.postDelayed({
            this.heartImage.visibility = View.GONE
        }, 600)
    }


    /* ######################################## Handlers ######################################## */

    /**
     * handleTrackInfo method of the activity.
     * This method handles the track information received from the Spotify API.
     * It updates the UI with the track information.
     * @param trackInfo The track information received from the Spotify API (data class)
     * @see fetchCurrentTrack
     * @see fetchArtistImage
     * @see fetchLikeStatus
     * @see handleArtistImage
     * @see handleTrackImage
     * @see handlePlayButton
     * @see handleLikeStatus
     * @see handleShuffleStatus
     * @see handleDeviceName
     */
    private fun handleTrackInfo(trackInfo: TrackInfo) {
        // Update the UI with the track information
        val context = this@PlayerActivity
        with(trackInfo) {
            // If the trackInfo values are null or empty, return
            if (id.isEmpty() || title.isEmpty()) {
                return
            }

            // If it is the first call, hide the initialization message
            if (context.isFirstCall) {
                // Hide the initialization message
                context.showMessage(false, 0)

                // Display the controls
                context.showControls()
                context.resetControlsTimer()
                context.startControlsTimer()
            }

            // If isn't the same track, update the UI with the new track information
            // Only the constants information are updated here
            if (!isSameTrack) {
                // Update the track information in the context:
                context.trackId = id // Save the ID of the track
                context.totalDuration = durationMs // Save the total duration of the track

                // Display the artist name(s) (list) in the TextView separated by commas
                val result = StringBuilder()
                for ((artistName, _) in artistsList) {
                    // Add the artist name to the result
                    result.append(artistName)
                    // Add a comma if it is not the last artist
                    if (artistsList.last().first != artistName) {
                        result.append(", ")
                    }
                }
                // Display the artist name(s) in the TextView
                context.trackArtist.text = result.toString()

                // Display the track title in the TextView
                context.trackTitle.text = title

                // Load the track image if it is not the same track and the image is not swiped
                // Avoid loading the image if it is the same track and the image is swiped
                // Or if the image is null (first call)
                if ((!isSameImage && !context.isImageSwiped)
                    || context.imageMusic.drawable == null) {
                    context.trackImage = imageUrl
                    context.handleTrackImage(imageUrl)
                } else if (context.isImageSwiped) {
                    // If the image is swiped, load the image after the swipe animation
                    // (true parameter)
                    context.trackImage = imageUrl
                    context.handleTrackImage(imageUrl, true)
                }

                // Load the artist image if it is not the same track (the first artist of the list)
                fetchArtistImage(artistsList[0].second)

                // Calculate the total time of the track and display it
                val totalMinutes = durationMs / 60000
                val totalSeconds = (durationMs % 60000) / 1000
                context.totalTimeView.text =
                    String.format(
                        Locale.getDefault(), "%02d:%02d", totalMinutes, totalSeconds)

                // Update the swipe status of the image if it is swiped
                if (context.isImageSwiped) {
                    context.isImageSwiped = false
                }
            }

            // Send the like request to the API
            // Use isLikeRequestSent to avoid sending to many requests (1 request every 2 seconds)
            if (context.isLikeTaskRunning) {
                context.isLikeTaskRunning = false
            } else if (!context.isLikeRequestSent) {
                context.isLikeTaskRunning = true // Change the status of the like task
                fetchLikeStatus(id) // Send the like request to the API

            }

            // Display the shuffle status
            // Update it only if it is different from the current status
            if (this.isShuffle != context.isShuffle
                || this.isSmartShuffle != context.isSmartShuffle
                || this.isShuffleAllowed != context.isShuffleAllowed) {
                context.handleShuffleStatus(isShuffle, isSmartShuffle, isShuffleAllowed)
            }

            // Update the volume and playback status if they are different from the current status
            // Avoid also to update the volume view if the volume is changed (request sent)
            if (context.isVolumeChanged) { // A request has been sent: don't update
                context.isVolumeChanged = false // Change the status of the volume change
            } else if (this.volume != context.percentVolume
                || this.isSupportsVolume != context.isSupportsVolume) {
                // Update the volume view
                context.handleVolume(volume, isSupportsVolume)
            }

            // Update the playback status if it is different from the current status
            // Avoid also to update the playback status if the playback is changed (request sent)
            if (context.isPlaybackChanged) { // A request has been sent: don't update
                context.isPlaybackChanged = false // Change the status of the playback change
            } else if ((this.isCurrentlyPlaying != context.isPlaying) || context.isFirstCall) {
                // Update the playback status
                context.handlePlayButton(this.isCurrentlyPlaying)
            }

            // Change the first call status
            if (context.isFirstCall) {
                context.isFirstCall = false
            }

            // Update the time progress of the track
            // Only if the progress bar isn't touched (avoid flickering)
            if (!context.isBarTouched) {
                val totalMinutes = currentProgress / 60000
                val totalSeconds = (currentProgress % 60000) / 1000
                context.progressTimeView.text =
                    String.format(
                        Locale.getDefault(), "%02d:%02d", totalMinutes, totalSeconds)

                // Animate the progress bar with the new progress
                val percentage = (currentProgress.toDouble() / durationMs.toDouble() * 1000).toInt()
                context.animateProgressBar(percentage)
            }

            // Update the device name if it is different from the current device name
            if (this.deviceName != context.deviceName) {
                context.handleDeviceName(deviceName)
            }
        }
    }

    /**
     * handleArtistImage method of the activity.
     * This method handles the artist image received from the Spotify API.
     * It updates the UI with the artist image provided (background image).
     * @param imageUrl The URL of the artist image
     * @see fetchArtistImage
     */
    private fun handleArtistImage(imageUrl: String?) {
        // First, check if the URL of the image is not null or empty
        if (!imageUrl.isNullOrEmpty()) {
            // Fade out the old image
            imageBackArtist.fadeOutImageArtiste(500) {
                // Once the fadeOut animation is finished, load the new image and start the fadeIn
                // animation
                try {
                    Glide.with(this@PlayerActivity)
                        .load(imageUrl)
                        .apply(if (BLUR_BACKGROUND)
                                    RequestOptions.bitmapTransform(BlurTransformation(
                                        BLUR_VALUE,
                                        if (BLUR_VALUE > 16)
                                            3
                                        else if (BLUR_VALUE > 10)
                                            2
                                        else
                                            1))
                                else RequestOptions())
                        .into(imageBackArtist)

                    imageBackArtist.fadeInImageArtiste(2000) // (duration: 2s)
                } catch (e: Exception) {
                    // An error occurred while loading the image: show a message
                    Toast.makeText(this@PlayerActivity,
                        "Error loading artist image",
                        Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // If the URL of the image is null or empty, show a message
            Toast.makeText(this,
                "Artist image not available",
                Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * handleTrackImage method of the activity.
     * This method handles the track image received from the Spotify API.
     * It updates the UI with the track image provided.
     * @param imageUrl The URL of the track image
     * @param isSwiped Boolean : True if the image is swiped, false if the image is not swiped
     * (used to show the image after the swipe animation)
     * @see handleTrackInfo
     */
    private fun handleTrackImage(imageUrl: String?, isSwiped: Boolean = false) {
        // First, check if the URL of the image is not null or empty
        if (!imageUrl.isNullOrEmpty()) {
            // Fade out the old image
            fadeOutImageTrack(imageMusic, object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Once the fadeOut animation is finished, load the new image and start the
                    // fadeIn animation
                    Picasso.get().load(imageUrl).into(imageMusic, object : Callback {
                        override fun onSuccess() {
                            // The image has been loaded successfully
                            if (isSwiped) {
                                // Show the image after the swipe animation if it is swiped
                                imageMusic.visibility = View.VISIBLE
                            }
                            fadeInImageTrack(imageMusic) // Fade in the image
                        }

                        override fun onError(e: Exception?) {
                            if (isSwiped) {
                                // Show the image after the swipe animation if it is swiped
                                imageMusic.visibility = View.VISIBLE
                            }

                            // An error occurred while loading the image: show a message
                            Toast.makeText(this@PlayerActivity,
                                "Error loading track image",
                                Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            })
        } else {
            if (isSwiped) {
                // Show the image after the swipe animation if it is swiped
                imageMusic.visibility = View.VISIBLE
            }

            // If the URL of the image is null or empty, show a message
            Toast.makeText(this,
                "Track image not available",
                Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * handleTimeProgress method of the activity.
     * This method handles the time progress of the track.
     * It updates the UI with the time progress of the track.
     * It's only called when the progress bar is touched, not when the track is playing.
     * @param progress The progress of the track in percentage
     * @see onCreate for the progress bar listener
     */
    private fun updateTimeProgress(progress: Int) {
        if (isBarTouched) { // Only update the progress time if the progress bar is touched
            // Calculate the progress time in minutes and seconds
            val progressMs = (progress * totalDuration) / 1000
            val totalMinutes = progressMs / 60000
            val totalSeconds = (progressMs % 60000) / 1000
            // Display the progress time in the TextView
            progressTimeView.text =
                String.format(Locale.getDefault(), "%02d:%02d", totalMinutes, totalSeconds)
        }
    }

    /**
     * handlePlayButton method of the activity.
     * This method handles the play button of the music player.
     * It updates the UI with the play button status (play or pause).
     * @param isPlaying Boolean : True if the track is playing, false if the track is paused
     * @see handleTrackInfo
     * @see sendPlaybackRequest
     */
    private fun handlePlayButton(isPlaying: Boolean) {
        // Update the UI with the play button status
        if (isPlaying) {
            // The track is playing: show the pause button
            playPauseButton.setImageResource(R.drawable.ic_play_circle)
            this.isPlaying = true // Update the state of the track
        } else {
            // The track is paused: show the play button
            playPauseButton.setImageResource(R.drawable.ic_pause_circle)
            this.isPlaying = false // Update the state of the track
        }
    }

    /**
     * handleLikeStatus method of the activity.
     * This method handles the like status of the track.
     * It updates the UI with the like status of the track.
     * @param isLiked Boolean : True if the track is liked, false if the track is not liked
     * @see fetchLikeStatus
     * @see sendLikeRequest
     */
    private fun handleLikeStatus(isLiked: Boolean) {
        // Update the UI with the like status of the track
        if (isLiked) {
            // The track is liked: show the like button
            likeButton.setImageResource(R.drawable.ic_like_on)
            this.isLiked = true // Update the state of the track
        } else {
            // The track is not liked: show the unlike button
            likeButton.setImageResource(R.drawable.ic_like_off)
            this.isLiked = false // Update the state of the track
        }
    }

    /**
     * handleShuffleStatus method of the activity.
     * This method handles the shuffle status of the music player.
     * It updates the UI with the shuffle status of the music player.
     * @param isShuffle Boolean : True if the shuffle mode is on, false if the shuffle mode is off
     * @param isSmShuffle Boolean : True if the smart shuffle mode is on, false if the smart shuffle
     * mode is off
     * @param isShuffleAllowed Boolean : True if the shuffle mode is allowed, false if the shuffle
     * mode is not allowed
     * @see handleTrackInfo
     * @see sendLikeRequest
     */
    private fun handleShuffleStatus(
        isShuffle: Boolean, isSmShuffle: Boolean, isShuffleAllowed: Boolean
    ) {
        // Check if the shuffle mode is allowed
        if (isShuffleAllowed) {
            if (isShuffle) {
                // The shuffle mode is on
                if (isSmShuffle) {
                    // The smart shuffle mode is on
                    shuffleButton.setImageResource(R.drawable.ic_shuffle_smart)
                    this.isSmartShuffle = true
                } else {
                    shuffleButton.setImageResource(R.drawable.ic_shuffle_on)
                    this.isSmartShuffle = false
                }

                this.isShuffle = true
            } else {
                // The shuffle mode is off
                shuffleButton.setImageResource(R.drawable.ic_shuffle_off)
                this.isShuffle = false
            }

            this.isShuffleAllowed = true
            shuffleButton.isEnabled = true
            shuffleButton.clearColorFilter()
        } else {
            // Shuffle mode is not allowed: update the UI accordingly and data
            shuffleButton.setImageResource(R.drawable.ic_shuffle_off)
            this.isShuffle = false
            this.isSmartShuffle = false

            // Also, disable the shuffle button and change its color to gray
            shuffleButton.isEnabled = false
            shuffleButton.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
        }
    }

    /**
     * handleVolume method of the activity.
     * This method handles the volume of the music player.
     * It updates the UI with the volume of the music player.
     * @param volume The volume of the music player
     * @param isSupportsVolume Boolean : True if the device supports volume, false if the device
     * does not support volume
     * @see handleTrackInfo
     * @see sendVolumeRequest
     */
    private fun handleVolume(volume: Int?, isSupportsVolume: Boolean?) {
        // Check if the volume is available
        if (volume != null) {
            // Update the volume of the music player
            this.percentVolume = volume

            // Update the progress of the volume bar (UI)
            volumeBar.progress = volume

            // If the device supports volume, show the volume bar, else hide it
            if (isSupportsVolume != null) {
                if (this.isSupportsVolume != isSupportsVolume) {
                    // Update the state of the volume support ans show/hide the volume bar
                    this.isSupportsVolume = isSupportsVolume
                    volumeBar.isVisible = isSupportsVolume
                }
            }
        }
    }

    /**
     * handleDeviceName method of the activity.
     * This method handles the device name of the music player.
     * It updates the UI with the device name of the music player.
     * @param deviceName The name of the device
     * @see handleTrackInfo
     */
    private fun handleDeviceName(deviceName: String?) {
        // Update the device name of the music player
        if (!deviceName.isNullOrEmpty()) {
            this.deviceName = deviceName
            deviceView.text = deviceName
        }
    }

    /**
     * showMessage method of the activity.
     * This method shows a message on the music player.
     * It shows any message on the music player (no track playing, loading, etc.).
     * @param show Boolean : True if the message is shown, false if the message is hidden
     * @param message The message to show (R.string)
     * @param addProgressCircle Boolean : True if a progress circle is added to the message, false
     * if not
     */
    private fun showMessage(show: Boolean, message: Int = 0, addProgressCircle: Boolean = false) {
        // Load the layout of the player
        val layoutPlayer = findViewById<ConstraintLayout>(R.id.layoutPlayer)

        // Show or hide the message
        if (show) {
            /* Hide the controls (not the settings button) */
            progressBar.visibility = View.GONE
            trackTitle.visibility = View.GONE
            trackArtist.visibility = View.GONE
            imageMusic.visibility = View.GONE
            imageBackArtist.visibility = View.GONE
            playPauseButton.visibility = View.GONE
            skipPreviousButton.visibility = View.GONE
            skipNextButton.visibility = View.GONE
            shuffleButton.visibility = View.GONE
            likeButton.visibility = View.GONE
            progressTimeView.visibility = View.GONE
            totalTimeView.visibility = View.GONE
            deviceView.visibility = View.GONE

            /* Remove the TextView and ProgressBar if they are already shown */
            if (isMessageVisible) {
                layoutPlayer.removeView(noPlaybackTextView)
                layoutPlayer.removeView(loadProgressBar)
            }

            /* Create a new TextView with the message */
            noPlaybackTextView = TextView(this)

            /* Set the properties of the TextView */
            noPlaybackTextView!!.text = getString(message) // Set the message
            noPlaybackTextView!!.textSize = 24f // Set the text size
            noPlaybackTextView!!.setTextColor(getColor(R.color.pure_white)) // Set the text color

            /* Add a progress circle if needed */
            if (addProgressCircle) {
                loadProgressBar = ProgressBar(this)
            }

            /* Set the layout parameters of the TextView */
            val layoutParams = ConstraintLayout.LayoutParams( // Fill the parent
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT)
            // Define the constraints of the TextView
            layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            // Set the padding of the TextView if a progress circle is added to add space between
            // the message and the circle
            if (addProgressCircle) {
                noPlaybackTextView!!.setPadding(0, 300, 0, 0)
            }
            // Set the layout parameters to the TextView
            noPlaybackTextView!!.layoutParams = layoutParams

            /* Create the progress circle if needed */
            if (addProgressCircle) {
                val progressLayoutParams = ConstraintLayout.LayoutParams( // Fill the parent
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT)
                // Define the constraints of the ProgressBar
                progressLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                progressLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                progressLayoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                progressLayoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                loadProgressBar!!.layoutParams = progressLayoutParams // Set the layout parameters
            }

            /* Add the TextView and ProgressBar to the layout */
            layoutPlayer.addView(noPlaybackTextView)
            if (addProgressCircle) { // Add the ProgressBar if needed
                layoutPlayer.addView(loadProgressBar)
            }

            /* Update the state of the message */
            isMessageVisible = true
        } else {
            // Hide the message only if it is shown
            if (isMessageVisible) {
                // Show the controls
                progressBar.visibility = View.VISIBLE
                trackTitle.visibility = View.VISIBLE
                trackArtist.visibility = View.VISIBLE
                imageMusic.visibility = View.VISIBLE
                imageBackArtist.visibility = View.VISIBLE
                playPauseButton.visibility = View.VISIBLE
                skipPreviousButton.visibility = View.VISIBLE
                skipNextButton.visibility = View.VISIBLE
                shuffleButton.visibility = View.VISIBLE
                likeButton.visibility = View.VISIBLE
                progressTimeView.visibility = View.VISIBLE
                totalTimeView.visibility = View.VISIBLE
                deviceView.visibility = View.VISIBLE

                // Remove the TextView and ProgressBar
                layoutPlayer.removeView(noPlaybackTextView)
                layoutPlayer.removeView(loadProgressBar)
            }

            // Update the state of the message
            isMessageVisible = false

        }
    }

    /**
     * showControls method of the activity.
     * This method shows the controls of the music player.
     * It shows the controls of the music player (play, pause, skip, shuffle, etc.).
     * @param show Boolean : True if the controls are shown, false if the controls are hidden
     * @see onSingleTapUp and onSingleTapConfirmed
     * @see startControlsTimer
     */
    private fun showControls(show: Boolean = true) {
        if (show) {
            // Show the controls button only if it's authorized (settings value)
            if (HIDE_CONTROLS) {
                // Show the controls with a delay depending on the position in the layout
                progressBar.animate().alpha(1f).setDuration(100).start()
                playPauseButton.animate().alpha(1f).setDuration(200).start()
                skipPreviousButton.animate().alpha(1f).setDuration(300).start()
                skipNextButton.animate().alpha(1f).setDuration(300).start()
                shuffleButton.animate().alpha(1f).setDuration(400).start()
                likeButton.animate().alpha(1f).setDuration(400).start()
                progressTimeView.animate().alpha(1f).setDuration(500).start()
                totalTimeView.animate().alpha(1f).setDuration(500).start()
            }

            // Show the settings button and the device name
            // They are always shown however the settings value
            settingsButton.animate().alpha(1f).setDuration(400).start()
            deviceView.animate().alpha(1f).setDuration(400).start()

        } else {
            // Hide the controls button only if it's authorized (settings value)
            if (HIDE_CONTROLS) {
                // Hide the controls with a delay depending on the position in the layout
                progressBar.animate().alpha(0f).setDuration(500).start()
                playPauseButton.animate().alpha(0f).setDuration(400).start()
                skipPreviousButton.animate().alpha(0f).setDuration(300).start()
                skipNextButton.animate().alpha(0f).setDuration(300).start()
                shuffleButton.animate().alpha(0f).setDuration(200).start()
                likeButton.animate().alpha(0f).setDuration(200).start()
                progressTimeView.animate().alpha(0f).setDuration(100).start()
                totalTimeView.animate().alpha(0f).setDuration(100).start()
            }

            // Hide the settings button and the device name
            // They are always shown however the settings value
            settingsButton.animate().alpha(0f).setDuration(200).start()
            deviceView.animate().alpha(0f).setDuration(200).start()
        }


        // Change the state of the controls (only if the settings value allows it)
        if (HIDE_CONTROLS) {
            // Allow or disallow the click
            progressBar.isClickable = show
            playPauseButton.isClickable = show
            skipPreviousButton.isClickable = show
            skipNextButton.isClickable = show
            shuffleButton.isClickable = show
            likeButton.isClickable = show
            progressTimeView.isClickable = show
            totalTimeView.isClickable = show

            // Allow or disallow the focus
            progressBar.isFocusable = show
            playPauseButton.isFocusable = show
            skipPreviousButton.isFocusable = show
            skipNextButton.isFocusable = show
            shuffleButton.isFocusable = show
            likeButton.isFocusable = show
            progressTimeView.isFocusable = show
            totalTimeView.isFocusable = show
        }

        // Same for the settings button and the device name but not depending on the settings value
        settingsButton.isClickable = show
        deviceView.isClickable = show

        settingsButton.isFocusable = show
        deviceView.isFocusable = show

        // Update the state of the controls visibility
        isControlsVisible = show
    }

    /**
     * showVolume method of the activity.
     * This method shows the volume bar of the music player.
     * @param show Boolean : True if the volume bar is shown, false if the volume bar is hidden
     * @see onSwipeUp and onSwipeDown
     * @see startVolumeTimer
     */
    private fun showVolume(show: Boolean = true) {
        // Display or not the volume bar with an animation
        if (show) {
            volumeBar.animate().alpha(1f).setDuration(200).start()
        } else {
            volumeBar.animate().alpha(0f).setDuration(300).start()
        }

        // Activate or deactivate the volume bar
        volumeBar.isClickable = show
        volumeBar.isFocusable = show
    }

    /**
     * displayNoInternetDialog method of the activity.
     * This method displays a dialog when the Internet connection is not available.
     * It shows a dialog to the user to connect to the Internet and try again.
     * @see DialogUtil
     */
    private fun displayNoInternetDialog() {
        noInternerDialog = DialogUtil.displayCustomDialog(
            context = this,
            message = "The connection to Spotify is impossible. Please connect to the Internet and"
                    + "try again.",
            positiveButtonText = "Retry",
            onPositiveClick = {
                // Retry the connection to Spotify
                checkTokenAndStartFetchingTask()
            },
            isCancelable = false // Disallow the user to cancel the dialog
        )
    }

    /**
     * displayAuthenticationErrorDialog method of the activity.
     * This method displays a dialog when an authentication error occurs.
     * It shows a dialog to the user to change the client ID or retry the authentication.
     * @see DialogUtil
     */
    private fun displayAuthenticationErrorDialog() {
        connectionErrorDialog = DialogUtil.displayCustomDialog(
            context = this,
            message = "An error occurred during authentication. Please try again.",
            positiveButtonText = "Change client ID",
            negativeButtonText = "Retry",
            onPositiveClick = {
                // Remove the client ID from the SharedPreferences
                sharedPref.edit().remove("client_id").apply()

                // Redirect the user to the login page
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            },
            onNegativeClick = {
                // Retry the authentication
                this.isAuthInError = false // Reset the authentication error status
                startSpotifyAuthentication()
            },
            isCancelable = false // Disallow the user to cancel the dialog
        )
    }


    /* ##################################### Timer methods ###################################### */

    /**
     * dispatchTouchEvent method of the activity.
     * override dispatchTouchEvent.
     * This method dispatches the touch event to the appropriate view.
     * It resets the controls timer on each touch event and starts the controls timer.
     * @param ev The motion event to dispatch
     * @return Boolean : True if the touch event is dispatched, false if not
     * @see resetControlsTimer
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Reset the controls timer on each touch event (everywhere on the screen)
        resetControlsTimer()
        startControlsTimer()
        return super.dispatchTouchEvent(ev)
    }

    /**
     * startControlsTimer method of the activity.
     * This method starts the controls timer (for the controls visibility).
     * It starts a timer to hide the controls after a certain time.
     * @see resetControlsTimer
     */
    private fun startControlsTimer() {
        // Cancel the existing timer if it exists
        resetControlsTimer()

        // Start a new timer
        countdownTimer = object :
            CountDownTimer(DISPLAY_TIME  * 1000L, 1000)
        {
            override fun onTick(millisUntilFinished: Long) {
                // Do nothing on each tick
            }

            override fun onFinish() {
                // When the timer ends, hide the controls
                showControls(false)
            }
        }.start()
    }

    /**
     * resetControlsTimer method of the activity.
     * This method resets the controls timer.
     * It cancels the existing timer if it exists.
     * @see startControlsTimer
     */
    private fun resetControlsTimer() {
        // Cancel the existing timer if it exists
        countdownTimer?.cancel()
    }

    /**
     * startVolumeTimer method of the activity.
     * This method starts the volume timer (for the volume bar visibility).
     * It starts a timer to hide the volume bar after a certain time.
     * @see resetVolumeTimer
     */
    private fun startVolumeTimer() {
        // Reset the existing timer if it exists
        resetVolumeTimer()

        // Start a new timer
        countdownVolumeTimer = object :
            CountDownTimer(volumeTimerSec * 1000L, 1000)
        {
            override fun onTick(millisUntilFinished: Long) {
                // Do nothing on each tick
            }

            override fun onFinish() {
                // When the timer ends, hide the volume bar
                showVolume(false)
            }
        }.start()
    }

    /**
     * resetVolumeTimer method of the activity.
     * This method resets the volume timer.
     * It cancels the existing timer if it exists.
     * @see startVolumeTimer
     */
    private fun resetVolumeTimer() {
        // Cancel the existing timer if it exists
        countdownVolumeTimer?.cancel()
    }


    /* ################################## Auto-refreshing task ################################## */

    /**
     * startFetchingTask method of the activity.
     * This method starts the auto-fetching task.
     * It starts a task to fetch the track information from the Spotify API.
     * @see fetchRunnable
     */
    private fun startFetchingTask() {
        // Start the auto-fetching task with a delay (fetchInterval) and the fetchRunnable object
        fetchHandler.postDelayed(fetchRunnable, fetchInterval)
    }

    /**
     * stopFetchingTask method of the activity.
     * This method stops the auto-fetching task.
     * It stops the task to fetch the track information from the Spotify API.
     * @see fetchRunnable
     */
    private fun stopFetchingTask() {
        // Stop the auto-fetching task
        fetchHandler.removeCallbacks(fetchRunnable)
    }

    /**
     * fetchRunnable object of the activity.
     * This object is a Runnable to fetch the track information from the Spotify API.
     * It fetches the track information from the Spotify API if the access token is not expired.
     * @see startFetchingTask
     */
    private val fetchRunnable = object : Runnable {
        // Use a Runnable to fetch the track information from the Spotify API
        override fun run() {
            if (isTokenExpired()) {
                // If the access token is expired, redirect the user to the login page
                startSpotifyAuthentication()
            } else {
                // If the access token is not expired, fetch the track information
                fetchCurrentTrack()
            }

            // Schedule the next execution of the task after the specified delay
            fetchHandler.postDelayed(this, fetchInterval)
            return
        }
    }



    /* #################################### Fetching methods #################################### */

    /**
     * fetchTrackFromApi method of the activity.
     * This method fetches starts the fetching task to get the track information from the Spotify
     * API.
     * It use coroutines to fetch the track information with asynchronous tasks.
     * @see fetchTrackFromApi
     */
    private fun fetchCurrentTrack() {
        // Create a coroutine to fetch the track information
        // Coroutine is used to fetch the track information asynchronously (non-blocking)
        CoroutineScope(Dispatchers.Main).launch {
            // Fetch the track information from the Spotify API and wait for the result
            val track = withContext(Dispatchers.IO) {
                fetchTrackFromApi() // Call the fetchTrackFromApi method
            }

            // Check if the track is a Boolean (error) or a String (data)
            if (track is Boolean) {
                return@launch
            } else {
                track as String // Transform the track to a String
            }

            // Check if no track is playing and show a message
            if (!checkTrackState(track)) {
                return@launch
            }

            // Extract the track information from the JSON data
            val trackData = JSONObject(track)
            val trackInfo = extractTrackInfo(trackData)

            // Check the result of the extraction
            if (trackInfo.id.isEmpty()) {
                return@launch
            }

            // Handle the track information (Update the UI)
            handleTrackInfo(trackInfo)
        }
    }

    /**
     * extractTrackInfo method of the activity.
     * This method extracts the track information from the JSON data received from the Spotify API.
     * It extracts the track information from the JSON data and returns a TrackInfo object.
     * @param trackData The JSON data received from the Spotify API
     * @return TrackInfo object with the track information
     * @see fetchCurrentTrack
     */
    private fun extractTrackInfo(trackData: JSONObject): TrackInfo {

        // Check if the track data is empty, if so, return a default TrackInfo object
        val item = trackData.optJSONObject("item") ?:
        return TrackInfo(
            "", false, false, "", emptyList(), 0,0,
            null, false, false, false, false, null,
            false, null
        )

        /* Extract the status of the track */
        val isPlaying = trackData.optBoolean("is_playing")
        val isShuffle = trackData.optBoolean("shuffle_state")
        val isSmartShuffle = trackData.optBoolean("smart_shuffle")

        /* Extract the device information */
        val device = trackData.optJSONObject("device")
        val volume = device?.optInt("volume_percent")
        val deviceName = device?.optString("name")
        val isSupportsVolume = device?.optBoolean("supports_volume")

        // Get the disallows object to check if toggling shuffle is allowed
        val togglingShuffle = try {
            val actions = trackData.getJSONObject("actions")
            val disallows = actions.getJSONObject("disallows")
            disallows.getBoolean("toggling_shuffle")
        } catch (e: Exception) {
            false
        }

        /* Extract the id of the track */
        val id = item.optString("id")

        /* Check if the track is the same as the previous one */
        var isSameTrack = true
        if (id != this.previousTrackId) {
            isSameTrack = false
            this.previousTrackId = id
        }

        /* Extract the artists of the track */
        val artistsArray = item.optJSONArray("artists")
        val artistsList =
            mutableListOf<Pair<String, String>>() // List of artists (name, href)
        artistsArray?.let {
            for (i in 0 until it.length()) {
                val artistObject = it.getJSONObject(i)
                val artistName = artistObject.optString("name")
                val artistHref = artistObject.optString("href")
                artistsList.add(Pair(artistName, artistHref))
            }
        }

        /* Extract the name and duration of the track */
        val name = item.optString("name")
        val durationMs = item.optLong("duration_ms")
        val currentProgress = trackData.optLong("progress_ms")

        /* Extract the album and the image of the track */
        val album = item.optJSONObject("album")
        val imagesArray = album?.optJSONArray("images")

        /* Check if the image is the same as the previous one */
        var imageUrl: String? = null
        var isSameImage = false
        if (imagesArray != null && imagesArray.length() > 0) {
            // Get the URL of the first image (the largest, i.e., the one with the lowest index)
            imageUrl = imagesArray.getJSONObject(0).optString("url")

            // Check if the image is the same as the previous one
            if (this.previousTrackImage != imageUrl) {
                // Update the previous image if it is different for the next comparison
                this.previousTrackImage = imageUrl
            } else {
                isSameImage = true
            }
        }

        // Return the TrackInfo object with the extracted information
        // Define the opposite of toggling shuffle to allow the user to toggle shuffle because the
        // API returns the disallows action
        return TrackInfo(
            id, isSameTrack, isPlaying, name, artistsList, durationMs, currentProgress,
            imageUrl, isSameImage, isShuffle, isSmartShuffle, !togglingShuffle, volume,
            isSupportsVolume, deviceName
        )
    }

    /**
     * fetchArtistImage method of the activity.
     * This methode starts the fetching task to get the artist image from the Spotify API.
     * It use coroutines to fetch the artist image with asynchronous tasks.
     * @param artistLink The link of the artist to get the image
     * @see handleTrackInfo
     */
    private fun fetchArtistImage(artistLink: String) {
        // Create a coroutine to fetch the artist image
        // Coroutine is used to fetch the artist image asynchronously (non-blocking)
        CoroutineScope(Dispatchers.Main).launch {
            // Fetch the artist image from the Spotify API and wait for the result
            val response = withContext(Dispatchers.IO) {
                fetchArtistImageFromApi(artistLink)
            }

            // Check if the response is a Boolean (error) or a String (data)
            // Also check if the response is a JSON object (avoid errors with extractArtistImage)
            if (response is Boolean || (response as String).first() != '{') {
                return@launch
            }

            // Extract the artist image URL from the JSON data
            extractArtistImage(response)
        }
    }

    /**
     * extractArtistImage method of the activity.
     * This method extracts the artist image from the JSON data received from the Spotify API.
     * It extracts the artist image URL from the JSON data and updates the UI with the artist image.
     * @param response The JSON data received from the Spotify API
     * @see fetchArtistImageFromApi
     * @see handleArtistImage
     */
    private fun extractArtistImage(response: String) {
        // Check if the image is empty, if so, show a message and return
        if (response.isEmpty()) {
            return
        }

        // Load the JSON data
        val artistData = JSONObject(response)

        // Check if the response is an error, if so, show a message and return
        val errorObject = artistData.optJSONObject("error")
        if (errorObject != null) {
            val errorMessage = errorObject.optString("message")
            Toast.makeText(
                this,
                "Error fetching artist image: $errorMessage",
                Toast.LENGTH_SHORT).show()
            return
        }

        // Extract the images URL from the JSON data
        val imagesArray = artistData.optJSONArray("images")

        // Check if the image is the same as the previous one
        var imageUrl: String? = null
        var isSameImage = false
        if (imagesArray != null && imagesArray.length() > 0) {
            // Get the URL of the first image (the largest, i.e., the one with the lowest index)
            imageUrl = imagesArray.getJSONObject(0).optString("url")

            // Check if the image is the same as the previous one
            if (this.previousArtistImage != imageUrl) {
                // Update the previous image for the next comparison
                this.previousArtistImage = imageUrl
            } else {
                isSameImage = true
            }
        }

        // Handle the artist image (Update the UI) only if the image is different
        if (!isSameImage || this.isFirstArtistCall) {
            this.isFirstArtistCall = false
            this.handleArtistImage(imageUrl)
        }
    }

    /**
     * fetchedLikeStatus method of the activity.
     * This method starts the fetching task to get the like status of the track from the Spotify
     * API.
     * It use coroutines to fetch the like status with asynchronous tasks.
     * @param trackId The ID of the track to get the like status
     * @see handleTrackInfo
     */
    private fun fetchLikeStatus(trackId: String) {
        // Create a coroutine to fetch the like status of the track
        // Coroutine is used to fetch the like status asynchronously (non-blocking)
        CoroutineScope(Dispatchers.Main).launch {
            // Fetch the like status of the track from the Spotify API and wait for the result
            val response = withContext(Dispatchers.IO) {
                fetchLikeStatusFromApi(trackId)
            }

            // Check if the response is a Boolean (error) or a String (data)
            // Also check if the response is a JSON array (avoid errors with extractLikeStatus)
            if (response is Boolean || (response as String).first() != '[') {
                return@launch
            }

            // Extract the like status from the JSON data
            extractLikeStatus(response)
        }
    }

    /**
     * extractLikeStatus method of the activity.
     * This method extracts the like status of the track from the JSON data received from the
     * Spotify API.
     * It extracts the like status from the JSON data and updates the UI with the like status.
     * @param response The JSON data received from the Spotify API
     * @see fetchLikeStatus
     * @see handleLikeStatus
     */
    private fun extractLikeStatus(response: String) {
        // Check if the response is empty, if so, show a message and return
        if (response.isEmpty()) {
            return
        }

        // Try to process the response as a JSON array
        try {
            // Get the like status of the track from the JSON array
            val isLiked = JSONArray(response).getBoolean(0)

            // Update the UI with the like status of the track only if it is different
            if (isLiked != this.isLiked) {
                this.handleLikeStatus(isLiked)
            }
        } catch (e: JSONException) {
            // If a JSONException is thrown, check if the response is a JSON error object
            if (response.contains("Too many requests")) {
                // Show a message if the user sends too many requests
                Toast.makeText(
                    this@PlayerActivity,
                    "Too many Like requests, please use only one device at a time with the" +
                            "same client ID",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // If the response is not a JSON array, show a message with the response content
                // Otherwise, show a generic message
                try {
                    // Try to extract the error message from the JSON response
                    val errorObject = JSONObject(response).optJSONObject("error")
                    if (errorObject != null) {
                        val errorMessage = errorObject.optString("message")
                        // Show the error message
                        Toast.makeText(
                            this@PlayerActivity,
                            "Error in like request: $errorMessage",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {
                        // Show a generic message
                        Toast.makeText(
                            this@PlayerActivity,
                            "Error processing like response",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (jsonException: JSONException) {
                    // Ignore the JSON exception
                    return
                }
            }
        }
    }
}

