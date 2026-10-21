package com.personal.workoutcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.workoutcoach.ui.coach.CoachScreen
import com.personal.workoutcoach.ui.components.appContainer
import com.personal.workoutcoach.ui.home.HomeScreen
import com.personal.workoutcoach.ui.onboarding.OnboardingScreen
import com.personal.workoutcoach.ui.progress.ProgressScreen
import com.personal.workoutcoach.ui.settings.EquipmentScreen
import com.personal.workoutcoach.ui.settings.MemoryScreen
import com.personal.workoutcoach.ui.settings.SettingsScreen
import com.personal.workoutcoach.ui.theme.WorkoutCoachTheme
import com.personal.workoutcoach.ui.workout.WorkoutScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as WorkoutCoachApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WorkoutCoachTheme { AppRoot() } }
    }

    override fun onStart() {
        super.onStart()
        container.timer.onForeground(this)
        lifecycleScope.launch {
            container.settings.recordAppOpen(System.currentTimeMillis())
            container.training.ensureSchedule()
        }
    }

    override fun onStop() {
        container.timer.onBackground(this)
        super.onStop()
    }
}

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    COACH("coach", "Coach", Icons.Default.Chat),
    WORKOUT("workout", "Workout", Icons.Default.FitnessCenter),
    PROGRESS("progress", "Progress", Icons.Default.ShowChart),
    SETTINGS("settings", "Profile", Icons.Default.Person),
}

@Composable
fun AppRoot() {
    val c = appContainer()
    // Wrapped so "loading" (no emission yet) is distinguishable from "no profile".
    val profileState by produceState<Result<com.personal.workoutcoach.data.db.UserProfileEntity?>?>(null) {
        c.training.profile.collect { value = Result.success(it) }
    }
    when {
        profileState == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        profileState!!.getOrNull()?.onboarded != true -> OnboardingScreen()
        else -> MainScaffold()
    }
}

@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val active by appContainer().training.activeSession.collectAsStateWithLifecycle(null)
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { go(t.route) },
                        icon = {
                            if (t == Tab.WORKOUT && active != null) BadgedBox(badge = { Badge() }) { Icon(t.icon, null) } else Icon(t.icon, null)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) { HomeScreen(onOpenWorkout = { go(Tab.WORKOUT.route) }, onOpenCoach = { go(Tab.COACH.route) }) }
            composable(Tab.COACH.route) {
                CoachScreen(onOpenWorkout = { go(Tab.WORKOUT.route) }, onOpenProgress = { go(Tab.PROGRESS.route) }, onOpenHome = { go(Tab.HOME.route) })
            }
            composable(Tab.WORKOUT.route) { WorkoutScreen(onFinished = { go(Tab.PROGRESS.route) }) }
            composable(Tab.PROGRESS.route) { ProgressScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen(onEquipment = { nav.navigate("equipment") }, onMemory = { nav.navigate("memory") }) }
            composable("equipment") { EquipmentScreen(onBack = { nav.popBackStack() }) }
            composable("memory") { MemoryScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
