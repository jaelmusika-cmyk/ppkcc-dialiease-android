package com.example.dialiease2.fragments

import androidx.core.content.res.ResourcesCompat
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import android.content.res.ColorStateList
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.example.dialiease2.R
import com.example.dialiease2.databinding.FragmentScheduleBinding
import com.example.dialiease2.utils.Constants
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.example.dialiease2.utils.UnsafeOkHttpClient

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()
    private lateinit var userEmail: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        loadHomePageData()
        return binding.root
    }

    private fun loadHomePageData() {
        val sharedPref = requireActivity().getSharedPreferences("DialiEasePrefs", Context.MODE_PRIVATE)
        val email = sharedPref.getString("user_email", null)

        if (email == null) {
            // binding.scheduleStatus.text = "No email found" <-- DELETE THIS LINE
            return
        }
        userEmail = email

        val json = JSONObject().apply {
            put("email", email)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.BASE_URL + "fetch_home_data.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    if (isAdded && _binding != null) {
                        // binding.scheduleStatus.text = "Failed to load data" <-- DELETE THIS LINE
                        // You can optionally add a Toast here if you want
                        // Toast.makeText(requireContext(), "Failed to load data", Toast.SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = JSONObject(response.body?.string() ?: "")
                activity?.runOnUiThread {
                    if (isAdded && _binding != null) {
                        displayAnnouncements(result.optJSONArray("announcements"))
                        displayReminders(result.optJSONArray("reminders"))
                        displayNotifications(result.optJSONArray("notifications"))
                        // displaySchedule(...) was already correctly removed
                    }
                }
            }
        })
    }

    private fun displayAnnouncements(announcements: JSONArray?) {
        binding.announcementsContainer.removeAllViews()
        if (announcements != null && announcements.length() > 0) {
            binding.announcementsContainer.visibility = View.VISIBLE
            for (i in 0 until announcements.length()) {
                val item = announcements.getJSONObject(i)
                val card = createAnnouncementCard(
                    item.getString("title"),
                    item.getString("message")
                )
                binding.announcementsContainer.addView(card)
            }
        } else {
            // --- THIS IS THE CHANGE ---
            binding.announcementsContainer.visibility = View.VISIBLE
            val emptyCard = createEmptyStateCard(
                requireContext(), // Pass context
                "No announcements at this time.",
                android.R.drawable.ic_dialog_info
            )
            binding.announcementsContainer.addView(emptyCard)
            // --- END OF CHANGE ---
        }
    }

    private fun displayReminders(reminders: JSONArray?) {
        binding.remindersContainer.removeAllViews()
        if (reminders != null && reminders.length() > 0) {
            binding.remindersContainer.visibility = View.VISIBLE
            for (i in 0 until reminders.length()) {
                val item = reminders.getJSONObject(i)
                val card = createReminderCard(
                    item.getString("date_of_absence"),
                    item.getString("amount")
                )
                binding.remindersContainer.addView(card)
            }
        } else {
            // --- THIS IS THE CHANGE ---
            binding.remindersContainer.visibility = View.VISIBLE
            val emptyCard = createEmptyStateCard(
                requireContext(), // Pass context
                "You have no pending updates or fees.",
                android.R.drawable.ic_menu_today
            )
            binding.remindersContainer.addView(emptyCard)
            // --- END OF CHANGE ---
        }
    }

    private fun displayNotifications(notifications: JSONArray?) {
        binding.notificationsContainer.removeAllViews()
        if (notifications != null && notifications.length() > 0) {
            binding.notificationsContainer.visibility = View.VISIBLE
            for (i in 0 until notifications.length()) {
                val item = notifications.getJSONObject(i)
                val card = createNotificationCard(
                    item.getInt("id"),
                    item.getString("message")
                )
                binding.notificationsContainer.addView(card)
            }
        } else {
            // --- THIS IS THE CHANGE ---
            // Show the container and add the empty state card
            binding.notificationsContainer.visibility = View.VISIBLE
            val emptyCard = createEmptyStateCard(
                requireContext(), // Pass context
                "You have no new notifications.",
                android.R.drawable.ic_notification_clear_all // A fitting icon
            )
            binding.notificationsContainer.addView(emptyCard)
            // --- END OF CHANGE ---
        }
    }



    private fun createAnnouncementCard(title: String, message: String): View {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            radius = 24f
            setCardBackgroundColor(Color.parseColor("#E3F2FD")) // Light Blue
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32)
        }
        val titleText = TextView(requireContext()).apply {
            text = "📢 $title"
            textSize = 16f
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_bold)
            setTextColor(ContextCompat.getColor(context, R.color.black)) // <-- ADD THIS LINE
        }
        val messageText = TextView(requireContext()).apply {
            text = message
            textSize = 14f
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
            setTextColor(ContextCompat.getColor(context, R.color.black)) // <-- ADD THIS LINE
        }
        layout.addView(titleText)
        layout.addView(messageText)
        card.addView(layout)
        return card
    }

    private fun createReminderCard(date: String, amount: String): View {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            radius = 24f
            setCardBackgroundColor(Color.parseColor("#FFCDD2")) // Light Red
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32)
        }
        val titleText = TextView(requireContext()).apply {
            text = "💰 No-Call No-Show Fee Reminder"
            textSize = 16f
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_bold)
            setTextColor(Color.parseColor("#B71C1C")) // Dark Red
        }
        val dateText = TextView(requireContext()).apply {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val parsedDate = inputFormat.parse(date)
            text = "Date of Absence: ${outputFormat.format(parsedDate!!)}"
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
            setTextColor(ContextCompat.getColor(context, R.color.black)) // <-- ADD THIS LINE
        }
        val amountText = TextView(requireContext()).apply {
            text = "Amount Payable: ₱${"%.2f".format(amount.toDouble())}"
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_bold)
            setTextColor(ContextCompat.getColor(context, R.color.black)) // <-- ADD THIS LINE
        }
        layout.addView(titleText)
        layout.addView(dateText)
        layout.addView(amountText)
        card.addView(layout)
        return card
    }

    private fun createNotificationCard(id: Int, message: String): View {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            radius = 24f
        }
        val rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32)
            gravity = Gravity.CENTER_VERTICAL
        }
        val messageText = TextView(requireContext()).apply {
            text = message
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val dismissButton = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setOnClickListener {
                dismissNotification(id, card)
            }
        }
        rootLayout.addView(messageText)
        rootLayout.addView(dismissButton)
        card.addView(rootLayout)
        return card
    }

    private fun dismissNotification(notificationId: Int, card: View) {
        val json = JSONObject().apply {
            put("email", userEmail)
            put("notification_id", notificationId)
        }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.BASE_URL + "dismiss_notification.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* Do nothing on failure */ }
            override fun onResponse(call: Call, response: Response) {
                val result = JSONObject(response.body?.string() ?: "")
                if (result.optString("status") == "success") {
                    activity?.runOnUiThread {
                        (card.parent as? ViewGroup)?.removeView(card)
                        if (binding.notificationsContainer.childCount == 0) {
                            binding.notificationsContainer.visibility = View.GONE
                        }
                    }
                }
            }
        })
    }

    private fun createStyledScheduleView(day: String, shift: String, startTime: String, duration: String): View {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setColor(
                    if (shift == "First Shift")
                        Color.parseColor("#C8E6C9") // light green
                    else
                        Color.parseColor("#FFF9C4") // light gold
                )
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val start = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).parse(startTime)
        val startFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(start ?: Date())
        val durationInt = duration.toIntOrNull() ?: 0
        val endMillis = (start?.time ?: 0L) + durationInt * 60 * 1000
        val endFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(endMillis))

        val dayText = TextView(context).apply {
            text = day
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_bold)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 16f
            setTextColor(Color.BLACK)
        }

        val shiftText = TextView(context).apply {
            text = "$shift | $startFormatted to $endFormatted"
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }

        container.addView(dayText)
        container.addView(shiftText)

        return container
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Creates a stylish "empty state" card to fill sections
 * that have no content.
 */
private fun createEmptyStateCard(context: Context, message: String, iconResId: Int): View {
    // 1. Create the dashed border background
    val shape = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 24f
        setColor(Color.parseColor("#F5F5F5")) // Light gray background
        setStroke(4, Color.parseColor("#DDDDDD"), 10f, 10f) // Dashed border
    }

    // 2. Create the CardView
    val card = CardView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) }
        radius = 24f
        elevation = 0f // No shadow, let the border do the work
        background = shape
    }

    // 3. Create the inner layout
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48)
        gravity = Gravity.CENTER
    }

    // 4. Create the Icon
    val icon = ImageView(context).apply {
        setImageResource(iconResId)
        imageTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E")) // Gray tint
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 }
    }

    // 5. Create the Message Text
    val messageText = TextView(context).apply {
        text = message
        textSize = 14f
        typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
        setTextColor(Color.parseColor("#757575")) // Darker gray
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    // 6. Assemble the view
    layout.addView(icon)
    layout.addView(messageText)
    card.addView(layout)

    return card
}