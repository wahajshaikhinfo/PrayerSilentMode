package com.prayersilent.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.prayersilent.app.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var cityMap: Map<String, List<CityInfo>>
    private lateinit var prayerRows: Map<String, SwitchMaterial>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        cityMap = CityRepository.loadCities(this)
        prayerRows = mapOf(
            "fajr" to binding.swFajr,
            "dhuhr" to binding.swDhuhr,
            "asr" to binding.swAsr,
            "maghrib" to binding.swMaghrib,
            "isha" to binding.swIsha
        )

        setupLocationSpinners()
        setupMethodSpinners()
        setupPrayerSwitches()
        setupDurationSeekBar()

        binding.switchMaster.isChecked = prefs.masterEnabled
        binding.switchMaster.setOnCheckedChangeListener { _, checked -> prefs.masterEnabled = checked }

        binding.btnGrantDnd.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        binding.btnGrantExactAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        binding.btnSave.setOnClickListener { saveAndSchedule() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        // If a location was already saved on a previous run, make sure alarms are active.
        if (!prefs.city.isNullOrEmpty()) {
            AlarmScheduler.rescheduleAll(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
        refreshPrayerTimesPreview()
    }

    private fun setupLocationSpinners() {
        val countries = cityMap.keys.sorted()
        binding.spinnerCountry.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countries)

        val savedCountryIndex = countries.indexOf(prefs.country).let { if (it >= 0) it else 0 }
        if (countries.isNotEmpty()) binding.spinnerCountry.setSelection(savedCountryIndex)

        fun populateCities(country: String) {
            val cities = cityMap[country].orEmpty().map { it.city }
            binding.spinnerCity.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cities)
            val idx = cities.indexOf(prefs.city).let { if (it >= 0) it else 0 }
            if (cities.isNotEmpty()) binding.spinnerCity.setSelection(idx)
        }

        if (countries.isNotEmpty()) populateCities(countries[savedCountryIndex])

        binding.spinnerCountry.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                populateCities(countries[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupMethodSpinners() {
        val methods = PrayerTimeCalculator.Method.values().map { it.name }
        binding.spinnerMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)
        binding.spinnerMethod.setSelection(methods.indexOf(prefs.method).coerceAtLeast(0))

        val asrOptions = PrayerTimeCalculator.AsrMethod.values().map { it.name }
        binding.spinnerAsr.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, asrOptions)
        binding.spinnerAsr.setSelection(asrOptions.indexOf(prefs.asrMethod).coerceAtLeast(0))
    }

    private fun setupPrayerSwitches() {
        for ((name, sw) in prayerRows) {
            sw.isChecked = prefs.isPrayerEnabled(name)
        }
    }

    private fun setupDurationSeekBar() {
        binding.seekDuration.max = 60
        binding.seekDuration.progress = prefs.durationMinutes
        binding.tvDuration.text = "Silent duration after each prayer: ${prefs.durationMinutes} min"
        binding.seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val minutes = progress.coerceAtLeast(1)
                binding.tvDuration.text = "Silent duration after each prayer: $minutes min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePermissionButtons() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        binding.btnGrantDnd.visibility = if (nm.isNotificationPolicyAccessGranted) View.GONE else View.VISIBLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            binding.btnGrantExactAlarm.visibility = if (am.canScheduleExactAlarms()) View.GONE else View.VISIBLE
        } else {
            binding.btnGrantExactAlarm.visibility = View.GONE
        }
    }

    private fun saveAndSchedule() {
        val countries = cityMap.keys.sorted()
        val countryPos = binding.spinnerCountry.selectedItemPosition
        if (countries.isEmpty() || countryPos !in countries.indices) return
        val country = countries[countryPos]

        val cities = cityMap[country].orEmpty()
        val cityPos = binding.spinnerCity.selectedItemPosition
        if (cityPos !in cities.indices) return
        val selectedCity = cities[cityPos]

        prefs.country = country
        prefs.city = selectedCity.city
        prefs.latitude = selectedCity.latitude.toFloat()
        prefs.longitude = selectedCity.longitude.toFloat()
        prefs.timezoneId = selectedCity.timezoneId

        val methods = PrayerTimeCalculator.Method.values()
        prefs.method = methods[binding.spinnerMethod.selectedItemPosition].name

        val asrOptions = PrayerTimeCalculator.AsrMethod.values()
        prefs.asrMethod = asrOptions[binding.spinnerAsr.selectedItemPosition].name

        for ((name, sw) in prayerRows) {
            prefs.setPrayerEnabled(name, sw.isChecked)
        }
        prefs.durationMinutes = binding.seekDuration.progress.coerceAtLeast(1)

        AlarmScheduler.rescheduleAll(this)
        refreshPrayerTimesPreview()
        binding.tvStatus.text = "Saved. Prayer alarms scheduled for ${selectedCity.city}, $country."
    }

    private fun refreshPrayerTimesPreview() {
        if (prefs.city.isNullOrEmpty()) return

        val zone = runCatching { ZoneId.of(prefs.timezoneId) }.getOrDefault(ZoneId.systemDefault())
        val today = LocalDate.now(zone)
        val method = runCatching { PrayerTimeCalculator.Method.valueOf(prefs.method) }
            .getOrDefault(PrayerTimeCalculator.Method.KARACHI)
        val asr = runCatching { PrayerTimeCalculator.AsrMethod.valueOf(prefs.asrMethod) }
            .getOrDefault(PrayerTimeCalculator.AsrMethod.HANAFI)

        val times = PrayerTimeCalculator.calculate(
            today.year, today.monthValue, today.dayOfMonth,
            prefs.latitude.toDouble(), prefs.longitude.toDouble(), zone, method, asr
        )

        binding.tvFajr.text = "Fajr — ${formatHour(times.fajr)}"
        binding.tvDhuhr.text = "Dhuhr — ${formatHour(times.dhuhr)}"
        binding.tvAsr.text = "Asr — ${formatHour(times.asr)}"
        binding.tvMaghrib.text = "Maghrib — ${formatHour(times.maghrib)}"
        binding.tvIsha.text = "Isha — ${formatHour(times.isha)}"
    }

    private fun formatHour(decimalHour: Double): String {
        if (decimalHour.isNaN()) return "--:--"
        val totalMinutes = Math.round(decimalHour * 60.0)
        val h = ((totalMinutes / 60) % 24).toInt()
        val m = (totalMinutes % 60).toInt()
        val amPm = if (h >= 12) "PM" else "AM"
        var h12 = h % 12
        if (h12 == 0) h12 = 12
        return String.format("%02d:%02d %s", h12, m, amPm)
    }
}
