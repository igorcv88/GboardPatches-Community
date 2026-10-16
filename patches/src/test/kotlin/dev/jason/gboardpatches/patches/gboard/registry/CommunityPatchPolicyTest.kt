package dev.jason.gboardpatches.patches.gboard.registry

import org.junit.Assert.*
import org.junit.Test

class CommunityPatchPolicyTest {
    @Test fun regionalOnlyRegistrationsAreNotPublishedOrExecuted() {
        val regional = listOf(gboardZhuyinSlideInputPatch, gboardZhuyinQuickTraditionalSimplifiedTogglePatch,
            gboardZhuyinBottomRowWeightPatch, gboardCustomSymbolsPatch)
        regional.forEach { patch ->
            assertNull(patch.name)
            assertTrue(patch.dependencies.isEmpty())
            assertFalse(GboardPublishedPatchCatalog.morpheRegistrations.contains(patch))
        }
    }
    @Test fun universalFeaturesRemainPublished() {
        listOf("Clipboard Enhancements", "Enable OCR / Scan Text", "English QWERTY Up-Flick Uppercase",
            "Enable Inline Autofill Suggestions").forEach { name ->
            assertEquals(1, GboardPublishedPatchCatalog.morpheRegistrations.count { it.name == name })
        }
        assertTrue(CommunityPatchPolicy.publishes("editing_access_points"))
        assertTrue(CommunityPatchPolicy.publishes("frosted_glass"))
    }
}
