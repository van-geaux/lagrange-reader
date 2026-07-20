package com.bookorbit.android

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

internal sealed interface ReadiumEpubOpenResult {
    data class Opened(val publication: Publication) : ReadiumEpubOpenResult
    data class Error(val message: String) : ReadiumEpubOpenResult
}

internal suspend fun openReadiumEpub(
    context: Context,
    file: File
): ReadiumEpubOpenResult = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L) {
        return@withContext ReadiumEpubOpenResult.Error("The EPUB file is unavailable.")
    }

    val appContext = context.applicationContext
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val assetResult = assetRetriever.retrieve(file, MediaType.EPUB)
    val asset = assetResult.getOrNull()
        ?: return@withContext ReadiumEpubOpenResult.Error("Readium could not read this EPUB file.")
    val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null
    )
    val publicationResult = PublicationOpener(publicationParser).open(
        asset = asset,
        allowUserInteraction = false
    )
    val publication = publicationResult.getOrNull()
        ?: return@withContext ReadiumEpubOpenResult.Error("Readium could not open this EPUB publication.")
    if (!publication.conformsTo(Publication.Profile.EPUB)) {
        publication.close()
        return@withContext ReadiumEpubOpenResult.Error("Readium did not recognize this file as an EPUB publication.")
    }
    ReadiumEpubOpenResult.Opened(publication)
}

class ReadiumEpubPreviewActivity : FragmentActivity() {
    private var publication: Publication? = null
    private var progressView: ProgressBar? = null
    private var readerContainerId: Int = View.NO_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }

        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        readerContainerId = View.generateViewId()
        root.addView(
            FrameLayout(this).apply { id = readerContainerId },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        progressView = ProgressBar(this).also { progress ->
            root.addView(
                progress,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
        setContentView(root)

        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        if (file == null) {
            showError("The EPUB preview file is unavailable.")
            return
        }
        lifecycleScope.launch {
            when (val result = openReadiumEpub(this@ReadiumEpubPreviewActivity, file)) {
                is ReadiumEpubOpenResult.Error -> showError(result.message)
                is ReadiumEpubOpenResult.Opened -> showPublication(result.publication)
            }
        }
    }

    private fun showPublication(openedPublication: Publication) {
        if (isFinishing || isDestroyed) {
            openedPublication.close()
            return
        }
        publication = openedPublication
        val navigatorFactory = EpubNavigatorFactory(openedPublication)
        val fragmentFactory = navigatorFactory.createFragmentFactory(initialLocator = null)
        supportFragmentManager.fragmentFactory = fragmentFactory
        val fragment = fragmentFactory.instantiate(
            classLoader,
            EpubNavigatorFragment::class.java.name
        ) as EpubNavigatorFragment
        supportFragmentManager.beginTransaction()
            .replace(readerContainerId, fragment, NAVIGATOR_TAG)
            .commitNow()
        fragment.addInputListener(DirectionalNavigationAdapter(fragment))
        progressView?.visibility = View.GONE
    }

    private fun showError(message: String) {
        progressView?.visibility = View.GONE
        val root = findViewById<FrameLayout>(android.R.id.content)
        root.addView(
            TextView(this).apply {
                text = "$message\n\nPress Back to return."
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
        publication = null
    }

    companion object {
        private const val EXTRA_FILE_PATH = "readium_epub_file_path"
        private const val EXTRA_TITLE = "readium_epub_title"
        private const val NAVIGATOR_TAG = "readium_epub_navigator"

        fun createIntent(context: Context, file: File, title: String): Intent =
            Intent(context, ReadiumEpubPreviewActivity::class.java)
                .putExtra(EXTRA_FILE_PATH, file.absolutePath)
                .putExtra(EXTRA_TITLE, title)
    }
}
