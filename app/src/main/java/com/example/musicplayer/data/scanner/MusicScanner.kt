package com.example.musicplayer.data.scanner

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traverses Storage Access Framework (SAF) document trees off the main thread
 * to discover supported audio files.
 */
@Singleton
class MusicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioFileDiscovery {
    companion object {
        val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus"
        )

        /** Image extensions accepted as a folder cover. */
        private val ARTWORK_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")

        /**
         * Conventional cover file base names, preferred order. Used only when
         * a folder holds more than one image and none of the conventional names
         * match; the first image alphabetically is used as a last resort so any
         * image file (e.g. Cover.jpg, folder.png, my-cover.webp) can serve as
         * the folder cover instead of being silently ignored.
         */
        private val PREFERRED_ARTWORK_NAMES = listOf(
            "cover", "folder", "front", "album", "albumart", "artwork", "thumb",
        )

        /** Cue sheet extension. */
        private const val CUE_EXTENSION = "cue"

        /** Guard against unbounded recursion on providers with cyclic trees. */
        private const val MAX_DIRECTORY_DEPTH = 20

        /**
         * Columns requested for one directory child cursor. One query returns
         * everything a child needs, replacing the several per-document
         * round-trips of `DocumentFile.name`, `type`, `length`, `lastModified`
         * and `isDirectory`.
         */
        private val DIRECTORY_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        /**
         * Upper bound for a cue sheet read. Sheets are a few kilobytes; the
         * limit only protects the scan from a provider returning a huge file.
         */
        private const val MAX_CUE_BYTES = 512 * 1024

        /**
         * Chooses the cover image of one folder from its file names.
         *
         * A single image in the folder is always treated as the cover. When
         * several exist, a conventionally named one is preferred in
         * [PREFERRED_ARTWORK_NAMES] order; otherwise the first image
         * alphabetically is used. Pure so the rule can be unit tested without a
         * document provider.
         */
        internal fun chooseFolderArtworkName(fileNames: List<String>): String? {
            val images = fileNames.filter { name ->
                name.substringAfterLast('.', "").lowercase(Locale.ROOT) in ARTWORK_EXTENSIONS
            }
            if (images.isEmpty()) return null
            if (images.size == 1) return images.first()

            for (preferred in PREFERRED_ARTWORK_NAMES) {
                images.firstOrNull { name ->
                    name.substringBeforeLast('.').trim().lowercase(Locale.ROOT) == preferred
                }?.let { return it }
            }

            return images.sorted().first()
        }
    }

    /**
     * Scans the specified folder tree recursively for supported audio candidates.
     *
     * @param folderUri The SAF tree URI of the folder to scan.
     * @return A [ScanResult.Success] containing discovered audio files, or [ScanResult.Failure] if unreadable.
     */
    override suspend fun scanFolder(folderUri: Uri): ScanResult = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()

        val rootDoc = try {
            DocumentFile.fromTreeUri(context, folderUri)
        } catch (e: Exception) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = e,
                message = "Failed to access folder tree: ${e.localizedMessage ?: e.message}"
            )
        }

        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = IllegalArgumentException("Invalid folder URI or folder does not exist"),
                message = "Selected folder does not exist or is not a directory."
            )
        }

        if (!rootDoc.canRead()) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = SecurityException("Permission denied for folder URI"),
                message = "Read permission for the selected folder is no longer available."
            )
        }

        // Traverse with one batched child query per folder instead of the
        // several provider round-trips DocumentFile needs per document. The
        // tree document ID is only available for document-tree URIs; without
        // it the traversal falls back to DocumentFile for the whole tree.
        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(folderUri)
        }.getOrNull()
        val rootDocumentUri = rootDocumentId?.let { documentId ->
            runCatching {
                DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
            }.getOrNull()
        } ?: folderUri

        val discoveredFiles = mutableListOf<DiscoveredAudioFile>()
        val warnings = mutableListOf<String>()
        var totalFilesScanned = 0

        fun traverse(dirUri: Uri, documentId: String?, currentDepth: Int) {
            if (currentDepth > MAX_DIRECTORY_DEPTH) {
                warnings.add("Exceeded maximum directory depth limit at $dirUri")
                return
            }

            val entries = try {
                listChildren(
                    treeUri = folderUri,
                    dirUri = dirUri,
                    documentId = documentId,
                )
            } catch (e: Exception) {
                warnings.add(
                    "Failed to list contents in folder " +
                        "'${dirUri.lastPathSegment ?: dirUri}': " +
                        (e.localizedMessage ?: e.message),
                )
                return
            }

            totalFilesScanned += entries.size

            // One lookup per folder: all audio files in it share the same cover.
            val (folderArtworkUri, folderArtworkLastModified) = selectFolderArtwork(entries)

            // A cue sheet next to the audio files can define the album order
            // even when the files themselves carry no track number.
            val cueHints = readCueHints(entries, warnings)

            for (entry in entries) {
                try {
                    if (entry.isDirectory) {
                        traverse(entry.uri, entry.documentId, currentDepth + 1)
                    } else if (isAudioCandidateByNameAndMime(entry.name, entry.mimeType)) {
                        val cueFile = cueHints[CueSheetParser.normalizeFileName(entry.name)]
                        // A cue FILE block with several tracks describes a
                        // single-file album: emit one DiscoveredAudioFile per
                        // cue track so each becomes a separate playable entry.
                        if (cueFile != null && cueFile.tracks.size > 1) {
                            for (cueTrack in cueFile.tracks) {
                                // Give every split track a unique URI so it
                                // persists as its own Room row instead of
                                // collapsing onto the shared audio file. The
                                // cue fragment is dropped before playback.
                                val splitUri = entry.uri.buildUpon()
                                    .fragment("cue/${cueTrack.number}")
                                    .build()
                                discoveredFiles.add(
                                    DiscoveredAudioFile(
                                        uri = splitUri,
                                        name = entry.name,
                                        mimeType = entry.mimeType,
                                        size = entry.size,
                                        lastModified = entry.lastModified,
                                        parentFolderUri = dirUri,
                                        artworkFileUri = folderArtworkUri,
                                        artworkFileLastModified = folderArtworkLastModified,
                                        cueTrackNumber = cueTrack.number,
                                        cueDiscNumber = cueFile.discNumber,
                                        cueTitle = cueTrack.title,
                                        cueAlbum = cueFile.albumTitle,
                                        cueArtist = cueFile.performer,
                                        cueStartOffsetMs = cueTrack.startOffsetMs,
                                    )
                                )
                            }
                        } else {
                            val track = cueFile?.tracks?.singleOrNull()
                            discoveredFiles.add(
                                DiscoveredAudioFile(
                                    uri = entry.uri,
                                    name = entry.name,
                                    mimeType = entry.mimeType,
                                    size = entry.size,
                                    lastModified = entry.lastModified,
                                    parentFolderUri = dirUri,
                                    artworkFileUri = folderArtworkUri,
                                    artworkFileLastModified = folderArtworkLastModified,
                                    cueTrackNumber = track?.number,
                                    cueDiscNumber = cueFile?.discNumber,
                                    cueAlbum = cueFile?.albumTitle,
                                    cueArtist = cueFile?.performer,
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    warnings.add(
                        "Failed to process item '${entry.name}': " +
                            (e.localizedMessage ?: e.message),
                    )
                }
            }
        }

        try {
            traverse(rootDocumentUri, rootDocumentId, 0)
        } catch (e: Exception) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = e,
                message = "Scan failed unexpectedly: ${e.localizedMessage ?: e.message}"
            )
        }

        val durationMs = System.currentTimeMillis() - startTime
        ScanResult.Success(
            folderUri = folderUri,
            audioFiles = discoveredFiles,
            totalFilesScanned = totalFilesScanned,
            durationMs = durationMs,
            warnings = warnings,
        )
    }

    /**
     * Lists one directory.
     *
     * A batched [DocumentsContract] child cursor is used when the provider
     * returned a tree document ID. Otherwise, or when the provider rejects
     * that query, the traversal falls back to DocumentFile so non-standard
     * providers keep working.
     */
    private fun listChildren(
        treeUri: Uri,
        dirUri: Uri,
        documentId: String?,
    ): List<ScannedDocument> {
        if (documentId != null) {
            runCatching { queryChildren(treeUri, documentId) }
                .getOrNull()
                ?.let { return it }
        }
        return queryChildrenWithDocumentFile(dirUri)
    }

    /**
     * Reads every child of [parentDocumentId] with one provider query.
     *
     * Returns `null` when the provider returns no cursor, which makes the
     * caller fall back to DocumentFile for this directory.
     */
    private fun queryChildren(
        treeUri: Uri,
        parentDocumentId: String,
    ): List<ScannedDocument>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId,
        )
        return context.contentResolver.query(
            childrenUri,
            DIRECTORY_PROJECTION,
            null,
            null,
            null,
        )?.use { cursor -> readDocuments(treeUri, cursor) }
    }

    /** Projects one child cursor into [ScannedDocument] entries. */
    private fun readDocuments(treeUri: Uri, cursor: Cursor): List<ScannedDocument> {
        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        if (idIndex < 0 || nameIndex < 0) {
            throw IllegalStateException(
                "Document provider did not return document ids or display names",
            )
        }
        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        val documents = ArrayList<ScannedDocument>(cursor.count)
        while (cursor.moveToNext()) {
            val documentId = cursor.getString(idIndex) ?: continue
            val name = cursor.getString(nameIndex) ?: continue
            val mimeType = mimeIndex.takeIf { it >= 0 }?.let(cursor::getString)
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex).coerceAtLeast(0L)
            } else {
                0L
            }
            val lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                cursor.getLong(modifiedIndex).coerceAtLeast(0L)
            } else {
                0L
            }
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            documents += ScannedDocument(
                documentId = documentId,
                uri = uri,
                name = name,
                mimeType = mimeType,
                size = size,
                lastModified = lastModified,
                isDirectory = resolveIsDirectory(uri, mimeType),
            )
        }
        return documents
    }

    /**
     * Resolves whether a cursor entry is a directory. Standard providers
     * report the directory MIME type; one that omits the MIME type (rare) gets
     * a single DocumentFile query so its subtree is not silently skipped.
     */
    private fun resolveIsDirectory(uri: Uri, mimeType: String?): Boolean {
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) return true
        if (mimeType != null) return false
        return runCatching {
            DocumentFile.fromSingleUri(context, uri)?.isDirectory == true
        }.getOrDefault(false)
    }

    /**
     * Fallback listing for providers that do not answer batched child
     * queries. Every property is read defensively because a provider may fail
     * on a single document without failing the whole folder.
     */
    private fun queryChildrenWithDocumentFile(dirUri: Uri): List<ScannedDocument> {
        val directory = if (DocumentsContract.isTreeUri(dirUri)) {
            DocumentFile.fromTreeUri(context, dirUri)
        } else {
            DocumentFile.fromSingleUri(context, dirUri)
                ?: DocumentFile.fromTreeUri(context, dirUri)
        } ?: throw IllegalArgumentException("Could not open folder $dirUri")

        return directory.listFiles().map { file ->
            ScannedDocument(
                documentId = runCatching {
                    DocumentsContract.getDocumentId(file.uri)
                }.getOrNull(),
                uri = file.uri,
                name = file.name ?: "",
                mimeType = runCatching { file.type }.getOrNull(),
                size = runCatching { file.length() }.getOrDefault(0L),
                lastModified = runCatching { file.lastModified() }.getOrDefault(0L),
                isDirectory = runCatching { file.isDirectory }.getOrDefault(false),
            )
        }
    }

    /**
     * Reads the cue sheets of one folder and maps each audio file name to the
     * cue tracks declared for it.
     *
     * `FILE` blocks with exactly one track are used as ordering hints (the app
     * plays the whole file as one item). `FILE` blocks with several tracks
     * describe a single-file album: each track in the block becomes a separate
     * playable library entry, split from the shared audio file.
     */
    private fun readCueHints(
        files: List<ScannedDocument>,
        warnings: MutableList<String>,
    ): Map<String, CueFileInfo> {
        val hints = mutableMapOf<String, CueFileInfo>()

        for (file in files) {
            if (file.isDirectory) continue
            if (file.name.substringAfterLast('.', "").lowercase(Locale.ROOT) != CUE_EXTENSION) {
                continue
            }

            try {
                val text = readCueText(file.uri) ?: continue
                val sheet = CueSheetParser.parse(text)

                for (cueFile in sheet.files) {
                    if (cueFile.tracks.isEmpty()) continue
                    val key = CueSheetParser.normalizeFileName(cueFile.fileName)
                    if (key.isEmpty() || key in hints) continue
                    hints[key] = CueFileInfo(
                        fileName = cueFile.fileName,
                        discNumber = sheet.discNumber,
                        albumTitle = sheet.albumTitle,
                        performer = sheet.performer,
                        tracks = cueFile.tracks.map { track ->
                            CueTrackInfo(
                                number = track.number,
                                title = track.title,
                                startOffsetMs = track.startOffsetMs,
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                warnings.add(
                    "Failed to read cue sheet '${file.name}': " +
                        (e.localizedMessage ?: e.message),
                )
            }
        }

        return hints
    }

    /** Reads a cue sheet's text, tolerating missing or unreadable documents. */
    private fun readCueText(uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var total = 0
            while (total < MAX_CUE_BYTES) {
                val read = stream.read(chunk)
                if (read < 0) break
                output.write(chunk, 0, read)
                total += read
            }
            output.toByteArray()
        } ?: return null

        return decodeCueText(bytes)
    }

    /**
     * Decodes cue text. UTF-8 (with or without BOM) is the common case; legacy
     * single-byte sheets fall back to Latin-1, which keeps the ASCII keywords,
     * numbers, and file names the parser needs intact.
     */
    internal fun decodeCueText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    /**
     * Resolves the folder cover document. Returns the cover URI and its
     * last-modified timestamp (or `null` and `0` when the folder has no usable
     * image).
     */
    private fun selectFolderArtwork(files: List<ScannedDocument>): Pair<Uri?, Long> {
        val images = files.filter { file ->
            !file.isDirectory &&
                file.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in ARTWORK_EXTENSIONS
        }

        val chosenName = chooseFolderArtworkName(images.map { it.name }) ?: return null to 0
        val chosen = images.firstOrNull { it.name == chosenName } ?: return null to 0
        return chosen.uri to chosen.lastModified
    }

    /**
     * Pure function to determine audio candidacy from file name and MIME type.
     */
    fun isAudioCandidateByNameAndMime(name: String, mimeType: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        val isAudioMime = mimeType?.startsWith("audio/", ignoreCase = true) == true
        val isSupportedExt = extension in SUPPORTED_EXTENSIONS

        return isAudioMime || isSupportedExt
    }

    /**
     * One child returned by a directory listing.
     *
     * @property documentId SAF document ID, or `null` when the fallback listing
     *   could not resolve one.
     * @property isDirectory True for directories, determined from the provider's
     *   MIME type (or a DocumentFile query when the MIME type is missing).
     */
    private data class ScannedDocument(
        val documentId: String?,
        val uri: Uri,
        val name: String,
        val mimeType: String?,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
    )

    /** Ordering hint declared for one audio file by a folder cue sheet. */
    private data class CueTrackHint(
        val trackNumber: Int,
        val discNumber: Int?,
    )

    /**
     * Full cue description for one audio file referenced by a cue sheet.
     *
     * When a `FILE` block holds exactly one track, [tracks] has one entry and the
     * file is played as one item (the cue only supplies an ordering hint). When a
     * `FILE` block holds several tracks, the audio file is a single-file album:
     * [MusicScanner] emits one [DiscoveredAudioFile] per cue track so each cue
     * track becomes a separate playable library entry.
     */
    private data class CueFileInfo(
        val fileName: String,
        val discNumber: Int?,
        val albumTitle: String?,
        val performer: String?,
        val tracks: List<CueTrackInfo>,
    )

    /**
     * One cue track declared inside a `FILE` block, including the start offset
     * and title needed to split a single-file album into separate playable tracks.
     */
    private data class CueTrackInfo(
        val number: Int,
        val title: String?,
        val startOffsetMs: Long,
    )
}
