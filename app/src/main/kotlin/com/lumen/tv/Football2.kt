package com.lumen.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Ausländische Ligen über die öffentliche ESPN-Schnittstelle (kostenlos, ohne Schlüssel, mit Vereinslogos).
 * OpenLigaDB kann nur die deutschen Ligen, daher kommen Premier League, La Liga, Serie A, Ligue 1 usw.
 * (jeweils 1. und 2. Liga) von hier. Vorher TheSportsDB – deren Gratis-Schlüssel liefert die Vereinsliste nicht mehr.
 * Die Codes "sdb:…" bleiben gleich, damit gespeicherte Einstellungen weiter passen.
 */
object SdbFootball {
    private const val SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
    private const val V2 = "https://site.api.espn.com/apis/v2/sports/soccer"

    /** Lumen-Code -> ESPN-Liga-Slug. Die Codes stehen auch in LEAGUES (Widgets.kt). */
    val DEF: Map<String, String> = linkedMapOf(
        "sdb:en1" to "eng.1", "sdb:en2" to "eng.2",
        "sdb:es1" to "esp.1", "sdb:es2" to "esp.2",
        "sdb:it1" to "ita.1", "sdb:it2" to "ita.2",
        "sdb:fr1" to "fra.1", "sdb:fr2" to "fra.2",
    )

    fun isSdb(code: String) = code.startsWith("sdb:")

    private fun i(o: JSONObject?, key: String): Int = o?.optString(key, "")?.toIntOrNull() ?: o?.optInt(key, 0) ?: 0

    private fun logo(t: JSONObject?): String {
        if (t == null) return ""
        t.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotEmpty() }?.let { return it }
        return t.optString("logo")
    }

    private fun team(t: JSONObject?) = FbTeam(
        i(t, "id"),
        t?.optString("displayName").orEmpty(),
        (t?.optString("shortDisplayName").orEmpty()).ifEmpty { t?.optString("displayName").orEmpty() },
        logo(t),
    )

    private val teamCache = mutableMapOf<String, List<FbTeam>>()

    suspend fun teams(code: String): List<FbTeam> = withContext(Dispatchers.IO) {
        teamCache[code]?.let { return@withContext it }
        val slug = DEF[code] ?: return@withContext emptyList()
        val txt = Http.get("$SITE/$slug/teams") ?: return@withContext emptyList()
        val list = runCatching {
            val a = JSONObject(txt).optJSONArray("sports")?.optJSONObject(0)
                ?.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("teams") ?: JSONArray()
            (0 until a.length()).mapNotNull { k -> a.optJSONObject(k)?.optJSONObject("team")?.let { team(it) } }
                .filter { it.id > 0 }.sortedBy { it.name }
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) teamCache[code] = list
        list
    }

    private fun stat(stats: JSONArray?, vararg names: String): Int {
        if (stats == null) return 0
        for (k in 0 until stats.length()) {
            val s = stats.optJSONObject(k) ?: continue
            if (names.any { s.optString("name").equals(it, true) || s.optString("type").equals(it, true) }) {
                return s.optDouble("value", 0.0).toInt()
            }
        }
        return 0
    }

    private fun table(slug: String): List<FbRow> {
        val txt = Http.get("$V2/$slug/standings") ?: return emptyList()
        return runCatching {
            val root = JSONObject(txt)
            val entries = root.optJSONArray("children")?.optJSONObject(0)?.optJSONObject("standings")?.optJSONArray("entries")
                ?: root.optJSONObject("standings")?.optJSONArray("entries") ?: return emptyList()
            (0 until entries.length()).mapNotNull { k ->
                val e = entries.optJSONObject(k) ?: return@mapNotNull null
                val st = e.optJSONArray("stats")
                FbRow(
                    stat(st, "rank").takeIf { it > 0 } ?: (k + 1),
                    team(e.optJSONObject("team")),
                    stat(st, "gamesPlayed"), stat(st, "pointDifferential", "goalDifference"), stat(st, "points"),
                )
            }.sortedBy { it.pos }
        }.getOrDefault(emptyList())
    }

    private fun score(c: JSONObject): Int? {
        c.optJSONObject("score")?.let { s ->
            return s.optString("displayValue").toIntOrNull() ?: s.optDouble("value", Double.NaN).takeIf { !it.isNaN() }?.toInt()
        }
        return c.optString("score").toIntOrNull()
    }

    private fun event(o: JSONObject): FbMatch? {
        val id = o.optString("id").toIntOrNull() ?: return null
        val time = runCatching {
            OffsetDateTime.parse(o.optString("date")).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        }.getOrNull() ?: return null
        val comp = o.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val cs = comp.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null; var away: JSONObject? = null
        for (k in 0 until cs.length()) {
            val c = cs.optJSONObject(k) ?: continue
            if (c.optString("homeAway") == "home") home = c else away = c
        }
        if (home == null || away == null) return null
        val type = (comp.optJSONObject("status") ?: o.optJSONObject("status"))?.optJSONObject("type")
        val finished = type?.optBoolean("completed") == true || type?.optString("state") == "post"
        val started = finished || type?.optString("state") == "in"
        return FbMatch(
            id, time, team(home.optJSONObject("team")), team(away.optJSONObject("team")), finished,
            if (started) score(home) else null, if (started) score(away) else null,
            o.optJSONObject("week")?.optString("number").orEmpty(), "",
        )
    }

    private fun schedule(slug: String, teamId: Int, fixtures: Boolean): List<FbMatch> {
        if (teamId <= 0) return emptyList()
        val txt = Http.get("$SITE/$slug/teams/$teamId/schedule" + if (fixtures) "?fixture=true" else "") ?: return emptyList()
        return runCatching {
            val a = JSONObject(txt).optJSONArray("events") ?: return emptyList()
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::event) }
        }.getOrDefault(emptyList())
    }

    private var cache: Pair<String, Pair<Long, FbData>>? = null

    suspend fun load(cfg: WidgetCfg, force: Boolean = false): FbData? = withContext(Dispatchers.IO) {
        val key = "${cfg.league}|${cfg.teamId}|${cfg.teamName}"
        cache?.let { c ->
            if (!force && c.first == key) {
                val age = System.currentTimeMillis() - c.second.first
                if (age < (if (c.second.second.live != null) 60_000L else 600_000L)) return@withContext c.second.second
            }
        }
        val slug = DEF[cfg.league] ?: return@withContext null
        val table = table(slug)
        // Verein bestimmen: gesetzte ID (falls in dieser Liga vorhanden), sonst per Name.
        val all = table.map { it.team }.ifEmpty { teams(cfg.league) }
        val tid = cfg.teamId.takeIf { id -> id > 0 && all.any { it.id == id } }
            ?: all.firstOrNull { it.name.equals(cfg.teamName, true) }?.id
            ?: all.firstOrNull { cfg.teamName.isNotEmpty() && (it.name.contains(cfg.teamName, true) || cfg.teamName.contains(it.short, true)) }?.id
            ?: cfg.teamId
        val mine = (schedule(slug, tid, false) + schedule(slug, tid, true))
            .distinctBy { it.id }.sortedBy { it.time }
        val now = LocalDateTime.now()
        val live = mine.firstOrNull { it.live }
        val last = mine.lastOrNull { it.finished }
        val next = mine.firstOrNull { !it.finished && it.time.isAfter(now) }
        val teamObj = all.firstOrNull { it.id == tid }
            ?: mine.firstOrNull()?.let { if (it.home.id == tid) it.home else it.away }
        if (table.isEmpty() && mine.isEmpty()) return@withContext cache?.takeIf { it.first == key }?.second?.second
        val data = FbData(
            teamObj, last, next, live, table,
            mine.filter { !it.finished }.take(5), mine.filter { it.finished }.takeLast(5).reversed(),
        )
        cache = key to (System.currentTimeMillis() to data)
        data
    }
}
