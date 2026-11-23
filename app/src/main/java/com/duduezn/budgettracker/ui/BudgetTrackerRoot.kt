package com.duduezn.budgettracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.duduezn.budgettracker.di.AppContainer
import com.duduezn.budgettracker.security.AppLockManager
import com.duduezn.budgettracker.ui.analysis.AnalysisScreen
import com.duduezn.budgettracker.ui.categories.CategoriesScreen
import com.duduezn.budgettracker.ui.dashboard.DashboardScreen
import com.duduezn.budgettracker.ui.settings.SettingsScreen
import com.duduezn.budgettracker.ui.transactions.TransactionsScreen

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("dashboard", "Accueil", Icons.Default.Home),
    Destination("transactions", "Transactions", Icons.AutoMirrored.Filled.List),
    Destination("analysis", "Analyse", Icons.Default.DateRange),
    Destination("categories", "Catégories", Icons.Default.ShoppingCart),
    Destination("settings", "Paramètres", Icons.Default.Settings)
)

@Composable
fun BudgetTrackerRoot(
    container: AppContainer,
    lockManager: AppLockManager,
    onLockNow: () -> Unit
) {
    val navController = rememberNavController()
    val factory = remember(container) { BudgetViewModelFactory(container) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = destinations.first().route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    viewModel = viewModel(factory = factory),
                    onSeePending = { navController.navigate("transactions") },
                    onSeeAnalysis = { navController.navigate("analysis") }
                )
            }
            composable("transactions") {
                TransactionsScreen(viewModel = viewModel(factory = factory))
            }
            composable("analysis") {
                AnalysisScreen(viewModel = viewModel(factory = factory))
            }
            composable("categories") {
                CategoriesScreen(viewModel = viewModel(factory = factory))
            }
            composable("settings") {
                SettingsScreen(
                    lockManager = lockManager,
                    onLockNow = onLockNow
                )
            }
        }
    }
}
