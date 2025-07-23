package com.example.pricelist.ui

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.pricelist.R
import com.google.accompanist.permissions.*
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.painterResource


/*------------------------------------------------------------*/
/* ---------------------  LOGIN SCREEN  ----------------------*/
/*------------------------------------------------------------*/
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val clientId = stringResource(R.string.default_web_client_id)
    val gClient = remember {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build()
        )
    }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val locPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(res.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential).addOnCompleteListener { result ->
                if (result.isSuccessful) {
                    auth.currentUser?.let { user ->
                        handleUserLogin(
                            context = context,
                            user = user,
                            fusedClient = fusedClient,
                            navController = navController,
                            onDenied = { msg ->
                                errorText = msg
                                loading = false
                                auth.signOut()
                                gClient.signOut()
                            }
                        )
                    }
                } else {
                    errorText = "Authentication failed."
                    loading = false
                }
            }
        } catch (e: Exception) {
            errorText = "Sign-in error: ${e.message}"
            loading = false
        }
    }

    LaunchedEffect(locPerm.status) {
        if (locPerm.status.isGranted && !loading && auth.currentUser == null) {
            loading = true
            startSignIn(context, launcher, clientId)
        }
    }

    // 🌟 Main Layout
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🖼 Logo
            Image(
                painter = painterResource(id = R.drawable.ga_logo),
                contentDescription = "Gokul Agencies Logo",
                modifier = Modifier
                    .height(140.dp)
                    .padding(bottom = 12.dp)
            )

            // 🏷 App Title
            Text(
                text = "GOKUL AGENCIES",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Staff login required to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        errorText = null

                        val afterPerms: () -> Unit = {
                            loading = true
                            gClient.signOut().addOnCompleteListener {
                                launcher.launch(gClient.signInIntent)
                            }
                        }

                        if (!locPerm.status.isGranted) {
                            locPerm.launchPermissionRequest()
                        } else {
                            afterPerms()
                        }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                    shape  = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Sign in with Google", color = Color.White)
                }

                errorText?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)

                    TextButton(onClick = {
                        errorText = null
                        if (!locPerm.status.isGranted) {
                            locPerm.launchPermissionRequest()
                        } else {
                            loading = true
                            startSignIn(context, launcher, clientId)
                        }
                    }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/*------------------------------------------------------------*/
/* ------------------  HELPER FUNCTIONS  ---------------------*/
/*------------------------------------------------------------*/
private fun startSignIn(
    context : Context,
    launcher: ActivityResultLauncher<android.content.Intent>,
    clientId: String
) {
    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(clientId)
        .requestEmail()
        .build()
    launcher.launch(GoogleSignIn.getClient(context, options).signInIntent)
}

/**
 * Write / update user record and decide navigation.
 * ‑ Creates the doc if absent with `whitelisted=false`
 * ‑ **Never** overwrites `whitelisted=true`
 */
private fun handleUserLogin(
    context      : Context,
    user         : FirebaseUser,
    fusedClient  : FusedLocationProviderClient,
    navController: NavController,
    onDenied     : (String) -> Unit
) {
    val db        = FirebaseFirestore.getInstance()
    val userDoc   = db.collection("users").document(user.uid)

    userDoc.get().addOnSuccessListener { snap ->

        /* --- build common metadata update --- */
        val now        = System.currentTimeMillis()
        val fmt        = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val readable   = fmt.format(Date(now))
        val baseUpdate = mutableMapOf<String, Any>(
            "email"        to (user.email ?: ""),
            "name"         to (user.displayName ?: ""),
            "lastLogin"    to now,
            "last30Days"   to FieldValue.arrayUnion(readable),
            "device"       to "${Build.MANUFACTURER} ${Build.MODEL}",
            "androidVer"   to Build.VERSION.RELEASE
        )

        /* --- add location if we already have permission --- */
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                loc?.let {
                    baseUpdate["location"] = mapOf("lat" to it.latitude, "lng" to it.longitude)
                }
                writeAndRoute(snap, userDoc, baseUpdate, navController, onDenied)
            }
        } else {
            writeAndRoute(snap, userDoc, baseUpdate, navController, onDenied)
        }
    }.addOnFailureListener {
        onDenied("Login failed: ${it.message}")
    }
}

/* decides navigation AND writes user doc */
private fun writeAndRoute(
    snap: DocumentSnapshot,
    userDoc: DocumentReference,
    update: Map<String, Any>,
    navController: NavController,
    onDenied: (String) -> Unit
) {
    val alreadyExists = snap.exists()
    val isWhitelisted = snap.getBoolean("whitelisted") == true

    when {
        alreadyExists && isWhitelisted -> {
            // ✅ Only update other metadata
            userDoc.update(update)
                .addOnSuccessListener {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                }
                .addOnFailureListener {
                    onDenied("Failed to update user info.")
                }
        }

        alreadyExists && !isWhitelisted -> {
            // ✅ Only update other metadata, preserve whitelist
            userDoc.update(update)
                .addOnSuccessListener {
                    onDenied("Access denied – awaiting admin approval.")
                }
                .addOnFailureListener {
                    onDenied("Failed to update user info.")
                }
        }

        !alreadyExists -> {
            // ✅ First time only, set whitelisted = false
            val newUserData = update.toMutableMap()
            newUserData["whitelisted"] = false
            userDoc.set(newUserData)
                .addOnSuccessListener {
                    onDenied("Access denied – awaiting admin approval.")
                }
                .addOnFailureListener {
                    onDenied("Failed to save user info.")
                }
        }
    }
}
