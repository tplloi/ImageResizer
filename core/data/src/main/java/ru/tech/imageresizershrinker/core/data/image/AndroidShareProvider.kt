/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2024 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package ru.tech.imageresizershrinker.core.data.image

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import ru.tech.imageresizershrinker.core.domain.dispatchers.DispatchersHolder
import ru.tech.imageresizershrinker.core.domain.image.ImageCompressor
import ru.tech.imageresizershrinker.core.domain.image.ImageGetter
import ru.tech.imageresizershrinker.core.domain.image.ShareProvider
import ru.tech.imageresizershrinker.core.domain.image.model.ImageInfo
import ru.tech.imageresizershrinker.core.domain.saving.ImageFilenameProvider
import ru.tech.imageresizershrinker.core.domain.saving.model.ImageSaveTarget
import ru.tech.imageresizershrinker.core.resources.R
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

internal class AndroidShareProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageGetter: ImageGetter<Bitmap, ExifInterface>,
    private val imageCompressor: ImageCompressor<Bitmap>,
    private val imageFilenameProvider: Lazy<ImageFilenameProvider>,
    dispatchersHolder: DispatchersHolder
) : DispatchersHolder by dispatchersHolder, ShareProvider<Bitmap> {

    override suspend fun shareImages(
        uris: List<String>,
        imageLoader: suspend (String) -> Pair<Bitmap, ImageInfo>?,
        onProgressChange: (Int) -> Unit
    ) = withContext(ioDispatcher) {
        val cachedUris = uris.mapIndexedNotNull { index, uri ->
            imageLoader(uri)?.let { (image, imageInfo) ->
                cacheImage(
                    image = image,
                    imageInfo = imageInfo
                )?.also {
                    onProgressChange(index + 1)
                }
            }
        }
        onProgressChange(-1)
        shareUris(cachedUris)
    }

    override suspend fun cacheImage(
        image: Bitmap,
        imageInfo: ImageInfo
    ): String? = withContext(ioDispatcher) {
        runCatching {
            val saveTarget = ImageSaveTarget<ExifInterface>(
                imageInfo = imageInfo,
                originalUri = "share",
                sequenceNumber = null,
                data = byteArrayOf()
            )

            val filename = imageFilenameProvider.get().constructImageFilename(saveTarget)
            val byteArray = imageCompressor.compressAndTransform(image, imageInfo)

            cacheByteArray(
                byteArray = byteArray,
                filename = filename
            )

        }.getOrNull()
    }

    override suspend fun shareImage(
        imageInfo: ImageInfo,
        image: Bitmap,
        onComplete: () -> Unit
    ) = withContext(ioDispatcher) {
        cacheImage(
            image = image,
            imageInfo = imageInfo
        )?.let {
            shareUri(
                uri = it,
                type = imageInfo.imageFormat.type
            )
        }
        onComplete()
    }

    override suspend fun shareUri(
        uri: String,
        type: String?
    ) = withContext(defaultDispatcher) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.type = type ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(
                    imageGetter.getExtension(uri)
                ) ?: "*/*"
        }
        val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share))
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    override suspend fun shareUris(
        uris: List<String>
    ) = shareImageUris(uris.map { it.toUri() })

    private suspend fun shareImageUris(
        uris: List<Uri>
    ) = withContext(defaultDispatcher) {
        if (uris.isEmpty()) return@withContext

        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            type = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(
                    imageGetter.getExtension(uris.first().toString())
                ) ?: "*/*"
        }
        val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share))
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    override suspend fun cacheByteArray(
        byteArray: ByteArray,
        filename: String
    ): String? = withContext(ioDispatcher) {
        val imagesFolder = File(context.cacheDir, "files")

        runCatching {
            imagesFolder.mkdirs()
            val file = File(imagesFolder, filename)
            FileOutputStream(file).use {
                it.write(byteArray)
            }
            FileProvider.getUriForFile(context, context.getString(R.string.file_provider), file)
                .also { uri ->
                    runCatching {
                        context.grantUriPermission(
                            context.packageName,
                            uri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
        }.getOrNull()?.toString()
    }

    override suspend fun shareByteArray(
        byteArray: ByteArray,
        filename: String,
        onComplete: () -> Unit
    ) = withContext(ioDispatcher) {
        cacheByteArray(
            byteArray = byteArray,
            filename = filename
        )?.let {
            shareUri(
                uri = it,
                type = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(
                        imageGetter.getExtension(it)
                    ) ?: "*/*"
            )
        }
        onComplete()
    }

}