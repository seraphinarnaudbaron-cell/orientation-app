package org.orientationjp.app

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Cap du téléphone (par rapport au nord géographique) via le capteur d'orientation.
 * N'est activé que quand on en a besoin (flèche vers la balise) pour économiser la batterie.
 */
class CompassTracker(context: Context, private val onHeading: (Float) -> Unit) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rot = FloatArray(9)
    private var sinF = 0.0
    private var cosF = 0.0
    private var has = false
    private var lastEmit = 0L
    private var declination = 0f

    /** false si le téléphone n'a pas de boussole. */
    fun start(lat: Double?, lon: Double?): Boolean {
        val s = sensor ?: return false
        if (lat != null && lon != null) {
            declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        }
        has = false
        return manager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, event.values)
        // direction « devant » : le haut du téléphone s'il est à plat, son dos s'il est tenu debout
        val hy = hypot(rot[1], rot[4])
        val hz = hypot(rot[2], rot[5])
        val east: Float
        val north: Float
        if (hy >= hz) {
            east = rot[1]
            north = rot[4]
        } else {
            east = -rot[2]
            north = -rot[5]
        }
        val az = Math.toDegrees(atan2(east.toDouble(), north.toDouble())) + declination
        val rad = Math.toRadians(az)
        if (!has) {
            sinF = sin(rad)
            cosF = cos(rad)
            has = true
        } else {
            // lissage (moyenne glissante sur le cercle) pour éviter une flèche qui tremble
            sinF += 0.15 * (sin(rad) - sinF)
            cosF += 0.15 * (cos(rad) - cosF)
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmit >= 120) {
            lastEmit = now
            var deg = Math.toDegrees(atan2(sinF, cosF)).toFloat()
            if (deg < 0f) deg += 360f
            onHeading(deg)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
