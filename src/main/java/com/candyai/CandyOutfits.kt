package com.candyai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What Candy can wear. The first one is her usual look (the original pictures); the others come with their own set of pictures. */
enum class Outfit(val key: String, val nameRes: Int, val fullRes: Int) {
    Default("default", R.string.outfit_default, R.drawable.candy_full_default),
    Rain("rain", R.string.outfit_rain, R.drawable.candy_full_rain),
    Green("green", R.string.outfit_green, R.drawable.candy_full_green),
    Stars("stars", R.string.outfit_stars, R.drawable.candy_full_stars),
    Academy("academy", R.string.outfit_academy, R.drawable.candy_full_academy);

    companion object {
        fun of(key: String?): Outfit = values().firstOrNull { it.key == key } ?: Default
    }
}

/**
 * The outfit she wears now, shared by every window (chat, floating Candy, over other apps).
 * Every picture that shows her body goes through [res]: give it the usual picture and it hands back the one for the current outfit.
 * (Face pieces, eyes and mouths are the same for every outfit, so they are not here.)
 */
object CandyOutfits {
    var current by mutableStateOf(Outfit.Default)
        private set

    fun load() {
        current = Outfit.of(Store.outfit)
    }

    fun choose(o: Outfit) {
        Store.outfit = o.key
        current = o
    }

    /** [base] is the usual (default outfit) picture; the result is the same picture in the current outfit. Reading it inside a composable updates the screen when the outfit changes. */
    fun res(base: Int): Int {
        val o = current
        if (o == Outfit.Default) return base
        return TABLE[base]?.get(o.ordinal - 1) ?: base
    }

    // order inside each row: rain, green, stars, academy (the same order as the Outfit entries after Default)
    private val TABLE: Map<Int, IntArray> = mapOf(
        R.drawable.candy_pose_listen to intArrayOf(R.drawable.candy_pose_listen_rain, R.drawable.candy_pose_listen_green, R.drawable.candy_pose_listen_stars, R.drawable.candy_pose_listen_academy),
        R.drawable.candy_pose_search to intArrayOf(R.drawable.candy_pose_search_rain, R.drawable.candy_pose_search_green, R.drawable.candy_pose_search_stars, R.drawable.candy_pose_search_academy),
        R.drawable.candy_pose_sorry to intArrayOf(R.drawable.candy_pose_sorry_rain, R.drawable.candy_pose_sorry_green, R.drawable.candy_pose_sorry_stars, R.drawable.candy_pose_sorry_academy),
        R.drawable.candy_pose_success to intArrayOf(R.drawable.candy_pose_success_rain, R.drawable.candy_pose_success_green, R.drawable.candy_pose_success_stars, R.drawable.candy_pose_success_academy),
        R.drawable.candy_pose_think to intArrayOf(R.drawable.candy_pose_think_rain, R.drawable.candy_pose_think_green, R.drawable.candy_pose_think_stars, R.drawable.candy_pose_think_academy),
        R.drawable.candy_stage_body to intArrayOf(R.drawable.candy_stage_body_rain, R.drawable.candy_stage_body_green, R.drawable.candy_stage_body_stars, R.drawable.candy_stage_body_academy),
        R.drawable.peek_body to intArrayOf(R.drawable.peek_body_rain, R.drawable.peek_body_green, R.drawable.peek_body_stars, R.drawable.peek_body_academy),
        R.drawable.candy_bar_lean_awake to intArrayOf(R.drawable.candy_bar_lean_awake_rain, R.drawable.candy_bar_lean_awake_green, R.drawable.candy_bar_lean_awake_stars, R.drawable.candy_bar_lean_awake_academy),
        R.drawable.candy_bar_lean_side to intArrayOf(R.drawable.candy_bar_lean_side_rain, R.drawable.candy_bar_lean_side_green, R.drawable.candy_bar_lean_side_stars, R.drawable.candy_bar_lean_side_academy),
        R.drawable.candy_bar_lean_half to intArrayOf(R.drawable.candy_bar_lean_half_rain, R.drawable.candy_bar_lean_half_green, R.drawable.candy_bar_lean_half_stars, R.drawable.candy_bar_lean_half_academy),
        R.drawable.candy_bar_lean_closed to intArrayOf(R.drawable.candy_bar_lean_closed_rain, R.drawable.candy_bar_lean_closed_green, R.drawable.candy_bar_lean_closed_stars, R.drawable.candy_bar_lean_closed_academy),
    )
}
