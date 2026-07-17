package com.sumafacturas.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sumafacturas.app.databinding.ActivityMainBinding
import com.sumafacturas.app.util.FileUtils
import com.sumafacturas.app.util.ScanSession

/**
 * Pantalla principal. La app NUNCA intenta conectarse al sistema de
 * facturacion: solo trabaja con fotos, capturas de pantalla o imagenes
 * guardadas, ademas de la opcion de ingreso manual.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingCaptureUri: Uri? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingCaptureUri?.let { openCrop(it) }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { openCrop(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ScanSession.reset()

        binding.btnTakePhoto.setOnClickListener { checkCameraPermissionAndLaunch() }
        binding.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        binding.btnAddAnother.setOnClickListener { checkCameraPermissionAndLaunch() }
        binding.btnViewResults.setOnClickListener {
            ScanSession.computeFinal()
            startActivity(Intent(this, ReviewActivity::class.java))
        }
        binding.btnManualEntry.setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java).putExtra(EXTRA_MANUAL, true))
        }
    }

    override fun onResume() {
        super.onResume()
        updateImageCounter()
    }

    private fun updateImageCounter() {
        val count = ScanSession.imageCount()
        binding.imageCountText.text = if (count == 0) {
            getString(com.sumafacturas.app.R.string.no_images_yet)
        } else {
            "$count imagen(es) procesada(s)"
        }
        binding.btnAddAnother.isEnabled = count > 0
        binding.btnViewResults.isEnabled = count > 0
    }

    private fun checkCameraPermissionAndLaunch() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val uri = FileUtils.createCaptureUri(this)
        pendingCaptureUri = uri
        takePicture.launch(uri)
    }

    private fun openCrop(uri: Uri) {
        startActivity(Intent(this, CropActivity::class.java).apply {
            putExtra(CropActivity.EXTRA_IMAGE_URI, uri)
            putExtra(CropActivity.EXTRA_IMAGE_INDEX, ScanSession.imageCount())
        })
    }

    companion object {
        const val EXTRA_MANUAL = "manual_entry"
    }
}
