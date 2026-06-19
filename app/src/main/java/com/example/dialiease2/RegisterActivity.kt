package com.example.dialiease2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dialiease2.utils.Constants
import com.example.dialiease2.utils.UnsafeOkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class RegisterActivity : AppCompatActivity() {

    // Use the UnsafeOkHttpClient to fix the network/SSL error
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()

    private lateinit var fullNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText // Added for validation
    private lateinit var phoneNumberInput: EditText
    private lateinit var guardianNumberInput: EditText
    private lateinit var registrationCodeInput: EditText
    private lateinit var registerButton: Button
    private lateinit var goToLoginText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize UI components with correct IDs from your XML
        fullNameInput = findViewById(R.id.editTextFullName)
        emailInput = findViewById(R.id.editTextEmail)
        passwordInput = findViewById(R.id.editTextPassword)
        confirmPasswordInput = findViewById(R.id.editTextConfirmPassword) // Added
        registrationCodeInput = findViewById(R.id.editTextRegistrationCode)
        registerButton = findViewById(R.id.buttonRegister)
        goToLoginText = findViewById(R.id.textGoToLogin)

        // --- FIXED ID REFERENCES ---
        phoneNumberInput = findViewById(R.id.editTextPhone)
        guardianNumberInput = findViewById(R.id.editTextGuardianPhone)


        // Set listener for the register button
        registerButton.setOnClickListener {
            handleRegistration()
        }

        // Set listener to navigate back to the Login screen
        goToLoginText.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun handleRegistration() {
        val fullName = fullNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()
        val phoneNumber = phoneNumberInput.text.toString().trim()
        val guardianNumber = guardianNumberInput.text.toString().trim()
        val registrationCode = registrationCodeInput.text.toString().trim()

        // Basic validation
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || phoneNumber.isEmpty() || registrationCode.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- NEW: Password confirmation check ---
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create JSON payload for the request
        val json = JSONObject().apply {
            put("full_name", fullName)
            put("email", email)
            put("password", password)
            put("phone_number", phoneNumber)
            put("guardian_number", guardianNumber) // Optional, can be empty
            put("registration_code", registrationCode)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(Constants.BASE_URL + "patient_register.php")
            .post(body)
            .build()

        // Execute the network call
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                try {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        val status = jsonResponse.optString("status")
                        val message = jsonResponse.optString("message")

                        runOnUiThread {
                            Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_LONG).show()
                            if (status == "success") {
                                // On success, go to Login screen so the user can log in
                                val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@RegisterActivity, "Registration failed: Server error", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, "Error parsing server response.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}