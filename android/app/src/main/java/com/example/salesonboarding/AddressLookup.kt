package com.example.salesonboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Address help for the address questions, from OpenStreetMap's free Nominatim service
 * (no account, no key, no bill). Nominatim's usage policy is followed: the app names itself
 * in every request, sends at most one request a second, never searches while the agent is
 * typing (only when a button is tapped), keeps recent answers instead of asking again, and
 * shows the OpenStreetMap credit next to the buttons.
 */

/** One address found by OpenStreetMap. */
data class AddressMatch(val label: String, val latitude: Double, val longitude: Double)

object OsmAddressLookup {
    const val CREDIT = "Address search by OpenStreetMap (© OpenStreetMap contributors)."
    const val USER_AGENT = "SalesMesh/1.0 (Android onboarding app; com.example.salesonboarding)"
    private const val BASE = "https://nominatim.openstreetmap.org"

    /** Up to five Kenyan addresses that match what was typed. */
    fun searchUrl(query: String, countryCode: String = "ke"): String =
        "$BASE/search?format=jsonv2&limit=5&countrycodes=$countryCode&q=" +
            URLEncoder.encode(query.trim(), "UTF-8")

    /** The address at a point, at street level. */
    fun reverseUrl(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%s/reverse?format=jsonv2&zoom=18&lat=%.6f&lon=%.6f", BASE, latitude, longitude)

    fun parseSearch(json: String): List<AddressMatch> {
        val results = JSONArray(json)
        return (0 until results.length()).mapNotNull { match(results.optJSONObject(it)) }.distinctBy { it.label }
    }

    /** Null when Nominatim found nothing there (it answers with an "error" field). */
    fun parseReverse(json: String): AddressMatch? {
        val result = JSONObject(json)
        return if (result.has("error")) null else match(result)
    }

    private fun match(result: JSONObject?): AddressMatch? {
        if (result == null) return null
        val label = shortLabel(result.optString("display_name"))
        val latitude = result.optString("lat").toDoubleOrNull()
        val longitude = result.optString("lon").toDoubleOrNull()
        if (label.isBlank() || latitude == null || longitude == null) return null
        return AddressMatch(label, latitude, longitude)
    }

    /** Nominatim's full name without the country at the end and without repeated parts. */
    internal fun shortLabel(displayName: String): String {
        val parts = displayName.split(",").map(String::trim).filter(String::isNotEmpty)
        val withoutCountry = if (parts.size > 1 && parts.last().equals("Kenya", ignoreCase = true)) {
            parts.dropLast(1)
        } else parts
        return withoutCountry.filterIndexed { index, part -> index == 0 || part != withoutCountry[index - 1] }
            .joinToString(", ")
            .take(FormField.MAX_ANSWER_LENGTH)
    }
}

/**
 * Sends the requests, one at a time and at least [MIN_GAP_MS] apart, and remembers the
 * last answers. One shared copy serves every address question.
 */
class OsmAddressService(
    private val get: suspend (String) -> String = ::httpGet,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    private val lock = Mutex()
    private var lastRequestAt: Long? = null
    private val recent = LinkedHashMap<String, String>()

    suspend fun search(query: String): List<AddressMatch> =
        OsmAddressLookup.parseSearch(fetch(OsmAddressLookup.searchUrl(query)))

    suspend fun reverse(latitude: Double, longitude: Double): AddressMatch? =
        OsmAddressLookup.parseReverse(fetch(OsmAddressLookup.reverseUrl(latitude, longitude)))

    private suspend fun fetch(url: String): String = lock.withLock {
        recent[url]?.let { return it }
        lastRequestAt?.let { last ->
            val gap = last + MIN_GAP_MS - clock()
            if (gap > 0) wait(gap)
        }
        lastRequestAt = clock()
        val body = get(url)
        recent[url] = body
        if (recent.size > KEEP_RECENT) recent.remove(recent.keys.first())
        body
    }

    companion object {
        const val MIN_GAP_MS = 1_000L
        private const val KEEP_RECENT = 20
        val shared: OsmAddressService by lazy { OsmAddressService() }
    }
}

private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", OsmAddressLookup.USER_AGENT)
        connection.setRequestProperty("Accept-Language", "en")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("The address service answered ${connection.responseCode}.")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

internal fun Context.hasLocationPermission(): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

/** One fresh reading of where the phone is, or null if location is off or takes over 15 s. */
@SuppressLint("MissingPermission")
internal suspend fun Context.currentLocation(): Location? {
    if (!hasLocationPermission()) return null
    val manager = getSystemService(LocationManager::class.java) ?: return null
    val provider = listOfNotNull(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER
    ).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
    val fresh = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine<Location?> { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(this@currentLocation)) {
                    if (continuation.isActive) continuation.resume(it)
                }
            } else {
                // Every method is written out: before Android 11 none of them had a default.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }
                    @Deprecated("Needed before Android 11")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }
    return fresh ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
}

/** Address questions are the text questions about an address (not the email address). */
internal fun FormField.isStreetAddress(): Boolean =
    fieldId.contains("address") && fieldType.lowercase(Locale.ROOT) == "text"

/**
 * "Use my location" and "Find address" under an address question. Both only suggest: the
 * agent taps Use to fill the question, after checking the suggestion with the customer.
 */
@Composable
internal fun AddressFinder(query: String, onUse: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Sm.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val online = rememberNetworkStatus()
    var busy by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf(emptyList<AddressMatch>()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun start(status: String): Boolean {
        if (busy != null) return false
        matches = emptyList()
        message = null
        if (!online) {
            message = "No internet connection. Type the address instead."
            return false
        }
        busy = status
        return true
    }

    fun locate() {
        if (!start("Finding where this phone is…")) return
        scope.launch {
            val here = context.currentLocation()
            if (here == null) {
                busy = null
                message = "Could not find this phone's location. Turn on location, or type the address."
                return@launch
            }
            busy = "Looking up the address…"
            runCatching { OsmAddressService.shared.reverse(here.latitude, here.longitude) }
                .onSuccess { found ->
                    if (found == null) message = "No address was found here. Type it instead."
                    else matches = listOf(found)
                }
                .onFailure { message = "Could not reach the address service. Try again, or type the address." }
            busy = null
        }
    }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) locate()
        else message = "Location was not allowed. Type the address, or use Find address."
    }

    fun search() {
        if (query.trim().length < 3) {
            matches = emptyList()
            message = "Type part of the address first, such as the street or area, then tap Find address."
            return
        }
        if (!start("Searching…")) return
        scope.launch {
            runCatching { OsmAddressService.shared.search(query) }
                .onSuccess { found ->
                    if (found.isEmpty()) message = "No matching address. Check the spelling, or type the full address."
                    else matches = found
                }
                .onFailure { message = "Could not reach the address service. Try again, or type the address." }
            busy = null
        }
    }

    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            AddressAction(Icons.Filled.LocationOn, "Use my location", enabled = busy == null) {
                if (context.hasLocationPermission()) locate()
                else askPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            AddressAction(Icons.Filled.Search, "Find address", enabled = busy == null, onClick = ::search)
        }
        busy?.let { status ->
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = c.ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = c.mutedStrong)
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.cherryDeep, modifier = Modifier.padding(top = 4.dp)) }
        if (matches.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = c.cream,
                border = BorderStroke(1.dp, c.line)
            ) {
                Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Check with the customer, then tap Use.", style = MaterialTheme.typography.titleSmall)
                    matches.forEach { match ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Text(match.label, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                            SecondaryButton("Use", onClick = {
                                onUse(match.label)
                                matches = emptyList()
                            }, modifier = Modifier.widthIn(max = 96.dp))
                        }
                    }
                }
            }
        }
        Text(
            OsmAddressLookup.CREDIT + " What is searched, or this phone's location, is sent to it.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AddressAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Sm.colors
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
            color = if (enabled) c.ink else c.muted
        )
    }
}
