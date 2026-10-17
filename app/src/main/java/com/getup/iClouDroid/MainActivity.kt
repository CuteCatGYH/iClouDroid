package com.getup.iClouDroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.getup.iClouDroid.ui.auth.AccountInfoDialog
import com.getup.iClouDroid.ui.auth.AuthScreen
import com.getup.iClouDroid.ui.auth.AuthUiState
import com.getup.iClouDroid.ui.auth.AuthViewModel
import com.getup.iClouDroid.ui.photos.PhotosScreen
import com.getup.iClouDroid.ui.photos.PhotosViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val photosViewModel: PhotosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by authViewModel.uiState.collectAsState()
                    var showAccountDialog by remember { mutableStateOf(false) }

                    when (val state = authState) {
                        is AuthUiState.LoggedIn -> {
                            PhotosScreen(
                                viewModel = photosViewModel,
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
}
