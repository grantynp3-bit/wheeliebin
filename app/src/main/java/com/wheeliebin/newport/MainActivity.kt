package com.wheeliebin.newport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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

    private var foundAddresses: List<AddressResult> = emptyList()
    private var selectedUprn: String = ""

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: if denied, the app simply won't be able to notify until enabled in Settings */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        selectedUprn = prefs.uprn

        maybeRequestNotificationPermission()

        binding.findAddressButton.setOnClickListener {
            findAddress()
        }

        binding.addressSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in foundAddresses.indices) {
                    selectedUprn = foundAddresses[position].uprn
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { /* no-op */ }
        }

        binding.saveButton.setOnClickListener {
            if (selectedUprn.isBlank()) {
                binding.statusText.text = getString(R.string.status_not_configured)
                return@setOnClickListener
            }
            prefs.uprn = selectedUprn
            BinCheckWorker.schedule(this)
            binding.statusText.text = "Saved. Reminders are on — checking a few times a day."
            checkNow()
        }

        binding.checkNowButton.setOnClickListener {
            checkNow()
        }

        if (selectedUprn.isNotBlank()) {
            BinCheckWorker.schedule(this)
            checkNow()
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

    private fun findAddress() {
        val postcode = binding.postcodeInput.text?.toString()?.trim().orEmpty()
        if (postcode.isBlank()) {
            binding.statusText.text = "Enter a postcode first."
            return
        }

        binding.statusText.text = "Looking up addresses…"
        lifecycleScope.launch {
            try {
                val addresses = AddressApi.findAddresses(postcode)
                foundAddresses = addresses

                if (addresses.isEmpty()) {
                    binding.addressLabel.visibility = View.GONE
                    binding.addressSpinner.visibility = View.GONE
                    binding.statusText.text = "No addresses found for that postcode. This app only " +
                        "covers Newport City Council collections — double-check the postcode and try again."
                } else {
                    val adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_item,
                        addresses.map { it.fullAddress }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.addressSpinner.adapter = adapter
                    binding.addressLabel.visibility = View.VISIBLE
                    binding.addressSpinner.visibility = View.VISIBLE
                    selectedUprn = addresses.first().uprn
                    val plural = if (addresses.size == 1) "" else "es"
                    binding.statusText.text = "Found ${addresses.size} address$plural. " +
                        "Pick yours above, then tap Save."
                }
            } catch (e: Exception) {
                binding.statusText.text = "Couldn't look up that postcode right now " +
                    "(${e.message ?: "unknown error"}). Please try again."
            }
        }
    }

    private fun checkNow() {
        val uprn = selectedUprn
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
            return "No upcoming collections were returned for this address. " +
                "Double-check it and try again."
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
