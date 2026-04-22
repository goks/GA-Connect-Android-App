package com.example.pricelist.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import android.location.Location
import android.net.Uri
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
import java.security.MessageDigest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.painterResource
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

private const val ADMIN_EMAIL = "gokulav2@gmail.com"

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

    val currentSHA1 = remember { getSigningCertificateSHA1(context) }
    LaunchedEffect(Unit) {
        Log.d("LoginDebug", "--------------------------------------")
        Log.d("LoginDebug", "DEBUG INFO FOR ERROR 10:")
        Log.d("LoginDebug", "Package Name: ${context.packageName}")
        Log.d("LoginDebug", "Current SHA-1: $currentSHA1")
        Log.d("LoginDebug", "Client ID used: $clientId")
        Log.d("LoginDebug", "--------------------------------------")
    }

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
                                gClient.revokeAccess()
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
            val statusCode = (e as? ApiException)?.statusCode
            val debugMsg = "Sign-in error (Code: $statusCode): ${e.message}\nApp SHA-1: $currentSHA1"
            Log.e("LoginScreen", debugMsg, e)
            errorText = debugMsg
            loading = false
        }
    }

    LaunchedEffect(locPerm.status) {
        if (locPerm.status.isGranted && !loading && auth.currentUser == null) {
            // Removed automatic startSignIn to prevent sign-in loops after logout.
            // Sign-in should only be triggered by the user clicking the button.
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
                painter = painterResource(id = R.drawable.ga_logo_old),
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
                                // Check Play Services before launching
                                val gpaStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                                if (gpaStatus != ConnectionResult.SUCCESS) {
                                    Log.e("LoginScreen", "Play Services not available before launcher: $gpaStatus")
                                    errorText = "Google Play Services not available or out of date. Please update Google Play Services."
                                    loading = false
                                    return@addOnCompleteListener
                                }
                                try {
                                    launcher.launch(gClient.signInIntent)
                                } catch (e: SecurityException) {
                                    Log.e("LoginScreen", "SecurityException launching sign-in intent", e)
                                    errorText = "Google sign-in failed: security error. Check Play Services and app configuration (package name / SHA-1)."
                                    loading = false
                                } catch (e: Exception) {
                                    Log.e("LoginScreen", "Error launching sign-in intent", e)
                                    errorText = "Google sign-in failed: ${e.message}"
                                    loading = false
                                }
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

                    Spacer(Modifier.height(8.dp))
                    // Offer a quick action to open Play Store for Google Play Services
                    val activity = LocalContext.current as? Activity
                    OutlinedButton(onClick = {
                        val playStoreIntent = try {
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"))
                        } catch (e: Exception) {
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms"))
                        }
                        activity?.startActivity(playStoreIntent)
                    }) {
                        Text("Update Google Play Services")
                    }

                    TextButton(onClick = {
                        errorText = null
                        if (!locPerm.status.isGranted) {
                            locPerm.launchPermissionRequest()
                        } else {
                            loading = true
                            // Before retrying, check Play Services again
                            val gpaStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                            if (gpaStatus != ConnectionResult.SUCCESS) {
                                errorText = "Google Play Services not available or out of date. Please update Google Play Services."
                                loading = false
                            } else {
                                try {
                                    startSignIn(context, launcher, clientId)
                                } catch (e: Exception) {
                                    Log.e("LoginScreen", "Error on retry startSignIn", e)
                                    errorText = "Google sign-in failed: ${e.message}"
                                    loading = false
                                }
                            }
                        }
                    }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

private fun getSigningCertificateSHA1(context: Context): String? {
    return try {
        val pm = context.packageManager
        val packageName = context.packageName
        val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            pi.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            pi.signatures.orEmpty()
        }

        if (signatures.isEmpty()) return null
        val md = MessageDigest.getInstance("SHA-1")
        val cert = signatures[0].toByteArray()
        val sha1 = md.digest(cert)
        sha1.joinToString(":") { String.format("%02X", it) }
    } catch (e: Exception) {
        Log.e("LoginScreen", "Error getting signing SHA1", e)
        null
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
    val email = (update["email"] as? String).orEmpty()
    val isAdmin = email.equals(ADMIN_EMAIL, ignoreCase = true)
    val isWhitelisted = snap.getBoolean("whitelisted") == true || isAdmin
    val routedUpdate = update.toMutableMap()

    if (isAdmin) {
        routedUpdate["whitelisted"] = true
        routedUpdate["role"] = "administrator"
        routedUpdate["isAdmin"] = true
    }

    when {
        alreadyExists && isWhitelisted -> {
            // ✅ Only update other metadata
            userDoc.set(routedUpdate, SetOptions.merge())
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
            userDoc.update(routedUpdate)
                .addOnSuccessListener {
                    onDenied("Access denied – awaiting admin approval.")
                }
                .addOnFailureListener {
                    onDenied("Failed to update user info.")
                }
        }

        !alreadyExists -> {
            // ✅ First time only, set whitelisted = false
            val newUserData = routedUpdate.toMutableMap()
            newUserData["whitelisted"] = isAdmin
            if (!isAdmin) {
                newUserData["role"] = "user"
                newUserData["isAdmin"] = false
            }
            userDoc.set(newUserData)
                .addOnSuccessListener {
                    if (isAdmin) {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        onDenied("Access denied – awaiting admin approval.")
                    }
                }
                .addOnFailureListener {
                    onDenied("Failed to save user info.")
                }
        }
    }
}
