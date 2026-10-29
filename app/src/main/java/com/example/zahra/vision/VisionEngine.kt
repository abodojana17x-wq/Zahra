package com.example.zahra.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.util.Base64
import cz.adaptech.tesseract4android.TessBaseAPI
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.Face
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream

/**
 * Vision Engine — محرك فهم الصور المتقدم لزهرة
 * 
 * يوفر:
 * 1. OCR — قراءة النصوص من الصور (Tesseract4Android)
 * 2. Face Detection — كشف الوجوه (ML Kit)
 * 3. Color Analysis — تحليل الألوان السائدة
 * 4. Image Metadata — استخراج بيانات الصورة
 * 5. Image Preprocessing — تحسين الصور للـ OCR
 * 6. Image Description — وصف شامل للصورة
 * 
 * Zero-Network: يعمل offline بالكامل
 * 
 * @author Zahra Team
 * @version 2.0
 */
class VisionEngine(private val context: Context) {
    
    data class ImageAnalysis(
        val width: Int,
        val height: Int,
        val dominantColors: List<ColorInfo>,
        val brightness: Double,
        val description: String,
        val detectedText: String? = null,
        val detectedFaces: List<FaceInfo> = emptyList(),
        val metadata: ImageMetadata = ImageMetadata()
    )
    
    data class FaceInfo(
        val boundingBox: android.graphics.Rect,
        val smilingProbability: Float,
        val leftEyeOpenProbability: Float,
        val rightEyeOpenProbability: Float,
        val headEulerAngleX: Float,
        val headEulerAngleY: Float,
        val headEulerAngleZ: Float,
        val trackingId: Int
    ) {
        fun isSmiling(): Boolean = smilingProbability > 0.7f
        fun areEyesOpen(): Boolean = leftEyeOpenProbability > 0.7f && rightEyeOpenProbability > 0.7f
    }
    
    data class ColorInfo(
        val name: String,
        val nameAr: String,
        val hex: String,
        val percentage: Double
    )
    
    data class ImageMetadata(
        val format: String = "unknown",
        val fileSizeKB: Long = 0,
        val aspectRatio: String = "unknown",
        val orientation: String = "unknown"
    )
    
    // خريطة الألوان العربية
    private val colorNamesAr = mapOf(
        "red" to "أحمر",
        "orange" to "برتقالي",
        "yellow" to "أصفر",
        "green" to "أخضر",
        "blue" to "أزرق",
        "purple" to "بنفسجي",
        "pink" to "وردي",
        "brown" to "بني",
        "black" to "أسود",
        "white" to "أبيض",
        "gray" to "رمادي"
    )
    
    // Tesseract OCR instance
    private var tessBaseAPI: TessBaseAPI? = null
    private var ocrInitialized = false
    
    /**
     * تهيئة محرك OCR
     * يجب استدعاؤها قبل استخدام extractText
     */
    fun initializeOCR(language: String = "ara+eng"): Boolean {
        return try {
            // Ensure tessdata directory exists
            val tessDataDir = File(context.filesDir, "tessdata")
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
            }
            
            // Copy traineddata files from assets if not present
            copyTessDataIfNeeded("ara.traineddata")
            copyTessDataIfNeeded("eng.traineddata")
            
            // Initialize Tesseract
            tessBaseAPI = TessBaseAPI()
            val dataPath = context.filesDir.absolutePath
            ocrInitialized = tessBaseAPI!!.init(dataPath, language)
            
            if (ocrInitialized) {
                // Set page segmentation mode for automatic detection
                tessBaseAPI!!.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            }
            
            ocrInitialized
        } catch (e: Throwable) {
            // D16: التهيئة اختيارية. فشل تحميل المكتبة الأصلية (UnsatisfiedLinkError)
            // أو غياب ملفات traineddata يجب ألا يُسقط الراوتر كله — وإلا لا يعمل أي
            // اختبار وحدة (AC-0.2) ولا يعمل التطبيق أصلًا على جهاز بلا OCR.
            ocrInitialized = false
            false
        }
    }
    
    /**
     * نسخ ملفات tessdata من assets إلى filesDir
     */
    private fun copyTessDataIfNeeded(fileName: String) {
        val tessDataDir = File(context.filesDir, "tessdata")
        val destFile = File(tessDataDir, fileName)
        
        if (!destFile.exists()) {
            try {
                context.assets.open("tessdata/$fileName").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // File might not exist in assets, that's okay
            }
        }
    }
    
    /**
     * تحليل صورة من Bitmap
     */
    fun analyzeImage(bitmap: Bitmap, onFacesDetected: ((List<FaceInfo>) -> Unit)? = null): ImageAnalysis {
        val width = bitmap.width
        val height = bitmap.height
        
        // تحليل الألوان
        val dominantColors = analyzeDominantColors(bitmap, 5)
        
        // حساب السطوع
        val brightness = calculateBrightness(bitmap)
        
        // استخراج metadata
        val metadata = extractMetadata(bitmap)
        
        // OCR - استخراج النص
        val detectedText = extractText(bitmap)
        
        // Face Detection - كشف الوجوه (async)
        val faces = mutableListOf<FaceInfo>()
        detectFaces(bitmap) { detectedFaces ->
            faces.addAll(detectedFaces)
            onFacesDetected?.invoke(detectedFaces)
        }
        
        // بناء الوصف
        val description = buildDescription(width, height, dominantColors, brightness, metadata, detectedText, faces)
        
        return ImageAnalysis(
            width = width,
            height = height,
            dominantColors = dominantColors,
            brightness = brightness,
            description = description,
            detectedText = detectedText,
            detectedFaces = faces,
            metadata = metadata
        )
    }
    
    /**
     * تحليل صورة من ملف
     */
    fun analyzeImageFile(filePath: String): ImageAnalysis? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            
            val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
            val analysis = analyzeImage(bitmap)
            
            // إضافة حجم الملف
            val updatedMetadata = analysis.metadata.copy(
                fileSizeKB = file.length() / 1024,
                format = filePath.substringAfterLast('.', "unknown")
            )
            
            analysis.copy(metadata = updatedMetadata)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * تحليل صورة من Base64
     */
    fun analyzeImageBase64(base64String: String): ImageAnalysis? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return null
            analyzeImage(bitmap)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * تحليل الألوان السائدة
     */
    private fun analyzeDominantColors(bitmap: Bitmap, topK: Int = 5): List<ColorInfo> {
        val colorCounts = mutableMapOf<String, Int>()
        val colorMap = mutableMapOf<String, Int>() // name -> rgb
        
        // Sample pixels (كل 10 بكسل للسرعة)
        val step = 10
        var totalPixels = 0
        
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val colorName = classifyColor(pixel)
                
                colorCounts[colorName] = (colorCounts[colorName] ?: 0) + 1
                colorMap[colorName] = pixel
                totalPixels++
            }
        }
        
        // ترتيب حسب التكرار
        val sorted = colorCounts.entries
            .sortedByDescending { it.value }
            .take(topK)
        
        return sorted.map { (colorName, count) ->
            val rgb = colorMap[colorName] ?: 0
            val hex = String.format("#%06X", 0xFFFFFF and rgb)
            val percentage = (count.toDouble() / totalPixels) * 100
            
            ColorInfo(
                name = colorName,
                nameAr = colorNamesAr[colorName] ?: colorName,
                hex = hex,
                percentage = percentage
            )
        }
    }
    
    /**
     * تصنيف اللون
     */
    private fun classifyColor(pixel: Int): String {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        // تحويل لـ HSB
        val hsb = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsb)
        val hue = hsb[0]
        val saturation = hsb[1]
        val brightness = hsb[2]
        
        return when {
            brightness < 0.2 -> "black"
            brightness > 0.8 && saturation < 0.2 -> "white"
            saturation < 0.3 -> "gray"
            hue < 15 || hue >= 345 -> "red"
            hue < 45 -> "orange"
            hue < 70 -> "yellow"
            hue < 150 -> "green"
            hue < 200 -> "blue"
            hue < 270 -> "purple"
            hue < 345 -> "pink"
            else -> "unknown"
        }
    }
    
    /**
     * حساب السطوع المتوسط
     */
    private fun calculateBrightness(bitmap: Bitmap): Double {
        var totalBrightness = 0.0
        var count = 0
        val step = 20
        
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Perceived brightness
                val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                totalBrightness += brightness
                count++
            }
        }
        
        return if (count > 0) totalBrightness / count else 0.0
    }
    
    /**
     * استخراج metadata
     */
    private fun extractMetadata(bitmap: Bitmap): ImageMetadata {
        val width = bitmap.width
        val height = bitmap.height
        
        val aspectRatio = when {
            width.toDouble() / height > 1.5 -> "landscape"
            height.toDouble() / width > 1.5 -> "portrait"
            else -> "square"
        }
        
        val orientation = if (width > height) "أفقي" else if (height > width) "رأسي" else "مربع"
        
        return ImageMetadata(
            format = "bitmap",
            aspectRatio = aspectRatio,
            orientation = orientation
        )
    }
    
    /**
     * بناء وصف للصورة
     */
    private fun buildDescription(
        width: Int,
        height: Int,
        colors: List<ColorInfo>,
        brightness: Double,
        metadata: ImageMetadata,
        detectedText: String? = null,
        faces: List<FaceInfo> = emptyList()
    ): String {
        val sb = StringBuilder()
        
        // الأبعاد
        sb.append("📐 صورة بحجم ${width}×${height} بكسل")
        sb.append(" (${metadata.orientation})\n")
        
        // الألوان السائدة
        if (colors.isNotEmpty()) {
            val topColors = colors.take(3).map { "${it.nameAr} (${it.hex})" }
            sb.append("🎨 الألوان السائدة: ${topColors.joinToString("، ")}\n")
        }
        
        // السطوع
        val brightnessDesc = when {
            brightness < 0.3 -> "داكنة"
            brightness < 0.6 -> "متوسطة الإضاءة"
            else -> "مضيئة"
        }
        sb.append("💡 الإضاءة: $brightnessDesc\n")
        
        // النص المكتشف
        if (!detectedText.isNullOrBlank()) {
            val textPreview = if (detectedText.length > 100) {
                detectedText.take(100) + "..."
            } else {
                detectedText
            }
            sb.append("📝 النص المكتشف: $textPreview\n")
        }
        
        // الوجوه المكتشفة
        if (faces.isNotEmpty()) {
            sb.append("👤 عدد الوجوه: ${faces.size}\n")
            faces.forEachIndexed { index, face ->
                val smile = if (face.isSmiling()) "مبتسم" else "غير مبتسم"
                val eyes = if (face.areEyesOpen()) "عيون مفتوحة" else "عيون مغلقة"
                sb.append("  • وجه ${index + 1}: $smile، $eyes\n")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * مقارنة صورتين (تشابه الألوان)
     */
    fun compareImages(bitmap1: Bitmap, bitmap2: Bitmap): Double {
        val colors1 = analyzeDominantColors(bitmap1, 5)
        val colors2 = analyzeDominantColors(bitmap2, 5)
        
        var similarity = 0.0
        var totalWeight = 0.0
        
        for (c1 in colors1) {
            for (c2 in colors2) {
                if (c1.name == c2.name) {
                    val weight = (c1.percentage + c2.percentage) / 2
                    similarity += weight
                    totalWeight += weight
                }
            }
        }
        
        return if (totalWeight > 0) similarity / totalWeight else 0.0
    }
    
    /**
     * معالجة الصورة للـ OCR (تحسين الجودة)
     */
    private fun preprocessImageForOCR(bitmap: Bitmap): Bitmap {
        // 1. تحويل إلى grayscale
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(grayscale)
        val paint = android.graphics.Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        // 2. زيادة التباين (contrast enhancement)
        val enhanced = Bitmap.createBitmap(grayscale.width, grayscale.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                val pixel = grayscale.getPixel(x, y)
                val gray = Color.red(pixel) // In grayscale, R=G=B
                
                // Apply contrast enhancement
                val enhanced_gray = ((gray - 128) * 1.5 + 128).toInt().coerceIn(0, 255)
                enhanced.setPixel(x, y, Color.rgb(enhanced_gray, enhanced_gray, enhanced_gray))
            }
        }
        
        grayscale.recycle()
        return enhanced
    }
    
    /**
     * استخراج النص من الصورة (OCR) باستخدام Tesseract
     */
    fun extractText(bitmap: Bitmap): String? {
        if (!ocrInitialized) {
            if (!initializeOCR()) {
                return null
            }
        }
        
        return try {
            // Preprocess image for better OCR results
            val processed = preprocessImageForOCR(bitmap)
            
            // Set image to Tesseract
            tessBaseAPI!!.setImage(processed)
            
            // Extract text
            val text = tessBaseAPI!!.utF8Text
            
            processed.recycle()
            
            // Clean up the extracted text
            text?.trim()?.replace(Regex("\\s+"), " ")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * كشف الوجوه في الصورة باستخدام ML Kit
     */
    fun detectFaces(bitmap: Bitmap, onComplete: (List<FaceInfo>) -> Unit) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            // Configure face detector with high accuracy
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build()
            
            val detector = FaceDetection.getClient(options)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val faceInfos = faces.map { face ->
                        FaceInfo(
                            boundingBox = face.boundingBox,
                            smilingProbability = face.smilingProbability ?: 0f,
                            leftEyeOpenProbability = face.leftEyeOpenProbability ?: 0f,
                            rightEyeOpenProbability = face.rightEyeOpenProbability ?: 0f,
                            headEulerAngleX = face.headEulerAngleX,
                            headEulerAngleY = face.headEulerAngleY,
                            headEulerAngleZ = face.headEulerAngleZ,
                            trackingId = face.trackingId ?: -1
                        )
                    }
                    onComplete(faceInfos)
                }
                .addOnFailureListener {
                    onComplete(emptyList())
                }
        } catch (e: Exception) {
            onComplete(emptyList())
        }
    }
    
    /**
     * كشف الوجوه بشكل متزامن (للاستخدام في الاختبارات)
     */
    fun detectFacesSync(bitmap: Bitmap): Int {
        // This is a placeholder - ML Kit is async by design
        // For sync usage, you'd need to use a CountDownLatch or similar
        return 0
    }
    
    /**
     * تحرير الموارد
     */
    fun release() {
        try {
            tessBaseAPI?.end()
            tessBaseAPI?.recycle()
            tessBaseAPI = null
            ocrInitialized = false
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
