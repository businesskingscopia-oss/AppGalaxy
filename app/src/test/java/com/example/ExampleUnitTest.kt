package com.example

import com.example.data.apk.ApkInspector
import com.example.data.model.STANDARD_MARKETPLACE_CATEGORIES
import com.example.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun packageNameValidation_acceptsValidPackagesAndRejectsInvalid() {
        assertTrue(ApkInspector.isValidPackageName("com.appgalaxy.store"))
        assertTrue(ApkInspector.isValidPackageName("org.example.my_app2"))
        assertFalse(ApkInspector.isValidPackageName("invalidpackage"))
        assertFalse(ApkInspector.isValidPackageName("123.bad.pkg"))
        assertFalse(ApkInspector.isValidPackageName(""))
    }

    @Test
    fun standardMarketplaceCategories_hasExpectedTwentyCategories() {
        assertEquals(20, STANDARD_MARKETPLACE_CATEGORIES.size)
        assertTrue(STANDARD_MARKETPLACE_CATEGORIES.any { it.name == "Games" && it.gameCategory })
        assertTrue(STANDARD_MARKETPLACE_CATEGORIES.any { it.name == "Productivity" && !it.gameCategory })
    }

    @Test
    fun themeMode_fromKey_mapsCorrectly() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
    }
}
