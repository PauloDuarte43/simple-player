package br.tec.pauloduarte.simpleplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AddPlaylistActivity : AppCompatActivity() {

    private lateinit var editTextName: TextInputEditText
    private lateinit var radioGroupType: RadioGroup

    // Campos de URL
    private lateinit var layoutUrl: TextInputLayout
    private lateinit var editTextUrl: TextInputEditText

    // Campos de Arquivo
    private lateinit var layoutFile: LinearLayout
    private lateinit var buttonSelectFile: Button
    private lateinit var textViewFileName: TextView

    private lateinit var buttonSave: Button
    private lateinit var loadingOverlay: FrameLayout

    private var selectedFileUri: Uri? = null

    // Lançador para o seletor de arquivos
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            // Exibe o nome do arquivo selecionado na UI
            textViewFileName.text = getFileName(uri)
            textViewFileName.visibility = View.VISIBLE
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_playlist)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bindViews()
        setupListeners()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.selectedItemId = R.id.nav_add // Marca o item correto ao iniciar

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Volta para a tela principal
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)
                    true
                }
                R.id.nav_add -> {
                    // Já estamos aqui, não faz nada
                    true
                }
                else -> false
            }
        }
    }

    private fun bindViews() {
        editTextName = findViewById(R.id.editTextName)
        radioGroupType = findViewById(R.id.radioGroupType)
        layoutUrl = findViewById(R.id.layoutUrl)
        editTextUrl = findViewById(R.id.editTextUrl)
        layoutFile = findViewById(R.id.layoutFile)
        buttonSelectFile = findViewById(R.id.buttonSelectFile)
        textViewFileName = findViewById(R.id.textViewFileName)
        buttonSave = findViewById(R.id.buttonSave)
        loadingOverlay = findViewById(R.id.loadingOverlay)
    }

    private fun setupListeners() {
        // Altera a UI com base na seleção do RadioButton
        radioGroupType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioUrl) {
                layoutUrl.visibility = View.VISIBLE
                layoutFile.visibility = View.GONE
                selectedFileUri = null // Limpa a seleção de arquivo
                textViewFileName.text = ""
            } else {
                layoutUrl.visibility = View.GONE
                layoutFile.visibility = View.VISIBLE
                editTextUrl.text = null // Limpa o campo de url
            }
        }

        // Abre o seletor de arquivos
        buttonSelectFile.setOnClickListener {
            // Você pode especificar o tipo de arquivo, ex: "*/*" para todos os tipos
            filePickerLauncher.launch("*/*")
        }

//        buttonSave.setOnClickListener {
//            savePlaylist()
//        }
        buttonSave.setOnClickListener {
            // A função agora apenas inicia o processo na coroutine
            // e não faz o trabalho pesado diretamente.
            startSaveProcess()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        buttonSave.isEnabled = !isLoading // Desabilita o botão para evitar cliques duplos
    }

    private fun startSaveProcess() {
        val name = editTextName.text.toString()
        if (name.isBlank()) {
            Toast.makeText(this, "Por favor, insira um nome para a playlist", Toast.LENGTH_SHORT).show()
            return
        }

        // Mostra o loading e desabilita o botão
        showLoading(true)

        // Inicia a coroutine no escopo do ciclo de vida da Activity
        lifecycleScope.launch {
            // A função savePlaylist agora é uma 'suspend fun'
            savePlaylistInBackground(name)
        }
    }

    private suspend fun savePlaylistInBackground(name: String) {
        var pathOrUrl: String?
        var description: String?

        if (radioGroupType.checkedRadioButtonId == R.id.radioUrl) {
            val url = editTextUrl.text.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                pathOrUrl = url
                description = url // Usa a URL como descrição
            } else {
                // Se der erro, volta para a Main Thread para mostrar o erro e esconder o loading
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    editTextUrl.error = "URL inválida. Deve iniciar com http:// ou https://"
                }
                return // Encerra a coroutine
            }
        } else {
            val localUri = selectedFileUri
            if (localUri == null) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@AddPlaylistActivity, "Por favor, selecione um arquivo", Toast.LENGTH_SHORT).show()
                }
                return
            }

            pathOrUrl = withContext(Dispatchers.IO) {
                copyFileToInternalStorage(localUri)
            }

            if (pathOrUrl == null) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@AddPlaylistActivity, "Falha ao salvar o arquivo", Toast.LENGTH_SHORT).show()
                }
                return
            }
            description = getFileName(localUri)
        }

        // Após o sucesso, volta para a Main Thread para finalizar a activity
        withContext(Dispatchers.Main) {
            val resultIntent = Intent()
            resultIntent.putExtra("NEW_PLAYLIST_NAME", name)
            resultIntent.putExtra("NEW_PLAYLIST_DESCRIPTION", description)
            resultIntent.putExtra("NEW_PLAYLIST_PATH_OR_URL", pathOrUrl)

            setResult(Activity.RESULT_OK, resultIntent)
            showLoading(false) // Esconde o loading antes de fechar
            finish()
        }
    }

    private fun savePlaylist() {
        val name = editTextName.text.toString()
        if (name.isBlank()) {
            Toast.makeText(this, "Por favor, insira um nome para a playlist", Toast.LENGTH_SHORT).show()
            return
        }

        val resultIntent = Intent()
        resultIntent.putExtra("NEW_PLAYLIST_NAME", name)

        // Verifica qual modo está selecionado
        if (radioGroupType.checkedRadioButtonId == R.id.radioUrl) {
            val url = editTextUrl.text.toString()
            // Valida se a URL é válida
            if (url.startsWith("http://") || url.startsWith("https://")) {
                resultIntent.putExtra("NEW_PLAYLIST_PATH_OR_URL", url)
                resultIntent.putExtra("NEW_PLAYLIST_DESCRIPTION", url)
            } else {
                editTextUrl.error = "URL inválida. Deve iniciar com http:// ou https://"
                return
            }
        } else {
            // Se for arquivo, verifica se um foi selecionado
            if (selectedFileUri == null) {
                Toast.makeText(this, "Por favor, selecione um arquivo", Toast.LENGTH_SHORT).show()
                return
            }
            // Copia o arquivo para o armazenamento interno e pega o novo caminho
            val internalPath = copyFileToInternalStorage(selectedFileUri!!)
            if (internalPath == null) {
                Toast.makeText(this, "Falha ao salvar o arquivo", Toast.LENGTH_SHORT).show()
                return
            }
            resultIntent.putExtra("NEW_PLAYLIST_PATH_OR_URL", internalPath)
            resultIntent.putExtra("NEW_PLAYLIST_DESCRIPTION", getFileName(selectedFileUri!!))
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    // Função que faz a cópia do arquivo
    private fun copyFileToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileName(uri)
            val file = File(filesDir, fileName) // filesDir é o diretório interno privado do app
            val outputStream = FileOutputStream(file)

            // Copia os bytes do arquivo de entrada para o de saída
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath // Retorna o caminho do arquivo copiado
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Função auxiliar para tentar obter o nome original do arquivo a partir da URI
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = cursor.getString(columnIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "playlist_${System.currentTimeMillis()}"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}