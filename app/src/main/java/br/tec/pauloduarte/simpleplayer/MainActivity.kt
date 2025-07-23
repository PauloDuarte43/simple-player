package br.tec.pauloduarte.simpleplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import br.tec.pauloduarte.simpleplayer.data.AppDatabase
import br.tec.pauloduarte.simpleplayer.data.PlayList
import br.tec.pauloduarte.simpleplayer.data.PlayListDao
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var playListAdapter: PlayListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var playListDao: PlayListDao

    private val addPlaylistLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val name = data?.getStringExtra("NEW_PLAYLIST_NAME")
            val pathOrUrl = data?.getStringExtra("NEW_PLAYLIST_PATH_OR_URL")
            var description = data?.getStringExtra("NEW_PLAYLIST_DESCRIPTION")

            // if not description, check if pathOrUrl is a file path then get the file name
            if (description == null && pathOrUrl != null) {
                val fileName = pathOrUrl.substringAfterLast('/')
                Log.d("MainActivity", "File name extracted: $fileName")
                // Use the file name as the description
                description = fileName
            }

            if (name != null && pathOrUrl != null) {
                val newPlaylist = PlayList(name = name, pathOrUrl = pathOrUrl, description = description)
                playListDao.insertAll(newPlaylist)
                loadPlaylists()
                Snackbar.make(recyclerView, "Playlist '$name' adicionada!", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        val db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "playlist-db").allowMainThreadQueries().build()
        playListDao = db.playListDao()

        if (playListDao.getAll().isEmpty()) {
            playListDao.insertAll(
                PlayList(name = "Lista Exemplo", pathOrUrl = "https://example.com/acao.m3u", description = "https://example.com/br.m3u"),
            )
        }

        recyclerView = findViewById(R.id.recyclerView)
        setupRecyclerView()
        loadPlaylists()

        setupBottomNavigation()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.selectedItemId = R.id.nav_home
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.selectedItemId = R.id.nav_home

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_add -> {
                    val intent = Intent(this, AddPlaylistActivity::class.java)
                    addPlaylistLauncher.launch(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        playListAdapter = PlayListAdapter(
            playlists = mutableListOf(),
            onPlayClick = {
                Snackbar.make(recyclerView, "Playing: ${it.name} (${it.pathOrUrl})", Snackbar.LENGTH_SHORT).show()
            },
            onDeleteClick = { playlist ->
                showDeleteConfirmationDialog(playlist)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = playListAdapter
    }

    private fun loadPlaylists() {
        val playlists = playListDao.getAll()
        playListAdapter.updateData(playlists)
    }

    private fun showDeleteConfirmationDialog(playlist: PlayList) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Exclusão")
            .setMessage("Tem certeza que deseja remover '${playlist.name}'?")
            .setPositiveButton("Sim") { _, _ ->
                playListDao.delete(playlist)
                if (playlist.pathOrUrl != null && playlist.pathOrUrl.startsWith("file://")) {
                    val file = java.io.File(playlist.pathOrUrl.removePrefix("file://"))
                    if (file.exists()) {
                        file.delete()
                    }
                }
                loadPlaylists()
            }
            .setNegativeButton("Não", null)
            .show()
    }
}