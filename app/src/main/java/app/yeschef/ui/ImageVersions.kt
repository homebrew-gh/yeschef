package app.yeschef.ui

import androidx.compose.runtime.mutableStateMapOf

/**
 * Recipe photos changed during this session, by recipe id. Image URLs carry the
 * version so the new photo shows at once instead of a cached old one (or the placeholder).
 */
object ImageVersions {
    private val versions = mutableStateMapOf<String, String>()
    operator fun get(recipeId: String): String? = versions[recipeId]
    fun bump(recipeId: String, version: String) {
        versions[recipeId] = version
    }
}
