package com.codding.test.comnecvnqrcodeexample

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_REQUEST_PERMISSON_CODE = 101
    }

    private lateinit var txtStatus : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txtStatus = findViewById(R.id.txtStatus)

        supportActionBar?.title = "QRScan demo"
    }

    fun startQrRealTimeScan(view : View) {
        if (isCameraPermissionEnabled()) {
            startCameraReviewScreen(true)
        }
    }

    fun startQrOneTimeScan(view : View) {
        if (isCameraPermissionEnabled()) {
            startCameraReviewScreen()
        }
    }

    private fun isCameraPermissionEnabled() : Boolean {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA ) ==
            PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA ), CAMERA_REQUEST_PERMISSON_CODE)
            return false
        }
        return true
    }

    private fun startCameraReviewScreen(realTimeMode : Boolean = false) {
        Intent(this, CameraReviewActivity::class.java).apply {
            if(realTimeMode) {
                putExtra(CameraReviewActivity.CAMERA_REALTIME_MODE, true)

            }
            startActivityForResult(this, CameraReviewActivity.CAMERA_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_REQUEST_PERMISSON_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraReviewScreen()
            } else {
                Toast.makeText(this, "Please accept camera permission to using app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CameraReviewActivity.CAMERA_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                var qrCode = data?.getStringExtra(CameraReviewActivity.INTENT_QR_SCANED)
                txtStatus.text  = qrCode
            } else {
                txtStatus.text  = ""
            }
        }
    }
}
