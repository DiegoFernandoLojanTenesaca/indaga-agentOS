package com.indagalab.agentos.bridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Captura una foto sin preview (Camera2 + ImageReader), de forma síncrona para
 * que el endpoint HTTP /camera pueda devolver el resultado. Reemplaza a
 * `termux-camera-photo`.
 */
object CameraCapture {

    @Suppress("MissingPermission")
    fun capture(ctx: Context, lensFacing: Int, path: String): String {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return """{"error":"sin permiso de cámara"}"""
        }
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == lensFacing
        } ?: cm.cameraIdList.firstOrNull() ?: return """{"error":"sin cámara disponible"}"""

        val map = cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)
        val size = sizes?.filter { it.width <= 1920 }?.maxByOrNull { it.width.toLong() * it.height } ?: sizes?.firstOrNull()
        val w = size?.width ?: 1280
        val h = size?.height ?: 720

        val thread = HandlerThread("agentos-cam").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(w, h, ImageFormat.JPEG, 1)
        val latch = CountDownLatch(1)
        var err: String? = "tiempo agotado"
        var bytesWritten = 0
        var device: CameraDevice? = null

        reader.setOnImageAvailableListener({ r ->
            try {
                r.acquireLatestImage()?.use { img ->
                    val buf = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                    File(path).parentFile?.mkdirs()
                    File(path).outputStream().use { it.write(bytes) }
                    bytesWritten = bytes.size
                    err = null
                }
            } catch (e: Exception) {
                err = e.message
            } finally {
                latch.countDown()
            }
        }, handler)

        try {
            cm.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam
                    val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }
                    @Suppress("DEPRECATION")
                    cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(req.build(), null, handler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            err = "no se pudo configurar la cámara"; latch.countDown()
                        }
                    }, handler)
                }

                override fun onDisconnected(cam: CameraDevice) { cam.close() }
                override fun onError(cam: CameraDevice, error: Int) {
                    err = "error de cámara ($error)"; cam.close(); latch.countDown()
                }
            }, handler)
        } catch (e: Exception) {
            err = e.message; latch.countDown()
        }

        latch.await(8, TimeUnit.SECONDS)
        runCatching { device?.close() }
        runCatching { reader.close() }
        runCatching { thread.quitSafely() }

        return if (err == null) """{"ok":true,"path":"$path","bytes":$bytesWritten}"""
        else """{"error":"$err"}"""
    }
}
