package com.example.spotifyplayback

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * DialogUtil is a utility object that displays a custom dialog.
 * @property displayCustomDialog Displays a custom dialog with the specified message and actions.
 */
object DialogUtil {

    /**
     * displayCustomDialog displays a custom dialog with the specified message and actions.
     * @param context The context in which the dialog will be displayed.
     * @param message The message to be displayed in the dialog.
     * @param positiveButtonText The text to be displayed on the positive button.
     * @param negativeButtonText The text to be displayed on the negative button. If it is null, the
     * negative button will be hidden. (Optional)
     * @param onPositiveClick The action to be performed when the positive button is clicked.
     * (Optional)
     * @param onNegativeClick The action to be performed when the negative button is clicked.
     * (Optional)
     * @param isCancelable Whether the dialog is cancelable or not. (Optional)
     * @return The dialog that was created.
     */
    fun displayCustomDialog(
        context: Context, message: String, positiveButtonText: String,
        negativeButtonText: String = null.toString(), onPositiveClick: () -> Unit = {},
        onNegativeClick: () -> Unit = {}, isCancelable: Boolean = true) : AlertDialog
    {
        // Inflate the custom dialog layout
        val dialogView: View =
            LayoutInflater.from(context).inflate(R.layout.custom_dialog, null)

        // Get the views from the custom dialog layout
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialog_message)
        val positiveButton = dialogView.findViewById<Button>(R.id.dialog_positive_button)
        val negativeButton = dialogView.findViewById<Button>(R.id.dialog_negative_button)

        // Set the message and button text
        dialogMessage.text = message
        positiveButton.text = positiveButtonText
        negativeButton.text = negativeButtonText

        // Create the dialog with the custom layout
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(isCancelable) // Set whether the dialog is cancelable
            .create()

        // Calculating the desired width based on 80% of the screen width
        val screenWidth = context.resources.displayMetrics.widthPixels
        val desiredWidth = (screenWidth * 0.8).toInt()

        // Défine a maximum width for the dialog
        val maxWidth = context.resources.getDimensionPixelSize(R.dimen.dialog_max_width)

        // Use the smaller of the two values as the width
        val width = desiredWidth.coerceAtMost(maxWidth)

        // Apply the width to the dialog
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Set the dialog animation
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        // Set the positive button click listener
        positiveButton.setOnClickListener {
            dialog.dismiss()
            onPositiveClick()
        }

        // Set the negative button click listener
        // If the negative button text is null, hide the button
        if (negativeButtonText == null.toString()) {
            // Hide the negative button
            negativeButton.visibility = View.GONE
        } else {
            if (negativeButton.visibility == View.GONE) {
                // Show the negative button if it is hidden
                negativeButton.visibility = View.VISIBLE
            }

            negativeButton.setOnClickListener {
                dialog.dismiss()
                onNegativeClick()
            }
        }

        // Show the dialog
        dialog.show()

        return dialog
    }
}