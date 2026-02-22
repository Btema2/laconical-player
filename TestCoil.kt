import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.decode.DataSource
import coil3.request.Options

class AudioAlbumArtFetcher(
    private val uri: Uri,
    private val context: Context,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            if (picture != null) {
                // Return a ByteBufferFetcher? No, in Coil 3...
            }
        } finally {
            retriever.release()
        }
        return null
    }
}
