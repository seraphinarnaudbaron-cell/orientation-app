package org.orientationjp.app

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Réseau de chemins construit à partir des traces enregistrées par l'admin.
 *
 * 1. Chaque trace est ré-échantillonnée tous les [SPACING] m (un point toutes les ~2 m).
 * 2. Les points consécutifs d'une trace sont reliés.
 * 3. Deux traces qui passent à moins de [JOIN_RADIUS] m l'une de l'autre (croisement, jonction,
 *    chemins qui se frôlent) sont reliées : c'est un nœud.
 * 4. Itinéraire = plus court chemin (Dijkstra) sur ce réseau.
 */
class TrailGraph(trails: List<Trail>) {

    companion object {
        const val SPACING = 2.0          // pas de ré-échantillonnage (m)
        const val JOIN_RADIUS = 5.0      // distance max pour fusionner deux chemins en un nœud (m)
        const val MAX_GAP = 80.0         // un trou GPS plus grand coupe la trace (m)
        const val MIN_SEQ_GAP = 10       // sur une même trace : ignorer les voisins à < 10 points (~20 m)
        const val SNAP_START_M = 60.0    // le coureur doit être à moins de 60 m d'un chemin
        const val SNAP_END_M = 40.0      // la balise doit être à moins de 40 m d'un chemin
    }

    private class Edge(val to: Int, val w: Double)

    private val lats = ArrayList<Double>()
    private val lons = ArrayList<Double>()
    private val group = ArrayList<Int>()
    private val seq = ArrayList<Int>()
    private val adj = ArrayList<ArrayList<Edge>>()

    val nodeCount: Int get() = lats.size

    init {
        build(trails)
    }

    private fun addNode(lat: Double, lon: Double, g: Int, s: Int): Int {
        lats.add(lat)
        lons.add(lon)
        group.add(g)
        seq.add(s)
        adj.add(ArrayList())
        return lats.size - 1
    }

    private fun addEdge(a: Int, b: Int, w: Double) {
        adj[a].add(Edge(b, w))
        adj[b].add(Edge(a, w))
    }

    private fun link(a: Int, b: Int) {
        addEdge(a, b, Geo.distance(lats[a], lons[a], lats[b], lons[b]))
    }

    private fun build(trails: List<Trail>) {
        var groupId = 0
        for (t in trails) {
            var prev = -1
            var prevLat = 0.0
            var prevLon = 0.0
            var s = 0
            for (p in t.points) {
                if (prev == -1) {
                    prev = addNode(p.lat, p.lon, groupId, s++)
                    prevLat = p.lat
                    prevLon = p.lon
                    continue
                }
                val d = Geo.distance(prevLat, prevLon, p.lat, p.lon)
                if (d > MAX_GAP) {
                    // trou GPS : on repart sur une nouvelle chaîne
                    groupId++
                    s = 0
                    prev = addNode(p.lat, p.lon, groupId, s++)
                    prevLat = p.lat
                    prevLon = p.lon
                    continue
                }
                val steps = floor(d / SPACING).toInt()
                for (k in 1..steps) {
                    val f = k * SPACING / d
                    if (f >= 0.999) break
                    val n = addNode(prevLat + (p.lat - prevLat) * f, prevLon + (p.lon - prevLon) * f, groupId, s++)
                    link(prev, n)
                    prev = n
                }
                val n = addNode(p.lat, p.lon, groupId, s++)
                link(prev, n)
                prev = n
                prevLat = p.lat
                prevLon = p.lon
            }
            groupId++
        }
        linkJunctions()
    }

    private fun cellKey(cx: Long, cy: Long): Long = cx * 10_000_000L + cy

    private fun linkJunctions() {
        val n = lats.size
        if (n == 0) return
        val kx = 111320.0 * cos(Math.toRadians(lats[0]))
        val ky = 110574.0
        val xs = DoubleArray(n) { lons[it] * kx }
        val ys = DoubleArray(n) { lats[it] * ky }

        val grid = HashMap<Long, ArrayList<Int>>()
        for (i in 0 until n) {
            val cx = floor(xs[i] / JOIN_RADIUS).toLong()
            val cy = floor(ys[i] / JOIN_RADIUS).toLong()
            grid.getOrPut(cellKey(cx, cy)) { ArrayList() }.add(i)
        }

        for (i in 0 until n) {
            val cx = floor(xs[i] / JOIN_RADIUS).toLong()
            val cy = floor(ys[i] / JOIN_RADIUS).toLong()
            val bestJ = HashMap<Int, Int>()
            val bestD = HashMap<Int, Double>()
            for (dx in -1L..1L) {
                for (dy in -1L..1L) {
                    val list = grid[cellKey(cx + dx, cy + dy)] ?: continue
                    for (j in list) {
                        if (j == i) continue
                        if (group[j] == group[i] && abs(seq[j] - seq[i]) <= MIN_SEQ_GAP) continue
                        val ddx = xs[i] - xs[j]
                        val ddy = ys[i] - ys[j]
                        val d = sqrt(ddx * ddx + ddy * ddy)
                        if (d > JOIN_RADIUS) continue
                        val g = group[j]
                        val cur = bestD[g]
                        if (cur == null || d < cur) {
                            bestD[g] = d
                            bestJ[g] = j
                        }
                    }
                }
            }
            // un seul lien par autre chaîne : le plus proche
            for ((g, j) in bestJ) {
                addEdge(i, j, bestD[g] ?: 0.0)
            }
        }
    }

    private fun nearest(lat: Double, lon: Double, maxM: Double): Int? {
        var best = -1
        var bestD = Double.MAX_VALUE
        for (i in 0 until lats.size) {
            val d = Geo.distance(lat, lon, lats[i], lons[i])
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return if (best >= 0 && bestD <= maxM) best else null
    }

    /** Itinéraire du coureur vers la balise en suivant les chemins, ou null s'il n'y en a pas. */
    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route? {
        if (lats.isEmpty()) return null
        val s = nearest(fromLat, fromLon, SNAP_START_M) ?: return null
        val t = nearest(toLat, toLon, SNAP_END_M) ?: return null

        val n = lats.size
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        dist[s] = 0.0
        val pq = PriorityQueue<DoubleArray>(16, Comparator<DoubleArray> { a, b -> a[0].compareTo(b[0]) })
        pq.add(doubleArrayOf(0.0, s.toDouble()))
        while (pq.isNotEmpty()) {
            val top = pq.poll() ?: break
            val u = top[1].toInt()
            if (top[0] > dist[u]) continue
            if (u == t) break
            for (e in adj[u]) {
                val nd = dist[u] + e.w
                if (nd < dist[e.to]) {
                    dist[e.to] = nd
                    prev[e.to] = u
                    pq.add(doubleArrayOf(nd, e.to.toDouble()))
                }
            }
        }
        if (dist[t] == Double.MAX_VALUE) return null

        val nodes = ArrayList<Int>()
        var cur = t
        while (cur != -1) {
            nodes.add(cur)
            cur = prev[cur]
        }
        nodes.reverse()
        val pts = nodes.map { GeoPt(lats[it], lons[it]) }
        val total = dist[t] +
            Geo.distance(fromLat, fromLon, lats[s], lons[s]) +
            Geo.distance(lats[t], lons[t], toLat, toLon)
        return Route(GeoPt(fromLat, fromLon), pts, GeoPt(toLat, toLon), total)
    }
}
