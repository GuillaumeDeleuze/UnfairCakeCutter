package com.unfaircake.cutter.ui

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.unfaircake.cutter.domain.CakeShape
import com.unfaircake.cutter.domain.DEFAULT_PEOPLE
import com.unfaircake.cutter.domain.DEFAULT_UNFAIRNESS
import com.unfaircake.cutter.domain.MAX_PEOPLE
import com.unfaircake.cutter.domain.MIN_PEOPLE
import com.unfaircake.cutter.domain.ShapeTransform
import com.unfaircake.cutter.domain.Shares
import com.unfaircake.cutter.domain.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

@Immutable
data class PersonUi(
    val index: Int,
    val name: String,
    val share: Double,
    val percentText: String,
)

@Immutable
data class CakeUiState(
    val people: List<PersonUi>,
    val unfairness: Int,
    val verdict: Verdict,
    val maxMinRatio: Double,
    val everyoneGetsTheSame: Boolean,
    val shape: CakeShape,
    val transform: ShapeTransform,
    val frozen: Boolean,
    val photoUri: Uri?,
    /** Person secretly handed the biggest slice, or -1. */
    val favorite: Int,
) {
    val peopleCount: Int get() = people.size
    val shares: List<Double> get() = people.map { it.share }
    val maxShare: Double get() = people.maxOfOrNull { it.share } ?: 1.0
}

/**
 * Single source of truth for the screen. Everything the user chose (people, unfairness, names,
 * seeds, cake outline) lives in [SavedStateHandle], so it survives rotation and process death.
 * Freeze and the picked photo are session-only: the frozen frame and the picker's temporary URI
 * grant would not survive process death anyway.
 */
class CakeViewModel(
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val random: Random = Random.Default

    private var count: Int = (savedState.get<Int>(KEY_COUNT) ?: DEFAULT_PEOPLE).coerceIn(MIN_PEOPLE, MAX_PEOPLE)
    private var unfairness: Int = (savedState.get<Int>(KEY_UNFAIRNESS) ?: DEFAULT_UNFAIRNESS).coerceIn(0, 100)
    private var seeds: List<Double> = savedState.get<DoubleArray>(KEY_SEEDS)?.toList().orEmpty()
    private var names: List<String> = savedState.get<ArrayList<String>>(KEY_NAMES)?.toList().orEmpty()
    private var shape: CakeShape = savedState.get<String>(KEY_SHAPE)
        ?.let { saved -> CakeShape.entries.firstOrNull { it.name == saved } }
        ?: CakeShape.ROUND
    private var transform: ShapeTransform = savedState.get<FloatArray>(KEY_TRANSFORM)
        ?.takeIf { it.size == 5 }
        ?.let { ShapeTransform(it[0], it[1], it[2], it[3], it[4]).clamped() }
        ?: ShapeTransform.defaultFor(shape)
    private var favorite: Int = savedState.get<Int>(KEY_FAVORITE) ?: NO_FAVORITE
    private var frozen: Boolean = false
    private var photoUri: Uri? = null

    private val _uiState: MutableStateFlow<CakeUiState>
    val uiState: StateFlow<CakeUiState>

    init {
        seeds = Shares.ensureSeeds(seeds, count, random)
        names = ensureNames(names, count)
        persist()
        _uiState = MutableStateFlow(buildState())
        uiState = _uiState.asStateFlow()
    }

    // People and shares

    fun setPeopleCount(value: Int) {
        val n = value.coerceIn(MIN_PEOPLE, MAX_PEOPLE)
        if (n == count) return
        count = n
        // Existing seeds and names are kept, so shrinking then growing restores the same people.
        seeds = Shares.ensureSeeds(seeds, count, random)
        names = ensureNames(names, count)
        commit()
    }

    fun addPerson() = setPeopleCount(count + 1)
    fun removePerson() = setPeopleCount(count - 1)

    fun setUnfairness(value: Int) {
        val u = value.coerceIn(0, 100)
        if (u == unfairness) return
        unfairness = u
        commit()
    }

    /** New random distribution at the same unfairness level. */
    fun reroll() {
        seeds = Shares.newSeeds(maxOf(seeds.size, count), random)
        commit()
    }

    fun setName(index: Int, name: String) {
        if (index !in names.indices || names[index] == name) return
        names = names.toMutableList().also { it[index] = name }
        commit()
    }

    /** Long-press on a name: that person always gets the biggest slice. Again on them: fair play. */
    fun toggleFavorite(index: Int) {
        if (index !in 0 until count) return
        favorite = if (favorite == index) NO_FAVORITE else index
        commit()
    }

    // Cake outline

    fun setShape(value: CakeShape) {
        if (value == shape) return
        val keepSize = !shape.isRound && !value.isRound && shape != CakeShape.LOG && value != CakeShape.LOG
        transform = if (keepSize) {
            transform
        } else {
            // New proportions for the new kind of cake, same place and angle.
            transform.copy(width = value.defaultWidth, height = value.defaultHeight)
        }
        shape = value
        commit()
    }

    fun setTransform(value: ShapeTransform) {
        val t = value.clamped()
        if (t == transform) return
        transform = t
        commit()
    }

    /** Outline found by Snap: the kind of cake and where it is, in one go. */
    fun applySnap(value: CakeShape, t: ShapeTransform) {
        shape = value
        transform = t.clamped()
        commit()
    }

    fun setSize(size: Float) = setTransform(transform.withSize(size))
    fun setRotation(degrees: Float) = setTransform(transform.withRotation(degrees))

    // Background

    fun toggleFreeze() {
        frozen = !frozen
        commit(persist = false)
    }

    /** Holds the current frame (for Snap); no-op if already frozen. */
    fun freeze() {
        if (frozen) return
        frozen = true
        commit(persist = false)
    }

    /** Used when there was no frame to freeze yet (camera still starting). */
    fun unfreeze() {
        if (!frozen) return
        frozen = false
        commit(persist = false)
    }

    fun showPhoto(uri: Uri) {
        photoUri = uri
        frozen = false
        commit(persist = false)
    }

    fun backToCamera() {
        photoUri = null
        frozen = false
        commit(persist = false)
    }

    // Internals

    private fun commit(persist: Boolean = true) {
        if (persist) persist()
        _uiState.value = buildState()
    }

    private fun persist() {
        savedState[KEY_COUNT] = count
        savedState[KEY_UNFAIRNESS] = unfairness
        savedState[KEY_SEEDS] = seeds.toDoubleArray()
        savedState[KEY_NAMES] = ArrayList(names)
        savedState[KEY_SHAPE] = shape.name
        savedState[KEY_FAVORITE] = favorite
        savedState[KEY_TRANSFORM] = floatArrayOf(
            transform.cx, transform.cy, transform.width, transform.height, transform.rotationDeg,
        )
    }

    private fun buildState(): CakeUiState {
        // A favourite beyond the current head count is kept but has no effect.
        val shares = Shares.compute(Shares.rig(seeds.take(count), favorite), unfairness)
        val people = shares.mapIndexed { i, share ->
            PersonUi(index = i, name = names[i], share = share, percentText = Shares.formatPercent(share))
        }
        return CakeUiState(
            people = people,
            unfairness = unfairness,
            verdict = Verdict.of(unfairness),
            maxMinRatio = Shares.maxMinRatio(shares),
            everyoneGetsTheSame = Shares.isEveryoneTheSame(shares),
            shape = shape,
            transform = transform,
            frozen = frozen,
            photoUri = photoUri,
            favorite = if (favorite < count) favorite else NO_FAVORITE,
        )
    }

    private fun ensureNames(existing: List<String>, n: Int): List<String> =
        if (existing.size >= n) existing
        else existing + (existing.size until n).map { defaultName(it) }

    companion object {
        private const val KEY_COUNT = "people_count"
        private const val KEY_UNFAIRNESS = "unfairness"
        private const val KEY_SEEDS = "seeds"
        private const val KEY_NAMES = "names"
        private const val KEY_SHAPE = "shape"
        private const val KEY_TRANSFORM = "transform"
        private const val KEY_FAVORITE = "favorite"
        const val NO_FAVORITE = -1

        /** Whoever holds the phone is person 1. */
        const val ME = "Me, obviously"

        fun defaultName(index: Int) = if (index == 0) ME else "Person ${index + 1}"
    }
}
