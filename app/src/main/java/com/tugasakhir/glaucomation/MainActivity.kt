package com.tugasakhir.glaucomation

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.tugasakhir.glaucomation.databinding.ActivityMainBinding
import com.tugasakhir.glaucomation.ml.Categoricalefficientnet
import com.yalantis.ucrop.UCrop
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var currentPhotoPath: String? = null
    lateinit var binding: ActivityMainBinding

    private val GALLERY_REQUEST_CODE = 1234
    private val WRITE_EXTERNAL_STORAGE_CODE = 1
    private var REQCODE = 0

    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    lateinit var finalUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.back.visibility = View.GONE
        binding.map.visibility = View.GONE

        checkPermission()
        requestPermission()

        binding.gallery.setOnClickListener {
            if (checkPermission()) {
                REQCODE = 1
                displayPopupDialog()
            } else {
                Toast.makeText(this, "Allow all permissions", Toast.LENGTH_SHORT).show()
                requestPermission()
            }
        }

        binding.camera.setOnClickListener {
            if (checkPermission()) {
                REQCODE = 2
                displayPopupDialog()
            } else {
                Toast.makeText(this, "Allow all permissions", Toast.LENGTH_SHORT).show()
                requestPermission()
            }
        }

        binding.save.setOnClickListener {
            binding.map.visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    requestPermissions(permission, WRITE_EXTERNAL_STORAGE_CODE)
                } else {
                    saveEditedImage()
                }
            }
        }

        binding.cancel.setOnClickListener {
            binding.resView.text = ""
            binding.map.visibility = View.GONE
            val builder = AlertDialog.Builder(this)
            builder.setMessage("DO YOU WANT TO REPEAT?")
            builder.setPositiveButton("YES") { dialog, which ->
                binding.image.visibility = View.GONE
                binding.cancel.visibility = View.GONE
                binding.save.visibility = View.GONE
                binding.detection.visibility = View.GONE
                binding.resView.visibility = View.GONE
                binding.back.visibility = View.GONE
                binding.selectToast.visibility = View.VISIBLE
                binding.camera.visibility = View.VISIBLE
                binding.gallery.visibility = View.VISIBLE
                binding.mbook.visibility = View.VISIBLE
                binding.judul.visibility = View.VISIBLE
            }
            builder.setNegativeButton("NO")
            { dialog, which -> }
            val alertDialog = builder.create()
            alertDialog.window?.setGravity(Gravity.CENTER)
            alertDialog.show()
        }

        binding.back.setOnClickListener { which ->
            binding.resView.text = ""
            binding.image.visibility = View.GONE
            binding.cancel.visibility = View.GONE
            binding.save.visibility = View.GONE
            binding.detection.visibility = View.GONE
            binding.resView.visibility = View.GONE
            binding.selectToast.visibility = View.VISIBLE
            binding.camera.visibility = View.VISIBLE
            binding.gallery.visibility = View.VISIBLE
            binding.mbook.visibility = View.VISIBLE
            binding.judul.visibility = View.VISIBLE
            binding.map.visibility = View.GONE
            binding.back.visibility = View.GONE
        }

        var imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224,224,ResizeOp.ResizeMethod.BILINEAR))
            .build()

        binding.detection.setOnClickListener {
            binding.judul.visibility = View.GONE
            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(MediaStore.Images.Media.getBitmap(this.contentResolver, finalUri))

            tensorImage = imageProcessor.process(tensorImage)

            val model = Categoricalefficientnet.newInstance(this)

            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1,224,224,3),DataType.FLOAT32)
            inputFeature0.loadBuffer(tensorImage.buffer)


            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray
            //val outputString = outputFeature0.joinToString(separator = " ")

            var glaucomaprob = outputFeature0[0]
            var normalprob = outputFeature0[1]

            var prediksi = if (glaucomaprob > normalprob) "GLAUCOMA" else "NORMAL"

            binding.resView.text = prediksi

            binding.map.visibility = View.VISIBLE


            model.close()
        }

        binding.mbook.setOnClickListener(this)

        binding.map.setOnClickListener {
            val query = Uri.parse("geo:0,0?q=${"Rumah Sakit Mata Terdekat"}")
            val mapIntent = Intent(Intent.ACTION_VIEW, query)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }

        activityResultLauncher =
            registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    val imageFile = File(currentPhotoPath)
                    val imageUri = Uri.fromFile(imageFile)
                    // Process the captured image
                    val data: Intent? = result.data
                    data?.data.let { uri -> launchImageCrop(imageUri) }
                }

                else { }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            WRITE_EXTERNAL_STORAGE_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this, "Enable permissions", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveEditedImage() {
        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, finalUri)
        saveMediaToStorage(bitmap)
    }

    private fun displayPopupDialog(){
        var popupDialog = Dialog(this)
        popupDialog.setCancelable(false)

        popupDialog.setContentView(R.layout.activity_popup)
        popupDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var txtAlertMessage = popupDialog.findViewById<TextView>(R.id.txtAlert)
        var ok = popupDialog.findViewById<TextView>(R.id.ok)
        var cancel = popupDialog.findViewById<TextView>(R.id.cancel)

        if (REQCODE == 1) {
            ok.setOnClickListener {
                popupDialog.dismiss()
                pickFromGallery()
            }
            cancel.setOnClickListener {
                popupDialog.dismiss()
            }
        }
        else if (REQCODE == 2) {
                ok.setOnClickListener {
                    popupDialog.dismiss()
                    pickFromCamera()
                }
                cancel.setOnClickListener{
                    popupDialog.dismiss()
                }
            }
        popupDialog.show()
    }

    private fun saveImage(image: Bitmap?, context: Context): Uri {
        var imageFolder = File(context.cacheDir, "images")
        var uri: Uri? = null

        try {

            imageFolder.mkdirs()
            var file: File = File(imageFolder, "captured_image.png")
            var stream: FileOutputStream = FileOutputStream(file)
            image?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
            uri = FileProvider.getUriForFile(
                context.applicationContext,
                "com.tugasakhir.glaucoma" + ".provider",
                file
            )

        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return uri!!

    }

    private fun pickFromGallery() {
        REQCODE = 0
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    private fun pickFromCamera() {
        REQCODE = 0
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE) // open camera
        if (intent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

            photoFile?.let {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.tugasakhir.glaucomation" + ".provider",
                    it
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                // open camera > save uncompressed image(using "EXTRA_OUTPUT") as temp > open ucrop with the input using file path
            }
            activityResultLauncher.launch(intent)
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    data?.data?.let { uri ->
                        launchImageCrop(uri)
                    }
                } else {

                }
            }
        }

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri: Uri? = UCrop.getOutput(data!!)

            setImage(resultUri!!)

            finalUri = resultUri

            binding.back.visibility = View.VISIBLE
            binding.image.visibility = View.VISIBLE
            binding.selectToast.visibility = View.GONE
            binding.save.visibility = View.VISIBLE
            binding.cancel.visibility = View.VISIBLE
            binding.detection.visibility = View.VISIBLE
            binding.resView.visibility = View.VISIBLE
            binding.camera.visibility = View.GONE
            binding.gallery.visibility = View.GONE
            binding.mbook.visibility = View.GONE
        }

    }

    private fun launchImageCrop(uri: Uri) {

        var destination: String = StringBuilder(UUID.randomUUID().toString()).toString()
        var options: UCrop.Options = UCrop.Options()

        UCrop.of(Uri.parse(uri.toString()), Uri.fromFile(File(cacheDir, destination)))
            .withOptions(options)
            .withAspectRatio(0F, 0F)
            .useSourceImageAspectRatio()
            .withMaxResultSize(2000, 2000)
            .start(this)
    }

    private fun setImage(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .into(binding.image)
    }


    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            ),
            100
        )
    }

    private fun saveMediaToStorage(bitmap: Bitmap) {
        val filename = "${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }
        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(this, "Saved to Photos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.mbook -> {
                val intentBiasa = Intent(this@MainActivity, MbookActivity::class.java)
                startActivity(intentBiasa)
            }
        }
    }
}

private fun TensorImage.load(image: ShapeableImageView) {
}
