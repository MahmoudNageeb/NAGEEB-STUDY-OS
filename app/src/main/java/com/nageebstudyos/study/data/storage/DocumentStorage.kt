package com.nageebstudyos.study.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.nageebstudyos.study.domain.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentStorage(private val context: Context) {
    private val imports
        get() = File(context.filesDir, "imports").apply { mkdirs() }

    suspend fun attach(
        uri: Uri,
        lessonId: String,
        imported: Boolean,
        fallbackTitle: String,
    ): Entry =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val id = newId()
            var title = fallbackTitle
            var size = -1L
            var temporary: File? = null
            var destination: File? = null
            try {
                resolver
                    .query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameColumn >= 0 && !cursor.isNull(nameColumn))
                                title = cursor.getString(nameColumn)
                            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn))
                                size = cursor.getLong(sizeColumn)
                        }
                    }
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val location =
                    if (imported) {
                        temporary = File(imports, "$id.part")
                        destination = File(imports, id)
                        resolver.openInputStream(uri)?.use { input ->
                            temporary.outputStream().use { output ->
                                size = input.copyTo(output, 8192)
                                output.fd.sync()
                            }
                        } ?: throw StudyException(Problem.FILE_ACCESS)
                        if (!temporary.renameTo(destination))
                            throw StudyException(Problem.IMPORT_FAILED)
                        destination.absolutePath
                    } else {
                        resolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                        resolver.openFileDescriptor(uri, "r")?.use {}
                            ?: throw StudyException(Problem.FILE_ACCESS)
                        uri.toString()
                    }
                Entry(
                    id,
                    title.ifBlank { fallbackTitle },
                    Kind.FILE,
                    ownerId = lessonId,
                    ownerKind = Kind.LESSON,
                    location = location,
                    mime = mime,
                    size = size,
                    storageType = if (imported) StorageType.IMPORTED else StorageType.LINKED,
                )
            } catch (e: Exception) {
                temporary?.delete()
                destination?.delete()
                if (e is CancellationException) throw e
                throw StudyException(
                    if (imported) Problem.IMPORT_FAILED else Problem.FILE_ACCESS,
                    e,
                )
            }
        }

    suspend fun open(entry: Entry) {
        val uri =
            withContext(Dispatchers.IO) {
                try {
                    if (entry.storageType == StorageType.IMPORTED) {
                        val file = safeFile(entry.location)
                        if (!file.isFile) throw StudyException(Problem.FILE_ACCESS)
                        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    } else {
                        val linked = Uri.parse(entry.location)
                        context.contentResolver.openFileDescriptor(linked, "r")?.use {}
                            ?: throw StudyException(Problem.FILE_ACCESS)
                        linked
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw StudyException(Problem.FILE_ACCESS, e)
                }
            }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, entry.mime)
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    )
            )
        } catch (e: Exception) {
            throw StudyException(Problem.FILE_ACCESS, e)
        }
    }

    fun openLink(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(StudyRules.url(url)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: StudyException) {
            throw e
        } catch (e: Exception) {
            throw StudyException(Problem.FILE_ACCESS, e)
        }
    }

    private fun safeFile(path: String): File {
        val file = File(path).canonicalFile
        if (file.parentFile != imports.canonicalFile) throw StudyException(Problem.FILE_ACCESS)
        return file
    }

    suspend fun removeCopy(path: String) =
        withContext(Dispatchers.IO) {
            val file = safeFile(path)
            if (file.exists() && !file.delete()) throw StudyException(Problem.FILE_ACCESS)
        }

    suspend fun cleanPending(repository: StudyRepository) =
        withContext(Dispatchers.IO) {
            repository.pendingFiles().forEach { (id, path) ->
                removeCopy(path)
                repository.completeDeletion(id)
            }
        }
}
