/*
 * Android aplikace: Editovatelná mapa obchodu s regály a nákupními položkami
 * Používá Jetpack Compose + Material3
 */

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

// Hlavní třída aplikace
@Composable
fun ShopMapApp(viewModel: ShopMapViewModel) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    context.contentResolver.openInputStream(it)?.let { stream ->
                        viewModel.importFromJson(stream)
                    }
                }
            }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { viewModel.exportToJson(context) }) {
                        Text("Sdílet mapu")
                    }
                    Button(onClick = { launcher.launch("application/json") }) {
                        Text("Import mapy")
                    }
                }
                ShopGrid(viewModel)
            }
        }
    }
}

// Zobrazí grid s regály
@Composable
fun ShopGrid(viewModel: ShopMapViewModel) {
    val gridItems by viewModel.gridItems.collectAsState()
    val openDialog = remember { mutableStateOf<GridItem?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.padding(8.dp)
    ) {
        items(60) { index ->
            val item = gridItems[index]
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .aspectRatio(1f)
                    .background(
                        color = item?.color ?: Color.LightGray,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        if (item == null) {
                            viewModel.addShelf(index)
                        } else {
                            openDialog.value = item
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (item != null) Text(item.name, textAlign = TextAlign.Center)
            }
        }
    }

    openDialog.value?.let { shelf ->
        ShelfDialog(shelf = shelf, onClose = { openDialog.value = null }, viewModel)
    }
}

// Dialog pro zobrazení a úpravu položek v regálu
@Composable
fun ShelfDialog(shelf: GridItem, onClose: () -> Unit, viewModel: ShopMapViewModel) {
    val textState = remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(shelf.name) },
        text = {
            Column {
                shelf.items.forEach {
                    Text("\u2022 ${it}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = textState.value,
                    onValueChange = { textState.value = it },
                    label = { Text("Přidat položku") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.addItemToShelf(shelf.index, textState.value)
                textState.value = ""
            }) { Text("Přidat") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Zavřít") }
        }
    )
}

// Datové struktury
@Serializable
data class GridItem(
    val index: Int,
    val name: String,
    val color: Color,
    val items: List<String>
)

// ViewModel
class ShopMapViewModel : ViewModel() {
    private val _gridItems = MutableStateFlow<List<GridItem?>>(List(60) { null })
    val gridItems: StateFlow<List<GridItem?>> = _gridItems

    fun addShelf(index: Int) {
        val name = "Regál ${index + 1}"
        val color = Color(0xFFB2DFDB)
        val newItem = GridItem(index, name, color, emptyList())
        _gridItems.value = _gridItems.value.toMutableList().also { it[index] = newItem }
    }

    fun addItemToShelf(index: Int, item: String) {
        _gridItems.value = _gridItems.value.toMutableList().also {
            val shelf = it[index]
            if (shelf != null) {
                it[index] = shelf.copy(items = shelf.items + item)
            }
        }
    }

    fun exportToJson(context: Context) {
        val items = _gridItems.value.map { it }
        val json = Json.encodeToString(items)
        val file = File(context.cacheDir, "shop_map.json")
        file.writeText(json)

        val uri = Uri.fromFile(file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Sdílet mapu obchodu"))
    }

    fun importFromJson(stream: InputStream) {
        val text = stream.bufferedReader().use { it.readText() }
        val list = Json.decodeFromString<List<GridItem?>>(text)
        _gridItems.value = list
    }
}

// V MainActivity spusť appku
class MainActivity : ComponentActivity() {
    private val viewModel: ShopMapViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShopMapApp(viewModel) }
    }
}
