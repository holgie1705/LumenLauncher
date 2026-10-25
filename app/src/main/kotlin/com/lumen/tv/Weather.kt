package com.lumen.tv

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

@Immutable class WeatherDay(val date: LocalDate, val today: Boolean, val code: Int, val min: Int, val max: Int, val rain: Int) {
    val label: String
        get() = if (today) t("Heute", "Today") else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Lang.locale)
}
@Immutable class WeatherInfo(
    val temp: Int, val feels: Int, val code: Int, val isDay: Boolean, val wind: Int, val days: List<WeatherDay>,
    val fahrenheit: Boolean = false, val sunrise: String = "", val sunset: String = "",
)
@Immutable class Place(val name: String, val region: String, val lat: Double, val lon: Double)

val PRESET_PLACES: List<Place>
    get() {
        val de = t("Deutschland", "Germany")
        return listOf(
            Place("Berlin", de, 52.52, 13.41), Place("Hamburg", de, 53.55, 9.99),
            Place(t("München", "Munich"), de, 48.14, 11.58), Place(t("Köln", "Cologne"), de, 50.94, 6.96),
            Place("Frankfurt am Main", de, 50.11, 8.68), Place("Stuttgart", de, 48.78, 9.18),
            Place("Düsseldorf", de, 51.23, 6.78), Place("Leipzig", de, 51.34, 12.37),
            Place("Dresden", de, 51.05, 13.74), Place("Hannover", de, 52.37, 9.74),
            Place(t("Nürnberg", "Nuremberg"), de, 49.45, 11.08), Place("Bremen", de, 53.08, 8.80),
            Place(t("Wien", "Vienna"), t("Österreich", "Austria"), 48.21, 16.37),
            Place(t("Zürich", "Zurich"), t("Schweiz", "Switzerland"), 47.38, 8.54),
        )
    }

fun weatherText(code: Int): String = when (code) {
    0 -> t("Klar", "Clear")
    1 -> t("Überwiegend klar", "Mostly clear")
    2 -> t("Teils bewölkt", "Partly cloudy")
    3 -> t("Bedeckt", "Overcast")
    45, 48 -> t("Nebel", "Fog")
    51, 53, 55 -> t("Nieselregen", "Drizzle")
    56, 57 -> t("Gefrierender Nieselregen", "Freezing drizzle")
    61 -> t("Leichter Regen", "Light rain")
    63 -> t("Regen", "Rain")
    65 -> t("Starker Regen", "Heavy rain")
    66, 67 -> t("Gefrierender Regen", "Freezing rain")
    71, 73, 75 -> t("Schneefall", "Snowfall")
    77 -> t("Schneegriesel", "Snow grains")
    80, 81, 82 -> t("Regenschauer", "Rain showers")
    85, 86 -> t("Schneeschauer", "Snow showers")
    95 -> t("Gewitter", "Thunderstorm")
    96, 99 -> t("Gewitter mit Hagel", "Thunderstorm with hail")
    else -> "–"
}

fun weatherIcon(code: Int, day: Boolean): String = when (code) {
    0 -> if (day) "☀️" else "🌙"
    1 -> if (day) "🌤️" else "🌙"
    2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "❄️"
    in 80..82 -> "🌧️"
    85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}

/** Open-Meteo: kostenlos, kein API-Schlüssel nötig. */
class WeatherRepo(ctx: Context) {
    private val sp = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private fun get(url: String): String? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 8_000; requestMethod = "GET"
        }
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun parse(txt: String, fahrenheit: Boolean): WeatherInfo? = runCatching {
        val o = JSONObject(txt)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val times = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val mins = d.getJSONArray("temperature_2m_min")
        val maxs = d.getJSONArray("temperature_2m_max")
        val rain = d.optJSONArray("precipitation_probability_max")
        val days = (0 until times.length()).map { i ->
            WeatherDay(
                date = LocalDate.parse(times.getString(i)),
                today = i == 0,
                code = codes.getInt(i),
                min = mins.getDouble(i).roundToInt(),
                max = maxs.getDouble(i).roundToInt(),
                rain = rain?.optInt(i, 0) ?: 0,
            )
        }
        WeatherInfo(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            feels = cur.getDouble("apparent_temperature").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            wind = cur.optDouble("wind_speed_10m", 0.0).roundToInt(),
            days = days,
            fahrenheit = fahrenheit,
            sunrise = d.optJSONArray("sunrise")?.optString(0, "")?.substringAfter('T').orEmpty(),
            sunset = d.optJSONArray("sunset")?.optString(0, "")?.substringAfter('T').orEmpty(),
        )
    }.getOrNull()

    fun cached(lat: Double, lon: Double, f: Boolean): WeatherInfo? =
        if (sp.getString("key", null) == "$lat,$lon,$f") sp.getString("json", null)?.let { parse(it, f) } else null

    suspend fun fetch(lat: Double, lon: Double, f: Boolean): WeatherInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            (if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "") +
            "&timezone=auto&forecast_days=4"
        val txt = get(url) ?: return@withContext null
        parse(txt, f)?.also { sp.edit { putString("key", "$lat,$lon,$f"); putString("json", txt) } }
    }

    suspend fun search(q: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=6&language=${Lang.code}&format=json"
        runCatching {
            val arr = JSONObject(get(url) ?: return@runCatching emptyList<Place>()).optJSONArray("results") ?: return@runCatching emptyList<Place>()
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                Place(
                    r.getString("name"),
                    listOf(r.optString("admin1", ""), r.optString("country", "")).filter { it.isNotEmpty() }.joinToString(", "),
                    r.getDouble("latitude"), r.getDouble("longitude"),
                )
            }
        }.getOrDefault(emptyList<Place>())
    }
}
