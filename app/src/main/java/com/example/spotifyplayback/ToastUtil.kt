package com.example.spotifyplayback

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Custom toast utility object to display a custom toast message.
 * @property showCustomToast Displays a custom toast message with a custom layout.
 */
object ToastUtil {

    /**
     * showCustomToast displays a custom toast message with a custom layout.
     * @param context The context in which the toast will be displayed.
     * @param message The message to be displayed in the toast.
     * @suppress DEPRECATION For the setView() method in the toast. Force set the view with
     * setView() method to have the custom text properties
     */
    @Suppress("DEPRECATION") // For the setView() method in the toast
    fun showCustomToast(context: Context, message: String) {
        // Create a temporary parent view group
        val tempParent = FrameLayout(context)

        // Inflate the custom layout for the toast
        val inflater = LayoutInflater.from(context)
        val layout: View? = inflater.inflate(R.layout.custom_toast, tempParent, false)

        // If the layout is not null, proceed with creating the toast
        layout?.let {
            // Set the message in the custom layout
            val text: TextView = it.findViewById(R.id.toast_text)
            text.text = message

            // Create the toast and set its properties
            val toast = Toast(context)
            toast.view = it // Force set the view with setView() method to have the custom text properties
            toast.duration = Toast.LENGTH_SHORT

            // Center the toast horizontally and offset it vertically
            toast.setGravity(Gravity.TOP, 0, getToastOffset(context))

            // Load animations for enter and exit
            val enterAnimation = AnimationUtils.loadAnimation(context, R.anim.toast_enter)
            val exitAnimation = AnimationUtils.loadAnimation(context, R.anim.toast_exit)

            // Set enter animation
            it.startAnimation(enterAnimation)

            // Set exit animation and remove view after animation ends
            exitAnimation.setAnimationListener(object :
                android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    toast.cancel() // Remove the view from the toast
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })

            // Show the toast
            toast.show()

            // Start exit animation after toast duration
            it.postDelayed({
                it.startAnimation(exitAnimation)
            }, (Toast.LENGTH_SHORT + 3000).toLong()) // 3s delay before exit animation
        } ?: run {
            // Handle case where layout is null
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Calculate the offset for the toast based on the activity and screen size.
     * Also, if the activity is Settings, the offset is increased by 50 (action bar).
     */
    private fun getToastOffset(context: Context): Int {
        // If the activity is Settings, increase the offset by 50
        val activityName = context.javaClass.simpleName
        var offsetY = if (activityName == "SettingsActivity") 150 else 100

        // Adjust the offset if screen width is less than 600dp
        if (context.resources.displayMetrics.widthPixels /
            context.resources.displayMetrics.density < 600) {
            offsetY += 50
        }

        return offsetY
    }
}