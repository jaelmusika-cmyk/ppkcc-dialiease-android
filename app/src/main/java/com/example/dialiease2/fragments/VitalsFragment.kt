package com.example.dialiease2.fragments

import com.example.dialiease2.R
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.example.dialiease2.databinding.FragmentVitalsBinding
import com.example.dialiease2.utils.Constants
import com.example.dialiease2.utils.UnsafeOkHttpClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VitalsFragment : Fragment() {

    private var _binding: FragmentVitalsBinding? = null
    private val binding get() = _binding!!
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()
    private val calendar: Calendar = Calendar.getInstance()
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVitalsBinding.inflate(inflater, container, false)

        binding.btnCalendar.setOnClickListener {
            showDatePicker()
        }

        // Fetch vitals for today initially
        fetchVitalsForDate(calendar.time)

        return binding.root
    }

    private fun showDatePicker() {
        if (!isAdded) return
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            fetchVitalsForDate(calendar.time)
        }

        DatePickerDialog(
            requireContext(),
            dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun fetchVitalsForDate(date: Date) {
        if (!isAdded || _binding == null) return

        val selectedApiDate = apiDateFormat.format(date)
        val selectedDisplayDate = displayDateFormat.format(date)
        binding.textSelectedDate.text = selectedDisplayDate

        val sharedPref = requireActivity().getSharedPreferences("DialiEasePrefs", Context.MODE_PRIVATE)
        val email = sharedPref.getString("user_email", null)

        if (email == null) {
            binding.textNoRecords.text = "Error: Could not find user email."
            binding.textNoRecords.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.vitalsContainer.removeAllViews()
        binding.textNoRecords.visibility = View.GONE

        val json = JSONObject().apply {
            put("email", email)
            put("date", selectedApiDate)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.BASE_URL + "fetch_patient_vitals.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    binding.progressBar.visibility = View.GONE
                    binding.textNoRecords.text = "Failed to load vitals due to a network error."
                    binding.textNoRecords.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    binding.progressBar.visibility = View.GONE
                    try {
                        val result = JSONObject(responseData ?: "")
                        if (result.getString("status") == "success") {
                            val vitalsArray = result.getJSONArray("data")
                            if (vitalsArray.length() > 0) {
                                for (i in 0 until vitalsArray.length()) {
                                    val vitalRecord = vitalsArray.getJSONObject(i)
                                    val card = createVitalCard(vitalRecord)
                                    binding.vitalsContainer.addView(card)
                                }
                            } else {
                                binding.textNoRecords.text = "No vitals recorded for this day."
                                var typeface =
                                    ResourcesCompat.getFont(context, R.font.agdasima_regular)
                                binding.textNoRecords.visibility = View.VISIBLE
                            }
                        } else {
                            val message = result.optString("message", "An unknown error occurred.")
                            binding.textNoRecords.text = message
                            binding.textNoRecords.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        binding.textNoRecords.text = "Failed to parse server response."
                        binding.textNoRecords.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    private fun isValuePresent(value: String): Boolean {
        return value.isNotBlank() && !value.equals("null", ignoreCase = true) && value != "N/A"
    }

    private fun createVitalCard(data: JSONObject): View {
        if (!isAdded) return View(context)
        val context = requireContext()

        val card = CardView(context).apply {
            radius = 24f
            cardElevation = 8f
            useCompatPadding = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32)
        }

        // Add the Shift title
        val shift = data.optString("shift", "N/A")
        val shiftText = TextView(context).apply {
            text = shift
            textSize = 18f
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_bold)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        layout.addView(shiftText)


        // ✅ --- NEW LOGIC STARTS HERE ---

        // 1. Get all potential values from the JSON
        val attendance = formatAttendance(data.optString("attendance_status", "N/A"))
        val preHdBp = data.optString("pre_hd_bp", "N/A")
        val postHdBp = data.optString("post_hd_bp", "N/A")
        val preHdWt = formatWeight(data.optString("pre_hd_wt", "N/A"))
        val postHdWt = formatWeight(data.optString("post_hd_wt", "N/A"))
        val medication = data.optString("medication", "N/A")
        val nurseInCharge = data.optString("nurse_in_charge_name", "N/A")

        // 2. Check if any *actual vitals* (not just metadata) are present
        val hasActualVitals = isValuePresent(preHdBp) || isValuePresent(postHdBp) ||
                isValuePresent(preHdWt) || isValuePresent(postHdWt) ||
                isValuePresent(medication)

        if (hasActualVitals) {
            // --- NORMAL CASE: At least one vital sign exists ---
            // Create a map of all available data and display it
            val vitalsMap = linkedMapOf(
                "ATTENDANCE" to attendance,
                "PRE-HD BP" to preHdBp,
                "POST-HD BP" to postHdBp,
                "PRE-HD WT" to preHdWt,
                "POST-HD WT" to postHdWt,
                "MEDICATION" to medication,
                "NURSE IN CHARGE" to nurseInCharge
            )

            vitalsMap.forEach { (label, value) ->
                if (isValuePresent(value)) { // Use our new helper function
                    layout.addView(createVitalRow(label, value))
                }
            }
        } else {
            // --- SPECIAL CASE: No actual vitals, maybe only attendance/nurse ---
            // Display attendance if it exists
            if (isValuePresent(attendance)) {
                layout.addView(createVitalRow("ATTENDANCE", attendance))

                // Add the special message below attendance
                val noVitalsText = TextView(context).apply {
                    text = "No Vitals Recorded for Today"
                    typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
                    typeface = Typeface.create(typeface, Typeface.ITALIC) // This line makes it italic
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 24; bottomMargin = 16 }
                }
                layout.addView(noVitalsText)
            }

            // Always display the nurse if they are present, regardless of vitals
            if (isValuePresent(nurseInCharge)) {
                layout.addView(createVitalRow("NURSE IN CHARGE", nurseInCharge))
            }
        }

        card.addView(layout)
        return card
    }

    private fun createVitalRow(label: String, value: String): View {
        val context = requireContext()
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        val labelText = TextView(context).apply {
            text = label
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_bold)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            setTextColor(Color.BLACK) // <-- ADD THIS LINE
        }

        val valueText = TextView(context).apply {
            text = value.ifEmpty { "N/A" }
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            setTextColor(Color.BLACK) // <-- ADD THIS LINE
        }

        rowLayout.addView(labelText)
        rowLayout.addView(valueText)
        return rowLayout
    }

    private fun formatAttendance(status: String): String {
        return status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun formatWeight(weight: String): String {
        return if (weight.isNotEmpty() && weight != "null" && weight != "N/A") {
            "$weight kg"
        } else {
            "N/A"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}