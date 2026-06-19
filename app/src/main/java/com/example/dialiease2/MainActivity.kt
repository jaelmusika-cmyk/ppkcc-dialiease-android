// MainActivity.kt
package com.example.dialiease2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.dialiease2.databinding.ActivityMainBinding
import com.example.dialiease2.fragments.ProfileFragment
import com.example.dialiease2.fragments.RequestFragment
import com.example.dialiease2.fragments.ScheduleFragment
import com.example.dialiease2.fragments.VitalsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Check if user is logged in via SharedPreferences
        val sharedPref = getSharedPreferences("DialiEasePrefs", MODE_PRIVATE)
        val email = sharedPref.getString("user_email", null)

        // ✅ If not logged in, go to LoginActivity
        if (email.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // ✅ User is logged in → continue to show bottom nav + fragments
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- CHANGE ---
        // Load default fragment. Per your logic, the old "ScheduleFragment"
        // is now the content for the "Home" screen, so this is correct.
        replaceFragment(ScheduleFragment())

        // --- CHANGE ---
        // The listener is updated to use the new menu item IDs.
        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> replaceFragment(ScheduleFragment()) // Was nav_schedule
                R.id.nav_schedule -> replaceFragment(RequestFragment()) // Was nav_request
                R.id.nav_vitals -> replaceFragment(VitalsFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
            }
            true
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}