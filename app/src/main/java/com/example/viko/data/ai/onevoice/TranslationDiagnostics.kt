package com.example.viko.data.ai.onevoice

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.example.viko.data.network.writeVoiceWav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Private recordings are opt-in. Each exported folder contains one turn's input and result. */
internal class TranslationDiagnostics(context: Context) {
    private val root = File(context.noBackupFilesDir, "translation-diagnostics")
    private val cache = context.cacheDir
    private val resolver = context.contentResolver
    private val access = Mutex()

    suspend fun capture(
        turn: Long, badgeId: String, direction: VoiceDirection, pcm: ByteArray, finalizeMs: Long
    ): String? = withContext(Dispatchers.IO) {
        access.withLock {
            val now = Instant.now()
            val id = "${now.toEpochMilli()}-${UUID.randomUUID()}"
            val directory = File(root, id)
            runCatching {
                check(directory.mkdirs())
                File(directory, "input.wav").writeVoiceWav(pcm)
                write(directory, JSONObject()
                    .put("turn", turn)
                    .put("captured_at_utc", now.toString())
                    .put("badge_id", badgeId)
                    .put("source", direction.source)
                    .put("target", direction.target)
                    .put("input", "input.wav")
                    .put("duration_ms", pcm.size / 32)
                    .put("finalize_ms", finalizeMs)
                    .put("status", "captured"))
                turns().drop(MAX_TURNS).forEach { it.deleteRecursively() }
                id
            }.onFailure {
                directory.deleteRecursively()
                Log.w(TAG, "Could not save translation recording", it)
            }.getOrNull()
        }
    }

    suspend fun update(id: String?, fields: JSONObject.() -> Unit) {
        if (id == null) return
        withContext(Dispatchers.IO) {
            access.withLock {
                val directory = File(root, id)
                val json = AtomicFile(File(directory, "turn.json"))
                if (!directory.isDirectory) return@withLock
                runCatching {
                    write(directory, JSONObject(json.readFully().toString(Charsets.UTF_8)).apply(fields))
                }.onFailure { Log.w(TAG, "Could not update translation log", it) }
            }
        }
    }

    suspend fun export(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val archive = File.createTempFile("translation-", ".zip", cache)
            try {
                val ready = access.withLock {
                    val entries = turns()
                    if (entries.isEmpty()) return@withLock false
                    ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                        for (entry in entries) {
                            for (name in listOf("turn.json", "input.wav")) {
                                val file = File(entry, name)
                                if (!file.isFile) continue
                                zip.putNextEntry(ZipEntry("${entry.name}/$name"))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                    true
                }
                if (!ready) ExportResult.Empty
                else {
                    checkNotNull(resolver.openOutputStream(uri, "wt")).use { output ->
                        archive.inputStream().use { it.copyTo(output) }
                    }
                    ExportResult.Saved
                }
            } finally {
                archive.delete()
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Log.w(TAG, "Could not export translation logs", it)
            ExportResult.Failed
        }
    }

    private fun turns(): List<File> = root.listFiles().orEmpty().filter { it.isDirectory }
        .sortedByDescending { it.name }

    private fun write(directory: File, row: JSONObject) {
        val json = AtomicFile(File(directory, "turn.json"))
        val output = json.startWrite()
        try {
            output.write(row.toString(2).toByteArray(Charsets.UTF_8))
            json.finishWrite(output)
        } catch (e: Exception) {
            json.failWrite(output)
            throw e
        }
    }

    enum class ExportResult { Saved, Empty, Failed }

    private companion object {
        const val TAG = "VikoDiagnostics"
        const val MAX_TURNS = 50
    }
}
