package com.rootimage.flasher

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Background = Color(0xFF0D1018)
private val SurfaceDark = Color(0xFF191F2D)
private val DialogSurface = Color(0xFF222B3D)
private val Accent = Color(0xFF9B8CFF)
private val TextMain = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFFB8C2D6)
private val Success = Color(0xFF53D69A)
private val Warning = Color(0xFFFFB45C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FlasherApp() }
    }
}

@Composable
fun FlasherApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var root by remember { mutableStateOf<Boolean?>(null) }
    var partitions by remember { mutableStateOf(emptyList<Partition>()) }
    var selected by remember { mutableStateOf<Partition?>(null) }
    var image by remember { mutableStateOf<File?>(null) }
    var imageName by remember { mutableStateOf("Nenhuma imagem selecionada") }
    var unlocked by remember {
        mutableStateOf(SettingsManager.isUnlocked(context))
    }

    var taps by remember { mutableIntStateOf(0) }
    var flashing by remember { mutableStateOf(false) }
    var loadingImage by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Aguardando operação") }
    var showConfirm by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var successIsBoot by remember { mutableStateOf(false) }
    var successPartition by remember { mutableStateOf("") }

    fun refresh() {
        if (flashing || refreshing) return

        scope.launch {
            refreshing = true

            val rootAvailable = withContext(Dispatchers.IO) {
                RootManager.isRootAvailable()
            }

            root = rootAvailable

            val found = if (rootAvailable) {
                withContext(Dispatchers.IO) {
                    PartitionManager.scan()
                }
            } else {
                emptyList()
            }

            partitions = found

            selected = selected?.let { previous ->
                found.firstOrNull {
                    it.name == previous.name && it.path == previous.path
                }
            }

            refreshing = false
            message = if (rootAvailable) {
                "${found.size} partições detectadas."
            } else {
                "Acesso Root indisponível."
            }
        }
    }

    fun displayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(
                    cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                )
            } else {
                null
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && !flashing && !loadingImage) {
            scope.launch {
                loadingImage = true

                try {
                    val originalName = withContext(Dispatchers.IO) {
                        displayName(uri)
                    } ?: "imagem"

                    val safeName = File(originalName).name

                    if (!safeName.endsWith(".img", ignoreCase = true)) {
                        error("Arquivo inválido: selecione um arquivo com extensão .img.")
                    }

                    val copiedFile = withContext(Dispatchers.IO) {
                        val destination = File(
                            context.cacheDir,
                            "image-${UUID.randomUUID()}-$safeName"
                        )

                        try {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.use { input ->
                                    destination.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                } ?: error("Não foi possível abrir o arquivo.")

                            if (!destination.exists() || destination.length() <= 0L) {
                                error("O arquivo copiado está vazio.")
                            }

                            destination
                        } catch (e: Exception) {
                            destination.delete()
                            throw e
                        }
                    }

                    val previousImage = image
                    image = copiedFile
                    imageName = safeName

                    if (previousImage != null && previousImage != copiedFile) {
                        withContext(Dispatchers.IO) {
                            previousImage.delete()
                        }
                    }

                    message = "Imagem carregada: ${copiedFile.length() / 1024 / 1024} MB"

                } catch (e: Exception) {
                    message = "Erro ao carregar imagem: ${e.message}"
                } finally {
                    loadingImage = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = SurfaceDark,
            onBackground = TextMain,
            onSurface = TextMain
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.FlashOn, null) },
                        label = { Text("Gravar") }
                    )

                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Storage, null) },
                        label = { Text("Partições") }
                    )

                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Configurações") }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DeveloperBoard,
                        null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            "ROOT IMAGE",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Text(
                            "FLASHER",
                            fontSize = 11.sp,
                            color = Accent,
                            letterSpacing = 3.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = { refresh() },
                        enabled = !flashing && !refreshing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            "Atualizar",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                when (tab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            item {
                                Text(
                                    "Gravar imagem",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Grave imagens diretamente nas partições.",
                                    color = TextMuted
                                )
                            }

                            item {
                                InfoCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Security,
                                            null,
                                            tint = if (root == true) Success else Warning,
                                            modifier = Modifier.size(30.dp)
                                        )

                                        Spacer(Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                when (root) {
                                                    true -> "Root autorizado"
                                                    false -> "Root indisponível"
                                                    else -> "Verificando Root..."
                                                }
                                            )
                                            Text(
                                                "Magisk / KernelSU / APatch",
                                                color = TextMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                InfoCard {
                                    Text(
                                        "ARQUIVO DE IMAGEM",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    Text(
                                        imageName,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    Button(
                                        onClick = {
                                            picker.launch(arrayOf("*/*"))
                                        },
                                        enabled = !flashing && !loadingImage,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Accent
                                        )
                                    ) {
                                        Icon(Icons.Default.FolderOpen, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (loadingImage) "Carregando..."
                                            else "Selecionar arquivo .img"
                                        )
                                    }
                                }
                            }

                            item {
                                var expanded by remember { mutableStateOf(false) }

                                InfoCard {
                                    Text(
                                        "PARTIÇÃO DE DESTINO",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Box {
                                        OutlinedButton(
                                            onClick = { expanded = true },
                                            enabled = !flashing,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                selected?.name ?: "Selecionar partição"
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.ArrowDropDown, null)
                                        }

                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(SurfaceDark)
                                        ) {
                                            partitions.forEach { partition ->
                                                val protected =
                                                    FlashManager.isProtected(partition.name)

                                                val permitted =
                                                    FlashManager.isFlashable(partition.name)

                                                if (permitted && (!protected || unlocked)) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(partition.name)
                                                                Text(
                                                                    partition.path,
                                                                    fontSize = 10.sp,
                                                                    color = TextMuted
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            selected = partition
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        "${partitions.size} partições detectadas",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            item {
                                Button(
                                    onClick = { showConfirm = true },
                                    enabled = !flashing &&
                                        !loadingImage &&
                                        root == true &&
                                        image != null &&
                                        selected != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Accent
                                    )
                                ) {
                                    Icon(Icons.Default.FlashOn, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "INICIAR GRAVAÇÃO",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    message,
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )

                                if (flashing) {
                                    Spacer(Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "Gravação em andamento. Não interrompa o processo.",
                                        color = Warning,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            "Partições do dispositivo",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Dispositivos de bloco identificados pelo Root.",
                            color = TextMuted
                        )

                        Spacer(Modifier.height(14.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(partitions, key = { it.name }) { partition ->
                                val protected =
                                    FlashManager.isProtected(partition.name)
                                val permitted =
                                    FlashManager.isFlashable(partition.name)

                                InfoCard {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Storage,
                                            null,
                                            tint = Accent
                                        )

                                        Spacer(Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                partition.name,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                partition.path,
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }

                                        Text(
                                            when {
                                                !permitted -> "BLOQUEADA"
                                                protected -> "PROTEGIDA"
                                                else -> "PERMITIDA"
                                            },
                                            fontSize = 9.sp,
                                            color = when {
                                                !permitted -> Color(0xFFFF6B6B)
                                                protected -> Warning
                                                else -> Success
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            item {
                                Text(
                                    "Configurações",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            item {
                                InfoCard {
                                    Text(
                                        "SEGURANÇA",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Proteção de partições")
                                            Text(
                                                "Bloqueia gravações em system, vendor e outras partições críticas.",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }

                                        Switch(
                                            checked = !unlocked,
                                            enabled = !flashing,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    unlocked = false
                                                    SettingsManager.setUnlocked(
                                                        context,
                                                        false
                                                    )
                                                    message = "Modo protegido ativado."
                                                } else {
                                                    message =
                                                        "Use sete toques na versão para desbloquear."
                                                }
                                            }
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Text(
                                        if (unlocked) {
                                            "Modo avançado desbloqueado"
                                        } else {
                                            "Modo protegido ativo"
                                        },
                                        color = if (unlocked) Warning else Success,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            item {
                                InfoCard {
                                    Text(
                                        "SOBRE O APLICATIVO",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    Text(
                                        "Root Image Flasher",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        "Versão de compilação: 1.2.0",
                                        modifier = Modifier.clickable {
                                            if (!unlocked) {
                                                taps++

                                                if (taps >= 7) {
                                                    unlocked = true
                                                    SettingsManager.setUnlocked(
                                                        context,
                                                        true
                                                    )
                                                    taps = 0
                                                    message =
                                                        "Modo avançado desbloqueado."
                                                }
                                            }
                                        },
                                        color = Accent
                                    )

                                    Text(
                                        "Toques: $taps / 7",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        "Android 11 ou superior",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            item {
                                InfoCard {
                                    Text("Ambiente Root")

                                    Text(
                                        when (root) {
                                            true -> "Acesso Root disponível"
                                            false -> "Acesso Root não disponível"
                                            else -> "Verificando..."
                                        },
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )

                                    TextButton(
                                        onClick = { refresh() },
                                        enabled = !flashing && !refreshing
                                    ) {
                                        Text("Verificar novamente")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Janela de confirmação com contraste reforçado.
    if (showConfirm) {
        val targetImage = image
        val targetPartition = selected

        AlertDialog(
            onDismissRequest = {
                if (!flashing) showConfirm = false
            },
            containerColor = DialogSurface,
            titleContentColor = TextMain,
            textContentColor = TextMuted,
            title = {
                Text(
                    "Confirmar gravação",
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp
                )
            },
            text = {
                Text(
                    "Imagem: ${targetImage?.name}\n\n" +
                    "Destino: ${targetPartition?.name}\n\n" +
                    "Esta operação substituirá os dados da partição selecionada.",
                    color = TextMain,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !flashing &&
                        targetImage != null &&
                        targetPartition != null,
                    onClick = {
                        val fileToFlash = targetImage ?: return@TextButton
                        val partitionToFlash = targetPartition ?: return@TextButton
                        val advancedMode = unlocked

                        showConfirm = false
                        flashing = true
                        message = "Preparando gravação..."

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                FlashManager.flash(
                                    fileToFlash,
                                    partitionToFlash,
                                    advancedMode
                                )
                            }

                            flashing = false

                            val completed = result.success &&
                                result.output.contains("FLASH_SUCCESS")

                            if (completed) {
                                successPartition = partitionToFlash.name
                                successIsBoot = partitionToFlash.name
                                    .lowercase()
                                    .removeSuffix("_a")
                                    .removeSuffix("_b") == "boot"

                                message = "Gravação concluída com sucesso."
                                showSuccess = true
                            } else {
                                message = "Falha na gravação: ${result.output}"
                            }
                        }
                    }
                ) {
                    Text(
                        "GRAVAR",
                        color = Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirm = false },
                    enabled = !flashing
                ) {
                    Text("CANCELAR", color = TextMain)
                }
            }
        )
    }

    // Janela de sucesso.
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = {
                showSuccess = false
            },
            containerColor = DialogSurface,
            titleContentColor = TextMain,
            textContentColor = TextMain,
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Gravação concluída!",
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp
                )
            },
            text = {
                Column {
                    Text(
                        "A imagem foi gravada com sucesso.",
                        color = Success,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "Partição: $successPartition",
                        color = TextMain,
                        fontSize = 14.sp
                    )

                    if (successIsBoot) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "A partição de inicialização foi atualizada. Reinicie o dispositivo para iniciar com a nova imagem.",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (successIsBoot) {
                    Button(
                        onClick = {
                            showSuccess = false

                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    RootManager.execute("reboot")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Success,
                            contentColor = Color(0xFF07150E)
                        )
                    ) {
                        Icon(Icons.Default.RestartAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "REINICIAR AGORA",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            showSuccess = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("CONCLUÍDO")
                    }
                }
            },
            dismissButton = {
                if (successIsBoot) {
                    TextButton(
                        onClick = {
                            showSuccess = false
                        }
                    ) {
                        Text("REINICIAR DEPOIS", color = TextMain)
                    }
                }
            }
        )
    }
}

@Composable
fun InfoCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        )
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            content = content
        )
    }
}
