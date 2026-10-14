package org.orientationjp.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid : User-Agent obligatoire (politique OSM) et cache dans le dossier de l'app
        val conf = Configuration.getInstance()
        conf.userAgentValue = Config.USER_AGENT
        conf.osmdroidBasePath = File(cacheDir, "osmdroid")
        conf.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")

        enableEdgeToEdge()
        setContent {
            OrientationTheme {
                AppRoot(vm)
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        vm.onPermissionResult(result.values.any { it })
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                vm.onPermissionResult(true)
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
        }
    }

    BackHandler(enabled = state.screen == Screen.Settings || state.screen == Screen.Admin) {
        vm.back()
    }

    when (state.screen) {
        Screen.Login -> LoginScreen(
            initialName = state.username,
            initialPrivate = state.privateMode,
            onStart = { name, priv -> vm.login(name, priv) },
        )
        Screen.Map -> MapScreen(state, vm)
        Screen.Settings -> SettingsScreen(state, vm)
        Screen.Admin -> AdminScreen(state, vm)
    }
}
