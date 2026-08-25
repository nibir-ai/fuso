package com.fuso.app.navigation

import android.content.Context
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fuso.core.designsystem.motion.FusoMotion
import com.fuso.core.model.EntryType
import com.fuso.core.ui.LocalNavAnimatedVisibilityScope
import com.fuso.core.ui.LocalSharedTransitionScope
import com.fuso.feature.calendar.CalendarRoute
import com.fuso.feature.calendar.CalendarScreen
import com.fuso.feature.editor.EditorRoutePattern
import com.fuso.feature.editor.EditorScreen
import com.fuso.feature.editor.NewEntryArg
import com.fuso.feature.editor.editorRoute
import com.fuso.feature.journal.JournalRoute
import com.fuso.feature.journal.JournalScreen
import com.fuso.feature.notes.NotesRoute
import com.fuso.feature.notes.NotesScreen
import com.fuso.feature.search.SearchRoute
import com.fuso.feature.search.SearchScreen
import com.fuso.feature.settings.SettingsRoute
import com.fuso.feature.settings.SettingsScreen
import com.fuso.feature.today.TodayRoute
import com.fuso.feature.today.TodayScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FusoApp(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val onboardingPrefs = remember { context.getSharedPreferences("fuso_app", Context.MODE_PRIVATE) }
    var onboarded by rememberSaveable { mutableStateOf(onboardingPrefs.getBoolean(KEY_ONBOARDED, false)) }

    if (!onboarded) {
        com.fuso.app.onboarding.OnboardingScreen(
            onFinish = {
                onboardingPrefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                onboarded = true
            },
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Scaffold(
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        FloatingNavBar(
                            currentRoute = currentDestination?.route,
                            onSelect = { route -> navigateToTopLevel(navController, route) },
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                        )
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = TopLevelDestination.TODAY.route,
                    modifier = Modifier.padding(innerPadding),
                    enterTransition = {
                        tabEnter(
                            from = ordinalOf(initialState.destination.route),
                            to = ordinalOf(targetState.destination.route),
                        )
                    },
                    exitTransition = {
                        tabExit(
                            from = ordinalOf(initialState.destination.route),
                            to = ordinalOf(targetState.destination.route),
                        )
                    },
                    popEnterTransition = {
                        tabEnter(
                            from = ordinalOf(initialState.destination.route),
                            to = ordinalOf(targetState.destination.route),
                        )
                    },
                    popExitTransition = {
                        tabExit(
                            from = ordinalOf(initialState.destination.route),
                            to = ordinalOf(targetState.destination.route),
                        )
                    },
                ) {
                    composable(route = TodayRoute) {
                        WithRouteScope(this) {
                            TodayScreen(
                                onEntryClick = { entryId -> navController.navigate(editorRoute(entryId)) },
                                onQuickCaptureClick = { navController.navigate(editorRoute(NewEntryArg)) },
                                onOpenSettings = { navController.navigate(SettingsRoute) },
                            )
                        }
                    }
                    composable(route = JournalRoute) {
                        WithRouteScope(this) {
                            JournalScreen(
                                onEntryClick = { entryId -> navController.navigate(editorRoute(entryId)) },
                                onStartWriting = { navController.navigate(editorRoute(NewEntryArg)) },
                            )
                        }
                    }
                    composable(route = CalendarRoute) {
                        WithRouteScope(this) {
                            CalendarScreen(
                                onEntryClick = { entryId -> navController.navigate(editorRoute(entryId)) },
                            )
                        }
                    }
                    composable(route = NotesRoute) {
                        WithRouteScope(this) {
                            NotesScreen(
                                onNoteClick = { noteId -> navController.navigate(editorRoute(noteId)) },
                                onCreateNote = { navController.navigate(editorRoute(NewEntryArg, EntryType.NOTE)) },
                            )
                        }
                    }
                    composable(route = SearchRoute) {
                        WithRouteScope(this) {
                            SearchScreen(
                                onEntryClick = { entryId -> navController.navigate(editorRoute(entryId)) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable(route = EditorRoutePattern) {
                        WithRouteScope(this) {
                            EditorScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable(route = SettingsRoute) {
                        WithRouteScope(this) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WithRouteScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope) {
        content()
    }
}

private fun navigateToTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FloatingNavBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f),
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .navigationBarsPaddingCompatInner(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.86f else 1f,
                    animationSpec = FusoMotion.springSnappy(),
                    label = "navScale",
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onSelect(destination.route) }
                        .animateContentSize(animationSpec = FusoMotion.springSnappy())
                        .then(
                            if (selected) {
                                Modifier.background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(50),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .padding(horizontal = if (selected) 18.dp else 12.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.navigationBarsPaddingCompatInner(): Modifier = this

private fun ordinalOf(route: String?): Int {
    val index = TopLevelDestination.entries.indexOfFirst { it.route == route }
    return if (index < 0) 0 else index
}

private fun tabEnter(from: Int, to: Int) = if (to >= from) {
    slideInHorizontally(
        animationSpec = tween(FusoMotion.DurationMedium, easing = FusoMotion.EmphasizedDecelerate),
    ) { it / 8 } + fadeIn(tween(FusoMotion.DurationShort))
} else {
    slideInHorizontally(
        animationSpec = tween(FusoMotion.DurationMedium, easing = FusoMotion.EmphasizedDecelerate),
    ) { -it / 8 } + fadeIn(tween(FusoMotion.DurationShort))
}

private fun tabExit(from: Int, to: Int) = if (to >= from) {
    slideOutHorizontally(animationSpec = tween(FusoMotion.DurationMedium)) { -it / 12 } +
        fadeOut(tween(FusoMotion.DurationShort))
} else {
    slideOutHorizontally(animationSpec = tween(FusoMotion.DurationMedium)) { it / 12 } +
        fadeOut(tween(FusoMotion.DurationShort))
}

private const val KEY_ONBOARDED = "onboarding_complete"
