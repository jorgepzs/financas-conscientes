package br.com.financasconscientes.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryDestinationTest {
    @Test
    fun `restores each primary destination from a saved key`() {
        PrimaryDestination.values().forEach { destination ->
            assertEquals(destination, PrimaryDestination.fromSavedKey(destination.savedKey))
        }
    }

    @Test
    fun `unknown or missing saved destination starts at home`() {
        assertEquals(PrimaryDestination.HOME, PrimaryDestination.fromSavedKey(null))
        assertEquals(PrimaryDestination.HOME, PrimaryDestination.fromSavedKey("settings"))
    }
}
