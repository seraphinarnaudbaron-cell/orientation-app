package org.orientationjp.app

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Arbre des plus courts chemins depuis une source (coût = temps de marche en secondes). */
class Tree(val src: Int, val cost: DoubleArray, val prev: IntArray)

class Metrics(val length: Double, val ascent: Double, val descent: Double)

/**
 * Réseau de chemins construit à partir des traces enregistrées par l'admin.
 *
 * - Chaque trace de type "path" est ré-échantillonnée tous les [SPACING] m, et ses points consécutifs sont reliés.
 * - Deux traces qui passent à moins de [JOIN_RADIUS] m (croisement, jonction) sont reliées (nœud), SAUF si
 *     * un obstacle ("barrier") passe entre les deux (rivière, clôture...), ou
 *     * leurs altitudes diffèrent de plus de [MAX_JUNCTION_DZ] m (falaise, pont, chemins à des niveaux différents).
 * - Coût d'une arête = temps de marche (formule de Tobler) : montées et descentes pénalisées, donc l'itinéraire
 *   est le plus RAPIDE, pas le plus court en 2D.
 * - Le point de départ / d'arrivée est rattaché au chemin le plus proche dont le raccord ne traverse pas d'obstacle.
 */
class TrailGraph(all: List<Trail>) {

    companion object {
        const val SPACING = 2.0
        const val JOIN_RADIUS = 5.0
        const val MAX_GAP = 80.0
        const val MIN_SEQ_GAP = 10
        const val SNAP_START_M = 60.0
        const val SNAP_END_M = 40.0
        const val MAX_JUNCTION_DZ = 3.0
        private const val SMOOTH_HALF = 4

        /** Vitesse de marche (m/s) selon la pente (dz/dx), formule de Tobler. */
        fun speedMs(slope: Double): Double {
            val s = slope.coerceIn(-0.6, 0.6)
            return 6.0 * exp(-3.5 * abs(s + 0.05)) / 3.6
        }
    }

    private class Edge(val to: Int, val d: Double, val junction: Boolean)

    // ---- construction
    private val lats = ArrayList<Double>()
    private val lons = ArrayList<Double>()
    private val eles = ArrayList<Double>()
    private val group = ArrayList<Int>()
    private val seq = ArrayList<Int>()
    private val adj = ArrayList<ArrayList<Edge>>()

    // projection locale (mètres)
    private val refLat: Double
    private val kx: Double
    private val ky = 110574.0

    // obstacles (segments en mètres)
    private val bx1 = ArrayList<Double>()
    private val by1 = ArrayList<Double>()
    private val bx2 = ArrayList<Double>()
    private val by2 = ArrayList<Double>()

    init {
        val first = all.firstOrNull { it.points.isNotEmpty() }?.points?.first()
        refLat = first?.lat ?: 45.0
        kx = 111320.0 * cos(Math.toRadians(refLat))
        for (t in all) {
            if (t.isBarrier) addBarrier(t) else addPath(t)
        }
        linkJunctions()
    }

    // tableaux compacts pour le calcul
    private val latA = lats.toDoubleArray()
    private val lonA = lons.toDoubleArray()
    private val eleA = eles.toDoubleArray()
    private val xA = DoubleArray(latA.size) { lonA[it] * kx }
    private val yA = DoubleArray(latA.size) { latA[it] * ky }
    private val hasBarriers = bx1.isNotEmpty()

    val nodeCount: Int get() = latA.size

    fun nodePt(i: Int): GeoPt = GeoPt(latA[i], lonA[i], eleA[i])

    private fun px(lon: Double) = lon * kx
    private fun py(lat: Double) = lat * ky

    private fun addBarrier(t: Trail) {
        for (i in 1 until t.points.size) {
            val a = t.points[i - 1]
            val b = t.points[i]
            bx1.add(px(a.lon)); by1.add(py(a.lat)); bx2.add(px(b.lon)); by2.add(py(b.lat))
        }
    }

    private fun addNode(lat: Double, lon: Double, ele: Double, g: Int, s: Int): Int {
        lats.add(lat); lons.add(lon); eles.add(ele); group.add(g); seq.add(s); adj.add(ArrayList())
        return lats.size - 1
    }

    private fun link(a: Int, b: Int) {
        val d = Geo.distance(lats[a], lons[a], lats[b], lons[b])
        adj[a].add(Edge(b, d, false))
        adj[b].add(Edge(a, d, false))
    }

    private var nextGroup = 0

    private fun smoothChain(nodes: List<Int>) {
        if (nodes.size < 3) return
        val raw = DoubleArray(nodes.size) { eles[nodes[it]] }
        if (raw.any { it.isNaN() }) return
        for (i in nodes.indices) {
            val lo = max(0, i - SMOOTH_HALF)
            val hi = min(nodes.size - 1, i + SMOOTH_HALF)
            var sum = 0.0
            for (k in lo..hi) sum += raw[k]
            eles[nodes[i]] = sum / (hi - lo + 1)
        }
    }

    private fun addPath(t: Trail) {
        val hasEle = t.points.isNotEmpty() && t.points.all { !it.ele.isNaN() }
        fun z(p: GeoPt) = if (hasEle) p.ele else Double.NaN

        var chain = ArrayList<Int>()
        var g = nextGroup++
        var s = 0
        var prev = -1
        var prevPt: GeoPt? = null

        for (p in t.points) {
            val pp = prevPt
            if (pp == null) {
                prev = addNode(p.lat, p.lon, z(p), g, s++)
                chain.add(prev)
                prevPt = p
                continue
            }
            val d = Geo.distance(pp.lat, pp.lon, p.lat, p.lon)
            if (d > MAX_GAP) {
                smoothChain(chain)
                chain = ArrayList()
                g = nextGroup++
                s = 0
                prev = addNode(p.lat, p.lon, z(p), g, s++)
                chain.add(prev)
                prevPt = p
                continue
            }
            val steps = floor(d / SPACING).toInt()
            for (k in 1..steps) {
                val f = k * SPACING / d
                if (f >= 0.999) break
                val ze = if (hasEle) z(pp) + (z(p) - z(pp)) * f else Double.NaN
                val n = addNode(pp.lat + (p.lat - pp.lat) * f, pp.lon + (p.lon - pp.lon) * f, ze, g, s++)
                link(prev, n)
                chain.add(n)
                prev = n
            }
            val n = addNode(p.lat, p.lon, z(p), g, s++)
            link(prev, n)
            chain.add(n)
            prev = n
            prevPt = p
        }
        smoothChain(chain)
    }

    // ---- géométrie des obstacles
    private fun ccw(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Boolean =
        (cy - ay) * (bx - ax) > (by - ay) * (cx - ax)

    /** Le segment (x1,y1)-(x2,y2) en mètres coupe-t-il un obstacle ? */
    private fun crossesBarrier(x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        if (bx1.isEmpty()) return false
        val minX = min(x1, x2); val maxX = max(x1, x2)
        val minY = min(y1, y2); val maxY = max(y1, y2)
        for (i in 0 until bx1.size) {
            val cx = bx1[i]; val cy = by1[i]; val dx = bx2[i]; val dy = by2[i]
            if (max(cx, dx) < minX || min(cx, dx) > maxX || max(cy, dy) < minY || min(cy, dy) > maxY) continue
            if (ccw(x1, y1, cx, cy, dx, dy) != ccw(x2, y2, cx, cy, dx, dy) &&
                ccw(x1, y1, x2, y2, cx, cy) != ccw(x1, y1, x2, y2, dx, dy)
            ) return true
        }
        return false
    }

    private fun cellKey(cx: Long, cy: Long): Long = cx * 10_000_000L + cy

    private fun linkJunctions() {
        val n = lats.size
        if (n == 0) return
        val xs = DoubleArray(n) { lons[it] * kx }
        val ys = DoubleArray(n) { lats[it] * ky }
        val grid = HashMap<Long, ArrayList<Int>>()
        for (i in 0 until n) {
            val cx = floor(xs[i] / JOIN_RADIUS).toLong()
            val cy = floor(ys[i] / JOIN_RADIUS).toLong()
            grid.getOrPut(cellKey(cx, cy)) { ArrayList() }.add(i)
        }
        val barriers = bx1.isNotEmpty()
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
                        // niveaux différents (falaise, pont) : pas un vrai croisement
                        val zi = eles[i]
                        val zj = eles[j]
                        if (!zi.isNaN() && !zj.isNaN() && abs(zi - zj) > MAX_JUNCTION_DZ) continue
                        // un obstacle (rivière, clôture) passe entre les deux : on ne relie pas
                        if (barriers && crossesBarrier(xs[i], ys[i], xs[j], ys[j])) continue
                        val g = group[j]
                        val cur = bestD[g]
                        if (cur == null || d < cur) {
                            bestD[g] = d
                            bestJ[g] = j
                        }
                    }
                }
            }
            for ((g, j) in bestJ) {
                val d = bestD[g] ?: 0.0
                adj[i].add(Edge(j, d, true))
                adj[j].add(Edge(i, d, true))
            }
        }
    }

    // ---- rattachement d'un point au réseau
    /** Nœud du réseau le plus proche (≤ maxM) dont le raccord ne traverse aucun obstacle. */
    fun snap(lat: Double, lon: Double, maxM: Double): Int? {
        val x = px(lon)
        val y = py(lat)
        val cand = ArrayList<Pair<Int, Double>>()
        for (i in 0 until latA.size) {
            val dx = x - xA[i]
            val dy = y - yA[i]
            val d = sqrt(dx * dx + dy * dy)
            if (d <= maxM) cand.add(Pair(i, d))
        }
        if (cand.isEmpty()) return null
        cand.sortBy { it.second }
        var tries = 0
        for (c in cand) {
            if (!hasBarriers || !crossesBarrier(x, y, xA[c.first], yA[c.first])) return c.first
            tries++
            if (tries >= 300) break
        }
        return null
    }

    // ---- plus court chemin (en temps)
    private fun edgeCost(a: Int, e: Edge): Double {
        if (e.junction) return e.d / speedMs(0.0)
        val za = eleA[a]
        val zb = eleA[e.to]
        val slope = if (za.isNaN() || zb.isNaN() || e.d < 0.1) 0.0 else (zb - za) / e.d
        return e.d / speedMs(slope)
    }

    fun tree(src: Int, stopAt: Int = -1): Tree {
        val n = latA.size
        val cost = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        cost[src] = 0.0
        val pq = PriorityQueue<DoubleArray>(16, Comparator<DoubleArray> { a, b -> a[0].compareTo(b[0]) })
        pq.add(doubleArrayOf(0.0, src.toDouble()))
        while (pq.isNotEmpty()) {
            val top = pq.poll() ?: break
            val u = top[1].toInt()
            if (top[0] > cost[u]) continue
            if (u == stopAt) break
            for (e in adj[u]) {
                val nd = cost[u] + edgeCost(u, e)
                if (nd < cost[e.to]) {
                    cost[e.to] = nd
                    prev[e.to] = u
                    pq.add(doubleArrayOf(nd, e.to.toDouble()))
                }
            }
        }
        return Tree(src, cost, prev)
    }

    fun pathNodes(tr: Tree, dst: Int): List<Int> {
        val nodes = ArrayList<Int>()
        var cur = dst
        while (cur != -1) {
            nodes.add(cur)
            cur = tr.prev[cur]
        }
        nodes.reverse()
        return nodes
    }

    fun metrics(nodes: List<Int>): Metrics {
        var len = 0.0
        var up = 0.0
        var down = 0.0
        for (i in 1 until nodes.size) {
            val a = nodes[i - 1]
            val b = nodes[i]
            len += Geo.distance(latA[a], lonA[a], latA[b], lonA[b])
            val za = eleA[a]
            val zb = eleA[b]
            if (!za.isNaN() && !zb.isNaN()) {
                val dz = zb - za
                if (dz > 0) up += dz else down -= dz
            }
        }
        return Metrics(len, up, down)
    }

    /** Itinéraire du coureur vers la balise en suivant les chemins (le plus rapide), ou null s'il n'y en a pas. */
    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route? {
        if (latA.isEmpty()) return null
        val s = snap(fromLat, fromLon, SNAP_START_M) ?: return null
        val t = snap(toLat, toLon, SNAP_END_M) ?: return null
        val tr = tree(s, t)
        if (tr.cost[t] == Double.MAX_VALUE) return null
        val nodes = pathNodes(tr, t)
        val m = metrics(nodes)
        val c1 = Geo.distance(fromLat, fromLon, latA[s], lonA[s])
        val c2 = Geo.distance(latA[t], lonA[t], toLat, toLon)
        val time = tr.cost[t] + (c1 + c2) / speedMs(0.0)
        return Route(
            GeoPt(fromLat, fromLon), nodes.map { nodePt(it) }, GeoPt(toLat, toLon),
            m.length + c1 + c2, time, m.ascent, m.descent,
        )
    }
}
