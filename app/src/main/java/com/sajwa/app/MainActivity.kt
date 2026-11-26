package com.sajwa.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.DateComponents
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

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 43, 35)
        window.navigationBarColor = android.graphics.Color.rgb(8, 43, 35)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(SajwaBridge(), "SajwaNative")
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.setBackgroundColor(android.graphics.Color.rgb(246, 245, 239))
        setContentView(webView)
        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html")
        else webView.restoreState(savedInstanceState)
    }

    inner class SajwaBridge {
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
            val provider = if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            val last = locationManager.getLastKnownLocation(provider)
            if (last != null) { publishPrayerData(last); return }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.getCurrentLocation(provider, null, mainExecutor) { loc ->
                    if (loc != null) publishPrayerData(loc) else publishError("Location unavailable. Please enable GPS and try again.")
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
        } catch (_: Exception) { publishError("Could not read location. Check location settings and permission.") }
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
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
            val times = JSONArray()
            listOf("Fajr" to prayers.fajr, "Sunrise" to prayers.sunrise, "Dhuhr" to prayers.dhuhr, "Asr" to prayers.asr, "Maghrib" to prayers.maghrib, "Isha" to prayers.isha).forEach { (name, date) ->
                times.put(JSONObject().put("name", name).put("time", fmt.format(date)).put("timestamp", date.time))
            }
            val data = JSONObject().put("ok", true).put("country", country).put("latitude", location.latitude).put("longitude", location.longitude)
                .put("method", method.name).put("madhab", params.madhab.name).put("times", times)
                .put("qibla", Qibla(coords).direction).put("timezone", TimeZone.getDefault().id)
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

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}