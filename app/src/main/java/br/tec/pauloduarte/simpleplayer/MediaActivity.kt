package br.tec.pauloduarte.simpleplayer

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import br.tec.pauloduarte.simpleplayer.data.AppDatabase
import br.tec.pauloduarte.simpleplayer.data.Media
import br.tec.pauloduarte.simpleplayer.data.PlayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.HttpURLConnection
import java.net.URL

class MediaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var searchView: SearchView
    private lateinit var progressBar: ProgressBar
    private lateinit var playlist: PlayList
    private var playlistId: Int = -1

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "playlist-db"
        ).fallbackToDestructiveMigration().build()
    }

    private val mediaViewModel: MediaViewModel by viewModels {
        MediaViewModelFactory(db.mediaDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        progressBar = findViewById(R.id.progressBar)

        playlistId = intent.getIntExtra("PLAYLIST_ID", -1)
        if (playlistId == -1) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            playlist = db.playListDao().loadAllByIds(intArrayOf(playlistId)).first()
            withContext(Dispatchers.Main) {
                supportActionBar?.title = playlist.name
            }
        }

        setupRecyclerView()
        setupSearchView()
        observeMedias()
        loadMediasIfNeeded()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewMedia)
        mediaAdapter = MediaAdapter { media ->
            handleMediaClick(media)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = mediaAdapter
    }

    private fun setupSearchView() {
        searchView = findViewById(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    performSearch(it)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    observeMedias()
                } else {
                    performSearch(newText)
                }
                return true
            }
        })
    }

    private fun observeMedias() {
        lifecycleScope.launch {
            mediaViewModel.getMedias(playlistId).collectLatest { pagingData ->
                mediaAdapter.submitData(pagingData)
            }
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            mediaViewModel.searchMedias(playlistId, query).collectLatest { pagingData ->
                mediaAdapter.submitData(pagingData)
            }
        }
    }


    private fun loadMediasIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mediaCount = db.mediaDao().getMediaCountForPlaylist(playlistId)
            if (mediaCount == 0) {
                if (playlist.pathOrUrl?.startsWith("http") == true) {
                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.textViewMessage).apply {
                            text = "Funcionalidade de playlists remotas em breve!"
                            visibility = View.VISIBLE
                        }
                    }
                } else {
                    loadMediasFromLocalFile()
                }
            }
        }
    }


    private fun handleMediaClick(media: Media) {
        val url = media.url
        if (url != null) {
            if (url.endsWith(".mp4", ignoreCase = true) || url.endsWith(".mkv", ignoreCase = true)) {
                showPlayDownloadDialog(media)
            } else {
                showPlayDialog(media)
            }
        } else {
            Toast.makeText(this, "URL da mídia inválida.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlayDialog(media: Media) {
        AlertDialog.Builder(this)
            .setTitle(media.name)
            .setMessage("Deseja reproduzir este conteúdo?")
            .setPositiveButton("Reproduzir") { _, _ ->
                playMedia(media)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPlayDownloadDialog(media: Media) {
        val options = arrayOf("Play", "Download")
        AlertDialog.Builder(this)
            .setTitle(media.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playMedia(media)
                    1 -> downloadMedia(media)
                }
            }
            .show()
    }

    private fun playMedia(media: Media) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val finalUrl = resolveRedirectedUrl(media.url!!)
            progressBar.visibility = View.GONE
            val intent = Intent(this@MediaActivity, VideoPlayerActivity::class.java)
            intent.putExtra("VIDEO_URL", finalUrl)
            startActivity(intent)
        }
    }

    private fun downloadMedia(media: Media) {
        if (media.url.isNullOrBlank()) {
            Toast.makeText(this@MediaActivity, "Erro: URL da mídia é inválida.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val finalUrl = resolveRedirectedUrl(media.url)
            progressBar.visibility = View.GONE

            try {
                val extension = finalUrl.substringBefore('?').substringAfterLast('.', "")

                val baseFileName = media.name?.trim() ?: "mediafile"

                val finalFileName = if (extension.isNotBlank()) {
                    "$baseFileName.$extension"
                } else {
                    baseFileName
                }

                val request = DownloadManager.Request(Uri.parse(finalUrl))
                    .setTitle(media.name)
                    .setDescription("Baixando mídia...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)

                Toast.makeText(this@MediaActivity, "Download iniciado...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MediaActivity, "Erro ao iniciar download: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun resolveRedirectedUrl(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                var connection = URL(url).openConnection() as HttpURLConnection
                // A maioria dos servidores de streaming precisa de um User-Agent.
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.instanceFollowRedirects = true
                connection.connect()

                var finalUrl = connection.url.toString()

                // Lida com redirecionamentos manuais (código 3xx) se instanceFollowRedirects não funcionar
                var redirects = 0
                while (redirects < 5 && (connection.responseCode in 300..399)) {
                    finalUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    connection = URL(finalUrl).openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connect()
                    redirects++
                }

                connection.disconnect()
                finalUrl
            } catch (e: Exception) {
                e.printStackTrace()
                // Se houver erro, retorna a URL original para tentar a sorte.
                url
            }
        }
    }


    private fun loadMediasFromLocalFile() {
        lifecycleScope.launch(Dispatchers.IO) {
            val medias = mutableListOf<Media>()
            try {
                val file = playlist.pathOrUrl?.let { File(it) }
                if (file?.exists() == true) {
                    BufferedReader(FileReader(file)).use { br ->
                        var line: String?
                        var mediaName: String? = null
                        var mediaGroup: String? = null
                        while (br.readLine().also { line = it } != null) {
                            if (line!!.startsWith("#EXTINF")) {
                                val nameMatch = Regex("tvg-name=\"([^\"]*)\"").find(line!!)
                                val groupMatch = Regex("group-title=\"([^\"]*)\"").find(line!!)
                                mediaName = nameMatch?.groups?.get(1)?.value ?: line!!.substringAfter(",")
                                mediaGroup = groupMatch?.groups?.get(1)?.value
                            } else if (line!!.isNotBlank() && !line!!.startsWith("#")) {
                                if (mediaName != null) {
                                    medias.add(
                                        Media(
                                            playlistId = playlistId,
                                            name = mediaName,
                                            groupName = mediaGroup,
                                            url = line
                                        )
                                    )
                                    mediaName = null
                                    mediaGroup = null
                                }
                            }
                        }
                    }
                    db.mediaDao().insertAll(*medias.toTypedArray())
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MediaActivity, "Arquivo da playlist não encontrado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MediaActivity, "Erro ao ler a playlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}