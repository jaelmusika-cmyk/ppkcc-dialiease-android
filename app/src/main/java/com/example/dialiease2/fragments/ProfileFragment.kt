package com.example.dialiease2.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import android.widget.LinearLayout // <-- Add this import
import androidx.fragment.app.Fragment
import com.example.dialiease2.LoginActivity
import com.example.dialiease2.R
import com.example.dialiease2.utils.UnsafeOkHttpClient
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import androidx.core.content.res.ResourcesCompat // <-- Add this import

class ProfileFragment : Fragment() {

    private lateinit var textFullName: TextView
    private lateinit var textEmail: TextView
    private lateinit var textPhone: TextView
    private lateinit var btnLogout: LinearLayout // <-- Change this to LinearLayout
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        textFullName = view.findViewById(R.id.textFullName)
        textEmail = view.findViewById(R.id.textEmail)
        textPhone = view.findViewById(R.id.textPhone)
        btnLogout = view.findViewById(R.id.btnLogout)

        val sharedPref = requireContext().getSharedPreferences("DialiEasePrefs", Context.MODE_PRIVATE)

        val fullName = sharedPref.getString("user_full_name", "N/A")
        val email = sharedPref.getString("user_email", "N/A")
        val phone = sharedPref.getString("user_phone", "N/A")

        textFullName.text = "Name: $fullName"
        textEmail.text = "Email: $email"
        textPhone.text = "Phone: $phone"

        // Apply Agdasima font to profile text
        textFullName.typeface = ResourcesCompat.getFont(requireContext(), R.font.agdasima_regular)
        textEmail.typeface = ResourcesCompat.getFont(requireContext(), R.font.agdasima_regular)
        textPhone.typeface = ResourcesCompat.getFont(requireContext(), R.font.agdasima_regular)

        // Set the font on the TextView inside the Logout LinearLayout
        val logoutText = btnLogout.findViewById<TextView>(R.id.logoutText)
        logoutText.typeface = ResourcesCompat.getFont(requireContext(), R.font.agdasima_regular)


        // 🔄 Fetch user details
        if (!email.isNullOrEmpty()) {
            fetchUserDetails(email)
        }

        // 🚪 Logout
        btnLogout.setOnClickListener {
            sharedPref.edit().clear().apply()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        return view
    }

    private fun fetchUserDetails(email: String) {
        val url = "https://darksalmon-loris-794235.hostingersite.com/=$email"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failure
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && !responseData.isNullOrEmpty()) {
                    val json = JSONObject(responseData)
                    val fullName = json.optString("full_name")
                    val phone = json.optString("phone_number")

                    activity?.runOnUiThread {
                        textFullName.text = "Name: $fullName"
                        textEmail.text = "Email: $email"
                        textPhone.text = "Phone: $phone"

                        // Apply Agdasima font to the fetched data
                        textFullName.typeface = ResourcesCompat.getFont(requireContext(), R.font.agdasima_regular)
                        textEmail.typeface = ResourcesCompat.getFont(requireContext(), R.font.agdasima_regular)
                        textPhone.typeface = ResourcesCompat.getFont(requireContext(), R.font.agdasima_regular)
                    }
                }
            }
        })
    }
}