package com.example.pricelist

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pricelist.ui.BrochureScreen
import com.example.pricelist.ui.HomeScreen
import com.example.pricelist.ui.LoginScreen
import com.google.firebase.auth.FirebaseAuth

const val ROUTE_BROCHURES = "brochures"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val startDestination = if (auth.currentUser != null) "home" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") { LoginScreen(navController) }
        composable("home") { HomeScreen(navController) }
        addBrochureRoute(navController) // ✅ Attach brochure screen
    }
}

fun NavGraphBuilder.addBrochureRoute(nav: NavController) {
    composable(ROUTE_BROCHURES) {
        BrochureScreen(onBack = { nav.popBackStack() })
    }
}
