package com.mathis.mandala.app

import com.mathis.mandala.app.Editing.addLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = SettingsStore(File(folder.root, "state/last-settings.json"))

    @Test
    fun savedSettingsComeBack() {
        val p = Editing.initial(18446744073709551615uL).addLayer(5uL).first
        store().save(p)
        assertEquals(p, store().load())
    }

    @Test
    fun nothingSavedGivesNull() {
        assertNull(store().load())
    }

    @Test
    fun aBrokenFileIsIgnored() {
        val f = File(folder.root, "state/last-settings.json").also { it.parentFile.mkdirs(); it.writeText("{\"layers\": [") }
        assertNull(SettingsStore(f).load())
    }

    @Test
    fun settingsWithoutLayersAreIgnored() {
        val f = File(folder.root, "state/last-settings.json").also { it.parentFile.mkdirs(); it.writeText("{\"layers\": []}") }
        assertNull(SettingsStore(f).load())
    }
}
