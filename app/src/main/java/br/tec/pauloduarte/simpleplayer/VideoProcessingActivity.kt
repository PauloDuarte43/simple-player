package br.tec.pauloduarte.simpleplayer

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@UnstableApi
class VideoProcessingActivity : AppCompatActivity() {

    // Views
    private lateinit var textViewVideoInfo: TextView
    private lateinit var editTextStartTime: TextInputEditText
    private lateinit var editTextNumberOfCuts: TextInputEditText
    private lateinit var editTextMinDuration: TextInputEditText
    private lateinit var editTextMaxDuration: TextInputEditText
    private lateinit var buttonProcessVideo: Button
    private lateinit var buttonSelectVideo: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewStatus: TextView
    private lateinit var layoutProgress: LinearLayout
    private lateinit var recyclerViewOutputFiles: RecyclerView

    // Processamento
    private var selectedVideoUri: Uri? = null
    private var videoDuration: Long = 0
    private lateinit var transformer: Transformer
    private val cutQueue = mutableListOf<Pair<Long, Long>>() // <StartMs, EndMs>
    private var currentCutIndex = 0
    private var currentTempPath: String? = null

    // Lista de Arquivos
    private lateinit var fileAdapter: FileListAdapter
    private val finalOutputUris = mutableListOf<Uri>()

    // Verificador de Progresso
    private val progressHandler = Handler(Looper.getMainLooper())
    private lateinit var progressChecker: Runnable
    private lateinit var progressHolder: ProgressHolder

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedVideoUri = uri
                // Permissão para ler o arquivo selecionado
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                displayVideoInfo(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_processing)
        bindViews()
        setupRecyclerView()
        setupTransformer()
        setupProgressChecker()

        buttonSelectVideo.setOnClickListener { openVideoPicker() }
        buttonProcessVideo.setOnClickListener { prepareAndProcessVideo() }
    }

    private fun bindViews() {
        textViewVideoInfo = findViewById(R.id.textViewVideoInfo)
        editTextStartTime = findViewById(R.id.editTextStartTime)
        editTextNumberOfCuts = findViewById(R.id.editTextNumberOfCuts)
        editTextMinDuration = findViewById(R.id.editTextMinDuration)
        editTextMaxDuration = findViewById(R.id.editTextMaxDuration)
        buttonProcessVideo = findViewById(R.id.buttonProcessVideo)
        buttonSelectVideo = findViewById(R.id.buttonSelectVideo)
        progressBar = findViewById(R.id.progressBar)
        textViewStatus = findViewById(R.id.textViewStatus)
        layoutProgress = findViewById(R.id.layoutProgress)
        recyclerViewOutputFiles = findViewById(R.id.recyclerViewOutputFiles)
    }

    // ALTERADO: O adapter agora é inicializado com a lista de Uris
    private fun setupRecyclerView() {
        fileAdapter = FileListAdapter(finalOutputUris) { uri -> openFile(uri) }
        recyclerViewOutputFiles.apply {
            layoutManager = LinearLayoutManager(this@VideoProcessingActivity)
            adapter = fileAdapter
        }
    }

    private fun setupTransformer() {
        transformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(transformerListener)
            .build()
    }

    private val transformerListener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            val tempPath = currentTempPath ?: return

            // Inicia uma coroutine que está ciente do ciclo de vida da UI
            lifecycleScope.launch {
                try {
                    // 1. Atualiza a UI na thread principal
                    withContext(Dispatchers.Main) {
                        textViewStatus.text = "Salvando na galeria..."
                    }

                    // 2. Chama a função suspensa. Ela executará em Dispatchers.IO (background)
                    // Passe o applicationContext para segurança
                    val finalUri = copyFileToMediaStore(applicationContext, File(tempPath))
                    File(tempPath).delete()

                    // 3. Volta para a thread principal para atualizar a UI com o resultado
                    withContext(Dispatchers.Main) {
                        if (finalUri != null) {
                            finalOutputUris.add(finalUri)
                            fileAdapter.notifyItemInserted(finalOutputUris.size - 1)
                            recyclerViewOutputFiles.scrollToPosition(finalOutputUris.size - 1)
                        } else {
                            textViewStatus.text = "Falha ao salvar o vídeo na galeria."
                            Toast.makeText(applicationContext, "Falha ao salvar o vídeo.", Toast.LENGTH_SHORT).show()
                        }

                        // Avança a fila
                        progressBar.progress = currentCutIndex + 1
                        currentCutIndex++
                        processNextCutInQueue()
                    }

                } catch (e: Exception) {
                    Log.e("VideoProcessingError", "Erro fatal na coroutine de cópia", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Erro crítico ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
                        resetUiState(isError = true)
                    }
                }
            }
        }

        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
            // Limpa o arquivo temporário em caso de erro
            currentTempPath?.let { File(it).delete() }
            runOnUiThread {
                Toast.makeText(applicationContext, "Erro na transformação: ${exportException.message}", Toast.LENGTH_LONG).show()
                resetUiState(isError = true)
            }
        }
    }

    private fun setupProgressChecker() {
        progressHolder = ProgressHolder()
        progressChecker = Runnable {
            if (!buttonProcessVideo.isEnabled) {
                val progressState = transformer.getProgress(progressHolder)
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val progressPercentage = progressHolder.progress
                    textViewStatus.text = "Processando corte ${currentCutIndex + 1} de ${cutQueue.size} ($progressPercentage%)"
                }
                progressHandler.postDelayed(progressChecker, 500)
            }
        }
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        videoPickerLauncher.launch(intent)
    }

    private fun displayVideoInfo(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val minutes = TimeUnit.MILLISECONDS.toMinutes(videoDuration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(videoDuration) - TimeUnit.MINUTES.toSeconds(minutes)
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(videoDuration)
            textViewVideoInfo.text = "Vídeo selecionado!\nDuração: ${minutes}m ${seconds}s\nTotal: ${totalSeconds}s"
        } catch (e: Exception) {
            textViewVideoInfo.text = "Erro ao ler informações do vídeo."
            e.printStackTrace()
        } finally {
            retriever.release()
        }
    }

    private fun prepareAndProcessVideo() {
        if (selectedVideoUri == null) {
            Toast.makeText(this, "Por favor, selecione um vídeo.", Toast.LENGTH_SHORT).show()
            return
        }

        val startTimeInput = editTextStartTime.text.toString().toLongOrNull()
        val numberOfCuts = editTextNumberOfCuts.text.toString().toIntOrNull() ?: 1
        val minDurationSec = editTextMinDuration.text.toString().toIntOrNull() ?: 30
        val maxDurationSec = editTextMaxDuration.text.toString().toIntOrNull() ?: 70

        if (videoDuration < (maxDurationSec * 1000)) {
            Toast.makeText(this, "A duração do vídeo é menor que a duração máxima do corte.", Toast.LENGTH_LONG).show()
            return
        }

        buttonProcessVideo.isEnabled = false
        buttonSelectVideo.isEnabled = false
        layoutProgress.visibility = View.VISIBLE
        finalOutputUris.clear()
        fileAdapter.notifyDataSetChanged()

        cutQueue.clear()
        val safeMaxStartTimeSec = (videoDuration / 1000) - maxDurationSec
        var nextCutStartSec = startTimeInput ?: Random.nextLong(0, if (safeMaxStartTimeSec > 0) safeMaxStartTimeSec else 0)

        for (i in 1..numberOfCuts) {
            val cutDurationSec = Random.nextInt(minDurationSec, maxDurationSec + 1).toLong()
            val startMs = nextCutStartSec * 1000
            val endMs = startMs + (cutDurationSec * 1000)

            if (endMs > videoDuration) {
                Toast.makeText(this, "Fim do vídeo alcançado. Menos cortes serão gerados.", Toast.LENGTH_LONG).show()
                break
            }
            cutQueue.add(Pair(startMs, endMs))
            nextCutStartSec += cutDurationSec
        }

        if (cutQueue.isEmpty()) {
            Toast.makeText(this, "Nenhum corte válido para processar.", Toast.LENGTH_SHORT).show()
            resetUiState()
            return
        }

        progressBar.progress = 0
        progressBar.max = cutQueue.size
        currentCutIndex = 0
        progressHandler.post(progressChecker)
        processNextCutInQueue()
    }

    // ALTERADO: Agora salva em um arquivo temporário no cache do app
    private fun processNextCutInQueue() {
        if (currentCutIndex >= cutQueue.size) {
            resetUiState()
            return
        }

        selectedVideoUri?.let { uri ->
            try {
                val (startMs, endMs) = cutQueue[currentCutIndex]
                textViewStatus.text = "Iniciando corte ${currentCutIndex + 1} de ${cutQueue.size}..."

                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs).setEndPositionMs(endMs).build()
                    ).build()

                // NOVO: Define o caminho de saída para o diretório de cache interno do app
                val tempFile = File(cacheDir, "temp_cut_${System.currentTimeMillis()}.mp4")
                this.currentTempPath = tempFile.absolutePath

                // Inicia o transformer com o caminho do arquivo temporário. Isso sempre funciona.
                transformer.start(mediaItem, currentTempPath!!)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Erro ao iniciar o processamento: ${e.message}", Toast.LENGTH_SHORT).show()
                resetUiState(isError = true)
            }
        }
    }

    // Adicione o parâmetro 'context' e use applicationContext para obter o resolver
// A função continua sendo 'suspend'
    private suspend fun copyFileToMediaStore(context: Context, sourceFile: File): Uri? {
        return withContext(Dispatchers.IO) {
            Log.d("VideoProcessingDebug", "Executando em: ${Thread.currentThread().name}") // Para depuração

            // Use o applicationContext para garantir a segurança contra memory leaks
            val resolver = context.applicationContext.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name.replace("temp_", ""))
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            var finalUri: Uri? = null
            try {
                Log.d("VideoProcessingDebug", "Tentando inserir no MediaStore...")
                finalUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                Log.d("VideoProcessingDebug", "MediaStore insert result: $finalUri")

                if (finalUri != null) {
                    resolver.openOutputStream(finalUri).use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            if (outputStream != null) {
                                inputStream.copyTo(outputStream, 8 * 1024)
                            } else {
                                Log.e("VideoProcessingDebug", "OutputStream é nulo para: $finalUri")
                            }
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(finalUri, values, null, null)
                    Log.d("VideoProcessingDebug", "MediaStore update concluído.")
                }
            } catch (e: Exception) { // Use Exception para capturar qualquer tipo de erro
                Log.e("VideoProcessingDebug", "Exceção durante copyFileToMediaStore", e)
                finalUri?.let { resolver.delete(it, null, null) }
                finalUri = null
            }
            finalUri
        }
    }

    private fun resetUiState(isError: Boolean = false) {
        progressHandler.removeCallbacks(progressChecker)
        currentTempPath = null

        buttonProcessVideo.isEnabled = true
        buttonSelectVideo.isEnabled = true
        layoutProgress.visibility = View.GONE
        val message = if (isError) "Processamento falhou" else "Processamento concluído!"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ALTERADO: O método agora recebe uma Uri, o que simplifica o código e remove a necessidade do FileProvider
    private fun openFile(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir o arquivo. Erro: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // REMOVIDO: Esta função não é mais necessária com a abordagem do MediaStore
    // private fun getOutputFilePath(fileName: String): String { ... }

    // ALTERADO: O adapter agora recebe uma lista de Uris
    private class FileListAdapter(
        private val uris: List<Uri>,
        private val onClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

        class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.textViewFilePath)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_file, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val uri = uris[position]
            // Exibe o último segmento da Uri, que geralmente é o nome do arquivo
            holder.textView.text = uri.lastPathSegment ?: uri.toString()
            holder.itemView.setOnClickListener { onClick(uri) }
        }

        override fun getItemCount() = uris.size
    }
}