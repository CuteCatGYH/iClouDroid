package com.getup.iClouDroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getup.iClouDroid.ui.AppShell
import com.getup.iClouDroid.ui.auth.AccountInfoDialog
import com.getup.iClouDroid.ui.auth.AuthScreen
import com.getup.iClouDroid.ui.auth.AuthUiState
import com.getup.iClouDroid.ui.auth.AuthViewModel
import com.getup.iClouDroid.feature.photos.ui.PhotosViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val photosViewModel: PhotosViewModel by viewModels()

    /**
     * Asks for POST_NOTIFICATIONS once (Android 13+), so the background scan can show its
     * progress notification (P2-016).
     *
     * Requested without ceremony: the notification is purely informational, and denying it only
     * means the scan is invisible — everything still works, so refusal must never block anything.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by authViewModel.uiState.collectAsState()
                    var showAccountDialog by remember { mutableStateOf(false) }

                    when (val state = authState) {
                        // P2-013: 'else -> AuthScreen' used to swallow this transient state and
                        // speculatively build the login WebView. Route it out explicitly so the
                        // only thing on screen during restoration is a spinner.
                        is AuthUiState.Checking -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is AuthUiState.LoggedIn -> {
                            // Authentication is an app-level concern; once it succeeds we hand off
                            // to the shell, which owns section switching (photos / drive / notes).
                            AppShell(
                                photosViewModel = photosViewModel,
                                onOpenAccountInfo = { showAccountDialog = true }
                            )

                            if (showAccountDialog) {
                                AccountInfoDialog(
                                    state = state,
                                    onDismiss = { showAccountDialog = false },
                                    onLogout = {
                                        showAccountDialog = false
                                        authViewModel.logout()
                                    }
                                )
                            }
                        }
                        else -> {
                            AuthScreen(viewModel = authViewModel)
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}
