package com.unfaircake.cutter.ui

import androidx.lifecycle.SavedStateHandle
import com.unfaircake.cutter.domain.CakeShape
import org.junit.Assert.assertEquals
import org.junit.Test

class CakeViewModelTest {

    private class MemoryStore : PartyStore {
        var party: Party? = null
        override fun load(): Party? = party
        override fun save(party: Party) {
            this.party = party
        }
    }

    @Test
    fun thePartyIsBackAfterTheAppIsClosed() {
        val store = MemoryStore()
        val first = CakeViewModel(SavedStateHandle(), store)
        first.setPeopleCount(4)
        first.setUnfairness(73)
        first.setName(1, "Chloé")
        first.setShape(CakeShape.TRAY_STRIPS)
        first.toggleFavorite(2)
        val before = first.uiState.value

        // A cold start: nothing in the saved state, only what the store kept.
        val again = CakeViewModel(SavedStateHandle(), store).uiState.value
        assertEquals(4, again.peopleCount)
        assertEquals(73, again.unfairness)
        assertEquals("Chloé", again.people[1].name)
        assertEquals(CakeShape.TRAY_STRIPS, again.shape)
        assertEquals(2, again.favorite)
        // Same draw: nobody's slice changed while the phone was in a pocket.
        assertEquals(before.shares, again.shares)
    }

    @Test
    fun savedStateWinsOverTheStore() {
        val store = MemoryStore()
        CakeViewModel(SavedStateHandle(), store).setPeopleCount(3)
        val handle = SavedStateHandle(mapOf("people_count" to 8))
        assertEquals(8, CakeViewModel(handle, store).uiState.value.peopleCount)
    }

    @Test
    fun firstLaunchUsesDefaults() {
        val state = CakeViewModel(SavedStateHandle(), MemoryStore()).uiState.value
        assertEquals(6, state.peopleCount)
        assertEquals(40, state.unfairness)
        // Unnamed: the screen shows "Me, obviously", "Person 2"… in the current language.
        assertEquals("", state.people[0].name)
    }
}
