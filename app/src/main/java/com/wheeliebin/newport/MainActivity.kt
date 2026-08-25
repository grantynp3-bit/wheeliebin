package com.wheeliebin.newport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.wheeliebin.newport.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: if denied, the app simply won't be able to notify until enabled in Settings */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        binding.uprnInput.setText(prefs.uprn)

        maybeRequestNotificationPermission()

        binding.saveButton.setOnClickListener {
            val uprn = binding.uprnInput.text?.toString()?.trim().orEmpty()
            if (uprn.isBlank()) {
                binding.statusText.text = getString(R.string.status_not_configured)
                return@setOnClickListener
            }
            prefs.uprn = uprn
            BinCheckWorker.schedule(this)
            binding.statusText.text = "Saved. Reminders are on — checking a few times a day."
        }

        binding.checkNowButton.setOnClickListener {
            checkNow()
        }

        if (prefs.uprn.isNotBlank()) {
            BinCheckWorker.schedule(this)
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkNow() {
        val uprn = binding.uprnInput.text?.toString()?.trim().orEmpty()
        if (uprn.isBlank()) {
            binding.statusText.text = getString(R.string.status_not_configured)
            return
        }

        binding.statusText.text = "Checking…"
        lifecycleScope.launch {
            try {
                val collections = NewportBinApi.fetchBinDates(uprn)
                binding.statusText.text = formatStatus(collections)
            } catch (e: Exception) {
                binding.statusText.text = "Couldn't reach Newport's bin lookup right now " +
                    "(${e.message ?: "unknown error"}). It will keep retrying automatically."
            }
        }
    }

    private fun formatStatus(collections: List<BinCollection>): String {
        if (collections.isEmpty()) {
            return "No upcoming collections were returned for this UPRN. " +
                "Double-check the number and try again."
        }
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("EEE d MMM")
        val upcoming = collections.filter { !it.date.isBefore(today) }.take(5)

        val lines = upcoming.joinToString("\n") { bin ->
            val label = when (bin.date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> bin.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)
            }
            "${bin.type}: ${bin.date.format(formatter)} ($label)"
        }
        return "Next collections:\n$lines"
    }
}
