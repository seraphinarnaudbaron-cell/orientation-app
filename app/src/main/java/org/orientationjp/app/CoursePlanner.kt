package org.orientationjp.app

/**
 * Planifie un parcours : départ -> toutes les balises choisies (dans le meilleur ordre) -> arrivée.
 * Ordre optimal exact (programmation dynamique de Held-Karp, jusqu'à 15 balises), coûts = temps de marche
 * sur le réseau de chemins (dénivelé compris). Quand deux points ne sont pas reliés par un chemin,
 * on suppose une ligne droite pénalisée (marche hors chemin).
 */
object CoursePlanner {
    const val MAX_BEACONS = 15
    private const val OFF_TRAIL_PENALTY = 1.6
    private const val INF = Double.MAX_VALUE / 4

    fun plan(graph: TrailGraph?, start: GeoPt, beacons: List<Beacon>, finish: GeoPt): Course {
        val n = beacons.size
        val pts = ArrayList<GeoPt>()
        pts.add(start)
        for (b in beacons) pts.add(GeoPt(b.lat, b.lon))
        pts.add(finish)
        val total = n + 2
        val flat = TrailGraph.speedMs(0.0)

        val nodes = arrayOfNulls<Int>(total)
        val trees = arrayOfNulls<Tree>(total)
        if (graph != null && graph.nodeCount > 0) {
            for (i in 0 until total) {
                nodes[i] = graph.snap(pts[i].lat, pts[i].lon, if (i == 0) TrailGraph.SNAP_START_M else TrailGraph.SNAP_END_M)
            }
            for (i in 0 until total - 1) {
                val ni = nodes[i]
                if (ni != null) trees[i] = graph.tree(ni)
            }
        }

        fun straight(i: Int, j: Int) = Geo.distance(pts[i].lat, pts[i].lon, pts[j].lat, pts[j].lon)

        /** true si i -> j se fait par les chemins */
        fun viaTrails(i: Int, j: Int): Boolean {
            val ni = nodes[i]
            val nj = nodes[j]
            val tr = trees[i]
            return graph != null && ni != null && nj != null && tr != null && tr.cost[nj] < Double.MAX_VALUE
        }

        fun legCost(i: Int, j: Int): Double {
            if (viaTrails(i, j)) {
                val g = graph!!
                val ni = nodes[i]!!
                val nj = nodes[j]!!
                val c1 = Geo.distance(pts[i].lat, pts[i].lon, g.nodePt(ni).lat, g.nodePt(ni).lon)
                val c2 = Geo.distance(pts[j].lat, pts[j].lon, g.nodePt(nj).lat, g.nodePt(nj).lon)
                return trees[i]!!.cost[nj] + (c1 + c2) / flat
            }
            return straight(i, j) / flat * OFF_TRAIL_PENALTY
        }

        val cost = Array(total) { DoubleArray(total) { INF } }
        for (i in 0 until total - 1) {
            for (j in 1 until total) {
                if (i != j) cost[i][j] = legCost(i, j)
            }
        }

        // ---- ordre optimal des balises (indices 0..n-1 -> stops 1..n)
        val order = ArrayList<Int>()
        if (n > 0) {
            val full = (1 shl n) - 1
            val dp = Array(1 shl n) { DoubleArray(n) { INF } }
            val par = Array(1 shl n) { IntArray(n) { -1 } }
            for (b in 0 until n) dp[1 shl b][b] = cost[0][b + 1]
            for (mask in 1..full) {
                for (last in 0 until n) {
                    if (mask and (1 shl last) == 0) continue
                    val cur = dp[mask][last]
                    if (cur >= INF) continue
                    for (nx in 0 until n) {
                        if (mask and (1 shl nx) != 0) continue
                        val nm = mask or (1 shl nx)
                        val c = cur + cost[last + 1][nx + 1]
                        if (c < dp[nm][nx]) {
                            dp[nm][nx] = c
                            par[nm][nx] = last
                        }
                    }
                }
            }
            var bestLast = 0
            var best = INF * 2
            for (last in 0 until n) {
                val c = dp[full][last] + cost[last + 1][n + 1]
                if (c < best) {
                    best = c
                    bestLast = last
                }
            }
            var mask = full
            var cur = bestLast
            while (cur != -1) {
                order.add(cur)
                val p = par[mask][cur]
                mask = mask xor (1 shl cur)
                cur = p
            }
            order.reverse()
        }

        // ---- géométrie et statistiques
        val stops = ArrayList<Int>()
        stops.add(0)
        for (o in order) stops.add(o + 1)
        stops.add(n + 1)

        fun label(stop: Int): String = when (stop) {
            0 -> "Départ"
            n + 1 -> "Arrivée"
            else -> "n°" + beacons[stop - 1].number + " " + beacons[stop - 1].title
        }

        val segments = ArrayList<CourseSeg>()
        val legs = ArrayList<CourseLeg>()
        var totalLen = 0.0
        var totalTime = 0.0
        var totalUp = 0.0
        var totalDown = 0.0

        for (k in 1 until stops.size) {
            val a = stops[k - 1]
            val b = stops[k]
            if (graph != null && viaTrails(a, b)) {
                val na = nodes[a]!!
                val nb = nodes[b]!!
                val path = graph.pathNodes(trees[a]!!, nb)
                val m = graph.metrics(path)
                val pa = graph.nodePt(na)
                val pb = graph.nodePt(nb)
                val c1 = Geo.distance(pts[a].lat, pts[a].lon, pa.lat, pa.lon)
                val c2 = Geo.distance(pb.lat, pb.lon, pts[b].lat, pts[b].lon)
                if (c1 > 1.0) segments.add(CourseSeg(listOf(pts[a], pa), true))
                segments.add(CourseSeg(path.map { graph.nodePt(it) }, false))
                if (c2 > 1.0) segments.add(CourseSeg(listOf(pb, pts[b]), true))
                val len = m.length + c1 + c2
                val time = trees[a]!!.cost[nb] + (c1 + c2) / flat
                legs.add(CourseLeg(label(a), label(b), len, time, true))
                totalLen += len
                totalTime += time
                totalUp += m.ascent
                totalDown += m.descent
            } else {
                val d = straight(a, b)
                val time = d / flat * OFF_TRAIL_PENALTY
                segments.add(CourseSeg(listOf(pts[a], pts[b]), true))
                legs.add(CourseLeg(label(a), label(b), d, time, false))
                totalLen += d
                totalTime += time
            }
        }

        return Course(order.map { beacons[it] }, finish, segments, legs, totalLen, totalTime, totalUp, totalDown)
    }
}
