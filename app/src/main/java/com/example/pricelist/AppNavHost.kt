package com.example.pricelist

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pricelist.ui.BrochureScreen
import com.example.pricelist.ui.HomeScreen
import com.example.pricelist.ui.LoginScreen
import com.example.pricelist.ui.StockAlertsScreen
import com.google.firebase.auth.FirebaseAuth

const val ROUTE_BROCHURES = "brochures"

@Composable
fun AppNavHost(startStockAlerts: Boolean = false) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val startDestination = when {
        auth.currentUser == null -> "login"
        startStockAlerts -> "stock_alerts"
        else -> "home"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") { LoginScreen(navController) }
        composable("home?highlightMasterCode={highlightMasterCode}",
            arguments = listOf(
                navArgument("highlightMasterCode") {
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val highlightMasterCode = backStackEntry.arguments?.getString("highlightMasterCode")
            HomeScreen(navController, highlightMasterCode)
        }
        composable("stock_alerts") {
            StockAlertsScreen(onBack = { masterCode ->
                if (masterCode != null) {
                    navController.navigate("home?highlightMasterCode=$masterCode") {
                        popUpTo("stock_alerts") { inclusive = true }
                    }
                } else {
                    navController.popBackStack()
                }
            })
        }
        addBrochureRoute(navController) // ✅ Attach brochure screen
    }
}

fun NavGraphBuilder.addBrochureRoute(nav: NavController) {
    composable(ROUTE_BROCHURES) {
        BrochureScreen(onBack = { nav.popBackStack() })
    }
}
