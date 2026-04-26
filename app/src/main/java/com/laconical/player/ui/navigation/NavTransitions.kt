package com.laconical.player.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

private const val SLIDE_DURATION_MS = 300

internal val TAB_ORDER = mapOf(
    NavRoute.TRACKS    to 0,
    NavRoute.ALBUMS    to 1,
    NavRoute.ARTISTS   to 2,
    NavRoute.PLAYLISTS to 3,
    // NavRoute.FAVORITES omitted — it is a drill-down from Playlists, not a peer tab.
    // isForwardNavigation defaults to true (forward push) for unrecognised routes.
)

/**
 * True when navigating to a higher tab index (or to a detail screen).
 * Pure function — testable without Android deps.
 */
internal fun isForwardNavigation(fromRoute: String?, toRoute: String?): Boolean {
    val fi = TAB_ORDER[fromRoute]
    val ti = TAB_ORDER[toRoute]
    return if (fi != null && ti != null) ti > fi else true
}

fun navEnterTransition(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { if (isForwardNavigation(from.destination.route, to.destination.route)) it else -it }
    )

fun navExitTransition(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutLinearInEasing),
        targetOffsetX = { if (isForwardNavigation(from.destination.route, to.destination.route)) -it else it }
    )

fun navPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { -it }
    )

fun navPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutLinearInEasing),
        targetOffsetX = { it }
    )
