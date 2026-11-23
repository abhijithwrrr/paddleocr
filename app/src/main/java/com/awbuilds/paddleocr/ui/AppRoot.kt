package com.awbuilds.paddleocr.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.viewmodel.AppScreen
import com.awbuilds.paddleocr.viewmodel.AppViewModel

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handy for demos and automated smoke tests:
    // adb shell am start -n com.awbuilds.paddleocr/.MainActivity --es sample pan
    LaunchedEffect(Unit) {
        val activity = context.findActivity()
        val sampleKey = activity?.intent?.getStringExtra("sample")
        if (sampleKey != null) {
            activity.intent.removeExtra("sample")
            DocumentType.fromKey(sampleKey)?.let(viewModel::processSample)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onDocumentPicked(uri) else viewModel.clearPendingPicker()
    }

    LaunchedEffect(state.pendingType) {
        if (state.pendingType != null) {
            picker.launch(arrayOf("image/*", "application/pdf"))
            viewModel.clearPendingPicker()
        }
    }

    BackHandler(enabled = state.screen !is AppScreen.Home) {
        viewModel.backHome()
    }

    Scaffold { padding ->
        when (val screen = state.screen) {
            is AppScreen.Home -> HomeScreen(
                state = state,
                contentPadding = padding,
                onPick = viewModel::requestPicker,
                onOpen = viewModel::openResult,
                onSample = viewModel::processSample
            )

            is AppScreen.Processing -> ProcessingScreen(
                screen = screen,
                contentPadding = padding,
                onCancel = viewModel::cancelProcessing
            )

            is AppScreen.Review -> ReviewScreen(
                screen = screen,
                contentPadding = padding,
                onBack = viewModel::backHome,
                onFieldChange = viewModel::updateField,
                onTypeChange = viewModel::changeDocumentType,
                onExport = viewModel::showExport
            )
        }
    }

    if (state.showExport && state.screen is AppScreen.Review) {
        val result = (state.screen as AppScreen.Review).result
        val json = remember(result) { com.awbuilds.paddleocr.data.JsonExporter.toJson(result) }
        ExportSheet(
            result = result,
            json = json,
            onDismiss = viewModel::hideExport
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Could not read document") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text("OK") }
            }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
