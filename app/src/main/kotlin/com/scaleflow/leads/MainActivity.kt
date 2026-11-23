package com.scaleflow.leads

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaleflow.leads.data.CallLogHelper
import com.scaleflow.leads.ui.main.MainScreen
import com.scaleflow.leads.ui.main.MainScreenViewModel
import com.scaleflow.leads.ui.theme.ScaleFlowLeadsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainScreenViewModel by viewModels()

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCallLogPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!CallLogHelper.hasCallLogPermission(this)) {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ScaleFlowLeadsTheme(darkTheme = state.config.darkTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPaused()
    }
}
