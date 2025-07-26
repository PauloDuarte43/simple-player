package br.tec.pauloduarte.simpleplayer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import br.tec.pauloduarte.simpleplayer.data.AppDatabase
import kotlinx.coroutines.launch

class GroupActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var groupAdapter: GroupAdapter
    private var playlistId: Int = -1

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "playlist-db"
        ).build()
    }

    private val groupViewModel: GroupViewModel by viewModels {
        GroupViewModelFactory(db.mediaDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Grupos"

        playlistId = intent.getIntExtra("PLAYLIST_ID", -1)
        if (playlistId == -1) {
            finish()
            return
        }

        setupRecyclerView()
        observeGroups()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewGroups)
        groupAdapter = GroupAdapter { groupName ->
            val intent = Intent(this, MediaActivity::class.java)
            intent.putExtra("PLAYLIST_ID", playlistId)
            intent.putExtra("GROUP_NAME", groupName)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = groupAdapter
    }

    private fun observeGroups() {
        lifecycleScope.launch {
            groupViewModel.getGroups(playlistId).collect { groups ->
                val groupList = mutableListOf("Todos")
                groupList.addAll(groups.filterNotNull())
                groupAdapter.submitList(groupList)
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