package app.yeschef.ui.recipes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentAdapter.LayoutResultCallback
import android.print.PrintDocumentAdapter.WriteResultCallback
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.yeschef.R
import app.yeschef.data.SharePart
import app.yeschef.data.recipeShareParts
import app.yeschef.data.recipeShareText
import app.yeschef.data.remote.RecipeDetailDto
import java.io.File
import java.io.FileOutputStream

/** What the recipe screen's share menu offers. */
enum class ShareKind { Text, Pdf, Print }

/** Opens the share sheet with the recipe as plain text. */
fun shareRecipeText(context: Context, detail: RecipeDetailDto, scale: Double) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, detail.name)
        putExtra(Intent.EXTRA_TEXT, recipeShareText(detail, scale))
    }
    context.startActivity(Intent.createChooser(send, "Share recipe"))
}

/** Opens the share sheet with the recipe as a PDF file. */
fun shareRecipePdf(context: Context, file: File, name: String) {
    // Shared through the same FileProvider as camera photos (see res/xml/file_paths.xml).
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share recipe PDF"))
}

/** Hands the PDF to Android's print dialog, which also offers "Save as PDF". */
fun printRecipePdf(context: Context, file: File, name: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancel.isCanceled) return callback.onLayoutCancelled()
            val info = PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
            callback.onLayoutFinished(info, old != new)
        }

        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback) {
            try {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
    printManager.print(name, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build())
}

/**
 * Writes the recipe as a US Letter PDF in the app's cache (replacing the last one)
 * and returns the file. Text only, in the app's colours and title font.
 */
fun writeRecipePdf(context: Context, detail: RecipeDetailDto, scale: Double): File {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val safeName = detail.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Recipe" }
    val file = File(dir, "$safeName.pdf")

    val serif = ResourcesCompat.getFont(context, R.font.dm_serif_display) ?: Typeface.SERIF
    val pdf = PdfDocument()
    try {
        RecipePdfWriter(pdf, serif).write(recipeShareParts(detail, scale))
        FileOutputStream(file).use { pdf.writeTo(it) }
    } finally {
        pdf.close()
    }
    return file
}

private const val PAGE_W = 612 // US Letter, in points
private const val PAGE_H = 792
private const val MARGIN = 54f
private const val CONTENT_W = 504 // page width less both margins
private const val NUMBER_W = 22f

private class RecipePdfWriter(private val pdf: PdfDocument, serif: Typeface) {
    private val ink = 0xFF1F2328.toInt()
    private val muted = 0xFF5F6670.toInt()
    private val blue = 0xFF2F6FB0.toInt()
    private val gold = 0xFFB08A3A.toInt()

    private fun paint(size: Float, color: Int, face: Typeface = Typeface.DEFAULT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }

    private val titlePaint = paint(26f, ink, serif)
    private val infoPaint = paint(10.5f, muted)
    private val headingPaint = paint(16f, blue, serif)
    private val subPaint = paint(11.5f, gold, Typeface.DEFAULT_BOLD)
    private val bodyPaint = paint(11f, ink)
    private val numberPaint = paint(11f, blue, Typeface.DEFAULT_BOLD)
    private val footerPaint = paint(8.5f, muted)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; strokeWidth = 1f }

    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = 0f
    private val bottom = PAGE_H - MARGIN

    fun write(parts: List<SharePart>) {
        newPage()
        var previous: SharePart? = null
        parts.forEachIndexed { index, part ->
            when (part) {
                is SharePart.Title -> {
                    block(part.text, titlePaint)
                    y += 6f
                    canvas.drawLine(MARGIN, y, MARGIN + 64f, y, rulePaint)
                    y += 12f
                }
                is SharePart.Info -> block(part.text, infoPaint, after = 4f)
                is SharePart.Heading -> {
                    y += 14f
                    // Keep a heading with at least a couple of lines of what follows.
                    if (bottom - y < 60f) newPage()
                    block(part.text, headingPaint, after = 6f)
                }
                is SharePart.SubHeading -> {
                    if (previous !is SharePart.Heading) y += 6f
                    if (bottom - y < 44f) newPage()
                    block(part.text, subPaint, after = 3f)
                }
                is SharePart.Ingredient -> block(part.text, bodyPaint, prefix = "•", prefixPaint = numberPaint, after = 3f)
                is SharePart.Step -> {
                    val last = parts.getOrNull(index + 1) !is SharePart.Step
                    block(part.text, bodyPaint, prefix = "${part.number}.", prefixPaint = numberPaint, after = if (last) 0f else 8f)
                }
            }
            previous = part
        }
        finishPage()
    }

    /** Draws wrapped text, carrying on to the next page line by line if it runs out of room. */
    private fun block(text: String, textPaint: TextPaint, prefix: String? = null, prefixPaint: TextPaint = textPaint, after: Float = 0f) {
        val indent = if (prefix != null) NUMBER_W else 0f
        val width = (CONTENT_W - indent).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()
        var line = 0
        var first = true
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line).toFloat()
            var end = line
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= bottom - y) end++
            if (end == line) {
                if (y > MARGIN) { newPage(); continue }
                end = line + 1 // a single line taller than the page; draw it anyway
            }
            val chunkBottom = layout.getLineBottom(end - 1).toFloat()
            if (first && prefix != null) {
                canvas.drawText(prefix, MARGIN, y + layout.getLineBaseline(0), prefixPaint)
            }
            canvas.save()
            canvas.translate(MARGIN + indent, y - top)
            canvas.clipRect(0f, top, width.toFloat(), chunkBottom)
            layout.draw(canvas)
            canvas.restore()
            y += chunkBottom - top
            line = end
            first = false
            if (line < layout.lineCount) newPage()
        }
        y += after
    }

    private fun newPage() {
        finishPage()
        pageNumber++
        val p = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        page = p
        canvas = p.canvas
        y = MARGIN
    }

    private fun finishPage() {
        val p = page ?: return
        canvas.drawText("YesChef  ·  page $pageNumber", MARGIN, PAGE_H - MARGIN / 2, footerPaint)
        pdf.finishPage(p)
        page = null
    }
}
