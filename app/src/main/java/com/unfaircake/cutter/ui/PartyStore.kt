package com.unfaircake.cutter.ui

import android.content.Context
import com.unfaircake.cutter.domain.CakeShape
import org.json.JSONArray

/** What a party keeps between launches: who is at the table and how the cake is cut. */
data class Party(
    val count: Int,
    val unfairness: Int,
    val seeds: List<Double>,
    val names: List<String>,
    val shape: CakeShape,
    val favorite: Int,
)

interface PartyStore {
    fun load(): Party?
    fun save(party: Party)
}

/** Nothing kept (previews, tests that don't care). */
object NoPartyStore : PartyStore {
    override fun load(): Party? = null
    override fun save(party: Party) = Unit
}

/** In the app's private preferences. The snapped outline and the cake's place are not kept: they
 * only make sense for the picture they were found on. */
class PrefsPartyStore(context: Context) : PartyStore {
    private val prefs = context.applicationContext.getSharedPreferences("party", Context.MODE_PRIVATE)

    override fun load(): Party? {
        if (!prefs.contains(COUNT)) return null
        return try {
            Party(
                count = prefs.getInt(COUNT, 0),
                unfairness = prefs.getInt(UNFAIRNESS, 0),
                seeds = JSONArray(prefs.getString(SEEDS, "[]")).let { a -> List(a.length()) { a.getDouble(it) } },
                names = JSONArray(prefs.getString(NAMES, "[]")).let { a -> List(a.length()) { a.getString(it) } },
                shape = CakeShape.entries.firstOrNull { it.name == prefs.getString(SHAPE, null) } ?: CakeShape.ROUND,
                favorite = prefs.getInt(FAVORITE, -1),
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun save(party: Party) {
        prefs.edit()
            .putInt(COUNT, party.count)
            .putInt(UNFAIRNESS, party.unfairness)
            .putString(SEEDS, JSONArray(party.seeds).toString())
            .putString(NAMES, JSONArray(party.names).toString())
            .putString(SHAPE, party.shape.name)
            .putInt(FAVORITE, party.favorite)
            .apply()
    }

    private companion object {
        const val COUNT = "count"
        const val UNFAIRNESS = "unfairness"
        const val SEEDS = "seeds"
        const val NAMES = "names"
        const val SHAPE = "shape"
        const val FAVORITE = "favorite"
    }
}
