package com.example.cameraxtestapp

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

class RecorderViewModel(
    application: Application,
    val cameraExecutor: Executor
): AndroidViewModel(application) {

    companion object {
        private const val TAG = "CamXTestApp"
    }

    private val mRecordState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val recordState: StateFlow<Boolean> = mRecordState

    private var useCasesBound = false
    private lateinit var currentRecording: Recording
    private lateinit var preview: Preview
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var recorder: Recorder
    private lateinit var contentValues: ContentValues
    private lateinit var mediaStoreOutputOptions: MediaStoreOutputOptions

    private val qualitySelector = QualitySelector.fromOrderedList(
        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))

    private val recordEventListener = fun(recordEvent: VideoRecordEvent) {
        // 2.6 Respond to VideoRecordEvents inside your event listener.
        when (recordEvent) {
            is VideoRecordEvent.Start -> {
                Log.d(TAG, "VideoRecordEvent.Start")
                mRecordState.value = true
                Toast.makeText(getApplication(), "Started Recording", Toast.LENGTH_SHORT).show()
            }
            is VideoRecordEvent.Pause -> {
                Log.d(TAG, "VideoRecordEvent.Pause")
                mRecordState.value = false
                Toast.makeText(getApplication(), "Started Paused", Toast.LENGTH_SHORT).show()
            }
            is VideoRecordEvent.Status -> {
                Log.d(TAG, "VideoRecordEvent.Status: \n$recordEvent")
            }
            is VideoRecordEvent.Finalize -> {
                mRecordState.value = false
                if (!recordEvent.hasError()) {
                    Log.d(TAG, "VideoRecordEvent.Finalize success with output URI: ${recordEvent.outputResults.outputUri}")
                    Toast.makeText(getApplication(), "Saved to: ${recordEvent.outputResults.outputUri}", Toast.LENGTH_LONG).show()
                } else {
                    currentRecording.close()
                    Log.e(TAG, "Video capture ended with error: " +
                            recordEventErrorToString(recordEvent.error))
                    Toast.makeText(getApplication(), "Had trouble saving!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    fun bindPreviewAndRecorder(
        viewModel: RecorderViewModel,
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector,
        previewView: PreviewView,
    ) {

        Log.d(TAG, "Binding camera preview and recorder")
        preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // 2.1 Create a Recorder with QualitySelector.
        recorder = Recorder.Builder()
            .setExecutor(cameraExecutor)
            .setQualitySelector(qualitySelector)
            .build()

        videoCapture = VideoCapture.withOutput(viewModel.recorder)

        // 1.1 Bind VideoCapture
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture)

        useCasesBound = true
    }


    @SuppressLint("MissingPermission")
    fun startRecording(
        startingContext: Context,
    ) {

        if (useCasesBound) {
            Log.d(TAG, "Starting recording...")

            contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            }

            mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(
                    startingContext.contentResolver, // Is this leaky?
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

            // 2.2 Configure the Recorder with one of the OutputOptions.
            currentRecording = videoCapture.output
                .prepareRecording(startingContext, mediaStoreOutputOptions)
                .withAudioEnabled()  // 2.3 Enable audio with withAudioEnabled() if needed.
                .start(cameraExecutor, recordEventListener) // 2.4 Call start() with a VideoRecordEvent listener to begin recording

        } else { // Start Recording
            Log.d(TAG, "Cannot start recording without valid videoCapture and recorder instances.")

        }
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