package com.example.ocrscanapp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
// 01.07.2025
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.FileOutputStream
class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        captureButton.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()
/*
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV")
        } else {
            Log.d("OpenCV", "OpenCV loaded")
        }

 */
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }
    /*
    private fun takePhoto() {
       /* val photoFile = File(
            externalMediaDirs.first(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        */
        val storageDir = getExternalFilesDir(null)
        val photoFile = File(
            storageDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(applicationContext, "Photo saved: ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                    //shareImageViaEmail(photoFile)
                    uploadToNextcloud(photoFile)
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }
    */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    private fun shareImageViaEmail(photoFile: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/image"
            putExtra(Intent.EXTRA_SUBJECT, "Scanned Document")
            putExtra(Intent.EXTRA_TEXT, "See attached image.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Send image via email"))
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    /* Changes on the 01.07.2025
    Set up Nextcloud
    Take a picture and upload it to nextcloud
    */
    private fun uploadToNextcloud(file: File) {
        Thread {
            try {
                val username = "andreangenendt2@gmail.com"
                val appPassword = "D5YZj-d6t6S-6DfbA-yG3ot-WFzPf"
                val folder = "OCRUploads"
                val uploadUrl = URL("https://kai.nl.tab.digital/remote.php/dav/files/$username/$folder/${file.name}")

                val connection = uploadUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"

                val auth = "$username:$appPassword"
                val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray())
                connection.setRequestProperty("Authorization", "Basic $encodedAuth")

                connection.doOutput = true
                val outputStream = connection.outputStream
                val fileInputStream = file.inputStream()

                fileInputStream.copyTo(outputStream)

                outputStream.flush()
                outputStream.close()
                fileInputStream.close()

                val responseCode = connection.responseCode
                if (responseCode == 201 || responseCode == 204) {
                    runOnUiThread {
                        Toast.makeText(this, "Upload successful", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Upload failed: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun runOCRFromImage(file: File, onResult: (String) -> Unit) {
        val image = InputImage.fromFilePath(this, Uri.fromFile(file))

        val recognizer = TextRecognition.getClient(
            TextRecognizerOptions.Builder().build()
        )

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                onResult(extractedText)
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Failed", e)
                onResult("OCR failed: ${e.message}")
            }
    }
   /*
    fun createPdfFromText(text: String, outputFile: File) {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val contentStream = PDPageContentStream(document, page)
        val font = PDType1Font.HELVETICA
        contentStream.beginText()
        contentStream.setFont(font, 12f)
        contentStream.newLineAtOffset(50f, 700f)

        // Split into lines
        val lines = text.split("\n")
        for (line in lines) {
            contentStream.showText(line)
            contentStream.newLineAtOffset(0f, -15f)
        }

        contentStream.endText()
        contentStream.close()

        document.save(outputFile)
        document.close()
    }

    */

    fun createPdfFromText(text: String, outputFile: File) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size @ 72 dpi
        val page = document.startPage(pageInfo)

        val canvas = page.canvas
        val paint = Paint().apply {
            textSize = 12f
        }

        val x = 40f
        var y = 50f
        val lineHeight = 16f
        val maxWidth = pageInfo.pageWidth - 80f

        val lines = text.split("\n")
        for (line in lines) {
            val words = line.split(" ")
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    canvas.drawText(currentLine, x, y, paint)
                    y += lineHeight
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine, x, y, paint)
                y += lineHeight
            }
        }

        document.finishPage(page)

        FileOutputStream(outputFile).use { outputStream ->
            document.writeTo(outputStream)
        }

        document.close()
    }
    private fun takePhoto() {
        val outputDirectory = getExternalFilesDir(null)

        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())

        val photoFile = File(outputDirectory, "$timestamp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(applicationContext, "Photo saved: ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()

                    runOCRFromImage(photoFile) { ocrText ->
                        Log.d(TAG, "OCR Result: $ocrText")

                        val pdfFile = File(outputDirectory, "$timestamp.pdf")
                        createPdfFromText(ocrText, pdfFile)
                        uploadToNextcloud(pdfFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }
}