package com.sajwa.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.data.DateComponents
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.Qibla
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var locationManager: LocationManager
    private val locationPermissionCode = 4102
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false
    private var compassRequested = false
    private var qiblaBearing: Double? = null
    private var magneticDeclination: Float = 0f
    private var qiblaAligned = false
    private val compassListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rotation = FloatArray(9)
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(rotation, event.values)
                Sensor.TYPE_ACCELEROMETER -> {
                    event.values.copyInto(gravityValues)
                    hasGravity = true
                    if (!hasMagnetic || !SensorManager.getRotationMatrix(rotation, null, gravityValues, magneticValues)) return
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    event.values.copyInto(magneticValues)
                    hasMagnetic = true
                    if (!hasGravity || !SensorManager.getRotationMatrix(rotation, null, gravityValues, magneticValues)) return
                }
                else -> return
            }
            val remappedRotation = FloatArray(9)
            val orientation = FloatArray(3)
            val (axisX, axisY) = when (windowManager.defaultDisplay.rotation) {
                android.view.Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                android.view.Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                android.view.Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remappedRotation)
            val azimuth = SensorManager.getOrientation(remappedRotation, orientation)[0]
            val heading = (Math.toDegrees(azimuth.toDouble()) + magneticDeclination + 360.0) % 360.0
            val bearing = qiblaBearing ?: return
            val relative = (bearing - heading + 360.0) % 360.0
            val angularError = minOf(relative, 360.0 - relative)
            if (angularError <= 3.0 && !qiblaAligned) {
                qiblaAligned = true
                runOnUiThread {
                    try {
                        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(90, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(90)
                        }
                    } catch (_: Exception) { }
                }
            } else if (angularError >= 8.0) {
                qiblaAligned = false
            }
            runOnUiThread { webView.evaluateJavascript("window.onSajwaCompass && window.onSajwaCompass($relative,$heading)", null) }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                runOnUiThread { webView.evaluateJavascript("window.onSajwaCompassError && window.onSajwaCompassError('Compass accuracy is low. Move away from magnets and slowly rotate the phone in a figure-eight.')", null) }
            }
        }
    }

    private fun registerCompassSensors() {
        val rotation = rotationSensor
        if (rotation != null) {
            sensorManager.registerListener(compassListener, rotation, SensorManager.SENSOR_DELAY_UI)
            return
        }
        val accel = accelerometerSensor
        val magnet = magnetometerSensor
        if (accel != null && magnet != null) {
            hasGravity = false
            hasMagnetic = false
            sensorManager.registerListener(compassListener, accel, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(compassListener, magnet, SensorManager.SENSOR_DELAY_UI)
        } else {
            webView.evaluateJavascript("window.onSajwaCompassError && window.onSajwaCompassError('This phone does not expose the sensors needed for a live compass.')", null)
        }
    }
    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 43, 35)
        window.navigationBarColor = android.graphics.Color.rgb(8, 43, 35)
        // Let Android fit the app window below the status bar and around display cutouts.
        // This avoids WebView content being laid out underneath the camera notch.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(SajwaBridge(), "SajwaNative")
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.setBackgroundColor(android.graphics.Color.rgb(246, 245, 239))
        // Decor fitting handles status/navigation bars and cutouts; do not add a second inset.
        setContentView(webView)
        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html")
        else webView.restoreState(savedInstanceState)
    }

    inner class SajwaBridge {
        @JavascriptInterface fun requestExactAlarmAccess(): Boolean {
            if (Build.VERSION.SDK_INT < 31) return true
            val alarms = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarms.canScheduleExactAlarms()) return true
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName)))
                } catch (_: Exception) { }
            }
            return false
        }
        @JavascriptInterface fun startCompass() { compassRequested = true; qiblaAligned = false; runOnUiThread { if (qiblaBearing != null) registerCompassSensors() } }
        @JavascriptInterface fun stopCompass() { compassRequested = false; runOnUiThread { sensorManager.unregisterListener(compassListener) } }
        @JavascriptInterface fun testAdhan() {
            val intent = Intent(this@MainActivity, AdhanPlaybackService::class.java).putExtra("prayer", "Test")
            try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent) } catch (_: Exception) { }
        }
        @JavascriptInterface fun stopAdhan() { stopService(Intent(this@MainActivity, AdhanPlaybackService::class.java)) }
        @JavascriptInterface fun setAdhanEnabled(enabled: Boolean) {
            getSharedPreferences("sajwa_prayer", MODE_PRIVATE).edit().putBoolean("adhan_enabled", enabled).apply()
        }
        @JavascriptInterface fun requestLocation() {
            runOnUiThread {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationPermissionCode)
                } else fetchLocation()
            }
        }
    }

    private fun fetchLocation() {
        try {
            val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val enabled = locationManager.getProviders(true)
            val preferred = if (fine) {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            } else {
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            }
            val providers = preferred.filter { enabled.contains(it) }
            if (providers.isEmpty()) {
                publishError("Location is turned off. Enable Location/GPS in Android settings and try again.")
                return
            }
            val recent = providers.mapNotNull { provider ->
                try { locationManager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
            if (recent != null && System.currentTimeMillis() - recent.time <= 10 * 60 * 1000L) {
                publishPrayerData(recent)
                return
            }
            val provider = providers.first()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.getCurrentLocation(provider, null, mainExecutor) { location ->
                    if (location != null) publishPrayerData(location)
                    else if (recent != null) publishPrayerData(recent)
                    else publishError("Could not get a location fix. Turn on GPS, check location permission and try again.")
                }
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) = publishPrayerData(location)
                    @Deprecated("Deprecated") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }, mainLooper)
            }
        } catch (_: SecurityException) {
            publishError("SAJWA does not have location permission. Allow approximate or precise location in Android settings.")
        } catch (_: Exception) {
            publishError("Could not read location. Turn on Location and check the app permission.")
        }
    }
    private fun publishPrayerData(location: Location) {
        try {
            val country = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    var code = "US"
                    val latch = java.util.concurrent.CountDownLatch(1)
                    Geocoder(this, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1) { list ->
                        if (list.isNotEmpty()) code = list[0].countryCode ?: code
                        latch.countDown()
                    }
                    latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                    code
                } else {
                    @Suppress("DEPRECATION")
                    Geocoder(this, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.countryCode ?: "US"
                }
            } catch (_: Exception) { "US" }
            val method = when (country.uppercase(Locale.US)) {
                "AE" -> CalculationMethod.DUBAI
                "BD", "PK", "IN", "AF" -> CalculationMethod.KARACHI
                "SA" -> CalculationMethod.UMM_AL_QURA
                "SG", "MY", "ID" -> CalculationMethod.SINGAPORE
                "EG" -> CalculationMethod.EGYPTIAN
                else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            }
            val params = method.getParameters()
            params.madhab = if (country.uppercase(Locale.US) in listOf("BD", "PK", "IN", "AF")) Madhab.HANAFI else Madhab.SHAFI
            val coords = Coordinates(location.latitude, location.longitude)
            val prayers = PrayerTimes(coords, DateComponents.from(Date()), params)
            val alarmScheduled = PrayerAlarmScheduler.schedule(this, coords, params, country)
            qiblaBearing = Qibla(coords).direction
            magneticDeclination = GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(), location.altitude.toFloat(), System.currentTimeMillis()).declination
            if (compassRequested) runOnUiThread { registerCompassSensors() }
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
            val times = JSONArray()
            listOf("Fajr" to prayers.fajr, "Sunrise" to prayers.sunrise, "Dhuhr" to prayers.dhuhr, "Asr" to prayers.asr, "Maghrib" to prayers.maghrib, "Isha" to prayers.isha).forEach { (name, date) ->
                times.put(JSONObject().put("name", name).put("time", fmt.format(date)).put("timestamp", date.time))
            }
            val data = JSONObject().put("ok", true).put("country", country).put("latitude", location.latitude).put("longitude", location.longitude)
                .put("method", method.name).put("madhab", params.madhab.name).put("times", times)
                .put("qibla", Qibla(coords).direction).put("timezone", TimeZone.getDefault().id).put("alarmsScheduled", alarmScheduled)
            if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103) }
            runOnUiThread { webView.evaluateJavascript("window.onSajwaPrayerData && window.onSajwaPrayerData(" + JSONObject.quote(data.toString()) + ")", null) }
        } catch (_: Exception) { publishError("Prayer time calculation failed. Please try again.") }
    }

    private fun publishError(message: String) {
        runOnUiThread { webView.evaluateJavascript("window.onSajwaPrayerError && window.onSajwaPrayerError(" + JSONObject.quote(message) + ")", null) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) fetchLocation()
            else publishError("Location permission is needed to calculate local prayer times.")
        }
    }

    override fun onResume() { super.onResume(); if (Build.VERSION.SDK_INT >= 31) { val alarms = getSystemService(ALARM_SERVICE) as AlarmManager; if (alarms.canScheduleExactAlarms()) PrayerAlarmScheduler.rescheduleFromSavedLocation(this) }; if (compassRequested && qiblaBearing != null) registerCompassSensors() }

    override fun onPause() { sensorManager.unregisterListener(compassListener); super.onPause() }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}