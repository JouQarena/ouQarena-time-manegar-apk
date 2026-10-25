package com.timemanager.android.ui

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timemanager.android.data.AppRepository
import com.timemanager.android.data.entity.PdfDocumentEntity
import kotlinx.coroutines.launch

/**
 * PDF Library — internal reader via android.graphics.pdf.PdfRenderer (API 21+).
 * No external reader required. SAF import, persisted URI permissions.
 */
@Composable
fun PdfLibraryScreen(repo: AppRepository, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<PdfDocumentEntity>>(emptyList()) }
    var openedPdfId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() { scope.launch { docs = repo.listPdfs() } }
    LaunchedEffect(Unit) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    // Take persistable permission
                    ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // Validate PDF opens
                    val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        val pages = renderer.pageCount
                        renderer.close(); pfd.close()
                        if (pages == 0) throw IllegalArgumentException("PDF has no pages")
                    }
                    val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) c.getString(idx) else uri.lastPathSegment ?: "document.pdf"
                        } else uri.lastPathSegment ?: "document.pdf"
                    } ?: "document.pdf"
                    repo.addPdf(name, uri.toString(), 0L)
                    reload()
                } catch (e: Exception) { error = "Import failed: ${e.message}" }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PDF Library") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Icon(Icons.Default.Add, "Import PDF") } }
    ) { padding ->
        if (openedPdfId != null) {
            PdfReaderView(repo, openedPdfId!!, onClose = { openedPdfId = null })
        } else {
            Column(Modifier.padding(padding).padding(12.dp)) {
                Text("Internal PDF reader — no external app needed. Files stored via SAF; permissions are persisted.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                if (docs.isEmpty()) {
                    Card { Column(Modifier.padding(20.dp)) { Text("No PDFs yet"); Text("Import one to start reading offline.", style = MaterialTheme.typography.bodySmall) } }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(docs, key = { it.id }) { doc ->
                            PdfRow(doc, onOpen = { id -> openedPdfId = id; scope.launch { repo.touchPdf(id) } },
                                onDelete = { scope.launch { repo.deletePdf(doc.id); reload() } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRow(doc: PdfDocumentEntity, onOpen: (Long) -> Unit, onDelete: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(doc.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("Imported ${doc.importedAt.take(10)} · ${doc.uri.take(40)}…", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                doc.lastOpenedAt?.let { Text("Last opened ${it.take(10)}", style = MaterialTheme.typography.labelSmall) }
            }
            IconButton(onClick = { onOpen(doc.id) }) { Icon(Icons.Default.MenuBook, "Open") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun PdfReaderView(repo: AppRepository, pdfId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var pageIndex by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    // Load doc entity
    var doc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    LaunchedEffect(pdfId) {
        doc = repo.db.pdfDao().getById(pdfId)
        doc?.let { d ->
            try {
                val uri = android.net.Uri.parse(d.uri)
                pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                renderer = pfd?.let { PdfRenderer(it) }
                pageCount = renderer?.pageCount ?: 0
                if (pageCount == 0) err = "Empty PDF"
                else renderPage(renderer, pageIndex) { bitmap = it }
            } catch (e: Exception) { err = "Failed to open: ${e.message}" }
        }
    }
    DisposableEffect(Unit) { onDispose { try { renderer?.close() } catch (_: Exception) {}; try { pfd?.close() } catch (_: Exception) {} } }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClose) { Text("← Back to library") }
            Text("Page ${pageIndex + 1} / $pageCount")
        }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        bitmap?.let { bmp ->
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "PDF page", modifier = Modifier.fillMaxWidth().weight(1f))
        } ?: Box(Modifier.weight(1f).fillMaxWidth()) {}
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                if (pageIndex > 0) { pageIndex--; renderPage(renderer, pageIndex) { bitmap = it } }
            }, enabled = pageIndex > 0) { Text("← Prev") }
            Button(onClick = {
                if (pageIndex + 1 < pageCount) { pageIndex++; renderPage(renderer, pageIndex) { bitmap = it } }
            }, enabled = pageIndex + 1 < pageCount) { Text("Next →") }
        }
        Text("Rendered with android.graphics.pdf.PdfRenderer — pinch zoom can be added with additional gesture layer.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun renderPage(renderer: PdfRenderer?, index: Int, onDone: (android.graphics.Bitmap) -> Unit) {
    if (renderer == null) return
    try {
        renderer.openPage(index).use { page ->
            val bmp = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            onDone(bmp)
        }
    } catch (_: Exception) {}
}
