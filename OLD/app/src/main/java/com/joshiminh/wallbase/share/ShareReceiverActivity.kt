package com.joshiminh.wallbase.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.joshiminh.wallbase.MainActivity
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.LibraryRepository.DirectAddResult
import com.joshiminh.wallbase.util.network.ServiceLocator
import java.util.LinkedHashSet
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    private val libraryRepository: LibraryRepository by lazy { ServiceLocator.libraryRepository }
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.ensureInitialized(applicationContext)
        processShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processShareIntent(intent)
    }

    private fun processShareIntent(intent: Intent?) {
        if (intent == null || isProcessing) {
            finish()
            return
        }
        isProcessing = true
        lifecycleScope.launch {
            val outcome = handleShareIntent(intent)
            Toast.makeText(this@ShareReceiverActivity, outcome.message, Toast.LENGTH_LONG).show()
            if (outcome.bringToFront) {
                startActivity(
                    Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
            }
            finish()
        }
    }

    private suspend fun handleShareIntent(intent: Intent): ShareOutcome {
        return when (intent.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent)
            else -> ShareOutcome("Content not supported.", bringToFront = false)
        }
    }

    private suspend fun handleSingleShare(intent: Intent): ShareOutcome {
        val uris = mutableListOf<Uri>()
        intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)?.let(uris::add)
        uris += extractClipUris(intent.clipData)
        if (uris.isNotEmpty()) {
            return importSharedUris(uris)
        }

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)
        val url = extractFirstUrl(text)
        if (url != null) {
            return addDirectFromShare(url)
        }
        return ShareOutcome("No shareable wallpapers were found.", bringToFront = false)
    }

    private suspend fun handleMultipleShare(intent: Intent): ShareOutcome {
        val uris = mutableListOf<Uri>()
        intent.getParcelableArrayListExtraCompat(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        uris += extractClipUris(intent.clipData)
        if (uris.isEmpty()) {
            return ShareOutcome("No images were shared.", bringToFront = false)
        }
        return importSharedUris(uris)
    }

    private suspend fun importSharedUris(candidates: List<Uri>): ShareOutcome {
        val unique = LinkedHashSet<Uri>().apply { addAll(candidates) }.toList()
        if (unique.isEmpty()) {
            return ShareOutcome("No images were shared.", bringToFront = false)
        }
        val result = libraryRepository.importLocalWallpapers(unique)
        val message = when {
            result.imported > 0 && result.skipped > 0 ->
                "Imported ${result.imported} wallpapers (skipped ${result.skipped})."
            result.imported > 0 ->
                if (result.imported == 1) "Added 1 wallpaper to your library." else "Added ${result.imported} wallpapers to your library."
            result.skipped > 0 -> "All shared images were already in your library."
            else -> "No images were added."
        }
        val bringToFront = result.imported > 0 || result.skipped > 0
        return ShareOutcome(message, bringToFront)
    }

    private suspend fun addDirectFromShare(url: String): ShareOutcome {
        return when (val result = libraryRepository.addDirectWallpaper(url)) {
            is DirectAddResult.Success -> {
                val title = result.wallpaper.title.takeIf { it.isNotBlank() } ?: "Wallpaper"
                ShareOutcome("Added \"$title\" to your library.", bringToFront = true)
            }

            is DirectAddResult.AlreadyExists -> {
                val title = result.wallpaper?.title?.takeIf { it.isNotBlank() } ?: "Wallpaper"
                ShareOutcome("\"$title\" is already in your library.", bringToFront = true)
            }

            is DirectAddResult.Failure -> ShareOutcome(result.reason, bringToFront = false)
        }
    }

    private fun extractClipUris(clipData: ClipData?): List<Uri> {
        if (clipData == null) return emptyList()
        val uris = mutableListOf<Uri>()
        for (index in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(index)
            val uri = item.uri
            if (uri != null) {
                uris += uri
            }
        }
        return uris
    }

    private fun extractFirstUrl(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group().trim() else null
    }

    private data class ShareOutcome(val message: String, val bringToFront: Boolean)

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Uri? =
        getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableArrayListExtraCompat(name: String): ArrayList<Uri>? =
        getParcelableArrayListExtra(name)
}