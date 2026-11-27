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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
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
    private val locationExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
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
            try {
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
            } catch (_: Exception) {
                runOnUiThread { webView.evaluateJavascript("window.onSajwaCompassError && window.onSajwaCompassError('Compass sensor data is temporarily unavailable. Move the phone slowly and try again.')", null) }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                runOnUiThread { webView.evaluateJavascript("window.onSajwaCompassError && window.onSajwaCompassError('Compass accuracy is low. Move away from magnets and slowly rotate the phone in a figure-eight.')", null) }
            }
        }
    }

    private fun registerCompassSensors() {
        try {
            sensorManager.unregisterListener(compassListener)
            val rotation = rotationSensor
            if (rotation != null && sensorManager.registerListener(compassListener, rotation, SensorManager.SENSOR_DELAY_UI)) return
            val accel = accelerometerSensor
            val magnet = magnetometerSensor
            if (accel != null && magnet != null) {
                hasGravity = false
                hasMagnetic = false
                val accelOk = sensorManager.registerListener(compassListener, accel, SensorManager.SENSOR_DELAY_UI)
                val magnetOk = sensorManager.registerListener(compassListener, magnet, SensorManager.SENSOR_DELAY_UI)
                if (accelOk && magnetOk) return
                sensorManager.unregisterListener(compassListener)
            }
            webView.evaluateJavascript("window.onSajwaCompassError && window.onSajwaCompassError('Compass sensors are unavailable or could not be started on this phone.')", null)
        } catch (_: Exception) {
            try { sensorManager.unregisterListener(compassListener) } catch (_: Exception) { }
            webView.evaluateJavascript("window.onSajwaCompassError && window.onSajwaCompassError('The compass could not start. Please close and reopen the Qibla screen.')", null)
        }
    }
    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sajwaForest = android.graphics.Color.rgb(8, 43, 35)
        // Keep the system-bar/inset area the same forest green as the sticky SAJWA header.
        // This also supplies the correct color on devices that briefly expose the window
        // background before the WebView has finished drawing.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(sajwaForest))
        window.statusBarColor = sajwaForest
        window.navigationBarColor = sajwaForest
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        // Draw the forest-green window edge-to-edge, but inset the WebView content
        // below the real status bar/display cutout and above the navigation/gesture bar.
        // On Android 15+ decor fitting may be ignored for apps targeting the enforced
        // edge-to-edge behavior, so apply the insets directly to the WebView.
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.e("SAJWA-WebView", "${message.message()} -- line ${message.lineNumber()} of ${message.sourceId()}")
                return true
            }
        }
        // Serve bundled HTML from a secure HTTPS-like origin. file:// origins often
        // block cross-origin Quran APIs, Hadith links and remote web fonts in WebView.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) { true }
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(android.net.Uri.parse(url))
            }
        }
        // Keep the system-bar background green while ensuring page content never sits
        // underneath status icons, the camera cutout, or the bottom gesture/navigation bar.
        webView.setBackgroundColor(sajwaForest)
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safeInsets.top, 0, safeInsets.bottom)
            insets
        }
        setContentView(webView)
        ViewCompat.requestApplyInsets(webView)
        if (savedInstanceState == null) webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        else webView.restoreState(savedInstanceState)
    }

    inner class SajwaBridge {
        @JavascriptInterface fun getDiagnostics(): String {
            val fine = ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val providers = try { locationManager.getProviders(true).joinToString(",") } catch (_: Exception) { "unavailable" }
            return JSONObject()
                .put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("webView", "loaded")
                .put("locationFine", fine)
                .put("locationCoarse", coarse)
                .put("enabledProviders", providers)
                .put("rotationSensor", rotationSensor != null)
                .put("accelerometer", accelerometerSensor != null)
                .put("magnetometer", magnetometerSensor != null)
                .put("qiblaBearingReady", qiblaBearing != null)
                .put("compassRequested", compassRequested)
                .toString()
        }
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
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Ask every permitted provider. Network location is usually quicker indoors;
                // GPS can still return a fix if the network provider has no result.
                val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
                val pending = java.util.concurrent.atomic.AtomicInteger(providers.size)
                for (provider in providers) {
                    try {
                        locationManager.getCurrentLocation(provider, null, mainExecutor) { location ->
                            if (location != null && delivered.compareAndSet(false, true)) {
                                publishPrayerData(location)
                            } else if (pending.decrementAndGet() == 0 && !delivered.get()) {
                                if (recent != null) publishPrayerData(recent)
                                else publishError("Could not get a location fix. Turn on GPS, allow location permission and try again.")
                            }
                        }
                    } catch (_: SecurityException) {
                        if (pending.decrementAndGet() == 0 && !delivered.get()) {
                            if (recent != null) publishPrayerData(recent)
                            else publishError("Allow precise or approximate location access for SAJWA in Android settings.")
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(providers.first(), object : LocationListener {
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
    private fun approximateCountry(lat: Double, lon: Double): String? {
        return when {
            lat in 22.5..26.5 && lon in 51.0..56.5 -> "AE"
            lat in 20.5..26.8 && lon in 88.0..92.8 -> "BD"
            lat in 23.5..37.5 && lon in 60.5..77.5 -> "PK"
            lat in 6.0..37.5 && lon in 68.0..97.5 -> "IN"
            lat in 16.0..33.5 && lon in 34.0..56.0 -> "SA"
            lat in 1.1..1.5 && lon in 103.6..104.1 -> "SG"
            lat in 0.8..7.5 && lon in 99.0..120.0 -> "MY"
            lat in -11.0..6.5 && lon in 95.0..141.0 -> "ID"
            lat in 22.0..32.0 && lon in 24.0..37.0 -> "EG"
            else -> null
        }
    }

    private fun publishPrayerData(location: Location) {
        // Geocoding and prayer calculations run off the UI thread. The Android 13+
        // Geocoder callback can be delivered on the main looper, so never wait for it
        // while blocking the UI thread.
        locationExecutor.execute {
            try {
                val country = try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        var code = approximateCountry(location.latitude, location.longitude)
                            ?: Locale.getDefault().country.uppercase(Locale.US).ifBlank { "US" }
                        val latch = java.util.concurrent.CountDownLatch(1)
                        Geocoder(this, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1) { list ->
                            if (list.isNotEmpty()) code = list[0].countryCode ?: code
                            latch.countDown()
                        }
                        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                        code
                    } else {
                        @Suppress("DEPRECATION")
                        Geocoder(this, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.countryCode
                            ?: Locale.getDefault().country.uppercase(Locale.US).ifBlank { "US" }
                    }
                } catch (_: Exception) {
                    approximateCountry(location.latitude, location.longitude)
                        ?: Locale.getDefault().country.uppercase(Locale.US).ifBlank { "US" }
                }
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
                // Prayer times must still be displayed even if Android blocks exact alarms.
                val alarmScheduled = try {
                    PrayerAlarmScheduler.schedule(this, coords, params, country)
                } catch (_: SecurityException) {
                    false
                } catch (_: Exception) {
                    false
                }
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
                runOnUiThread {
                    if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103)
                    }
                    webView.evaluateJavascript("window.onSajwaPrayerData && window.onSajwaPrayerData(" + JSONObject.quote(data.toString()) + ")", null)
                }
            } catch (_: Exception) {
                publishError("Prayer time calculation failed. Please try again.")
            }
        }
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

    override fun onDestroy() { locationExecutor.shutdownNow(); super.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}