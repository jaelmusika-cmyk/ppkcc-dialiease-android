package com.example.dialiease2.fragments

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.example.dialiease2.R
import com.example.dialiease2.databinding.FragmentRequestBinding
import com.example.dialiease2.utils.Constants
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.example.dialiease2.utils.UnsafeOkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

class RequestFragment : Fragment() {

    private var _binding: FragmentRequestBinding? = null
    private val binding get() = _binding!!
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()
    private val currentSchedule = mutableMapOf<String, JSONObject>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRequestBinding.inflate(inflater, container, false)
        loadCurrentSchedule()

        binding.editCurrentScheduleBtn.setOnClickListener {
            showEditScheduleOptions()
        }

        binding.requestNewWeeklyBtn.setOnClickListener {
            showWeeklyScheduleDialog()
        }

        binding.yourRequestsBtn.setOnClickListener {
            showYourRequestsDialog()
        }

        // NEW: Listener for the Inform Absence button
        binding.informAbsenceBtn.setOnClickListener {
            showInformAbsenceDialog()
        }

        return binding.root
    }

    // --- NEW METHODS FOR INFORM ABSENCE ---

    private fun showInformAbsenceDialog() {
        val context = requireContext()
        val editText = EditText(context).apply {
            typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
            hint = "Type when and why you will be absent here..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setPadding(48, 24, 48, 24)
        }

        val container = FrameLayout(context).apply {
            setPadding(48, 24, 48, 0)
            addView(editText)
        }

        AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_dialog_email)
            .setTitle("Inform Absence")
            .setView(container)
            .setPositiveButton("Send") { dialog, _ ->
                val message = editText.text.toString().trim()
                if (message.isNotEmpty()) {
                    submitAbsenceInfo(message)
                } else {
                    showErrorDialog("Message cannot be empty.")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitAbsenceInfo(message: String) {
        val email = getEmail() ?: return
        val json = JSONObject().apply {
            put("email", email)
            put("message", message)
        }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.BASE_URL + "submit_absence.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    showErrorDialog("Network error, please try again.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isAdded) return
                val responseBody = response.body?.string()
                try {
                    val result = JSONObject(responseBody ?: "")
                    val status = result.optString("status")
                    if (status == "success") {
                        requireActivity().runOnUiThread {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Message Sent")
                                .setMessage("Thank you for informing us. Please wait for an acknowledgement notification.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    } else {
                        val errorMessage = result.optString("message", "An unknown error occurred.")
                        requireActivity().runOnUiThread {
                            showErrorDialog(errorMessage)
                        }
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        showErrorDialog("Failed to parse server response.")
                    }
                }
            }
        })
    }


    private fun showTimePicker(editText: EditText) {
        val cal = Calendar.getInstance()
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            val selectedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
            }
            val formatted = SimpleDateFormat("h:mm", Locale.getDefault()).format(selectedTime.time)
            editText.setText(formatted)
        }

        TimePickerDialog(
            requireContext(), timeSetListener,
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
        ).show()
    }

    private fun convertTo24Hour(time: String, ampm: String): String {
        val input = SimpleDateFormat("h:mm a", Locale.getDefault())
        val output = SimpleDateFormat("HH:mm", Locale.getDefault())
        return output.format(input.parse("$time $ampm") ?: Date())
    }


    private fun loadCurrentSchedule() {
        val email = getEmail() ?: return
        if (_binding == null || !isAdded) return  // 👈 CRITICAL GUARD

        val json = JSONObject().apply { put("email", email) }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.BASE_URL + "fetch_patient_schedule.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded || _binding == null) return
                requireActivity().runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    showErrorDialog("Failed to load schedule")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = JSONObject(response.body?.string() ?: "")

                // NEW: Get status from the response, default to "Pending"
                val status = result.optString("schedule_status", "Pending")
                val scheduleArray = result.optJSONArray("schedules")

                if (!isAdded || _binding == null) return  // 👈 prevent crash before UI update
                requireActivity().runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread

                    // NEW: Update status text and color
                    binding.scheduleStatus.text = "Status: $status"
                    binding.scheduleStatus.setBackgroundColor(
                        if (status.lowercase(Locale.ROOT) == "pending")
                            android.graphics.Color.parseColor("#FFD700") // Gold
                        else
                            android.graphics.Color.parseColor("#4CAF50") // Green
                    )

                    currentSchedule.clear()
                    // NEW: Use scheduleContainer
                    binding.scheduleContainer.removeAllViews()

                    if (scheduleArray != null && scheduleArray.length() > 0) {
                        for (i in 0 until scheduleArray.length()) {
                            val item = scheduleArray.getJSONObject(i)
                            val day = item.getString("day").lowercase()
                            currentSchedule[day] = item

                            // NEW: Use createStyledScheduleView
                            val card = createStyledScheduleView(
                                item.getString("day"),
                                item.getString("shift"),
                                item.getString("start_time"),
                                item.getString("duration")
                            )

                            // NEW: Add card to scheduleContainer
                            binding.scheduleContainer.addView(card)
                        }
                    } else {
                        val empty = TextView(requireContext()).apply {
                            text = "No schedules yet"
                            setPadding(16)
                            typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
                            setTextColor(ContextCompat.getColor(context, android.R.color.black))
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                        }
                        // NEW: Add to scheduleContainer
                        binding.scheduleContainer.addView(empty)
                    }
                }
            }
        })
    }


    private fun getEmail(): String? {
        return requireActivity().getSharedPreferences("DialiEasePrefs", Context.MODE_PRIVATE)
            .getString("user_email", null)
    }

    private fun formatScheduleItem(item: JSONObject): String {
        val day = item.getString("day")
        val shift = item.getString("shift")
        val start = item.getString("start_time")
        val duration = item.getString("duration").toIntOrNull() ?: 0

        val startDate = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).parse(start)
        val startFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(startDate ?: Date())
        val endMillis = (startDate?.time ?: 0L) + duration * 60 * 1000
        val endFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(endMillis))

        return "$day | $shift | $startFormatted to $endFormatted"
    }

    private fun computeDurationMinutes(start: String, end: String): Int {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val s = sdf.parse(start)
        val e = sdf.parse(end)
        return ((e?.time ?: 0) - (s?.time ?: 0)).toInt() / (1000 * 60)
    }

    private fun isValidShiftTime(shift: String, start: String, end: String): Boolean {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sTime = sdf.parse(start)?.time ?: return false
        val eTime = sdf.parse(end)?.time ?: return false

        return when (shift) {
            "First Shift" -> {
                val minStart = sdf.parse("06:00")!!.time
                val maxEnd = sdf.parse("10:00")!!.time
                sTime in minStart..(maxEnd - 60 * 60 * 1000) && eTime in (sTime + 60 * 1000)..maxEnd
            }
            "Second Shift" -> {
                val minStart = sdf.parse("11:00")!!.time
                val maxEnd = sdf.parse("15:00")!!.time
                sTime in minStart..(maxEnd - 60 * 60 * 1000) && eTime in (sTime + 60 * 1000)..maxEnd
            }
            else -> false
        }
    }


    private fun submitScheduleRequest(
        email: String,
        day: String,
        shift: String?,
        startTime: String?,
        duration: String?,
        note: String,
        batchId: String? = null,
        replacesDay: String? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val json = JSONObject().apply {
            put("email", email)
            put("day", day)
            put("shift", shift ?: JSONObject.NULL)
            put("start_time", startTime ?: JSONObject.NULL)  // snake_case here
            put("duration", duration ?: JSONObject.NULL)
            put("note", note)
            put("batch_id", batchId ?: JSONObject.NULL)       // snake_case
            if (replacesDay != null) put("replaces_day", replacesDay)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.BASE_URL + "submit_schedule_request.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded || _binding == null) return
                requireActivity().runOnUiThread {
                    showErrorDialog("Failed to connect to server. Please try again.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val rawBody = response.body?.string()


                if (rawBody.isNullOrBlank()) {
                    requireActivity().runOnUiThread {
                        showErrorDialog("Empty response from server.")
                    }
                    return
                }

                val resp = try {
                    JSONObject(rawBody.trim())
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        showErrorDialog("Invalid JSON: ${e.localizedMessage}\nResponse:\n$rawBody")
                    }
                    return
                }

                if (!isAdded || _binding == null) return

                val status = resp.optString("status")
                val message = resp.optString("message", "Unknown server response.")

                requireActivity().runOnUiThread {
                    if (status == "error") {
                        showErrorDialog(message)
                    } else {
                        showSuccessDialog(message)
                        loadCurrentSchedule()
                        onSuccess?.invoke()
                    }
                }
            }
        })
    }

    private fun showEditScheduleOptions() {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        for ((day, _) in currentSchedule) {
            options.add("Edit ${day.replaceFirstChar { it.uppercase() }} Schedule")
            actions.add { showEditDayDialog(day) }
        }

        options.add("Add +")
        actions.add { showAddDayDialog() }

        AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_menu_edit)
            .setTitle("Edit Current Schedule")
            .setItems(options.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDayDialog(day: String) {
        val existing = currentSchedule[day] ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_day, null)

        val daySpinner = dialogView.findViewById<Spinner>(R.id.daySpinner)
        val shiftSpinner = dialogView.findViewById<Spinner>(R.id.shiftSpinner)
        val startTimeInput = dialogView.findViewById<EditText>(R.id.startTimeInput)
        val endTimeInput = dialogView.findViewById<EditText>(R.id.endTimeInput)
        val startAmPmSpinner = dialogView.findViewById<Spinner>(R.id.startAmPmSpinner)
        val endAmPmSpinner = dialogView.findViewById<Spinner>(R.id.endAmPmSpinner)
        val clearButton = dialogView.findViewById<Button>(R.id.clearButton)

        startTimeInput.setOnClickListener { showTimePicker(startTimeInput) }
        endTimeInput.setOnClickListener { showTimePicker(endTimeInput) }

        val allDays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        daySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allDays.map { it.replaceFirstChar { c -> c.uppercase() } })
        daySpinner.setSelection(allDays.indexOf(day))

        val shiftOptions = listOf("First Shift", "Second Shift")
        shiftSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, shiftOptions)

        val originalShift = existing.getString("shift")
        val rawOriginalStart = existing.getString("start_time").substring(0, 5)
        val originalStart = convertTo24Hour(SimpleDateFormat("h:mm", Locale.getDefault()).format(SimpleDateFormat("HH:mm").parse(rawOriginalStart)!!), "AM")
        val originalDuration = existing.getString("duration").toIntOrNull() ?: 0
        val startDate = SimpleDateFormat("HH:mm", Locale.getDefault()).parse(originalStart)
        val endMillis = (startDate?.time ?: 0L) + originalDuration * 60 * 1000
        val originalEnd = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endMillis))

        val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
        startTimeInput.setText(SimpleDateFormat("h:mm", Locale.getDefault()).format(startDate ?: Date()))
        startAmPmSpinner.setSelection(if (amPmFormat.format(startDate ?: Date()) == "AM") 0 else 1)
        endTimeInput.setText(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(endMillis)))
        endAmPmSpinner.setSelection(if (amPmFormat.format(Date(endMillis)) == "AM") 0 else 1)
        shiftSpinner.setSelection(shiftOptions.indexOf(originalShift))

        shiftSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (shiftOptions[pos] != originalShift) {
                    startTimeInput.setText("")
                    endTimeInput.setText("")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_menu_my_calendar)
            .setTitle("Edit ${day.replaceFirstChar { it.uppercase() }}")
            .setView(dialogView)
            .setPositiveButton("Submit Request", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = getEmail() ?: return@setOnClickListener
                val selectedDay = allDays[daySpinner.selectedItemPosition]
                val selectedShift = shiftSpinner.selectedItem.toString()
                val startRaw = startTimeInput.text.toString()
                val endRaw = endTimeInput.text.toString()
                val startAmPm = startAmPmSpinner.selectedItem.toString()
                val endAmPm = endAmPmSpinner.selectedItem.toString()

                if (startRaw.isBlank() || endRaw.isBlank()) {
                    showErrorDialog("Start and end times are required")
                    return@setOnClickListener
                }

                val start24 = convertTo24Hour(startRaw, startAmPm)
                val end24 = convertTo24Hour(endRaw, endAmPm)
                val duration = computeDurationMinutes(start24, end24)

                val changed = selectedDay != day || selectedShift != originalShift || start24 != originalStart || end24 != originalEnd
                if (!changed) {
                    showInfoDialog("No changes made")
                    return@setOnClickListener
                }

                if (!isValidShiftTime(selectedShift, start24, end24)) {
                    showErrorDialog("Invalid time range for $selectedShift")
                    return@setOnClickListener
                }

                submitScheduleRequest(
                    email,
                    selectedDay,
                    selectedShift,
                    "$start24:00",
                    duration.toString(),
                    "Edit schedule",
                    null,
                    if (selectedDay != day) day else null,
                    onSuccess = { dialog.dismiss() }
                )
            }

            clearButton.setOnClickListener {
                val email = getEmail() ?: return@setOnClickListener
                submitScheduleRequest(
                    email,
                    day,
                    null,
                    null,
                    null,
                    "Requesting to clear schedule",
                    null,
                    null,
                    onSuccess = { dialog.dismiss() }
                )
            }
        }

        dialog.show()
    }

    private fun showAddDayDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_day, null)
        val daySpinner = dialogView.findViewById<Spinner>(R.id.daySpinner)
        val shiftSpinner = dialogView.findViewById<Spinner>(R.id.shiftSpinner)
        val startTimeInput = dialogView.findViewById<EditText>(R.id.startTimeInput)
        val endTimeInput = dialogView.findViewById<EditText>(R.id.endTimeInput)
        val startAmPmSpinner = dialogView.findViewById<Spinner>(R.id.startAmPmSpinner)
        val endAmPmSpinner = dialogView.findViewById<Spinner>(R.id.endAmPmSpinner)
        val clearButton = dialogView.findViewById<Button>(R.id.clearButton)

        startTimeInput.setOnClickListener { showTimePicker(startTimeInput) }
        endTimeInput.setOnClickListener { showTimePicker(endTimeInput) }
        clearButton.visibility = View.GONE

        val allDays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val availableDays = allDays.filterNot { currentSchedule.containsKey(it) }

        if (availableDays.isEmpty()) {
            showInfoDialog("You already have schedules for all days.")
            return
        }

        daySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, availableDays.map { it.replaceFirstChar { c -> c.uppercase() } })
        shiftSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("First Shift", "Second Shift"))

        val dialog = AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_input_add)
            .setTitle("Add New Schedule")
            .setView(dialogView)
            .setPositiveButton("Submit Request", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = getEmail() ?: return@setOnClickListener
                val selectedDay = availableDays[daySpinner.selectedItemPosition]
                val selectedShift = shiftSpinner.selectedItem.toString()
                val startRaw = startTimeInput.text.toString()
                val endRaw = endTimeInput.text.toString()
                val startAmPm = startAmPmSpinner.selectedItem.toString()
                val endAmPm = endAmPmSpinner.selectedItem.toString()

                if (startRaw.isBlank() || endRaw.isBlank()) {
                    showErrorDialog("Start and end times are required")
                    return@setOnClickListener
                }

                val start24 = convertTo24Hour(startRaw, startAmPm)
                val end24 = convertTo24Hour(endRaw, endAmPm)
                val duration = computeDurationMinutes(start24, end24)

                if (!isValidShiftTime(selectedShift, start24, end24)) {
                    Toast.makeText(requireContext(), "Invalid time range for $selectedShift", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                submitScheduleRequest(
                    email, selectedDay, selectedShift, "$start24:00", duration.toString(),
                    "Add new schedule", null, null,
                    onSuccess = { dialog.dismiss() }
                )
            }
        }

        dialog.show()
    }

    private fun showWeeklyScheduleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_request_weekly, null)
        val dayIds = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

        val shiftIds = mapOf(
            "monday" to R.id.shiftSpinnerMonday,
            "tuesday" to R.id.shiftSpinnerTuesday,
            "wednesday" to R.id.shiftSpinnerWednesday,
            "thursday" to R.id.shiftSpinnerThursday,
            "friday" to R.id.shiftSpinnerFriday,
            "saturday" to R.id.shiftSpinnerSaturday
        )
        val startIds = mapOf(
            "monday" to R.id.startTimeInputMonday,
            "tuesday" to R.id.startTimeInputTuesday,
            "wednesday" to R.id.startTimeInputWednesday,
            "thursday" to R.id.startTimeInputThursday,
            "friday" to R.id.startTimeInputFriday,
            "saturday" to R.id.startTimeInputSaturday
        )
        val endIds = mapOf(
            "monday" to R.id.endTimeInputMonday,
            "tuesday" to R.id.endTimeInputTuesday,
            "wednesday" to R.id.endTimeInputWednesday,
            "thursday" to R.id.endTimeInputThursday,
            "friday" to R.id.endTimeInputFriday,
            "saturday" to R.id.endTimeInputSaturday
        )
        val startAmPmIds = mapOf(
            "monday" to R.id.startAmPmSpinnerMonday,
            "tuesday" to R.id.startAmPmSpinnerTuesday,
            "wednesday" to R.id.startAmPmSpinnerWednesday,
            "thursday" to R.id.startAmPmSpinnerThursday,
            "friday" to R.id.startAmPmSpinnerFriday,
            "saturday" to R.id.startAmPmSpinnerSaturday
        )
        val endAmPmIds = mapOf(
            "monday" to R.id.endAmPmSpinnerMonday,
            "tuesday" to R.id.endAmPmSpinnerTuesday,
            "wednesday" to R.id.endAmPmSpinnerWednesday,
            "thursday" to R.id.endAmPmSpinnerThursday,
            "friday" to R.id.endAmPmSpinnerFriday,
            "saturday" to R.id.endAmPmSpinnerSaturday
        )

        val shiftOptions = listOf("First Shift", "Second Shift")
        dayIds.forEach { day ->
            dialogView.findViewById<Spinner>(shiftIds[day]!!).apply {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, shiftOptions)
            }
            dialogView.findViewById<EditText>(startIds[day]!!).setOnClickListener { showTimePicker(it as EditText) }
            dialogView.findViewById<EditText>(endIds[day]!!).setOnClickListener { showTimePicker(it as EditText) }

            dialogView.findViewById<Spinner>(startAmPmIds[day]!!).adapter =
                ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("AM", "PM"))
            dialogView.findViewById<Spinner>(endAmPmIds[day]!!).adapter =
                ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("AM", "PM"))
        }

        // --- START: UNIVERSAL COPY LOGIC ---
        val btnUniversalCopy = dialogView.findViewById<Button>(R.id.btnUniversalCopy)
        btnUniversalCopy.setOnClickListener {
            val daysList = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val daysKeys = arrayOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

            // STEP 1: Select Source Day
            AlertDialog.Builder(requireContext())
                .setTitle("Step 1: Copy FROM which day?")
                .setItems(daysList) { _, sourceIndex ->
                    val sourceKey = daysKeys[sourceIndex]

                    // Grab values from source day
                    val sShiftIdx = dialogView.findViewById<Spinner>(shiftIds[sourceKey]!!).selectedItemPosition
                    val sStart = dialogView.findViewById<EditText>(startIds[sourceKey]!!).text.toString()
                    val sStartAmPmIdx = dialogView.findViewById<Spinner>(startAmPmIds[sourceKey]!!).selectedItemPosition
                    val sEnd = dialogView.findViewById<EditText>(endIds[sourceKey]!!).text.toString()
                    val sEndAmPmIdx = dialogView.findViewById<Spinner>(endAmPmIds[sourceKey]!!).selectedItemPosition

                    if (sStart.isBlank() || sEnd.isBlank()) {
                        Toast.makeText(requireContext(), "The source day (${daysList[sourceIndex]}) is empty!", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }

                    // STEP 2: Select Target Days
                    val checkedItems = BooleanArray(daysList.size) { false }
                    // Disable the source day in the list conceptually (optional, but good UX)

                    AlertDialog.Builder(requireContext())
                        .setTitle("Step 2: Apply TO which days?")
                        .setMultiChoiceItems(daysList, checkedItems) { _, which, isChecked ->
                            checkedItems[which] = isChecked
                        }
                        .setPositiveButton("Apply") { _, _ ->
                            var count = 0
                            for (i in daysList.indices) {
                                if (checkedItems[i] && i != sourceIndex) { // Don't copy to self
                                    val targetKey = daysKeys[i]

                                    // Apply values
                                    dialogView.findViewById<Spinner>(shiftIds[targetKey]!!).setSelection(sShiftIdx)
                                    dialogView.findViewById<EditText>(startIds[targetKey]!!).setText(sStart)
                                    dialogView.findViewById<Spinner>(startAmPmIds[targetKey]!!).setSelection(sStartAmPmIdx)
                                    dialogView.findViewById<EditText>(endIds[targetKey]!!).setText(sEnd)
                                    dialogView.findViewById<Spinner>(endAmPmIds[targetKey]!!).setSelection(sEndAmPmIdx)
                                    count++
                                }
                            }
                            Toast.makeText(requireContext(), "Copied ${daysList[sourceIndex]} to $count days.", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        // --- END: UNIVERSAL COPY LOGIC ---

        val dialog = AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_menu_week)
            .setTitle("Submit Weekly Schedule Request")
            .setView(dialogView)
            .setPositiveButton("Submit Request", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = getEmail() ?: return@setOnClickListener
                val batchId = UUID.randomUUID().toString()

                val toSubmit = mutableListOf<Map<String, String>>()

                for (day in dayIds) {
                    val shift = dialogView.findViewById<Spinner>(shiftIds[day]!!).selectedItem.toString()
                    val startRaw = dialogView.findViewById<EditText>(startIds[day]!!).text.toString()
                    val endRaw = dialogView.findViewById<EditText>(endIds[day]!!).text.toString()
                    val startAmPm = dialogView.findViewById<Spinner>(startAmPmIds[day]!!).selectedItem.toString()
                    val endAmPm = dialogView.findViewById<Spinner>(endAmPmIds[day]!!).selectedItem.toString()

                    if (startRaw.isBlank() || endRaw.isBlank()) continue

                    val start24 = convertTo24Hour(startRaw, startAmPm)
                    val end24 = convertTo24Hour(endRaw, endAmPm)

                    if (!isValidShiftTime(shift, start24, end24)) {
                        showErrorDialog("Invalid time for $day ($shift)")
                        return@setOnClickListener
                    }

                    val duration = computeDurationMinutes(start24, end24)
                    toSubmit.add(
                        mapOf(
                            "email" to email,
                            "day" to day,
                            "shift" to shift,
                            "start_time" to "$start24:00",    // snake_case
                            "duration" to duration.toString(),
                            "note" to "Weekly schedule request",
                            "batch_id" to batchId             // snake_case
                        )
                    )
                }

                if (toSubmit.isEmpty()) {
                    showInfoDialog("Please fill in at least one valid schedule")
                    return@setOnClickListener
                }

                // Phase 1: Check all conflicts before submitting
                validateWeeklyBatch(toSubmit) { isValid, conflictMsg ->
                    if (!isValid) {
                        showErrorDialog(conflictMsg)
                        return@validateWeeklyBatch
                    }

                    // Phase 2: If all valid, submit entire batch with one request
                    // NOTE: The success message will now ONLY be shown by the API response handler
                    // inside the submitWeeklyBatchRequest function.
                    submitWeeklyBatchRequest(email, batchId, toSubmit) {
                        dialog.dismiss()
                        // Removed redundant showWeeklyResultDialog call.
                    }
                }
            }
        }

        dialog.show()
    }

    private fun validateWeeklyBatch(
        batchData: List<Map<String, String>>,
        callback: (isValid: Boolean, conflictMessage: String) -> Unit
    ) {
        val url = "https://darksalmon-loris-794235.hostingersite.com/api/validate_schedule_batch.php"

        // 1. Build the JSON payload
        val batchArray = JSONArray()
        for (entry in batchData) {
            val obj = JSONObject()
            obj.put("email", entry["email"])
            obj.put("day", entry["day"])
            obj.put("shift", entry["shift"])
            obj.put("start_time", entry["start_time"])
            obj.put("duration", entry["duration"])
            obj.put("batch_id", entry["batch_id"])
            batchArray.put(obj)
        }
        val wrapper = JSONObject().apply {
            put("batch", batchArray)
        }

        // 2. Create the OkHttp Request
        val body = wrapper.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        // 3. Use the existing 'client' to make the call
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    callback(false, "Network or server error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isAdded) return
                val responseBody = response.body?.string()
                try {
                    val result = JSONObject(responseBody ?: "")
                    val success = result.optBoolean("success", false)
                    val message = result.optString("message", "Unknown error occurred.")
                    requireActivity().runOnUiThread {
                        callback(success, message)
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        callback(false, "Failed to parse validation response.")
                    }
                }
            }
        })
    }

    private fun showWeeklyResultDialog(results: List<String>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Weekly Submission Results")
            .setMessage(results.joinToString("\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun submitWeeklyBatchRequest(
        email: String,
        batchId: String,
        requestList: List<Map<String, String>>,
        onSuccess: (() -> Unit)? = null
    ) {
        // Create the JSON Array for the requests first
        val requestsArray = JSONArray()
        requestList.forEach { entry ->
            val requestObject = JSONObject().apply {
                put("day", entry["day"])
                put("shift", entry["shift"])
                put("start_time", entry["start_time"])
                put("duration", entry["duration"])
                // 'replaces_day' is not needed for batch replace, handled by admin approval logic
            }
            requestsArray.put(requestObject)
        }

        // Create the main JSON body
        val jsonBody = JSONObject().apply {
            put("email", email)
            put("note", "Weekly schedule request")
            put("batch_id", batchId)
            put("requests", requestsArray)
        }

        val body = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

        // Use the constant for the URL for consistency with other requests
        val request = Request.Builder()
            .url(Constants.BASE_URL + "submit_schedule_request.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    showErrorDialog("Network error: ${e.localizedMessage}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isAdded) return
                val rawBody = response.body?.string()

                if (rawBody.isNullOrBlank()) {
                    requireActivity().runOnUiThread {
                        showErrorDialog("Received an empty response from the server.")
                    }
                    return
                }

                val jsonResponse = try {
                    JSONObject(rawBody)
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        showErrorDialog("Invalid server response. Please check server logs.\nResponse: $rawBody")
                    }
                    return
                }

                val status = jsonResponse.optString("status", "error")
                val message = jsonResponse.optString("message", "An unknown error occurred.")

                requireActivity().runOnUiThread {
                    if (status == "success") {
                        showSuccessDialog(message)
                        onSuccess?.invoke()
                        loadCurrentSchedule()
                    } else {
                        showErrorDialog(message)
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showErrorDialog(message: String) {
        if (!isAdded || activity == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("Request Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSuccessDialog(message: String) {
        if (!isAdded || activity == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("Success")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInfoDialog(message: String) {
        if (!isAdded || activity == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("Info")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showYourRequestsDialog() {
        val email = getEmail() ?: return
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(ProgressBar(requireContext()))
            addView(TextView(requireContext()).apply { text = "Loading history..." })
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_menu_recent_history)
            .setTitle("Your Request History")
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        dialog.show()





        val json = JSONObject().apply { put("email", email) }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.BASE_URL + "fetch_patient_requests.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    dialog.dismiss()
                    showErrorDialog("Failed to load request history.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isAdded) return
                val responseBody = response.body?.string()
                try {
                    val result = JSONObject(responseBody ?: "")
                    if (result.optString("status") == "success") {
                        val requestsArray = result.getJSONArray("requests")
                        requireActivity().runOnUiThread {
                            dialog.dismiss()
                            displayRequestsInDialog(requestsArray)
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            dialog.dismiss()
                            showErrorDialog(result.optString("message", "An unknown error occurred."))
                        }
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        dialog.dismiss()
                        showErrorDialog("Failed to parse server response.")
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

    private fun displayRequestsInDialog(requests: JSONArray) {
        val context = requireContext()
        if (requests.length() == 0) {
            showInfoDialog("You have no request history.")
            return
        }

        val container = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(48, 24, 48, 24)
        }

        for (i in 0 until requests.length()) {
            val request = requests.getJSONObject(i)
            val status = request.getString("status")
            val createdAt = request.getString("created_at")
            val note = request.getString("note")

            val actionId = request.optString("action_id", "N/A") // Get the Action ID
            val details = request.getJSONArray("details")

            val detailsText = StringBuilder()
            val timeFormatIn = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val timeFormatOut = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())

            for (j in 0 until details.length()) {

                val detail = details.getJSONObject(j)
                val day = detail.getString("day").replaceFirstChar { it.uppercase() }
                val shift = detail.optString("shift")
                val startTime = detail.optString("start_time", null)
                val duration = detail.optInt("duration", 0)


                if (shift.isNullOrEmpty() ||
                    shift == "null") {
                    // This handles "clear schedule" requests
                    detailsText.append("• $day: Clear Schedule\n")
                } else {
                    val formattedTime = if (startTime != null) {

                        try {
                            val dateObj = timeFormatIn.parse(startTime)
                            timeFormatOut.format(dateObj)

                        } catch (e: Exception) {
                            "" // Fallback to empty if parsing fails
                        }
                    } else {

                        ""
                    }
                    detailsText.append("• $day: $shift, $formattedTime ($duration mins)\n")
                }
            }

            // --- FIXED SECTION: Replaced ic_menu_checklist with ic_input_get ---
            // Get both color and icon based on status
            val (statusColor, statusIcon) = when (status) {
                // "Approved": The only one with a checkmark.
                "Approved" -> Pair(android.graphics.Color.parseColor("#4CAF50"),
                    android.R.drawable.checkbox_on_background)

                // "Rejected": Same exact box, but "off" and colored red.
                "Rejected" -> Pair(android.graphics.Color.parseColor("#F44336"),
                    android.R.drawable.checkbox_off_background)

                // "Expired": Same exact box, but "off" and colored gray.
                "Expired" -> Pair(android.graphics.Color.GRAY,
                    android.R.drawable.checkbox_off_background)

                // "Pending": Same exact box, but "off" and colored blue.
                else -> Pair(android.graphics.Color.BLUE,
                    android.R.drawable.checkbox_off_background)
            }
            // --- END FIXED SECTION ---

            val
                    card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE

                    cornerRadius = 16f
                    setStroke(2, android.graphics.Color.LTGRAY)
                    setColor(android.graphics.Color.WHITE)
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,

                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 32
                }
                layoutParams = params

            }

            // --- MODIFIED SECTION ---
            val statusView = TextView(context).apply {
                text = status
                textSize = 16f
                typeface = ResourcesCompat.getFont(context, R.font.agdasima_bold)
                setTextColor(statusColor)
                setCompoundDrawablesWithIntrinsicBounds(statusIcon, 0, 0, 0) // Set icon
                compoundDrawablePadding = 16 // Add padding between icon and text

            }
            // --- END MODIFIED SECTION ---

            val dateView = TextView(context).apply {
                text = "Date: $createdAt"
                textSize = 12f
                typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
                setTextColor(android.graphics.Color.DKGRAY)

            }

            val noteView = TextView(context).apply {
                text = "Note: $note"
                typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
                typeface = Typeface.create(typeface, Typeface.ITALIC)
                setTextColor(android.graphics.Color.BLACK)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16

                }
                layoutParams = params
            }

            val detailsView = TextView(context).apply {
                text = detailsText.toString().trim()
                typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)

                setTextColor(android.graphics.Color.BLACK)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {

                    topMargin = 8
                }
                layoutParams = params
            }

            card.addView(statusView)
            val actionIdView = TextView(context).apply {
                text = "ID: $actionId"

                textSize = 10f
                typeface = ResourcesCompat.getFont(context, R.font.agdasima_regular)
                setTextColor(android.graphics.Color.GRAY)
            }
            card.addView(actionIdView)
            card.addView(dateView)
            card.addView(noteView)

            card.addView(detailsView)
            layout.addView(card)
        }

        container.addView(layout)

        // --- MODIFIED SECTION (Dialog Title Icon) ---
        val dialog = AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_menu_recent_history) // Added icon for the dialog title
            .setTitle("Your Request History")
            .setView(container)
            .setPositiveButton("OK", null)
            .create()
        // --- END MODIFIED SECTION ---


        dialog.show()




    }
}

