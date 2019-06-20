package com.codding.test.comnecvnqrcodeexample


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.SparseIntArray
import android.view.MenuItem
import android.view.Surface
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.annotation.RequiresApi
import androidx.databinding.DataBindingUtil
import com.codding.test.comnecvnqrcodeexample.databinding.ActivityCameraReviewBinding
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraOptions
import com.otaliastudios.cameraview.Frame
import kotlinx.coroutines.*
import timber.log.Timber

class CameraReviewActivity : AppCompatActivity(), CoroutineScope by CoroutineScope(Dispatchers.Default) {

    companion object {
        const val CAMERA_REQUEST_CODE = 0x91
        const val CAMERA_REALTIME_MODE = "camera_in_realtime"
        const val INTENT_QR_SCANED = "intent_qr_scanned"
    }

    private lateinit var binding : ActivityCameraReviewBinding
    private var isAnalyzingFrame = false
    private var isInRealTimeScanMode = false
    private var usingCameraID : String? = null

    // QR Scanning options
    private var barcodeDetectorOption = FirebaseVisionBarcodeDetectorOptions.Builder()
        .setBarcodeFormats(
            FirebaseVisionBarcode.FORMAT_QR_CODE)
        .build()
    private val ORIENTATIONS = SparseIntArray()

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_camera_review)
        binding.cameraView.setLifecycleOwner(this)

        intent?.let {
            isInRealTimeScanMode = it.getBooleanExtra(CAMERA_REALTIME_MODE, false)
            if (!isInRealTimeScanMode) {
                binding.txtStatus.visibility = View.GONE
            }
        }

        supportActionBar?.title = "QRScan demo"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initCameraProcesser()
        initCameraEvent()
    }

    fun initCameraEvent() {
        binding.cameraView.addCameraListener(object : CameraListener() {
            override fun onCameraOpened(options: CameraOptions) {
                super.onCameraOpened(options)
                usingCameraID = getCameraBackId()
                Timber.d("onCameraOpened $usingCameraID")
                // Start focus animation on camera
                focusCameraAnimation()
            }
        })
    }

    fun initCameraProcesser() {
        Timber.d("initCameraProcesser")
        binding.cameraView.addFrameProcessor{startAnalyzeFrame(it)}
    }

    /**
     * Detect each image frame on camera
     */
    private fun startAnalyzeFrame(frame: Frame) {
        Timber.d("startAnalyFrame : $isAnalyzingFrame")
        if (isAnalyzingFrame) return
        isAnalyzingFrame = true

        // Get image from frame
        val data = frame.data
        var rotation = FirebaseVisionImageMetadata.ROTATION_90
        usingCameraID?.let {
            rotation = getRotationCompensation(it, this@CameraReviewActivity, this@CameraReviewActivity)
        }
        Timber.d("startAnalyFrame rotation $rotation")
        val imageData = FirebaseVisionImageMetadata.Builder()
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
            .setRotation(rotation)
            .setWidth(frame.size.width) // 480x360 is typically sufficient for
            .setHeight(frame.size.height) // image recognition
            .build()

        val image = FirebaseVisionImage.fromByteArray(data, imageData)

        var detector = FirebaseVision.getInstance().getVisionBarcodeDetector(barcodeDetectorOption)
        detector.detectInImage(image)
            .addOnSuccessListener { barcode ->
                isAnalyzingFrame = false
                if (barcode.size > 0) {
                    var rawValue = barcode[0].rawValue
                    if (isInRealTimeScanMode) {
                        codeDetectedSuccess(rawValue)
                    } else {
                        Intent().apply {
                            putExtra(INTENT_QR_SCANED, rawValue)
                            setResult(Activity.RESULT_OK, this)
                            finish()
                        }
                    }
                } else {
                    Timber.d("detectInImage empty")
                    codeDetectedUnknown()
                }
            }
            .addOnFailureListener {
                Timber.d("detectInImage fail")
                isAnalyzingFrame = false
                codeDetectedUnknown()
            }

    }

    /**
     * unknown QR code
     */
    private fun codeDetectedUnknown() {
        Timber.d("codeDetectedUnknow")
        binding.txtStatusLabel.text = "Scanning..."
        binding.txtStatus.text = ""
    }

    /**
     * Scan QR success
     */
    private fun codeDetectedSuccess(code : String?) {
        Timber.d("codeDetectedSuccess")
        binding.txtStatusLabel.text = "Scanned code: "
        binding.txtStatus.text = code
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if  (item.itemId == android.R.id.home) {
                finish()
                return true
        }
        return super.onOptionsItemSelected(item)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraAccessException::class)
    private fun getRotationCompensation(cameraId: String, activity: Activity, context: Context): Int {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        val deviceRotation = activity.windowManager.defaultDisplay.rotation
        var rotationCompensation = ORIENTATIONS.get(deviceRotation)

        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        val cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
        val sensorOrientation = cameraManager
            .getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360

        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        val result: Int
        when (rotationCompensation) {
            0 -> result = FirebaseVisionImageMetadata.ROTATION_0
            90 -> result = FirebaseVisionImageMetadata.ROTATION_90
            180 -> result = FirebaseVisionImageMetadata.ROTATION_180
            270 -> result = FirebaseVisionImageMetadata.ROTATION_270
            else -> {
                result = FirebaseVisionImageMetadata.ROTATION_0
            }
        }
        return result
    }


    /**
     * Get ID of Back camera
     */
    private fun getCameraBackId() : String? {
        var manager = getSystemService(CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        for (id in manager.cameraIdList) {
            var cameraCharacteristics = manager.getCameraCharacteristics(id)
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                cameraId = id
                break
            }
        }
        return cameraId
    }

    private fun focusCameraAnimation() {
        launch {
            while (true) {
                delay(1000)
                withContext(Dispatchers.Main) {
                    var animation = AlphaAnimation(0.0f, 1.0f)
                    animation.duration = 1000
                    binding.viewFocus.alpha = 1.0f
                    binding.viewFocus.startAnimation(animation)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
