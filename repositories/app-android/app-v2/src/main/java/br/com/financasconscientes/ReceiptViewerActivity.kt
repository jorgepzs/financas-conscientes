package br.com.financasconscientes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.com.financasconscientes.data.DecryptedReceiptAttachment
import br.com.financasconscientes.data.EncryptedReceiptAttachmentStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** In-app preview. PDF plaintext exists only in private cache while this screen is open; no URI is shared. */
class ReceiptViewerActivity : ThemeAwareActivity() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var temporaryPdf: File? = null
    private var pageIndex = 0
    private var currentBitmap: Bitmap? = null
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViewer()
        cleanupStalePdfCacheFiles()
        val attachmentId = intent.getStringExtra(EXTRA_ATTACHMENT_ID)
        if (attachmentId.isNullOrBlank()) {
            showFailure("Comprovante não encontrado.")
            return
        }
        status.setText(R.string.viewer_opening)
        worker.execute {
            runCatching {
                val decrypted = EncryptedReceiptAttachmentStore(applicationContext).decrypt(attachmentId)
                if (decrypted.mimeType == "application/pdf") openPdf(decrypted)
                else openImage(decrypted)
            }.onFailure { error ->
                runOnUiThread { showFailure(error.message ?: "Não foi possível abrir o comprovante.") }
            }
        }
    }

    private fun buildViewer() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(16))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_app)
            contentDescription = "Logo Finanças Conscientes"
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        val title = TextView(this).apply {
            setText(R.string.viewer_title)
            textSize = 18f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            setText(R.string.close)
            isAllCaps = false
            textSize = 13f
            setTextColor(getColor(R.color.primary))
            background = roundedBackground(getColor(R.color.primary_soft), dp(12).toFloat())
            stateListAnimator = null
            minHeight = dp(44)
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        root.addView(header)

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(status)

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = "Prévia do comprovante"
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = roundedBackground(getColor(R.color.surface), dp(18).toFloat())
            addView(preview, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        previousButton = Button(this).apply {
            setText(R.string.previous_page)
            isAllCaps = false
            textSize = 13f
            setTextColor(getColor(R.color.primary))
            background = roundedBackground(getColor(R.color.primary_soft), dp(12).toFloat())
            stateListAnimator = null
            minHeight = dp(48)
            isEnabled = false
            setOnClickListener { renderPdfPage(pageIndex - 1) }
        }
        nextButton = Button(this).apply {
            setText(R.string.next_page)
            isAllCaps = false
            textSize = 13f
            setTextColor(getColor(R.color.primary))
            background = roundedBackground(getColor(R.color.primary_soft), dp(12).toFloat())
            stateListAnimator = null
            minHeight = dp(48)
            isEnabled = false
            setOnClickListener { renderPdfPage(pageIndex + 1) }
        }
        pages.addView(previousButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        pages.addView(nextButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(pages)
        setContentView(root)
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun openImage(decrypted: DecryptedReceiptAttachment) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(decrypted.bytes, 0, decrypted.bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2_000 || bounds.outHeight / sample > 2_000 ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MAX_PREVIEW_PIXELS) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            decrypted.bytes,
            0,
            decrypted.bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("A imagem do comprovante não pôde ser decodificada.")
        runOnUiThread {
            if (closing || isFinishing) {
                bitmap.recycle()
                return@runOnUiThread
            }
            currentBitmap?.recycle()
            currentBitmap = bitmap
            preview.setImageBitmap(bitmap)
            status.text = when (decrypted.mimeType) {
                "image/png" -> getString(R.string.viewer_png_image)
                "image/jpeg" -> getString(R.string.viewer_jpeg_image)
                else -> getString(R.string.viewer_webp_image)
            }
            previousButton.visibility = Button.GONE
            nextButton.visibility = Button.GONE
        }
    }

    private fun openPdf(decrypted: DecryptedReceiptAttachment) {
        val file = File(cacheDir, "receipt-preview-${UUID.randomUUID()}.pdf")
        temporaryPdf = file
        synchronized(activePdfFiles) { activePdfFiles.add(file.name) }
        var pfd: ParcelFileDescriptor? = null
        val pdfRenderer = try {
            FileOutputStream(file).use { stream ->
                stream.write(decrypted.bytes)
                stream.fd.sync()
            }
            val openedDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = openedDescriptor
            PdfRenderer(openedDescriptor).also {
                descriptor = openedDescriptor
                renderer = it
            }
        } catch (error: Throwable) {
            runCatching { pfd?.close() }
            deleteTemporaryPdf(file)
            throw error
        }
        runOnUiThread {
            if (closing || isFinishing) return@runOnUiThread
            status.text = resources.getQuantityString(
                R.plurals.viewer_pdf_pages,
                pdfRenderer.pageCount,
                pdfRenderer.pageCount,
            )
            previousButton.visibility = Button.VISIBLE
            nextButton.visibility = Button.VISIBLE
        }
        renderPdfPage(0)
    }

    private fun renderPdfPage(index: Int) {
        worker.execute {
            val pdf = renderer ?: return@execute
            if (index !in 0 until pdf.pageCount) return@execute
            val bitmap = runCatching {
                pdf.openPage(index).use { page ->
                    val scale = minOf(MAX_PDF_WIDTH.toFloat() / page.width, MAX_PDF_HEIGHT.toFloat() / page.height, 1f)
                    val image = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    image
                }
            }.getOrElse { error ->
                runOnUiThread { showFailure(error.message ?: "Não foi possível renderizar a página do PDF.") }
                return@execute
            }
            runOnUiThread {
                if (closing || isFinishing) {
                    bitmap.recycle()
                    return@runOnUiThread
                }
                currentBitmap?.recycle()
                currentBitmap = bitmap
                pageIndex = index
                preview.setImageBitmap(bitmap)
                status.text = getString(R.string.viewer_pdf_page, index + 1, pdf.pageCount)
                previousButton.isEnabled = index > 0
                nextButton.isEnabled = index + 1 < pdf.pageCount
            }
        }
    }

    private fun showFailure(message: String) {
        if (!::status.isInitialized) return
        status.text = message
        previousButton.visibility = Button.GONE
        nextButton.visibility = Button.GONE
    }

    override fun onDestroy() {
        closing = true
        worker.execute {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
            currentBitmap?.recycle()
            temporaryPdf?.let(::deleteTemporaryPdf)
        }
        worker.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun cleanupStalePdfCacheFiles() {
        val cacheRoot = runCatching { cacheDir.canonicalFile }.getOrNull() ?: return
        cacheRoot.listFiles()?.forEach { file ->
            if (!isOwnedPreviewFile(file, cacheRoot)) return@forEach
            val active = synchronized(activePdfFiles) { file.name in activePdfFiles }
            if (!active) file.delete()
        }
    }

    private fun isOwnedPreviewFile(file: File, cacheRoot: File): Boolean =
        file.name.matches(PREVIEW_FILE_NAME) &&
            runCatching { file.canonicalFile.parentFile == cacheRoot }.getOrDefault(false)

    private fun deleteTemporaryPdf(file: File) {
        val cacheRoot = runCatching { cacheDir.canonicalFile }.getOrNull()
        if (cacheRoot != null && isOwnedPreviewFile(file, cacheRoot)) file.delete()
        synchronized(activePdfFiles) { activePdfFiles.remove(file.name) }
    }

    companion object {
        const val EXTRA_ATTACHMENT_ID = "receipt_attachment_id"
        const val MAX_PREVIEW_PIXELS = 4_000_000L
        const val MAX_PDF_WIDTH = 1_400
        const val MAX_PDF_HEIGHT = 1_900
        private val activePdfFiles = mutableSetOf<String>()
        private val PREVIEW_FILE_NAME = Regex("receipt-preview-[0-9a-fA-F-]{36}\\.pdf")
    }
}
