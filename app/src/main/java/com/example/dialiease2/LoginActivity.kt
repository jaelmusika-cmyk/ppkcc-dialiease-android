package com.example.dialiease2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dialiease2.utils.Constants
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.widget.TextView
import com.example.dialiease2.utils.UnsafeOkHttpClient


class LoginActivity : AppCompatActivity() {
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // must match your layout file name:
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.editTextEmail)
        passwordInput = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)

        val goToRegister = findViewById<TextView>(R.id.textGoToRegister)
        goToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }


        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()




            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            // Modern: use toRequestBody extension
            val jsonString = json.toString()
            val body = jsonString
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(Constants.BASE_URL + "patient_login.php")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Network Error", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseData = response.body?.string()
                    if (response.isSuccessful && responseData != null) {
                        val jsonResponse = JSONObject(responseData)
                        if (jsonResponse.getString("status") == "success") {
                            runOnUiThread {
                                Toast.makeText(this@LoginActivity, "Login Success", Toast.LENGTH_SHORT).show()

                                val userJson = jsonResponse.getJSONObject("user")
                                val fullName = userJson.getString("full_name")
                                val emailFromResponse = userJson.getString("email")
                                val phoneNumber = userJson.getString("phone_number")

                                val sharedPref = getSharedPreferences("DialiEasePrefs", MODE_PRIVATE)
                                sharedPref.edit()
                                    .putString("user_full_name", fullName)
                                    .putString("user_email", emailFromResponse)
                                    .putString("user_phone", phoneNumber)
                                    .apply()


                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                intent.putExtra("user_email", emailFromResponse)
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@LoginActivity,
                                    jsonResponse.getString("message"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            })
        }
    }
}
