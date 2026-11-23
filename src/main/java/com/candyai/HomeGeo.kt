package com.candyai

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject

/**
 * "Remind me when I leave home". The home place and the notes waiting for it live on the phone only (nothing goes to the server).
 * One geofence around home watches for EXIT; when it fires, Candy lists the waiting notes once and they stop waiting.
 */
object HomeGeo {
    private const val PREFS = "home_geo"
    private const val GEOFENCE_ID = "home"
    const val MIN_RADIUS = 50
    const val MAX_RADIUS = 1000
    const val DEFAULT_RADIUS = 150

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    class Home(val lat: Double, val lon: Double, val radius: Int)

    fun home(ctx: Context): Home? {
        val p = sp(ctx)
        if (!p.contains("lat")) return null
        return Home(Double.fromBits(p.getLong("lat", 0)), Double.fromBits(p.getLong("lon", 0)), p.getInt("radius", DEFAULT_RADIUS))
    }

    fun setHome(ctx: Context, lat: Double, lon: Double, radius: Int) {
        sp(ctx).edit().putLong("lat", lat.toBits()).putLong("lon", lon.toBits()).putInt("radius", radius.coerceIn(MIN_RADIUS, MAX_RADIUS)).apply()
        register(ctx)
    }

    fun setRadius(ctx: Context, radius: Int) {
        sp(ctx).edit().putInt("radius", radius.coerceIn(MIN_RADIUS, MAX_RADIUS)).apply()
        register(ctx)
    }

    /** id → title of the notes waiting for "when I leave home". */
    private fun waiting(ctx: Context): JSONObject = try { JSONObject(sp(ctx).getString("leave", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    fun isWaiting(ctx: Context, id: String): Boolean = waiting(ctx).has(id)

    fun setWaiting(ctx: Context, id: String, title: String, on: Boolean) {
        val w = waiting(ctx)
        if (on) w.put(id, title) else w.remove(id)
        sp(ctx).edit().putString("leave", w.toString()).apply()
        register(ctx)
    }

    /** Called with the notes from the server: forget the ones that are gone, refresh the titles, and keep the geofence in line. */
    fun sync(ctx: Context, notes: List<Note>) {
        val w = waiting(ctx)
        var changed = false
        for (id in w.keys().asSequence().toList()) {
            val n = notes.firstOrNull { it.id == id }
            if (n == null) { w.remove(id); changed = true }
            else if (w.optString(id) != Reminders.title(n.text)) { w.put(id, Reminders.title(n.text)); changed = true }
        }
        if (changed) sp(ctx).edit().putString("leave", w.toString()).apply()
        register(ctx)
    }

    fun hasFine(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackground(ctx: Context) = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx.applicationContext, 4242, Intent(ctx.applicationContext, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    /** Watches home while some note waits for it (and the phone allows it); otherwise stops watching. */
    @android.annotation.SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        val app = ctx.applicationContext
        val client = LocationServices.getGeofencingClient(app)
        val h = home(app)
        if (h == null || waiting(app).length() == 0 || !hasFine(app) || !hasBackground(app)) {
            try { client.removeGeofences(pending(app)) } catch (_: Exception) { }
            return
        }
        try {
            val fence = Geofence.Builder().setRequestId(GEOFENCE_ID)
                .setCircularRegion(h.lat, h.lon, h.radius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
            client.addGeofences(GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(fence).build(), pending(app))
        } catch (_: Exception) { }
    }

    /** The phone's current place, for choosing home. [onResult] gets null when it could not be found. */
    @android.annotation.SuppressLint("MissingPermission")
    fun locate(ctx: Context, onResult: (Pair<Double, Double>?) -> Unit) {
        if (!hasFine(ctx)) { onResult(null); return }
        try {
            LocationServices.getFusedLocationProviderClient(ctx.applicationContext)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { onResult(it?.let { l -> l.latitude to l.longitude }) }
                .addOnFailureListener { onResult(null) }
        } catch (_: Exception) { onResult(null) }
    }

    /** The geofence fired: tell the person once, then the notes stop waiting. */
    fun fire(ctx: Context) {
        val w = waiting(ctx)
        val titles = w.keys().asSequence().map { w.optString(it) }.filter { it.isNotBlank() }.toList()
        sp(ctx).edit().putString("leave", "{}").apply()
        register(ctx)
        if (titles.isEmpty()) return
        Reminders.channel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 78,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_sugar", "list"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = ctx.getString(R.string.leave_notif_body, titles.joinToString("، "))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "sugar_reminders") else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val n = b.setSmallIcon(R.drawable.ic_edge_tile)
            .setContentTitle(ctx.getString(R.string.sugar_remind_title))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(4243, n)
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        if (ev.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) HomeGeo.fire(context)
    }
}
