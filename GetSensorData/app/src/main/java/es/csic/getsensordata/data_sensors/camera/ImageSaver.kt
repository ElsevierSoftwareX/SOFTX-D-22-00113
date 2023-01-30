package es.csic.getsensordata.data_sensors.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


internal class ImageSaver(
    private val image: Image,
    private val imageFormat: Int,
    private val file: File,
    private val exifData: Map<String, String>
) : Runnable {

    override fun run() {
        Log.d(Tag, "run()")

        val bytes = getBytesFromImage(image, imageFormat)
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file).apply {
                write(bytes)
            }
        } catch (e: Throwable) {
            Log.e(Tag, e.toString())
        } finally {
            val exif = ExifInterface(file.absolutePath)
            for ((key, value) in exifData) {
                exif.setAttribute(key, value)
            }
            exif.saveAttributes()

            image.close()
            output?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    Log.e(Tag, e.toString())
                }
            }
            Log.d(Tag, "ImageSaver.run() finished")
        }
    }

    private fun getBytesFromImage(image: Image, imageFormat: Int): ByteArray {
        Log.d(Tag, "getBytesFromImage(image=$image, imageFormat=$imageFormat)")

        return when (imageFormat) {
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                bytes
            }
            ImageFormat.YUV_420_888 -> {
                val yuvBytes = convertYUV420ToN21(image)
                val yuvImage = YuvImage(yuvBytes, ImageFormat.NV21, image.width, image.height, null)
                val yuvOutputStream = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, yuvOutputStream)
                val bytes: ByteArray = yuvOutputStream.toByteArray()
                bytes
            }
            else -> ByteArray(0)
        }
    }

    private fun convertYUV420ToN21(imgYUV420: Image): ByteArray? {
        Log.d(Tag, "convertYUV420ToN21(imgYUV420=$imgYUV420)")
        val buffer0 = imgYUV420.planes[0].buffer
        val buffer2 = imgYUV420.planes[2].buffer
        val buffer0Size: Int = buffer0.remaining()
        val buffer2Size: Int = buffer2.remaining()
        val rez = ByteArray(buffer0Size + buffer2Size)
        buffer0.get(rez, 0, buffer0Size)
        buffer2.get(rez, buffer0Size, buffer2Size)
        return rez
    }

    companion object {
        private val Tag = ImageSaver::class.java.simpleName
    }
}
