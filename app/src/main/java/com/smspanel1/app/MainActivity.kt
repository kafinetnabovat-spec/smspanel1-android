package com.smspanel1.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvContent = findViewById<TextView>(R.id.tvContent)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val fab = findViewById<FloatingActionButton>(R.id.fab)

        tvDate.text = getShamsiDate()

        bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_messages -> { tvTitle.text = "پیام‌ها"; tvContent.text = "لیست پیام‌ها\n\nهنوز پیامی نفرستادی\nروی + بزن"; true }
                R.id.nav_contacts -> { tvTitle.text = "مخاطبین"; tvContent.text = "لیست مخاطبین\n\nمخاطبین شما اینجا"; true }
                else -> false
            }
        }

        fab.setOnClickListener {
            Toast.makeText(this, "پیام جدید - نسخه مینیمال 6.1", Toast.LENGTH_SHORT).show()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 1001)
            }
        }
    }

    private fun getShamsiDate(): String {
        return try {
            val cal = Calendar.getInstance()
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val shamsiY = if (m < 3 || (m == 3 && d < 21)) y - 622 else y - 621
            val months = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
            val shamsiM = if (m >= 3) m - 2 else m + 10
            val idx = (shamsiM - 1).coerceIn(0,11)
            "امروز: $d ${months[idx]} $shamsiY - شمسی"
        } catch (e: Exception) { "تاریخ شمسی" }
    }
}
