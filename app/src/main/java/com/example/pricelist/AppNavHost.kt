package com.example.pricelist

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pricelist.ui.AdminScreen
import com.example.pricelist.ui.BrochureScreen
import com.example.pricelist.ui.HomeScreen
import com.example.pricelist.ui.LoginScreen
import com.example.pricelist.ui.StockAlertsScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

const val ROUTE_BROCHURES = "brochures"
const val ROUTE_ADMIN = "admin"

@Composable
fun AppNavHost(startStockAlerts: Boolean = false) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()

    // Automatic Blacklist Check
    DisposableEffect(auth.currentUser) {
        val user = auth.currentUser
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        
        if (user != null) {
            listener = FirebaseFirestore.getInstance().collection("users")
                .document(user.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val whitelisted = snapshot.getBoolean("whitelisted") ?: true
                        if (!whitelisted) {
                            auth.signOut()
                            // Aggressively clear Google Sign In cache
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                            val gClient = GoogleSignIn.getClient(navController.context, gso)
                            gClient.revokeAccess().addOnCompleteListener {
                                gClient.signOut()
                            }
                            
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
        }
        
        onDispose {
            listener?.remove()
        }
    }
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
        composable(ROUTE_ADMIN) {
            AdminScreen(onBack = { navController.popBackStack() })
        }
        addBrochureRoute(navController) // ✅ Attach brochure screen
    }
}

fun NavGraphBuilder.addBrochureRoute(nav: NavController) {
    composable(ROUTE_BROCHURES) {
        BrochureScreen(onBack = { nav.popBackStack() })
    }
}
