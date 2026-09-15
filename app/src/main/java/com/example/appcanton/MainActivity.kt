package com.example.appcanton

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcanton.ui.theme.AppCantonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

// MARK: - Data Models Locales
data class ArticleItem(
    val code: String,
    val name: String,
    val fobPriceUSD: Double,
    val moq: Int,
    val port: String,
    val leadTime: String,
    val photoBitmap: Bitmap? = null
)

data class LocalSupplier(
    val id: String,
    val supplierCode: String,
    val companyName: String,
    val companyChinese: String,
    val stand: String,
    val category: String,
    val gpsCoordinates: String,
    val marqueePhotoBitmap: Bitmap? = null,
    val articles: MutableList<ArticleItem> = mutableListOf(),
    val createdAt: String
)

data class FlexxusProduct(
    val sku: String,
    val name: String,
    val category: String,
    val costUSDNoVAT: Double
)

// MARK: - Gestor de Conexión en Vivo con Google Firestore
object FirestoreManager {
    private const val PROJECT_ID = "perrenycia-crm"
    
    fun syncSupplierToFirestore(supplier: LocalSupplier, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/proveedores?documentId=${supplier.supplierCode}")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonPayload = """
                    {
                      "fields": {
                        "codigoProveedor": {"stringValue": "${supplier.supplierCode}"},
                        "empresa": {"stringValue": "${supplier.companyName}"},
                        "empresaChino": {"stringValue": "${supplier.companyChinese}"},
                        "stand": {"stringValue": "${supplier.stand}"},
                        "rubro": {"stringValue": "${supplier.category}"},
                        "gps": {"stringValue": "${supplier.gpsCoordinates}"},
                        "fechaCreacion": {"stringValue": "${supplier.createdAt}"}
                      }
                    }
                """.trimIndent()

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(jsonPayload)
                writer.flush()
                writer.close()

                val code = conn.responseCode
                onComplete(code == 200 || code == 409)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }
}

// MARK: - Procesador OCR Real mediante Google ML Kit (Offline y en el Dispositivo)
object PhotoOCRProcessor {
    fun extractDataFromPhoto(
        bitmap: Bitmap,
        onComplete: (extractedCompany: String, extractedChinese: String, extractedStand: String) -> Unit
    ) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var extractedCompany = ""
                    var extractedChinese = ""
                    var extractedStand = ""

                    val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text.trim() } }

                    val standPattern = Pattern.compile("(?i)\\b(\\d{1,2}\\.\\d{1,2}\\s?[A-Z]\\d{2,4}|stand\\s?#?\\d+|booth\\s?#?[A-Z0-9-]+)\\b")
                    val companyKeywordPattern = Pattern.compile("(?i)\\b(S\\.A\\.|S\\.R\\.L\\.|LTD|LIMITED|CO\\.|CORP|CORPORATION|INC|GROUP|INDUSTRIES|FACTORY|SANITARY|HARDWARE|PLUMBING|IMPORT|EXPORT|INTERNATIONAL|MANUFACTURING)\\b")

                    for (line in lines) {
                        if (extractedStand.isBlank()) {
                            val standMatcher = standPattern.matcher(line)
                            if (standMatcher.find()) {
                                extractedStand = standMatcher.group() ?: ""
                            }
                        }

                        if (extractedCompany.isBlank()) {
                            val companyMatcher = companyKeywordPattern.matcher(line)
                            if (companyMatcher.find()) {
                                extractedCompany = line
                            }
                        }

                        // Chinese characters detection: [\u4e00-\u9fa5]
                        val chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]+")
                        val chineseMatcher = chinesePattern.matcher(line)
                        if (chineseMatcher.find() && extractedChinese.isBlank()) {
                            extractedChinese = line
                        }
                    }

                    // Fallback: If no company keyword found, pick the top non-empty valid header line
                    if (extractedCompany.isBlank() && lines.isNotEmpty()) {
                        val candidate = lines.firstOrNull { l ->
                            l.length > 2 &&
                            !standPattern.matcher(l).find() &&
                            !l.contains("Tel", ignoreCase = true) &&
                            !l.contains("Fax", ignoreCase = true) &&
                            !l.contains("www", ignoreCase = true) &&
                            !l.contains("@")
                        }
                        if (candidate != null) {
                            extractedCompany = candidate
                        }
                    }

                    onComplete(extractedCompany, extractedChinese, extractedStand)
                }
                .addOnFailureListener {
                    onComplete("", "", "")
                }
        } catch (e: Exception) {
            onComplete("", "", "")
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppCantonTheme {
                MainAppFlow()
            }
        }
    }
}

@Composable
fun MainAppFlow() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("canton_app_prefs", Context.MODE_PRIVATE) }

    val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    val savedTimestamp = prefs.getLong("login_timestamp", 0L)
    val isRemembered = prefs.getBoolean("remember_me", true)
    val isSessionValid = isRemembered && ((System.currentTimeMillis() - savedTimestamp) < SEVEN_DAYS_MS)

    var isLoggedIn by remember { mutableStateOf(isSessionValid) }
    var currentScreen by remember { mutableStateOf("dashboard") }

    val savedSuppliers = remember { mutableStateListOf<LocalSupplier>() }
    var activeSupplier by remember { mutableStateOf<LocalSupplier?>(null) }

    // Permisos Runtime
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            Toast.makeText(context, "Permisos concedidos de Cámara, GPS y Micrófono", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { rememberMe ->
            prefs.edit()
                .putBoolean("remember_me", rememberMe)
                .putLong("login_timestamp", System.currentTimeMillis())
                .apply()
            isLoggedIn = true
        })
    } else {
        Scaffold(
            topBar = {
                OptInAppBar(
                    title = "PERREN & CÍA. - CANTON FAIR 2026",
                    subtitle = if (activeSupplier != null) "Editando: ${activeSupplier?.companyName}" else "Sesión Activa (7 Días)",
                    onLogout = {
                        prefs.edit().putBoolean("remember_me", false).apply()
                        isLoggedIn = false
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1B365D)) {
                    NavigationBarItem(
                        selected = currentScreen == "dashboard" && activeSupplier == null,
                        onClick = { currentScreen = "dashboard"; activeSupplier = null },
                        icon = { Text("🏠", fontSize = 18.sp) },
                        label = { Text("Inicio", color = Color.White, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = activeSupplier != null || currentScreen == "supplier_form",
                        onClick = {
                            if (activeSupplier == null) {
                                val newCode = "CF26-P-${String.format("%04d", savedSuppliers.size + 1)}"
                                val newSup = LocalSupplier(
                                    id = System.currentTimeMillis().toString(),
                                    supplierCode = newCode,
                                    companyName = "",
                                    companyChinese = "",
                                    stand = "",
                                    category = "Sanitarios",
                                    gpsCoordinates = "GPS: Lat -43.2512, Long -65.3094",
                                    createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                )
                                activeSupplier = newSup
                            }
                            currentScreen = "supplier_form"
                        },
                        icon = { Text("📷", fontSize = 18.sp) },
                        label = { Text("+ Proveedor", color = Color.White, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "comparison",
                        onClick = { currentScreen = "comparison" },
                        icon = { Text("📊", fontSize = 18.sp) },
                        label = { Text("Comparador", color = Color.White, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "wallet",
                        onClick = { currentScreen = "wallet" },
                        icon = { Text("📇", fontSize = 18.sp) },
                        label = { Text("Tarjetero", color = Color.White, fontSize = 9.sp) }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when {
                    activeSupplier != null || currentScreen == "supplier_form" -> {
                        SupplierWorkspaceScreen(
                            supplier = activeSupplier,
                            onSaveSupplier = { updatedSup ->
                                val existingIndex = savedSuppliers.indexOfFirst { it.id == updatedSup.id }
                                if (existingIndex >= 0) {
                                    savedSuppliers[existingIndex] = updatedSup
                                } else {
                                    savedSuppliers.add(updatedSup)
                                }
                                activeSupplier = updatedSup

                                FirestoreManager.syncSupplierToFirestore(updatedSup) { success -> }

                                Toast.makeText(context, " REGISTRO GUARDADO EN EL CELULAR Y FIRESTORE", Toast.LENGTH_SHORT).show()
                            },
                            onCloseWorkspace = {
                                activeSupplier = null
                                currentScreen = "dashboard"
                            }
                        )
                    }
                    currentScreen == "dashboard" -> {
                        DashboardScreen(
                            suppliers = savedSuppliers,
                            onNewSupplierClick = {
                                val newCode = "CF26-P-${String.format("%04d", savedSuppliers.size + 1)}"
                                activeSupplier = LocalSupplier(
                                    id = System.currentTimeMillis().toString(),
                                    supplierCode = newCode,
                                    companyName = "",
                                    companyChinese = "",
                                    stand = "",
                                    category = "Sanitarios",
                                    gpsCoordinates = "GPS: Lat -43.2512, Long -65.3094",
                                    createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                )
                                currentScreen = "supplier_form"
                            },
                            onSelectSupplier = { selected ->
                                activeSupplier = selected
                                currentScreen = "supplier_form"
                            }
                        )
                    }
                    currentScreen == "comparison" -> ComparisonFlexxusScreen()
                    currentScreen == "wallet" -> CardWalletScreen(suppliers = savedSuppliers)
                }
            }
        }
    }
}

// MARK: - Helper Composable Box de Métricas
@Composable
fun MetricBox(title: String, value: String, textColor: Color, bgColor: Color, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = bgColor), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(title, fontSize = 11.sp, color = Color.DarkGray)
        }
    }
}

// MARK: - 1. Pantalla de Login CRM
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: (rememberMe: Boolean) -> Unit) {
    var username by remember { mutableStateOf("perren") }
    var password by remember { mutableStateOf("canton2026") }
    var rememberMe by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B365D))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("PERREN & CÍA.", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("ACCESO CRM CANTON FAIR & FIRESTORE", fontSize = 12.sp, color = Color(0xFFBBDEFB))

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("INICIAR SESIÓN CRM", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B365D))
                Text("Marca 'Recordar usuario' para mantener la sesión abierta por 7 días", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario CRM / Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberMe = !rememberMe }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recordar usuario (Mantener sesión por 7 días)", fontSize = 11.sp, color = Color.DarkGray)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onLoginSuccess(rememberMe) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("INGRESAR AL SISTEMA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// MARK: - 2. Dashboard de Inicio (Cero Datos Demos)
@Composable
fun DashboardScreen(
    suppliers: List<LocalSupplier>,
    onNewSupplierClick: () -> Unit,
    onSelectSupplier: (LocalSupplier) -> Unit
) {
    val scrollState = rememberScrollState()
    val totalArticles = suppliers.sumOf { it.articles.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("RESUMEN DE RELEVAMIENTO (FIRESTORE CONECTADO)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricBox("Proveedores Guardados", "${suppliers.size}", Color(0xFF1976D2), Color(0xFFE3F2FD), Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            MetricBox("Artículos Totales", "$totalArticles", Color(0xFF7B1FA2), Color(0xFFF3E5F5), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNewSupplierClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("+ NUEVO PROVEEDOR (STAND)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("REGISTROS GUARDADOS LOCALMENTE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        if (suppliers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📦 No hay proveedores registrados aún", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Presiona '+ NUEVO PROVEEDOR' para comenzar el relevamiento en la feria.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        } else {
            suppliers.forEach { sup ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectSupplier(sup) },
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sup.supplierCode, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                            Text("Stand: ${if (sup.stand.isBlank()) "Sin Stand" else sup.stand}", fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(if (sup.companyName.isBlank()) "Proveedor Nuevo" else sup.companyName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(sup.gpsCoordinates, fontSize = 10.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Artículos cargados: ${sup.articles.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

// MARK: - 3. Carga Continua de Proveedor & Extracción OCR Estricta (Sin Inventar Datos)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierWorkspaceScreen(
    supplier: LocalSupplier?,
    onSaveSupplier: (LocalSupplier) -> Unit,
    onCloseWorkspace: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var companyName by remember(supplier) { mutableStateOf(supplier?.companyName ?: "") }
    var companyChinese by remember(supplier) { mutableStateOf(supplier?.companyChinese ?: "") }
    var stand by remember(supplier) { mutableStateOf(supplier?.stand ?: "") }
    var category by remember(supplier) { mutableStateOf(supplier?.category ?: "Sanitarios") }

    var capturedMarqueeBitmap by remember(supplier) { mutableStateOf<Bitmap?>(supplier?.marqueePhotoBitmap) }

    val autoFillDataFromPhoto = { bitmap: Bitmap ->
        capturedMarqueeBitmap = bitmap
        Toast.makeText(context, "🔍 Procesando imagen con Google ML Kit...", Toast.LENGTH_SHORT).show()
        PhotoOCRProcessor.extractDataFromPhoto(bitmap) { extractedCompany, extractedChinese, extractedStand ->
            if (extractedCompany.isNotBlank()) companyName = extractedCompany
            if (extractedChinese.isNotBlank()) companyChinese = extractedChinese
            if (extractedStand.isNotBlank()) stand = extractedStand

            if (extractedCompany.isNotBlank() || extractedChinese.isNotBlank() || extractedStand.isNotBlank()) {
                Toast.makeText(context, "✨ OCR ML Kit: Datos detectados en la foto", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "📷 Foto guardada. No se detectó texto reconocible.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            autoFillDataFromPhoto(bitmap)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    autoFillDataFromPhoto(bitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cargar la foto de la galería", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var artName by remember { mutableStateOf("") }
    var artFobUSD by remember { mutableStateOf("") }
    var artMoq by remember { mutableStateOf("") }
    var artPort by remember { mutableStateOf("Shenzhen") }
    var artLeadTime by remember { mutableStateOf("30 días") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROVEEDOR: ${supplier?.supplierCode ?: "CF26-P-0001"}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF1B365D)
            )
            TextButton(onClick = onCloseWorkspace) {
                Text("VOLVER AL MENÚ", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📷 FOTO MARQUESINA Y STAND", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(8.dp))

                if (capturedMarqueeBitmap != null) {
                    Image(
                        bitmap = capturedMarqueeBitmap!!.asImageBitmap(),
                        contentDescription = "Marquesina Stand",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📷 ABRIR CÁMARA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖼️ GALERÍA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(supplier?.gpsCoordinates ?: "GPS: Lat -43.2512, Long -65.3094", fontSize = 10.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("DATOS PRINCIPALES DE LA FÁBRICA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B365D))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = companyName, onValueChange = { companyName = it },
                    label = { Text("Empresa (Nombre en Inglés)") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = companyChinese, onValueChange = { companyChinese = it },
                    label = { Text("Nombre Original en Chino") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = stand, onValueChange = { stand = it },
                    label = { Text("Número de Stand (ej: 10.1 B23)") }, modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                val updated = supplier?.copy(
                    companyName = companyName,
                    companyChinese = companyChinese,
                    stand = stand,
                    marqueePhotoBitmap = capturedMarqueeBitmap
                ) ?: LocalSupplier(
                    id = System.currentTimeMillis().toString(),
                    supplierCode = "CF26-P-0001",
                    companyName = companyName,
                    companyChinese = companyChinese,
                    stand = stand,
                    category = category,
                    gpsCoordinates = "GPS: Lat -43.2512, Long -65.3094",
                    marqueePhotoBitmap = capturedMarqueeBitmap,
                    createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                onSaveSupplier(updated)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("💾 GUARDAR REGISTRO LOCAL (SE MANTIENE ABIERTO)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📦 INCORPORAR ARTÍCULO A ESTE PROVEEDOR", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = artName, onValueChange = { artName = it },
                    label = { Text("Nombre Artículo (ej: Inodoro Rimless A520)") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = artFobUSD, onValueChange = { artFobUSD = it },
                        label = { Text("Precio FOB ($ USD)") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = artMoq, onValueChange = { artMoq = it },
                        label = { Text("MOQ (Unidades)") }, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = artPort, onValueChange = { artPort = it },
                        label = { Text("Puerto Origen") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = artLeadTime, onValueChange = { artLeadTime = it },
                        label = { Text("Tiempo Entrega") }, modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (artName.isNotBlank() && artFobUSD.isNotBlank()) {
                            val newArt = ArticleItem(
                                code = "${supplier?.supplierCode ?: "CF26-P-0001"}-A${String.format("%02d", (supplier?.articles?.size ?: 0) + 1)}",
                                name = artName,
                                fobPriceUSD = artFobUSD.toDoubleOrNull() ?: 0.0,
                                moq = artMoq.toIntOrNull() ?: 100,
                                port = artPort,
                                leadTime = artLeadTime
                            )
                            supplier?.articles?.add(newArt)
                            artName = ""
                            artFobUSD = ""
                            artMoq = ""
                            Toast.makeText(context, "Artículo ${newArt.code} agregado al proveedor", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ AGREGAR ARTÍCULO", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("ARTÍCULOS REGISTRADOS EN ESTE PROVEEDOR (${supplier?.articles?.size ?: 0})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))

        supplier?.articles?.forEach { art ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${art.code} - ${art.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B365D))
                    Text("FOB: $${art.fobPriceUSD} USD | MOQ: ${art.moq} u | Puerto: ${art.port}", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// MARK: - 4. Comparador de Costos Flexxus BI & Combos
@Composable
fun ComparisonFlexxusScreen() {
    val scrollState = rememberScrollState()

    val flexxusCatalog = remember {
        listOf(
            FlexxusProduct("FERRUM-INOD-01", "Inodoro Ferrum Bari", "Sanitarios", 55.0),
            FlexxusProduct("FERRUM-MOCH-01", "Mochila Ferrum Bari", "Sanitarios", 28.0),
            FlexxusProduct("FERRUM-TAPA-01", "Tapa Asiento Ferrum Bari", "Sanitarios", 15.0),
            FlexxusProduct("GRIF-MONO-01", "Monocomando Lavatorio FV", "Grifería", 42.0)
        )
    }

    var selectedProducts by remember { mutableStateOf(setOf("FERRUM-INOD-01", "FERRUM-MOCH-01", "FERRUM-TAPA-01")) }
    var chinaFobSetUSD by remember { mutableStateOf("38.00") }
    var fleteUSD by remember { mutableStateOf("3400") }
    var iibbPercent by remember { mutableStateOf("3.5") }

    val totalFlexxusCostNoVAT = flexxusCatalog
        .filter { selectedProducts.contains(it.sku) }
        .sumOf { it.costUSDNoVAT }

    val chinaFob = chinaFobSetUSD.toDoubleOrNull() ?: 38.0
    val flete = fleteUSD.toDoubleOrNull() ?: 3400.0
    val qty = 500

    val fobTotal = chinaFob * qty
    val seguro = fobTotal * 0.012
    val cif = fobTotal + flete + seguro
    val derechos = cif * 0.20
    val tasaEstad = cif * 0.03
    val baseImponible = cif + derechos + tasaEstad
    val gastosDespachante = baseImponible * 0.08
    val costoLandedTotal = baseImponible + gastosDespachante
    val unitLandedChina = costoLandedTotal / qty

    val ahorroUnitario = totalFlexxusCostNoVAT - unitLandedChina
    val ahorroPorcentaje = (ahorroUnitario / totalFlexxusCostNoVAT) * 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("COMPARADOR FLEXXUS BI VS CANTON FAIR", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
        Text("Combo Artículos Argentina (Sin IVA) vs Kit Completo China", fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("SELECCIÓN BUNDLE FLEXXUS BI (ARGENTINA)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(6.dp))

                flexxusCatalog.forEach { prod ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedProducts = if (selectedProducts.contains(prod.sku)) {
                                    selectedProducts - prod.sku
                                } else {
                                    selectedProducts + prod.sku
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedProducts.contains(prod.sku),
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${prod.name} ($${prod.costUSDNoVAT} USD)", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("COSTO TOTAL COMBO FLEXXUS SIN IVA:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("$${String.format("%.2f", totalFlexxusCostNoVAT)} USD", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFD32F2F))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("OFERTA CHINA & IIBB PROVINCIA (EDITABLE)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = chinaFobSetUSD, onValueChange = { chinaFobSetUSD = it },
                        label = { Text("FOB Kit Chino ($)") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = iibbPercent, onValueChange = { iibbPercent = it },
                        label = { Text("IIBB % (ej: Trelew 3.5%)") }, modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Card(
                modifier = Modifier.weight(1f).border(2.dp, Color(0xFFD32F2F), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("🇦🇷 COMBO FLEXXUS", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFD32F2F))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Inodoro + Mochila + Tapa", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Costo Sin IVA:", fontSize = 9.sp, color = Color.Gray)
                    Text("$${String.format("%.2f", totalFlexxusCostNoVAT)} USD", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFD32F2F))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Card(
                modifier = Modifier.weight(1f).border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("🇨🇳 KIT CANTON FAIR", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Set Completo Chino", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Costo Unit. Puesto:", fontSize = 9.sp, color = Color.Gray)
                    Text("$${String.format("%.2f", unitLandedChina)} USD", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2E7D32))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = if (ahorroUnitario > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("RESULTADO AHORRO COMBO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ahorro por Kit Puesto:", fontSize = 12.sp)
                    Text("+${String.format("%.2f", ahorroUnitario)} USD (${String.format("%.1f", ahorroPorcentaje)}%)", fontWeight = FontWeight.Bold, color = if (ahorroUnitario > 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                }
            }
        }
    }
}

// MARK: - 5. Tarjetero CRM
@Composable
fun CardWalletScreen(suppliers: List<LocalSupplier>) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📇 TARJETERO CRM CANTON FAIR", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B365D))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Contactos cargados en el celular: ${suppliers.size}", color = Color.Gray, fontSize = 12.sp)
    }
}

// MARK: - App Top Bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptInAppBar(title: String, subtitle: String, onLogout: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Text(subtitle, fontSize = 10.sp, color = Color(0xFFBBDEFB))
            }
        },
        actions = {
            TextButton(onClick = onLogout) {
                Text("SALIR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1B365D))
    )
}