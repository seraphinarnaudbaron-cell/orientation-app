package org.orientationjp.app

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Morceau de parcours à dessiner : plein ou pointillé, déjà parcouru (gris) ou restant (bleu). */
class CoursePart(val points: List<GeoPt>, val dashed: Boolean, val done: Boolean)

/** Point du tracé le plus proche du coureur : distance (m) et abscisse curviligne (m depuis le départ). */
class NearestOnPath(val dist: Double, val progress: Double)

/**
 * Tracé continu d'un parcours avec distances cumulées. Sert à :
 * - savoir où en est le coureur (progression le long du tracé) ;
 * - dessiner la partie parcourue en gris et la partie restante en bleu ;
 * - détecter qu'il s'est écarté du tracé.
 */
class CourseGeometry(course: Course) {
    val pts = ArrayList<GeoPt>()
    private val edgeDashed = ArrayList<Boolean>()
    private val cum = ArrayList<Double>()
    /** stopCum[k] = distance cumulée à la fin de l'étape k (balise k, puis l'arrivée pour la dernière). */
    val stopCum: DoubleArray
    val total: Double

    init {
        var acc = 0.0
        val legEnd = HashMap<Int, Double>()
        for (seg in course.segments) {
            for (i in seg.points.indices) {
                val p = seg.points[i]
                if (pts.isEmpty()) {
                    pts.add(p)
                    cum.add(0.0)
                    continue
                }
                val prev = if (i == 0) pts.last() else seg.points[i - 1]
                val d = Geo.distance(prev.lat, prev.lon, p.lat, p.lon)
                if (i == 0 && d < 0.01) continue // raccord : même point que la fin du morceau précédent
                acc += d
                pts.add(p)
                edgeDashed.add(seg.dashed)
                cum.add(acc)
            }
            legEnd[seg.leg] = acc
        }
        total = acc
        stopCum = DoubleArray(course.legs.size) { legEnd[it] ?: acc }
    }

    /** Point du tracé le plus proche de (lat, lon), en ne regardant que la portion [fromM, toM] du tracé. */
    fun nearest(lat: Double, lon: Double, fromM: Double, toM: Double): NearestOnPath? {
        if (pts.size < 2) return null
        val kx = 111320.0 * cos(Math.toRadians(lat))
        val ky = 110574.0
        var bestD = Double.MAX_VALUE
        var bestP = 0.0
        for (i in 0 until pts.size - 1) {
            val c0 = cum[i]
            val c1 = cum[i + 1]
            if (c1 < fromM || c0 > toM) continue
            val ax = (pts[i].lon - lon) * kx
            val ay = (pts[i].lat - lat) * ky
            val bx = (pts[i + 1].lon - lon) * kx
            val by = (pts[i + 1].lat - lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-9) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / len2))
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (d < bestD) {
                bestD = d
                bestP = c0 + t * (c1 - c0)
            }
        }
        return if (bestD == Double.MAX_VALUE) null else NearestOnPath(bestD, bestP)
    }

    /** Découpe le tracé à la distance [progress] : ce qui est derrière est « done » (gris), le reste est bleu. */
    fun renderParts(progress: Double): List<CoursePart> {
        val parts = ArrayList<CoursePart>()
        var curPts = ArrayList<GeoPt>()
        var curDashed = false
        var curDone = false

        fun flush() {
            if (curPts.size >= 2) parts.add(CoursePart(curPts, curDashed, curDone))
            curPts = ArrayList()
        }

        fun addEdge(a: GeoPt, b: GeoPt, dashed: Boolean, done: Boolean) {
            if (curPts.isNotEmpty() && (dashed != curDashed || done != curDone)) flush()
            if (curPts.isEmpty()) {
                curPts.add(a)
                curDashed = dashed
                curDone = done
            }
            curPts.add(b)
        }

        for (i in 0 until pts.size - 1) {
            val c0 = cum[i]
            val c1 = cum[i + 1]
            val a = pts[i]
            val b = pts[i + 1]
            val dashed = edgeDashed[i]
            if (c1 <= progress) {
                addEdge(a, b, dashed, true)
            } else if (c0 >= progress) {
                addEdge(a, b, dashed, false)
            } else {
                val f = (progress - c0) / (c1 - c0)
                val mid = GeoPt(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
                addEdge(a, mid, dashed, true)
                addEdge(mid, b, dashed, false)
            }
        }
        flush()
        return parts
    }
}
