package dev.jason.gboardpatches.patches.gboard.registry

/** Fork catalog policy, independent of target admission and native language support. */
internal object CommunityPatchPolicy {
    val excludedRegionalFeatures = setOf(
        "zhuyin_slide_input",
        "zhuyin_quick_traditional_simplified_toggle",
        "zhuyin_bottom_row_key_sizes",
        // This contribution only changes the Zhuyin symbols entry, despite its generic name.
        "custom_symbols",
    )

    fun publishes(featureId: String) = featureId !in excludedRegionalFeatures
}
