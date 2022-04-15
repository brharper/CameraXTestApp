package com.example.cameraxtestapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.camera.core.Preview
import androidx.camera.video.*
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.cameraxtestapp.ui.theme.CameraXTestAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

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
        recorderViewModel = RecorderViewModel(cameraExecutor)
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

class RecorderViewModel(
    val cameraExecutor: Executor
): ViewModel() {

    companion object {
        private const val TAG = "CamXTestApp"
    }

    private val mRecordState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val recordState: StateFlow<Boolean> = mRecordState

    lateinit var currentRecording: Recording

//    Set externally by composable
    var videoCapture: VideoCapture<Recorder>? = null
    var recorder: Recorder? = null
//

    val qualitySelector = QualitySelector.fromOrderedList(
        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))

    private val recordEventListener = fun(recordEvent: VideoRecordEvent) {
        // 2.6 Respond to VideoRecordEvents inside your event listener.
        when (recordEvent) {
            is VideoRecordEvent.Start -> {
                Log.d(TAG, "VideoRecordEvent.Start")
                mRecordState.value = true
            }
            is VideoRecordEvent.Pause -> {
                Log.d(TAG, "VideoRecordEvent.Pause")
            }
            is VideoRecordEvent.Status -> {
                Log.d(TAG, "VideoRecordEvent.Status: \n$recordEvent")
            }
            is VideoRecordEvent.Finalize -> {
                mRecordState.value = false
                if (!recordEvent.hasError()) {
                    Log.d(TAG, "VideoRecordEvent.Finalize success with output URI: ${recordEvent.outputResults.outputUri}")
                } else {
                    currentRecording.close()
                    Log.e(TAG, "Video capture ended with error: " +
                            recordEventErrorToString(recordEvent.error))
                }
            }
        }
    }


    fun bindPreviewAndRecorder(
        cameraExecutor: Executor,
        viewModel: RecorderViewModel,
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector,
        previewView: PreviewView,
        qualitySelector: QualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
    ) {

        Log.d(TAG, "Binding camera preview and recorder") // ToDo: Remove

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // 2.1 Create a Recorder with QualitySelector.
        viewModel.recorder = Recorder.Builder().build()
//        .setExecutor(cameraExecutor)
//        .setQualitySelector(qualitySelector)
//        .build()

        viewModel.videoCapture = VideoCapture.withOutput(viewModel.recorder!!)

        // 1.1 Bind VideoCapture
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, viewModel.videoCapture)


    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        userLocale: Locale,
        startingContext: Context,
    ) {

        if (videoCapture == null || recorder == null) {
            Log.d(TAG, "Cannot start recording without valid videoCapture and recorder instances.")
            return
        }

        Log.d(TAG, "Starting recording...")

        val nameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", userLocale)
        val startDateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", userLocale) // ToDo: User locale might impact ISO8601 format assumed by database
        startDateTimeFormat.timeZone = TimeZone.getTimeZone("UTC")
        val curTime = System.currentTimeMillis()
        val tripName = "trip_" + nameFormat.format(curTime)
        val fileName = "$tripName.mp4"
        val videoMIMEType = "video/mp4"
        val relativeVideoPath = "recordings/" // ToDo: Implement error handling for invalid directories

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
//            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
//            }
        }
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(startingContext.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // 2.2 Configure the Recorder with one of the OutputOptions.
        currentRecording = videoCapture!!.output
            .prepareRecording(startingContext, mediaStoreOutputOptions)
            .withAudioEnabled()  // 2.3 Enable audio with withAudioEnabled() if needed.
            .start(cameraExecutor, recordEventListener) // 2.4 Call start() with a VideoRecordEvent listener to begin recording
    }


    fun stopRecording() {
        Log.d(TAG, "Stopping recording.")
        currentRecording.stop() // 2.5 Use pause()/resume()/stop() on the Recording to control the recording.
        mRecordState.value = false // ToDo: Might be redundant
    }

    private fun recordEventErrorToString(errorNum: Int): String {
        return when(errorNum){
            VideoRecordEvent.Finalize.ERROR_NONE -> "ERROR_NONE"
            VideoRecordEvent.Finalize.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "ERROR_FILE_SIZE_LIMIT_REACHED"
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "ERROR_INSUFFICIENT_STORAGE"
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "ERROR_SOURCE_INACTIVE"
            VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "ERROR_INVALID_OUTPUT_OPTIONS"
            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "ERROR_ENCODING_FAILED"
            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "ERROR_RECORDER_ERROR"
            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "ERROR_NO_VALID_DATA"
            else -> "UNKNOWN"
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
                    cameraExecutor = viewModel.cameraExecutor,
                    viewModel = viewModel,
                    cameraProvider = cameraProvider,
                    lifecycleOwner = lifecycleOwner,
                    cameraSelector = cameraSelector,
                    previewView = previewView,
                    qualitySelector = viewModel.qualitySelector
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
    val userLocale = LocalContext.current.resources.configuration.locales.get(0)
    val recordState = viewModel.recordState.collectAsState()

    IconButton(
        onClick = {
            Log.d(TAG, "RecordVideoButton onClick recordState.value = ${recordState.value}") // ToDo: Remove
            if (recordState.value) {
                viewModel.stopRecording()
            } else {
                viewModel.startRecording(
                    userLocale = userLocale,
                    startingContext = localContext
                )
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
