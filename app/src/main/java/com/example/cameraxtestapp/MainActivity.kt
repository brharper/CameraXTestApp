package com.example.cameraxtestapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Lens
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.cameraxtestapp.ui.theme.CameraXTestAppTheme
import com.example.cameraxtestapp.RecorderViewModel

/** From: https://developer.android.com/training/camerax/video-capture
 *
 * 1 To integrate the CameraX VideoCapture use case into your app, do the following:
 * 1.1 Bind VideoCapture
 * 1.2 Prepare and configure recording.
 * 1.3 Start and control the runtime recording.
 *
 * 2 Interaction with Major Recording Objects
 * 2.1 Create a Recorder with QualitySelector.
 * 2.2 Configure the Recorder with one of the OutputOptions.
 * 2.3 Enable audio with withAudioEnabled() if needed.
 * 2.4 Call start() with a VideoRecordEvent listener to begin recording.
 * 2.5 Use pause()/resume()/stop() on the Recording to control the recording.
 * 2.6 Respond to VideoRecordEvents inside your event listener.
 */

@RequiresApi(Build.VERSION_CODES.P)
class MainActivity : AppCompatActivity() {

    private val cameraExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private lateinit var recorderViewModel: RecorderViewModel

    companion object {
        const val TAG = "CamXTestApp"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "in onCreate")
        super.onCreate(savedInstanceState)
        recorderViewModel = RecorderViewModel(application, cameraExecutor)
        setContent {
            CameraXTestAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background) {
                    CreateApp(viewModel = recorderViewModel)
                }
            }
        }
    }
}



private const val TAG = "CamXTestApp"
@Composable
fun CreateApp(viewModel: RecorderViewModel){

    Log.d(TAG, "Compose CreateApp")

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()) { hasPermission ->
        if (hasPermission) {
            Log.d(TAG, "User gave permission")
        } else {
            // ToDo Do Something about the user not giving the permissions...
        }
    }

    LaunchedEffect(ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
        Log.d(TAG, "Launching permissionLauncher ${Manifest.permission.CAMERA}")
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED){
        Log.d(TAG, "Launching permissionLauncher ${Manifest.permission.RECORD_AUDIO}")
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }


    Box(){

        VideoPreview(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter),
            viewModel = viewModel)

        RecordVideoButton(
            modifier = Modifier.align(Alignment.BottomCenter),
            viewModel = viewModel)
    }

}

@Composable
fun VideoPreview(
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel
){

    Log.d(TAG, "Compose VideoRecorderSurface") // ToDo: Remove

    val currentContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember {ProcessCameraProvider.getInstance(currentContext)}
    val previewView = remember { PreviewView(currentContext) }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()


    AndroidView(
        factory = { context ->
            Log.d(TAG, "In AndroidView Factory Call") // ToDo: Remove
            val cameraExecutor = ContextCompat.getMainExecutor(context)
            val bindOnFutureComplete = {
                val cameraProvider = cameraProviderFuture.get()
                viewModel.bindPreviewAndRecorder(
                    viewModel = viewModel,
                    cameraProvider = cameraProvider,
                    lifecycleOwner = lifecycleOwner,
                    cameraSelector = cameraSelector,
                    previewView = previewView
                )
            }
            cameraProviderFuture.addListener(bindOnFutureComplete, cameraExecutor)
            previewView },
        modifier = modifier)

}


@Composable
fun RecordVideoButton(
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel
){
    Log.d(TAG, "Compose RecordVideoButton") // ToDo: Remove
    val localContext = LocalContext.current
    val recordState = viewModel.recordState.collectAsState()

    IconButton(
        onClick = {
            Log.d(TAG, "RecordVideoButton onClick recordState.value = ${recordState.value}") // ToDo: Remove
            if (recordState.value) {
                viewModel.stopRecording()
            } else {
                viewModel.startRecording(localContext)
            }
        },
        modifier = modifier.defaultMinSize(40.dp)
    ) {
        Icon(
            imageVector = Icons.Sharp.Lens,
            tint = colorResource(
                if (recordState.value) { R.color.recorder_red } else { R.color.light_gray}
            ),
            contentDescription = "Record",
            modifier = Modifier
                .size(60.dp)
                .border(6.dp, colorResource(id = R.color.white), CircleShape),
        )
    }
}
