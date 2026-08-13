package com.example.ui.navigation

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.PreferencesViewModel
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.scanner.DocumentSessionViewModel
import com.example.scanner.model.DocumentQuad
import com.example.scanner.ui.CameraScannerScreen
import com.example.scanner.ui.DocumentEnhanceScreen
import com.example.scanner.ui.DocumentSessionScreen
import com.example.scanner.ui.PdfResultScreen
import com.example.scanner.ui.PerspectiveCropScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.viewmodel.DocumentListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Destinations {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SCANNER = "scanner"
    const val CROP = "crop"
    const val ENHANCE = "enhance"
    const val SESSION = "session"
    const val PDF_RESULT = "pdf_result"
    const val DOCUMENT_DETAIL = "document_detail"
    const val OCR = "ocr"
}

@Composable
fun ScanovaNavGraph(
    preferencesViewModel: PreferencesViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isOnboardingCompleted by preferencesViewModel.isOnboardingCompleted.collectAsState()

    // Persistent ViewModels for Part 5
    val documentListViewModel: DocumentListViewModel = viewModel()
    val documentSessionViewModel: DocumentSessionViewModel = viewModel()

    // Temporary storage for captured scan state between camera, crop, and enhance
    var capturedImagePath by remember { mutableStateOf<String?>(null) }
    var capturedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var correctedImagePath by remember { mutableStateOf<String?>(null) }

    // Last created document for PDF Result screen
    var lastCreatedDocument by remember { mutableStateOf<ScannedDocumentEntity?>(null) }

    // Selected document for Detail & OCR screens
    var selectedDocumentForDetail by remember { mutableStateOf<ScannedDocumentEntity?>(null) }

    // Gallery Picker Contract for Direct Import from Session/Home
    val sessionGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val cachedFile = copyUriToCache(context, uri)
                if (cachedFile != null) {
                    capturedImagePath = cachedFile.absolutePath
                    capturedQuad = null
                    navController.navigate(Destinations.CROP)
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        modifier = modifier
    ) {
        // Splash Screen Route
        composable(
            route = Destinations.SPLASH,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            SplashScreen(
                isOnboardingCompleted = isOnboardingCompleted ?: false,
                onNavigateNext = { destination ->
                    navController.navigate(destination) {
                        popUpTo(Destinations.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding Route
        composable(
            route = Destinations.ONBOARDING,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            OnboardingScreen(
                onFinishOnboarding = {
                    preferencesViewModel.setOnboardingCompleted(true)
                    navController.navigate(Destinations.MAIN) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // Main Shell Route (Home, Documents, Settings bottom tabs)
        composable(
            route = Destinations.MAIN,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            MainContainer(
                preferencesViewModel = preferencesViewModel,
                documentListViewModel = documentListViewModel,
                onOpenScanner = {
                    navController.navigate(Destinations.SCANNER)
                },
                onRevisitOnboarding = {
                    preferencesViewModel.setOnboardingCompleted(false)
                    navController.navigate(Destinations.ONBOARDING) {
                        popUpTo(Destinations.MAIN) { inclusive = true }
                    }
                },
                onDocumentClick = { doc ->
                    selectedDocumentForDetail = doc
                    navController.navigate(Destinations.DOCUMENT_DETAIL)
                }
            )
        }

        // Camera Scanner Screen Route
        composable(
            route = Destinations.SCANNER,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            CameraScannerScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onImageCaptured = { imagePath, quad ->
                    capturedImagePath = imagePath
                    capturedQuad = quad
                    navController.navigate(Destinations.CROP)
                }
            )
        }

        // Perspective Auto Crop & Straighten Screen Route (PART 3)
        composable(
            route = Destinations.CROP,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val path = capturedImagePath
            if (path != null) {
                PerspectiveCropScreen(
                    imagePath = path,
                    initialQuad = capturedQuad,
                    onRetake = {
                        navController.popBackStack(Destinations.SCANNER, false)
                    },
                    onContinue = { correctedPath ->
                        correctedImagePath = correctedPath
                        navController.navigate(Destinations.ENHANCE)
                    }
                )
            } else {
                navController.popBackStack(Destinations.MAIN, false)
            }
        }

        // Document Enhancement Engine Screen Route (PART 4)
        composable(
            route = Destinations.ENHANCE,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val path = correctedImagePath
            if (path != null) {
                DocumentEnhanceScreen(
                    imagePath = path,
                    onBack = {
                        navController.popBackStack()
                    },
                    onContinue = { enhancedPath ->
                        // Add page to active session and go to Session Manager
                        val corrPath = correctedImagePath ?: enhancedPath
                        documentSessionViewModel.addPage(enhancedPath, corrPath)
                        navController.navigate(Destinations.SESSION) {
                            popUpTo(Destinations.MAIN) { inclusive = false }
                        }
                    }
                )
            } else {
                navController.popBackStack(Destinations.MAIN, false)
            }
        }

        // Multi-Page Document Session Manager Screen Route (PART 5)
        composable(
            route = Destinations.SESSION,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            DocumentSessionScreen(
                viewModel = documentSessionViewModel,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Destinations.MAIN) {
                            popUpTo(Destinations.MAIN) { inclusive = true }
                        }
                    }
                },
                onAddPageCamera = {
                    navController.navigate(Destinations.SCANNER)
                },
                onAddPageGallery = {
                    sessionGalleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPdfCreated = { createdDoc ->
                    lastCreatedDocument = createdDoc
                    navController.navigate(Destinations.PDF_RESULT)
                }
            )
        }

        // PDF Creation Success & Preview Screen Route (PART 5)
        composable(
            route = Destinations.PDF_RESULT,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val doc = lastCreatedDocument
            if (doc != null) {
                PdfResultScreen(
                    document = doc,
                    onDone = {
                        documentSessionViewModel.clearSession()
                        navController.navigate(Destinations.MAIN) {
                            popUpTo(Destinations.MAIN) { inclusive = true }
                        }
                    }
                )
            } else {
                navController.navigate(Destinations.MAIN) {
                    popUpTo(Destinations.MAIN) { inclusive = true }
                }
            }
        }

        // Document Details Route
        composable(
            route = Destinations.DOCUMENT_DETAIL,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val doc = selectedDocumentForDetail
            if (doc != null) {
                DocumentDetailScreen(
                    document = doc,
                    viewModel = documentListViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenOcr = { navController.navigate(Destinations.OCR) }
                )
            } else {
                navController.popBackStack(Destinations.MAIN, false)
            }
        }

        // OCR Extracted Text Route
        composable(
            route = Destinations.OCR,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val doc = selectedDocumentForDetail
            if (doc != null) {
                OcrScreen(
                    document = doc,
                    viewModel = documentListViewModel,
                    onDone = { navController.popBackStack() }
                )
            } else {
                navController.popBackStack(Destinations.MAIN, false)
            }
        }
    }
}

private suspend fun copyUriToCache(context: android.content.Context, uri: Uri): File? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val outputFile = File(
                context.cacheDir,
                "GALLERY_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            )
            val outputStream = FileOutputStream(outputFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            outputFile
        } catch (e: Exception) {
            Log.e("ScanovaNavGraph", "Failed to copy gallery image to cache", e)
            null
        }
    }
}
