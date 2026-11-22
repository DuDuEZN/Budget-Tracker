package com.duduezn.budgettracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
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
import com.duduezn.budgettracker.ui.categories.CategoriesScreen
import com.duduezn.budgettracker.ui.dashboard.DashboardScreen
import com.duduezn.budgettracker.ui.transactions.TransactionsScreen

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("dashboard", "Accueil", Icons.Default.Home),
    Destination("transactions", "Transactions", Icons.AutoMirrored.Filled.List),
    Destination("categories", "Catégories", Icons.Default.ShoppingCart)
)

@Composable
fun BudgetTrackerRoot(container: AppContainer) {
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
            composable(destinations[0].route) {
                DashboardScreen(
                    viewModel = viewModel(factory = factory),
                    onSeePending = { navController.navigate(destinations[1].route) }
                )
            }
            composable(destinations[1].route) {
                TransactionsScreen(viewModel = viewModel(factory = factory))
            }
            composable(destinations[2].route) {
                CategoriesScreen(viewModel = viewModel(factory = factory))
            }
        }
    }
}
