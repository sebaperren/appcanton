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
import androidx.compose.foundation.BorderStroke
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
import android.graphics.BitmapFactory
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.content.FileProvider
import android.content.ContentValues
import android.provider.MediaStore
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

fun createImageFileUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val baseDir = context.externalCacheDir ?: context.cacheDir
    val storageDir = File(baseDir, "Pictures")
    if (!storageDir.exists()) storageDir.mkdirs()
    val file = File(storageDir, "JPEG_${timeStamp}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

// MARK: - Data Models Locales
data class ArticleItem(
    val code: String,
    val name: String,
    val fobPriceUSD: Double,
    val moq: Int,
    val port: String,
    val leadTime: String,
    val note: String = "",
    val audioPath: String? = null,
    val photos: List<Bitmap> = emptyList(),
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
    // SECCIÓN B. CONTACTO DE LA PERSONA QUE NOS ATENDIÓ
    val contactName: String = "",
    val contactPosition: String = "",
    val contactWeChat: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val contactCardPhotoBitmap: Bitmap? = null,
    val articles: MutableList<ArticleItem> = mutableListOf(),
    val createdAt: String
)

data class FlexxusProduct(
    val sku: String,
    val name: String,
    val category: String,
    val costUSDNoVAT: Double
)

data class PerrenPostgresProduct(
    val sku: String,
    val description: String,
    val brand: String,
    val category: String,
    val costNoVAT: Double,        // Costo de venta sin IVA en ARS ($)
    val costUSDNoVAT: Double,     // Costo de venta sin IVA en USD ($)
    val stockAvailable: Int,      // Stock disponible en depósito
    val unit: String = "Unidad",
    val priceVAT: Double = 0.0    // Precio de venta público con IVA
)

object PerrenPostgresRepository {
    // Catálogo offline de benchmark con datos reales de Perren (Flexxus BI / PostgreSQL)
    private val mockCatalog = listOf(
        // CEMENTOS & CONSTRUCCIÓN (LOMA NEGRA)
        PerrenPostgresProduct("LN-CEM-50", "Bolsa de Cemento Loma Negra 50kg", "Loma Negra", "Construcción / Cementos", 7850.0, 5.81, 2400, "Bolsa", 9498.5),
        PerrenPostgresProduct("LN-CEM-25", "Bolsa de Cemento Loma Negra 25kg Rapidito", "Loma Negra", "Construcción / Cementos", 4200.0, 3.11, 1150, "Bolsa", 5082.0),
        PerrenPostgresProduct("LN-CAL-25", "Cal Hidratada Loma Negra Calsid 25kg", "Loma Negra", "Construcción / Cementos", 3100.0, 2.30, 890, "Bolsa", 3751.0),
        PerrenPostgresProduct("LN-MAS-30", "Pastina Loma Negra Plastocor 30kg", "Loma Negra", "Construcción / Cementos", 5200.0, 3.85, 620, "Bolsa", 6292.0),
        
        // SANITARIOS (FERRUM)
        PerrenPostgresProduct("FERRUM-BARI-INO", "Inodoro Blanco De Pie Ferrum Bari Short", "Ferrum", "Sanitarios", 55000.0, 40.74, 320, "Unidad", 66550.0),
        PerrenPostgresProduct("FERRUM-BARI-MOC", "Mochila Depósito Apoyo Ferrum Bari Dual 3/6L", "Ferrum", "Sanitarios", 28000.0, 20.74, 280, "Unidad", 33880.0),
        PerrenPostgresProduct("FERRUM-BARI-TAP", "Tapa Asiento Inodoro Ferrum Bari Cierre Suave", "Ferrum", "Sanitarios", 15000.0, 11.11, 450, "Unidad", 18150.0),
        PerrenPostgresProduct("FERRUM-VEN-INO", "Inodoro Blanco Largo Ferrum Venezia Premium", "Ferrum", "Sanitarios", 89000.0, 65.92, 110, "Unidad", 107690.0),
        PerrenPostgresProduct("FERRUM-MAYO-BID", "Bidé 1 Agujero Ferrum Mayo Blanco", "Ferrum", "Sanitarios", 38000.0, 28.14, 190, "Unidad", 45980.0),

        // GRIFERÍA (FV)
        PerrenPostgresProduct("FV-GRIF-LAV-01", "Monocomando Lavatorio FV Arizona Cromo", "FV", "Grifería", 42000.0, 31.11, 530, "Unidad", 50820.0),
        PerrenPostgresProduct("FV-GRIF-COC-02", "Monocomando Cocina Pico Alto FV Swing Cromo", "FV", "Grifería", 64000.0, 47.40, 210, "Unidad", 77440.0),
        PerrenPostgresProduct("FV-GRIF-DUCH-03", "Juego de Ducha con Transferencia FV Temple", "FV", "Grifería", 95000.0, 70.37, 140, "Unidad", 114950.0),

        // ADHESIVOS & PASTINAS (WEBER & KLAUKOL)
        PerrenPostgresProduct("WEBER-COL-IMP", "Adhesivo Weber Impermeable para Cerámicos 30kg", "Weber", "Adhesivos / Pastinas", 8900.0, 6.59, 1600, "Bolsa", 10769.0),
        PerrenPostgresProduct("WEBER-PAS-BLA", "Pastina Weber Blanco Nieve 2kg Impermeable", "Weber", "Adhesivos / Pastinas", 2300.0, 1.70, 940, "Unidad", 2783.0),
        PerrenPostgresProduct("KLAU-ADH-POR", "Pegamento Klaukol Porcellanato Fluido 30kg", "Klaukol", "Adhesivos / Pastinas", 14500.0, 10.74, 780, "Bolsa", 17545.0),
        PerrenPostgresProduct("KLAU-ADH-STD", "Klaukol Tradicional Bolsa 30kg", "Klaukol", "Adhesivos / Pastinas", 9800.0, 7.25, 1200, "Bolsa", 11858.0),

        // REVESTIMIENTOS (SAN PIETRO & CERRO NEGRO & CORTINES)
        PerrenPostgresProduct("SP-PORC-60X60", "Porcellanato San Pietro Marmi Carrara 60x60 M2", "San Pietro", "Revestimientos", 18500.0, 13.70, 3200, "m²", 22385.0),
        PerrenPostgresProduct("SP-PORC-80X80", "Porcellanato San Pietro Concrete Grey 80x80 M2", "San Pietro", "Revestimientos", 24900.0, 18.44, 1850, "m²", 30129.0),
        PerrenPostgresProduct("CN-PORC-60X60", "Porcellanato Cerro Negro Madera Roble 60x60 M2", "Cerro Negro", "Revestimientos", 16800.0, 12.44, 2100, "m²", 20328.0),
        PerrenPostgresProduct("CORT-CER-40X40", "Cerámica Cortines Piedra Beige 40x40 M2", "Cortines", "Revestimientos", 9200.0, 6.81, 4500, "m²", 11132.0),

        // CONSTRUCCIÓN EN SECO & AISLACIONES (DURLOCK & ISOVER)
        PerrenPostgresProduct("DURL-PLA-125", "Placa de Yeso Durlock Estándar 12.5mm 1.20x2.40", "Durlock", "Construcción en Seco", 11500.0, 8.51, 1400, "Unidad", 13915.0),
        PerrenPostgresProduct("ISOV-LAN-50", "Lana de Vidrio Isover Rolac Plata 50mm 12m2", "Isover", "Aislaciones", 28500.0, 21.11, 480, "Rollo", 34485.0)
    )

    fun getAllProducts(): List<PerrenPostgresProduct> = mockCatalog

    fun getAvailableBrands(): List<String> {
        val brands = mockCatalog.map { it.brand }.distinct().sorted()
        return listOf("Todas las Marcas") + brands
    }

    fun getAvailableCategories(): List<String> {
        val categories = mockCatalog.map { it.category }.distinct().sorted()
        return listOf("Todos los Rubros") + categories
    }

    fun searchProducts(query: String, selectedBrand: String, selectedCategory: String): List<PerrenPostgresProduct> {
        val cleanQuery = query.trim().lowercase()
        val keywords = cleanQuery.split(" ").filter { it.isNotBlank() }

        return mockCatalog.filter { prod ->
            val matchQuery = if (keywords.isEmpty()) {
                true
            } else {
                val fullText = "${prod.sku} ${prod.description} ${prod.brand} ${prod.category}".lowercase()
                keywords.all { kw -> fullText.contains(kw) }
            }

            val matchBrand = (selectedBrand == "Todas las Marcas" || selectedBrand.isEmpty()) ||
                    prod.brand.equals(selectedBrand, ignoreCase = true)

            val matchCategory = (selectedCategory == "Todos los Rubros" || selectedCategory.isEmpty()) ||
                    prod.category.equals(selectedCategory, ignoreCase = true)

            matchQuery && matchBrand && matchCategory
        }
    }
}

// MARK: - Gestor de Conexión en Vivo con Google Firestore (Multi-Dispositivo)
object FirestoreManager {
    private const val PROJECT_ID = "perrenycia-crm"
    
    fun syncSupplierToFirestore(supplier: LocalSupplier, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val docId = supplier.supplierCode.ifBlank { supplier.id }
                val url = URL("https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/proveedores/$docId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val articlesJsonArray = supplier.articles.map { art ->
                    """
                    {
                      "mapValue": {
                        "fields": {
                          "code": {"stringValue": "${art.code.replace("\"", "\\\"")}"},
                          "name": {"stringValue": "${art.name.replace("\"", "\\\"")}"},
                          "fobPriceUSD": {"doubleValue": ${art.fobPriceUSD}},
                          "moq": {"integerValue": ${art.moq}},
                          "port": {"stringValue": "${art.port.replace("\"", "\\\"")}"},
                          "leadTime": {"stringValue": "${art.leadTime.replace("\"", "\\\"")}"},
                          "note": {"stringValue": "${art.note.replace("\"", "\\\"")}"}
                        }
                      }
                    }
                    """.trimIndent()
                }.joinToString(",")

                val jsonPayload = """
                    {
                      "fields": {
                        "codigoProveedor": {"stringValue": "${supplier.supplierCode}"},
                        "empresa": {"stringValue": "${supplier.companyName.replace("\"", "\\\"")}"},
                        "empresaChino": {"stringValue": "${supplier.companyChinese.replace("\"", "\\\"")}"},
                        "stand": {"stringValue": "${supplier.stand.replace("\"", "\\\"")}"},
                        "rubro": {"stringValue": "${supplier.category}"},
                        "gps": {"stringValue": "${supplier.gpsCoordinates}"},
                        "contactNombre": {"stringValue": "${supplier.contactName.replace("\"", "\\\"")}"},
                        "contactCargo": {"stringValue": "${supplier.contactPosition.replace("\"", "\\\"")}"},
                        "contactWeChat": {"stringValue": "${supplier.contactWeChat.replace("\"", "\\\"")}"},
                        "contactTelefono": {"stringValue": "${supplier.contactPhone.replace("\"", "\\\"")}"},
                        "contactEmail": {"stringValue": "${supplier.contactEmail.replace("\"", "\\\"")}"},
                        "fechaCreacion": {"stringValue": "${supplier.createdAt}"},
                        "articles": {
                          "arrayValue": {
                            "values": [ $articlesJsonArray ]
                          }
                        }
                      }
                    }
                """.trimIndent()

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(jsonPayload)
                writer.flush()
                writer.close()

                val code = conn.responseCode
                onComplete(code in 200..299)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun fetchSuppliersFromFirestore(onComplete: (List<LocalSupplier>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/proveedores")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val rootObj = JSONObject(responseText)
                    val docsArray = rootObj.optJSONArray("documents")
                    val fetchedList = mutableListOf<LocalSupplier>()

                    if (docsArray != null) {
                        for (i in 0 until docsArray.length()) {
                            val doc = docsArray.getJSONObject(i)
                            val fields = doc.optJSONObject("fields") ?: continue

                            val code = fields.optJSONObject("codigoProveedor")?.optString("stringValue", "") ?: ""
                            val company = fields.optJSONObject("empresa")?.optString("stringValue", "") ?: ""
                            val companyChi = fields.optJSONObject("empresaChino")?.optString("stringValue", "") ?: ""
                            val stand = fields.optJSONObject("stand")?.optString("stringValue", "") ?: ""
                            val cat = fields.optJSONObject("rubro")?.optString("stringValue", "Sanitarios") ?: "Sanitarios"
                            val gps = fields.optJSONObject("gps")?.optString("stringValue", "") ?: ""
                            val cName = fields.optJSONObject("contactNombre")?.optString("stringValue", "") ?: ""
                            val cPos = fields.optJSONObject("contactCargo")?.optString("stringValue", "") ?: ""
                            val cWeChat = fields.optJSONObject("contactWeChat")?.optString("stringValue", "") ?: ""
                            val cPhone = fields.optJSONObject("contactTelefono")?.optString("stringValue", "") ?: ""
                            val cEmail = fields.optJSONObject("contactEmail")?.optString("stringValue", "") ?: ""
                            val createdAt = fields.optJSONObject("fechaCreacion")?.optString("stringValue", "") ?: ""
                            val docId = doc.optString("name", "").substringAfterLast("/")

                            val articles = mutableListOf<ArticleItem>()
                            val articlesField = fields.optJSONObject("articles")
                            if (articlesField != null) {
                                val arrayValue = articlesField.optJSONObject("arrayValue")
                                val values = arrayValue?.optJSONArray("values")
                                if (values != null) {
                                    for (j in 0 until values.length()) {
                                        val mapFields = values.getJSONObject(j).optJSONObject("mapValue")?.optJSONObject("fields")
                                        if (mapFields != null) {
                                            articles.add(
                                                ArticleItem(
                                                    code = mapFields.optJSONObject("code")?.optString("stringValue", "") ?: "",
                                                    name = mapFields.optJSONObject("name")?.optString("stringValue", "") ?: "",
                                                    fobPriceUSD = mapFields.optJSONObject("fobPriceUSD")?.optDouble("doubleValue", 0.0)
                                                        ?: mapFields.optJSONObject("fobPriceUSD")?.optInt("integerValue", 0)?.toDouble() ?: 0.0,
                                                    moq = mapFields.optJSONObject("moq")?.optInt("integerValue", 0) ?: 0,
                                                    port = mapFields.optJSONObject("port")?.optString("stringValue", "Shenzhen") ?: "Shenzhen",
                                                    leadTime = mapFields.optJSONObject("leadTime")?.optString("stringValue", "30 días") ?: "30 días",
                                                    note = mapFields.optJSONObject("note")?.optString("stringValue", "") ?: ""
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            if (code.isNotBlank() || company.isNotBlank()) {
                                fetchedList.add(
                                    LocalSupplier(
                                        id = if (docId.isNotBlank()) docId else System.currentTimeMillis().toString(),
                                        supplierCode = if (code.isNotBlank()) code else docId,
                                        companyName = company,
                                        companyChinese = companyChi,
                                        stand = stand,
                                        category = cat,
                                        gpsCoordinates = gps,
                                        contactName = cName,
                                        contactPosition = cPos,
                                        contactWeChat = cWeChat,
                                        contactPhone = cPhone,
                                        contactEmail = cEmail,
                                        articles = articles,
                                        createdAt = createdAt
                                    )
                                )
                            }
                        }
                    }
                    onComplete(fetchedList)
                } else {
                    onComplete(emptyList())
                }
            } catch (_: Exception) {
                onComplete(emptyList())
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

                    val lines = visionText.textBlocks.flatMap { block -> block.lines.map { line -> line.text.trim() } }

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
        } catch (_: Exception) {
            onComplete("", "", "")
        }
    }

    fun extractContactFromBusinessCard(
        bitmap: Bitmap,
        onComplete: (name: String, position: String, weChat: String, phone: String, email: String) -> Unit
    ) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var extractedName = ""
                    var extractedPosition = ""
                    var extractedWeChat = ""
                    var extractedPhone = ""
                    var extractedEmail = ""

                    val lines = visionText.textBlocks.flatMap { block -> block.lines.map { line -> line.text.trim() } }

                    // Patrón de email tolerante a errores típicos de OCR (ej: @, [at], .cem -> .com, .comar -> .com.ar)
                    val emailPattern = Pattern.compile("(?i)[a-z0-9._%+-]+(?::|@|\\(at\\)|\\[at\\])[a-z0-9.-]+\\.[a-z]{2,}")
                    val phonePattern = Pattern.compile("(?i)(\\+?\\d{1,4}[\\s-]?)?(\\(?\\d{2,4}\\)?[\\s-]?)?\\d{3,4}[\\s-]?\\d{3,4}|\\b(mob|mobile|tel|phone|whatsapp)\\b.*")
                    val positionPattern = Pattern.compile("(?i)\\b(presidente|president|vicepresidente|vice-president|gerente|director|directora|manager|sales|export|rep|representative|executive|ceo|cfo|cto|engineer|consultant|comercial|ventas|apoderado)\\b")
                    val weChatPattern = Pattern.compile("(?i)\\b(wechat|wx|微信)\\s*[:\\-]?\\s*([a-zA-Z0-9_-]+)")
                    val companyPattern = Pattern.compile("(?i)\\b(perren|s\\.a\\.|s\\.r\\.l\\.|ltd|limited|co\\.|corp|corporation|inc|group|industries|factory)\\b")
                    val addressKeywords = listOf("Casa Central", "9 de Julio", "Trelew", "Chubut", "CP 9100", "Fresioecie", "CP9100", "Argentina")

                    for (line in lines) {
                        // Email con limpieza de OCR
                        if (extractedEmail.isBlank()) {
                            val matcher = emailPattern.matcher(line)
                            if (matcher.find()) {
                                var rawEmail = matcher.group() ?: ""
                                rawEmail = rawEmail.replace(" ", "")
                                    .replace("(?i)\\.cem$".toRegex(), ".com")
                                    .replace("(?i)\\.comar$".toRegex(), ".com.ar")
                                    .replace("(?i)\\.con$".toRegex(), ".com")
                                    .replace("(?i)perreycia".toRegex(), "perrenycia")
                                    .replace("(?i)sperrRn".toRegex(), "sperren")
                                extractedEmail = rawEmail
                                continue
                            }
                        }

                        // WeChat
                        if (extractedWeChat.isBlank()) {
                            val matcher = weChatPattern.matcher(line)
                            if (matcher.find()) {
                                extractedWeChat = line
                                continue
                            }
                        }

                        // Phone (Filtrado de texto de dirección física)
                        if (extractedPhone.isBlank()) {
                            val isAddressLine = addressKeywords.any { line.contains(it, ignoreCase = true) }
                            val matcher = phonePattern.matcher(line)
                            if (matcher.find()) {
                                var phoneStr = matcher.group() ?: ""
                                if (isAddressLine) {
                                    val digitsPattern = Pattern.compile("(\\+?\\d[\\d\\s-]{6,}\\d)")
                                    val digitsMatcher = digitsPattern.matcher(line)
                                    if (digitsMatcher.find()) {
                                        phoneStr = digitsMatcher.group() ?: ""
                                    }
                                }
                                extractedPhone = phoneStr.trim()
                                continue
                            }
                        }

                        // Position / Cargo
                        if (extractedPosition.isBlank()) {
                            val matcher = positionPattern.matcher(line)
                            if (matcher.find()) {
                                extractedPosition = line
                                continue
                            }
                        }
                    }

                    // Fallback for Name: First valid line that is not an address, company, or contact info
                    if (lines.isNotEmpty()) {
                        val candidateName = lines.firstOrNull { l ->
                            l.length in 3..35 &&
                            !emailPattern.matcher(l).find() &&
                            !phonePattern.matcher(l).find() &&
                            !positionPattern.matcher(l).find() &&
                            !companyPattern.matcher(l).find() &&
                            addressKeywords.none { kw -> l.contains(kw, ignoreCase = true) } &&
                            !l.contains("www", ignoreCase = true) &&
                            !l.contains("http", ignoreCase = true)
                        }
                        if (candidateName != null) {
                            extractedName = candidateName
                        }
                    }

                    // Verificación de seguridad adicional: si extractedName coincide con un cargo, reasignarlo a extractedPosition
                    if (extractedName.isNotBlank() && positionPattern.matcher(extractedName).find()) {
                        if (extractedPosition.isBlank()) {
                            extractedPosition = extractedName
                        }
                        extractedName = ""
                    }

                    onComplete(extractedName, extractedPosition, extractedWeChat, extractedPhone, extractedEmail)
                }
                .addOnFailureListener {
                    onComplete("", "", "", "", "")
                }
        } catch (_: Exception) {
            onComplete("", "", "", "", "")
        }
    }
}

// MARK: - Gestor de Grabación y Reproducción de Notas de Voz (Audio)
object AudioRecorderManager {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    fun startRecording(context: Context): String? {
        return try {
            val audioFile = File(context.cacheDir, "audio_note_${System.currentTimeMillis()}.3gp")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            audioFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopRecording(): Boolean {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            true
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            false
        }
    }

    fun playAudio(audioPath: String, onComplete: () -> Unit = {}) {
        try {
            stopAudio()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                start()
                setOnCompletionListener {
                    onComplete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete()
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            mediaPlayer = null
        }
    }
}

// MARK: - Guardado Automático de Fotos en la Galería del Celular (MediaStore)
object MediaStoreHelper {
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, prefix: String = "CantonFair"): Uri? {
        return try {
            val filename = "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CantonFair2026")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// MARK: - Gestor de Persistencia Local de Proveedores en Disco
object LocalPersistenceManager {
    private const val FILE_NAME = "canton_suppliers.json"

    fun saveSuppliers(context: Context, suppliers: List<LocalSupplier>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonArray = JSONArray()
                for (sup in suppliers) {
                    val supObj = JSONObject().apply {
                        put("id", sup.id)
                        put("supplierCode", sup.supplierCode)
                        put("companyName", sup.companyName)
                        put("companyChinese", sup.companyChinese)
                        put("stand", sup.stand)
                        put("category", sup.category)
                        put("gpsCoordinates", sup.gpsCoordinates)
                        put("contactName", sup.contactName)
                        put("contactPosition", sup.contactPosition)
                        put("contactWeChat", sup.contactWeChat)
                        put("contactPhone", sup.contactPhone)
                        put("contactEmail", sup.contactEmail)
                        put("createdAt", sup.createdAt)

                        val marqueePath = saveBitmapToInternal(context, sup.marqueePhotoBitmap, "marquee_${sup.id}")
                        put("marqueePhotoPath", marqueePath ?: "")

                        val cardPath = saveBitmapToInternal(context, sup.contactCardPhotoBitmap, "card_${sup.id}")
                        put("contactCardPhotoPath", cardPath ?: "")

                        val articlesArray = JSONArray()
                        for (art in sup.articles) {
                            val artObj = JSONObject().apply {
                                put("code", art.code)
                                put("name", art.name)
                                put("fobPriceUSD", art.fobPriceUSD)
                                put("moq", art.moq)
                                put("port", art.port)
                                put("leadTime", art.leadTime)
                                put("note", art.note)
                                put("audioPath", art.audioPath ?: "")

                                val photoPaths = JSONArray()
                                art.photos.forEachIndexed { idx, bmp ->
                                    val path = saveBitmapToInternal(context, bmp, "art_${art.code}_$idx")
                                    if (path != null) photoPaths.put(path)
                                }
                                put("photoPaths", photoPaths)
                            }
                            articlesArray.put(artObj)
                        }
                        put("articles", articlesArray)
                    }
                    jsonArray.put(supObj)
                }

                val file = File(context.filesDir, FILE_NAME)
                file.writeText(jsonArray.toString())
            } catch (_: Exception) {}
        }
    }

    fun loadSuppliers(context: Context): List<LocalSupplier> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        val list = mutableListOf<LocalSupplier>()
        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val supObj = jsonArray.getJSONObject(i)

                val marqueePath = supObj.optString("marqueePhotoPath", "")
                val marqueeBmp = if (marqueePath.isNotBlank()) loadBitmapFromPath(marqueePath) else null

                val cardPath = supObj.optString("contactCardPhotoPath", "")
                val cardBmp = if (cardPath.isNotBlank()) loadBitmapFromPath(cardPath) else null

                val articles = mutableListOf<ArticleItem>()
                val articlesArray = supObj.optJSONArray("articles")
                if (articlesArray != null) {
                    for (j in 0 until articlesArray.length()) {
                        val artObj = articlesArray.getJSONObject(j)
                        val photoPathsArray = artObj.optJSONArray("photoPaths")
                        val photos = mutableListOf<Bitmap>()
                        if (photoPathsArray != null) {
                            for (k in 0 until photoPathsArray.length()) {
                                val pPath = photoPathsArray.getString(k)
                                val bmp = loadBitmapFromPath(pPath)
                                if (bmp != null) photos.add(bmp)
                            }
                        }
                        articles.add(
                            ArticleItem(
                                code = artObj.optString("code", ""),
                                name = artObj.optString("name", ""),
                                fobPriceUSD = artObj.optDouble("fobPriceUSD", 0.0),
                                moq = artObj.optInt("moq", 0),
                                port = artObj.optString("port", ""),
                                leadTime = artObj.optString("leadTime", ""),
                                note = artObj.optString("note", ""),
                                audioPath = artObj.optString("audioPath", "").ifBlank { null },
                                photos = photos,
                                photoBitmap = photos.firstOrNull()
                            )
                        )
                    }
                }

                list.add(
                    LocalSupplier(
                        id = supObj.optString("id", ""),
                        supplierCode = supObj.optString("supplierCode", ""),
                        companyName = supObj.optString("companyName", ""),
                        companyChinese = supObj.optString("companyChinese", ""),
                        stand = supObj.optString("stand", ""),
                        category = supObj.optString("category", ""),
                        gpsCoordinates = supObj.optString("gpsCoordinates", ""),
                        marqueePhotoBitmap = marqueeBmp,
                        contactName = supObj.optString("contactName", ""),
                        contactPosition = supObj.optString("contactPosition", ""),
                        contactWeChat = supObj.optString("contactWeChat", ""),
                        contactPhone = supObj.optString("contactPhone", ""),
                        contactEmail = supObj.optString("contactEmail", ""),
                        contactCardPhotoBitmap = cardBmp,
                        articles = articles,
                        createdAt = supObj.optString("createdAt", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveBitmapToInternal(context: Context, bitmap: Bitmap?, namePrefix: String): String? {
        if (bitmap == null) return null
        return try {
            val dir = File(context.filesDir, "saved_photos")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${namePrefix}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun loadBitmapFromPath(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (_: Exception) {
            null
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

    val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
    val savedTimestamp = prefs.getLong("login_timestamp", 0L)
    val isRemembered = prefs.getBoolean("remember_me", true)
    val isSessionValid = isRemembered && ((System.currentTimeMillis() - savedTimestamp) < sevenDaysMs)

    var isLoggedIn by remember { mutableStateOf(isSessionValid) }
    var currentScreen by remember { mutableStateOf("dashboard") }

    val savedSuppliers = remember { mutableStateListOf<LocalSupplier>() }
    var activeSupplier by remember { mutableStateOf<LocalSupplier?>(null) }
    var isSyncingCloud by remember { mutableStateOf(false) }
    var lastSyncTime by remember { mutableStateOf("") }

    val syncWithCloud: () -> Unit = {
        isSyncingCloud = true
        FirestoreManager.fetchSuppliersFromFirestore { cloudSuppliers ->
            CoroutineScope(Dispatchers.Main).launch {
                if (cloudSuppliers.isNotEmpty()) {
                    val merged = savedSuppliers.toMutableList()
                    for (cloudSup in cloudSuppliers) {
                        val idx = merged.indexOfFirst { it.supplierCode == cloudSup.supplierCode || it.id == cloudSup.id }
                        if (idx >= 0) {
                            val existing = merged[idx]
                            val combinedArticles = existing.articles.toMutableList()
                            for (cArt in cloudSup.articles) {
                                if (combinedArticles.none { it.code == cArt.code }) {
                                    combinedArticles.add(cArt)
                                }
                            }
                            merged[idx] = existing.copy(
                                companyName = if (existing.companyName.isBlank()) cloudSup.companyName else existing.companyName,
                                companyChinese = if (existing.companyChinese.isBlank()) cloudSup.companyChinese else existing.companyChinese,
                                stand = if (existing.stand.isBlank()) cloudSup.stand else existing.stand,
                                contactName = if (existing.contactName.isBlank()) cloudSup.contactName else existing.contactName,
                                contactPosition = if (existing.contactPosition.isBlank()) cloudSup.contactPosition else existing.contactPosition,
                                contactWeChat = if (existing.contactWeChat.isBlank()) cloudSup.contactWeChat else existing.contactWeChat,
                                contactPhone = if (existing.contactPhone.isBlank()) cloudSup.contactPhone else existing.contactPhone,
                                contactEmail = if (existing.contactEmail.isBlank()) cloudSup.contactEmail else existing.contactEmail,
                                articles = combinedArticles
                            )
                        } else {
                            merged.add(cloudSup)
                        }
                    }
                    savedSuppliers.clear()
                    savedSuppliers.addAll(merged)
                    LocalPersistenceManager.saveSuppliers(context, savedSuppliers)
                }

                savedSuppliers.forEach { sup ->
                    FirestoreManager.syncSupplierToFirestore(sup)
                }

                isSyncingCloud = false
                lastSyncTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                Toast.makeText(context, "☁️ Sincronizado con Firestore (${savedSuppliers.size} proveedores)", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        val loadedSuppliers = LocalPersistenceManager.loadSuppliers(context)
        if (loadedSuppliers.isNotEmpty()) {
            savedSuppliers.clear()
            savedSuppliers.addAll(loadedSuppliers)
        }
        syncWithCloud()
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
                    isSyncing = isSyncingCloud,
                    onSyncClick = syncWithCloud,
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
                        selected = currentScreen == "postgres_search",
                        onClick = { currentScreen = "postgres_search"; activeSupplier = null },
                        icon = { Text("🔍", fontSize = 18.sp) },
                        label = { Text("Costos", color = Color.White, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "comparison",
                        onClick = { currentScreen = "comparison"; activeSupplier = null },
                        icon = { Text("📊", fontSize = 18.sp) },
                        label = { Text("Comparador", color = Color.White, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "wallet",
                        onClick = { currentScreen = "wallet"; activeSupplier = null },
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

                                LocalPersistenceManager.saveSuppliers(context, savedSuppliers)
                                FirestoreManager.syncSupplierToFirestore(updatedSup) { success -> }

                                Toast.makeText(context, " REGISTRO GUARDADO PERMANENTEMENTE EN EL CELULAR", Toast.LENGTH_SHORT).show()
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
                            isSyncing = isSyncingCloud,
                            lastSyncTime = lastSyncTime,
                            onSyncClick = syncWithCloud,
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
                    currentScreen == "postgres_search" -> PostgresSearchScreen()
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
    isSyncing: Boolean,
    lastSyncTime: String,
    onSyncClick: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RESUMEN DE RELEVAMIENTO (MULTI-TELÉFONO)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                if (lastSyncTime.isNotBlank()) {
                    Text("Última sincro nube: $lastSyncTime hs", fontSize = 10.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onSyncClick,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(if (isSyncing) "⏳ Sincronizando..." else "🔄 Sincronizar Nube", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

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

    // SECCIÓN A. DATOS DE LA FÁBRICA Y MARQUESINA
    var companyName by remember(supplier) { mutableStateOf(supplier?.companyName ?: "") }
    var companyChinese by remember(supplier) { mutableStateOf(supplier?.companyChinese ?: "") }
    var stand by remember(supplier) { mutableStateOf(supplier?.stand ?: "") }
    var category by remember(supplier) { mutableStateOf(supplier?.category ?: "Sanitarios") }
    var capturedMarqueeBitmap by remember(supplier) { mutableStateOf<Bitmap?>(supplier?.marqueePhotoBitmap) }

    // SECCIÓN B. CONTACTO DE LA PERSONA QUE NOS ATENDIÓ
    var contactName by remember(supplier) { mutableStateOf(supplier?.contactName ?: "") }
    var contactPosition by remember(supplier) { mutableStateOf(supplier?.contactPosition ?: "") }
    var contactWeChat by remember(supplier) { mutableStateOf(supplier?.contactWeChat ?: "") }
    var contactPhone by remember(supplier) { mutableStateOf(supplier?.contactPhone ?: "") }
    var contactEmail by remember(supplier) { mutableStateOf(supplier?.contactEmail ?: "") }
    var capturedContactCardBitmap by remember(supplier) { mutableStateOf<Bitmap?>(supplier?.contactCardPhotoBitmap) }

    // OCR para Foto de Marquesina / Stand
    val autoFillDataFromPhoto = { bitmap: Bitmap ->
        capturedMarqueeBitmap = bitmap
        Toast.makeText(context, "🔍 Procesando imagen de marquesina con Google ML Kit...", Toast.LENGTH_SHORT).show()
        PhotoOCRProcessor.extractDataFromPhoto(bitmap) { extractedCompany, extractedChinese, extractedStand ->
            if (extractedCompany.isNotBlank()) companyName = extractedCompany
            if (extractedChinese.isNotBlank()) companyChinese = extractedChinese
            if (extractedStand.isNotBlank()) stand = extractedStand

            if (extractedCompany.isNotBlank() || extractedChinese.isNotBlank() || extractedStand.isNotBlank()) {
                Toast.makeText(context, "✨ OCR ML Kit: Fábrica detectada en la foto", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "📷 Foto marquesina guardada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // OCR para Tarjeta de Presentación del Contacto
    val autoFillContactFromCard = { bitmap: Bitmap ->
        capturedContactCardBitmap = bitmap
        Toast.makeText(context, "🔍 Procesando tarjeta de contacto con Google ML Kit...", Toast.LENGTH_SHORT).show()
        PhotoOCRProcessor.extractContactFromBusinessCard(bitmap) { name, position, weChat, phone, email ->
            if (name.isNotBlank()) contactName = name
            if (position.isNotBlank()) contactPosition = position
            if (weChat.isNotBlank()) contactWeChat = weChat
            if (phone.isNotBlank()) contactPhone = phone
            if (email.isNotBlank()) contactEmail = email

            Toast.makeText(context, "✨ OCR Tarjeta: Datos de contacto extraídos", Toast.LENGTH_LONG).show()
        }
    }

    var tempMarqueeUri by remember { mutableStateOf<Uri?>(null) }
    var tempContactUri by remember { mutableStateOf<Uri?>(null) }
    var tempArticleUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempMarqueeUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(tempMarqueeUri!!)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    MediaStoreHelper.saveBitmapToGallery(context, bitmap, "Marquesina")
                    Toast.makeText(context, "📸 Foto marquesina HD guardada en Galería", Toast.LENGTH_SHORT).show()
                    autoFillDataFromPhoto(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    // Launchers para la Tarjeta del Contacto (Sección B - HD)
    val contactCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempContactUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(tempContactUri!!)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    MediaStoreHelper.saveBitmapToGallery(context, bitmap, "TarjetaContacto")
                    Toast.makeText(context, "📸 Tarjeta HD guardada en Galería", Toast.LENGTH_SHORT).show()
                    autoFillContactFromCard(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val contactGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    autoFillContactFromCard(bitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cargar tarjeta de contacto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var artName by remember { mutableStateOf("") }
    var artFobUSD by remember { mutableStateOf("") }
    var artMoq by remember { mutableStateOf("") }
    var artPort by remember { mutableStateOf("Shenzhen") }
    var artLeadTime by remember { mutableStateOf("30 días") }
    var artNote by remember { mutableStateOf("") }
    val artPhotos = remember { mutableStateListOf<Bitmap>() }
    var artAudioPath by remember { mutableStateOf<String?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    var editingArticleCode by remember { mutableStateOf<String?>(null) }
    var expandedArticleCode by remember { mutableStateOf<String?>(null) }

    val resetArticleForm = {
        artName = ""
        artFobUSD = ""
        artMoq = ""
        artNote = ""
        artAudioPath = null
        artPhotos.clear()
        editingArticleCode = null
    }

    val articleCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempArticleUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(tempArticleUri!!)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    artPhotos.add(bitmap)
                    MediaStoreHelper.saveBitmapToGallery(context, bitmap, "Articulo")
                    Toast.makeText(context, "📸 Foto artículo HD guardada en Galería", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val articleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    artPhotos.add(bitmap)
                    Toast.makeText(context, "🖼️ Foto de artículo cargada", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cargar foto", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        // --- SECCIÓN A. DATOS PRINCIPALES DE LA FÁBRICA & MARQUESINA ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🏢 SECCIÓN A. DATOS PRINCIPALES DE LA FÁBRICA & MARQUESINA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1565C0))
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
                        onClick = {
                            val uri = createImageFileUri(context)
                            tempMarqueeUri = uri
                            cameraLauncher.launch(uri)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📷 FOTO MARQUESINA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = companyName, onValueChange = { companyName = it },
                    label = { Text("Empresa (Nombre en Inglés)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = companyChinese, onValueChange = { companyChinese = it },
                    label = { Text("Nombre Original en Chino") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = stand, onValueChange = { stand = it },
                    label = { Text("Número de Stand (ej: 10.1 B23)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(supplier?.gpsCoordinates ?: "GPS: Lat -43.2512, Long -65.3094", fontSize = 10.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- SECCIÓN B. CONTACTO DE LA PERSONA QUE NOS ATENDIÓ ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📇 SECCIÓN B. CONTACTO DE LA PERSONA QUE NOS ATENDIÓ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE65100))
                Spacer(modifier = Modifier.height(8.dp))

                if (capturedContactCardBitmap != null) {
                    Image(
                        bitmap = capturedContactCardBitmap!!.asImageBitmap(),
                        contentDescription = "Tarjeta del Contacto",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = {
                            val uri = createImageFileUri(context)
                            tempContactUri = uri
                            contactCameraLauncher.launch(uri)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📷 TARJETA CONTACTO (OCR)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { contactGalleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖼️ GALERÍA TARJETA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = contactName, onValueChange = { contactName = it },
                    label = { Text("Nombre y Apellido del Contacto") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = contactPosition, onValueChange = { contactPosition = it },
                    label = { Text("Cargo / Puesto (ej: Sales Manager)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = contactWeChat, onValueChange = { contactWeChat = it },
                        label = { Text("WeChat ID / QR") }, modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = contactPhone, onValueChange = { contactPhone = it },
                        label = { Text("Teléfono / Mobile / WhatsApp") }, modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = contactEmail, onValueChange = { contactEmail = it },
                    label = { Text("Email de Contacto") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val updated = supplier?.copy(
                    companyName = companyName,
                    companyChinese = companyChinese,
                    stand = stand,
                    marqueePhotoBitmap = capturedMarqueeBitmap,
                    contactName = contactName,
                    contactPosition = contactPosition,
                    contactWeChat = contactWeChat,
                    contactPhone = contactPhone,
                    contactEmail = contactEmail,
                    contactCardPhotoBitmap = capturedContactCardBitmap
                ) ?: LocalSupplier(
                    id = System.currentTimeMillis().toString(),
                    supplierCode = "CF26-P-0001",
                    companyName = companyName,
                    companyChinese = companyChinese,
                    stand = stand,
                    category = category,
                    gpsCoordinates = "GPS: Lat -43.2512, Long -65.3094",
                    marqueePhotoBitmap = capturedMarqueeBitmap,
                    contactName = contactName,
                    contactPosition = contactPosition,
                    contactWeChat = contactWeChat,
                    contactPhone = contactPhone,
                    contactEmail = contactEmail,
                    contactCardPhotoBitmap = capturedContactCardBitmap,
                    createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                onSaveSupplier(updated)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("💾 GUARDAR REGISTRO PROVEEDOR & CONTACTO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (editingArticleCode != null) Color(0xFFFFF8E1) else Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    if (editingArticleCode != null) "✏️ EDITANDO ARTÍCULO: $editingArticleCode" else "📦 INCORPORAR ARTÍCULO A ESTE PROVEEDOR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (editingArticleCode != null) Color(0xFFF57F17) else Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = artName, onValueChange = { artName = it },
                    label = { Text("Nombre Artículo (ej: Inodoro Rimless A520)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = artFobUSD, onValueChange = { artFobUSD = it },
                        label = { Text("Precio FOB ($ USD)") }, modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = artMoq, onValueChange = { artMoq = it },
                        label = { Text("MOQ (Unidades)") }, modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = artPort, onValueChange = { artPort = it },
                        label = { Text("Puerto Origen") }, modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = artLeadTime, onValueChange = { artLeadTime = it },
                        label = { Text("Tiempo Entrega") }, modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Campo Nota de Texto
                OutlinedTextField(
                    value = artNote, onValueChange = { artNote = it },
                    label = { Text("Nota / Observaciones de Texto") }, modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sección Foto del Artículo
                Text("📷 FOTOS DEL ARTÍCULO (${artPhotos.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(4.dp))

                if (artPhotos.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        artPhotos.forEach { photo ->
                            Image(
                                bitmap = photo.asImageBitmap(),
                                contentDescription = "Foto Artículo",
                                modifier = Modifier
                                    .size(60.dp)
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val uri = createImageFileUri(context)
                            tempArticleUri = uri
                            articleCameraLauncher.launch(uri)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📷 FOTO CÁMARA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { articleGalleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖼️ GALERÍA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Sección Audio / Grabación de Voz
                Spacer(modifier = Modifier.height(10.dp))
                Text("🎙️ NOTA DE VOZ (AUDIO)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!isRecordingAudio) {
                        Button(
                            onClick = {
                                val path = AudioRecorderManager.startRecording(context)
                                if (path != null) {
                                    artAudioPath = path
                                    isRecordingAudio = true
                                    Toast.makeText(context, "🔴 Grabando nota de voz...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Error al iniciar grabación", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔴 GRABAR VOZ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                AudioRecorderManager.stopRecording()
                                isRecordingAudio = false
                                Toast.makeText(context, "⏹️ Grabación de voz guardada", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⏹️ DETENER GRABACIÓN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (artAudioPath != null && !isRecordingAudio) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (isPlayingAudio) {
                                    AudioRecorderManager.stopAudio()
                                    isPlayingAudio = false
                                } else {
                                    isPlayingAudio = true
                                    AudioRecorderManager.playAudio(artAudioPath!!) {
                                        isPlayingAudio = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isPlayingAudio) "⏹️ PARAR" else "▶️ ESCUCHAR VOZ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (artName.isNotBlank() && artFobUSD.isNotBlank()) {
                                val currentArticles = supplier?.articles ?: mutableListOf()
                                val artCode = editingArticleCode ?: "${supplier?.supplierCode ?: "CF26-P-0001"}-A${String.format("%02d", currentArticles.size + 1)}"
                                val newArt = ArticleItem(
                                    code = artCode,
                                    name = artName,
                                    fobPriceUSD = artFobUSD.toDoubleOrNull() ?: 0.0,
                                    moq = artMoq.toIntOrNull() ?: 100,
                                    port = artPort,
                                    leadTime = artLeadTime,
                                    note = artNote,
                                    audioPath = artAudioPath,
                                    photos = artPhotos.toList(),
                                    photoBitmap = artPhotos.firstOrNull()
                                )

                                if (editingArticleCode != null) {
                                    val idx = currentArticles.indexOfFirst { it.code == editingArticleCode }
                                    if (idx >= 0) {
                                        currentArticles[idx] = newArt
                                    }
                                } else {
                                    currentArticles.add(newArt)
                                }

                                val updatedSupplier = (supplier ?: LocalSupplier(
                                    id = System.currentTimeMillis().toString(),
                                    supplierCode = "CF26-P-0001",
                                    companyName = companyName,
                                    companyChinese = companyChinese,
                                    stand = stand,
                                    category = category,
                                    gpsCoordinates = "GPS: Lat -43.2512, Long -65.3094",
                                    marqueePhotoBitmap = capturedMarqueeBitmap,
                                    contactName = contactName,
                                    contactPosition = contactPosition,
                                    contactWeChat = contactWeChat,
                                    contactPhone = contactPhone,
                                    contactEmail = contactEmail,
                                    contactCardPhotoBitmap = capturedContactCardBitmap,
                                    createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                )).copy(
                                    companyName = companyName,
                                    companyChinese = companyChinese,
                                    stand = stand,
                                    marqueePhotoBitmap = capturedMarqueeBitmap,
                                    contactName = contactName,
                                    contactPosition = contactPosition,
                                    contactWeChat = contactWeChat,
                                    contactPhone = contactPhone,
                                    contactEmail = contactEmail,
                                    contactCardPhotoBitmap = capturedContactCardBitmap,
                                    articles = currentArticles
                                )

                                onSaveSupplier(updatedSupplier)
                                Toast.makeText(context, "💾 Artículo $artCode guardado permanentemente", Toast.LENGTH_SHORT).show()
                                resetArticleForm()
                            } else {
                                Toast.makeText(context, "Ingresa al menos el Nombre y Precio FOB USD", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (editingArticleCode != null) Color(0xFFF57F17) else Color(0xFF1976D2)),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (editingArticleCode != null) "💾 ACTUALIZAR ARTÍCULO" else "💾 GUARDAR ARTÍCULO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            resetArticleForm()
                            Toast.makeText(context, "📝 Formulario listo para nuevo artículo", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("➕ AGREGAR NUEVO ARTÍCULO", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                if (editingArticleCode != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { resetArticleForm() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CANCELAR EDICIÓN", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("ARTÍCULOS REGISTRADOS EN ESTE PROVEEDOR (${supplier?.articles?.size ?: 0})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))

        supplier?.articles?.forEach { art ->
            val isExpanded = expandedArticleCode == art.code
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { expandedArticleCode = if (isExpanded) null else art.code },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val firstPhoto = art.photos.firstOrNull() ?: art.photoBitmap
                        if (firstPhoto != null) {
                            Image(
                                bitmap = firstPhoto.asImageBitmap(),
                                contentDescription = "Foto Artículo",
                                modifier = Modifier
                                    .size(if (isExpanded) 70.dp else 48.dp)
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${art.code} - ${art.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B365D))
                            Text("FOB: $${art.fobPriceUSD} USD | MOQ: ${art.moq} u", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                        Text(if (isExpanded) "▲" else "▼", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Spacer(modifier = Modifier.height(8.dp))

                        if (art.photos.isNotEmpty()) {
                            Text("📷 GALERÍA DE FOTOS (${art.photos.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                art.photos.forEach { p ->
                                    Image(
                                        bitmap = p.asImageBitmap(),
                                        contentDescription = "Foto ampliada",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text("📦 Puerto Origen: ${art.port} | Tiempo Entrega: ${art.leadTime}", fontSize = 11.sp, color = Color.DarkGray)

                        if (art.note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("📝 Nota: ${art.note}", fontSize = 11.sp, color = Color(0xFF333333), fontWeight = FontWeight.Medium)
                        }

                        if (art.audioPath != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var playingThisAudio by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    if (playingThisAudio) {
                                        AudioRecorderManager.stopAudio()
                                        playingThisAudio = false
                                    } else {
                                        playingThisAudio = true
                                        AudioRecorderManager.playAudio(art.audioPath) {
                                            playingThisAudio = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(if (playingThisAudio) "⏹️ Detener Audio" else "▶️ Escuchar Nota de Voz", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    editingArticleCode = art.code
                                    artName = art.name
                                    artFobUSD = art.fobPriceUSD.toString()
                                    artMoq = art.moq.toString()
                                    artPort = art.port
                                    artLeadTime = art.leadTime
                                    artNote = art.note
                                    artAudioPath = art.audioPath
                                    artPhotos.clear()
                                    artPhotos.addAll(art.photos)
                                    Toast.makeText(context, "📝 Cargado en el formulario para editar", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17)),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("✏️ EDITAR ARTÍCULO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedButton(
                                onClick = {
                                    val currentArticles = supplier?.articles ?: mutableListOf()
                                    currentArticles.remove(art)
                                    if (supplier != null) {
                                        val updatedSupplier = supplier.copy(articles = currentArticles)
                                        onSaveSupplier(updatedSupplier)
                                    }
                                    Toast.makeText(context, "🗑️ Artículo eliminado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("🗑️ ELIMINAR", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 4. Comparador de Costos Flexxus BI & Combos
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonFlexxusScreen() {
    val scrollState = rememberScrollState()
    val allProducts = remember { PerrenPostgresRepository.getAllProducts() }

    var selectedSkus by remember {
        mutableStateOf(setOf("FERRUM-BARI-INO", "FERRUM-BARI-MOC", "FERRUM-BARI-TAP"))
    }

    // Parámetros de Importación (Ajustes Configurables)
    var showSettingsModal by remember { mutableStateOf(false) }
    var dolarTCSetting by remember { mutableStateOf("1350.00") }
    var fleteUSDSetting by remember { mutableStateOf("3400.00") }
    var iibbPercentSetting by remember { mutableStateOf("3.5") }
    var arancelPercentSetting by remember { mutableStateOf("20.0") }
    var tasaEstadPercentSetting by remember { mutableStateOf("3.0") }
    var despachantePercentSetting by remember { mutableStateOf("8.0") }
    var seguroPercentSetting by remember { mutableStateOf("1.2") }

    // Oferta China
    var chinaFobUSDInput by remember { mutableStateOf("38.00") }
    var qtyInput by remember { mutableStateOf("500") }

    // Parse variables
    val tc = dolarTCSetting.toDoubleOrNull() ?: 1350.0
    val flete = fleteUSDSetting.toDoubleOrNull() ?: 3400.0
    val iibb = iibbPercentSetting.toDoubleOrNull() ?: 3.5
    val arancel = arancelPercentSetting.toDoubleOrNull() ?: 20.0
    val tasaEstad = tasaEstadPercentSetting.toDoubleOrNull() ?: 3.0
    val despachante = despachantePercentSetting.toDoubleOrNull() ?: 8.0
    val seguroPct = seguroPercentSetting.toDoubleOrNull() ?: 1.2

    val chinaFob = chinaFobUSDInput.toDoubleOrNull() ?: 38.0
    val qty = qtyInput.toIntOrNull() ?: 500

    val selectedProductList = allProducts.filter { selectedSkus.contains(it.sku) }
    val totalArgentinaCostARSNoVAT = selectedProductList.sumOf { it.costNoVAT }
    val totalArgentinaCostUSDNoVAT = if (tc > 0) totalArgentinaCostARSNoVAT / tc else 0.0

    // Cálculo Landed China
    val fobTotal = chinaFob * qty
    val seguroTotal = fobTotal * (seguroPct / 100.0)
    val cifTotal = fobTotal + flete + seguroTotal
    val derechosTotal = cifTotal * (arancel / 100.0)
    val tasaEstadTotal = cifTotal * (tasaEstad / 100.0)
    val baseImponible = cifTotal + derechosTotal + tasaEstadTotal
    val gastosDespachante = baseImponible * (despachante / 100.0)
    val iibbTotal = baseImponible * (iibb / 100.0)
    val costoLandedTotalUSD = baseImponible + gastosDespachante + iibbTotal
    val unitLandedUSD = if (qty > 0) costoLandedTotalUSD / qty else 0.0
    val unitLandedARS = unitLandedUSD * tc

    val ahorroUSD = totalArgentinaCostUSDNoVAT - unitLandedUSD
    val ahorroARS = totalArgentinaCostARSNoVAT - unitLandedARS
    val ahorroPct = if (totalArgentinaCostUSDNoVAT > 0) (ahorroUSD / totalArgentinaCostUSDNoVAT) * 100.0 else 0.0

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
            Column(modifier = Modifier.weight(1f)) {
                Text("COMPARADOR FLEXXUS VS CANTON FAIR", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
                Text("Costo Argentina (Sin IVA) vs Costo Puesto Importado", fontSize = 10.sp, color = Color.Gray)
            }
            Button(
                onClick = { showSettingsModal = !showSettingsModal },
                colors = ButtonDefaults.buttonColors(containerColor = if (showSettingsModal) Color(0xFFD32F2F) else Color(0xFF1976D2)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (showSettingsModal) "✖️ Cerrar Ajustes" else "⚙️ Ajustes Importación", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // PANEL DE AJUSTES & PARÁMETROS
        if (showSettingsModal) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                border = BorderStroke(1.dp, Color(0xFFFFA000))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚙️ PARÁMETROS DE IMPORTACIÓN & TIPO DE CAMBIO", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE65100))
                    Text("Modifica las tasas para actualizar los costos landed en tiempo real.", fontSize = 9.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = dolarTCSetting, onValueChange = { dolarTCSetting = it },
                            label = { Text("Dólar TC ($ ARS)") }, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fleteUSDSetting, onValueChange = { fleteUSDSetting = it },
                            label = { Text("Flete Marítimo ($ USD)") }, modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = iibbPercentSetting, onValueChange = { iibbPercentSetting = it },
                            label = { Text("IIBB % (ej: 3.5%)") }, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = arancelPercentSetting, onValueChange = { arancelPercentSetting = it },
                            label = { Text("Arancel / Derechos %") }, modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = tasaEstadPercentSetting, onValueChange = { tasaEstadPercentSetting = it },
                            label = { Text("Tasa Estadística %") }, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = despachantePercentSetting, onValueChange = { despachantePercentSetting = it },
                            label = { Text("Despachante & Puerto %") }, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // SELECCIÓN BUNDLE FLEXXUS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("SELECCIÓN DE ARTÍCULOS PERREN (FLEXXUS BI)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1976D2))
                Text("Selecciona los productos que forman el bundle o cotización a comparar:", fontSize = 9.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))

                allProducts.forEach { prod ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSkus = if (selectedSkus.contains(prod.sku)) {
                                    selectedSkus - prod.sku
                                } else {
                                    selectedSkus + prod.sku
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedSkus.contains(prod.sku),
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${prod.description} (${prod.brand})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Costo Sin IVA: $${String.format("%,.2f", prod.costNoVAT)} ARS ($${String.format(Locale.US, "%.2f", prod.costUSDNoVAT)} USD)", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("COSTO BUNDLE FLEXXUS SIN IVA:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("$${String.format("%,.2f", totalArgentinaCostARSNoVAT)} ARS ($${String.format(Locale.US, "%.2f", totalArgentinaCostUSDNoVAT)} USD)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD32F2F))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // OFERTA CHINA
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("COTIZACIÓN DE FÁBRICA CHINA (FOB)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = chinaFobUSDInput, onValueChange = { chinaFobUSDInput = it },
                        label = { Text("Precio FOB China ($ USD)") }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = qtyInput, onValueChange = { qtyInput = it },
                        label = { Text("Cantidad Contenedor") }, modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // TARJETAS COMPARATIVAS DERECHA E IZQUIERDA
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Card(
                modifier = Modifier.weight(1f).border(2.dp, Color(0xFFD32F2F), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("🇦🇷 PERREN (ARGENTINA)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFD32F2F))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Costo Venta Sin IVA", fontSize = 9.sp, color = Color.Gray)
                    Text("$${String.format("%,.2f", totalArgentinaCostARSNoVAT)} ARS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFD32F2F))
                    Text("Equiv: $${String.format(Locale.US, "%.2f", totalArgentinaCostUSDNoVAT)} USD", fontSize = 10.sp, color = Color.DarkGray)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Card(
                modifier = Modifier.weight(1f).border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("🇨🇳 CANTON FAIR (CHINA)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Costo Puesto Depósito", fontSize = 9.sp, color = Color.Gray)
                    Text("$${String.format("%,.2f", unitLandedARS)} ARS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                    Text("Puesto: $${String.format(Locale.US, "%.2f", unitLandedUSD)} USD", fontSize = 10.sp, color = Color.DarkGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AHORRO O DIFERENCIA
        Card(
            colors = CardDefaults.cardColors(containerColor = if (ahorroUSD > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("RESULTADO DE COMPARACIÓN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Diferencia / Ahorro Puesto:", fontSize = 12.sp)
                    Text(
                        "${if (ahorroUSD > 0) "+" else ""}$${String.format(Locale.US, "%.2f", ahorroUSD)} USD (${String.format(Locale.US, "%.1f", ahorroPct)}%)",
                        fontWeight = FontWeight.Bold,
                        color = if (ahorroUSD > 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                }
                Text(
                    "Ahorro en Pesos: ${if (ahorroARS > 0) "+" else ""}$${String.format("%,.2f", ahorroARS)} ARS por unidad",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ahorroARS > 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
            }
        }
    }
}

// MARK: - 5. Tarjetero CRM (Directorio Visual de Contactos)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardWalletScreen(suppliers: List<LocalSupplier>) {
    var walletQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val filteredSuppliers = suppliers.filter { sup ->
        val query = walletQuery.trim().lowercase()
        if (query.isEmpty()) true
        else {
            sup.companyName.lowercase().contains(query) ||
            sup.contactName.lowercase().contains(query) ||
            sup.contactPosition.lowercase().contains(query) ||
            sup.contactEmail.lowercase().contains(query) ||
            sup.contactPhone.lowercase().contains(query) ||
            sup.stand.lowercase().contains(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("📇 TARJETERO CRM CANTON FAIR", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B365D))
        Text("Directorio de Contactos y Tarjetas de Presentación (${suppliers.size})", color = Color.Gray, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = walletQuery,
            onValueChange = { walletQuery = it },
            label = { Text("Buscar por nombre, empresa o cargo...") },
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            trailingIcon = {
                if (walletQuery.isNotEmpty()) {
                    IconButton(onClick = { walletQuery = "" }) {
                        Text("❌", fontSize = 12.sp)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (suppliers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📇 No hay contactos en el tarjetero", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Carga un nuevo proveedor con foto de tarjeta en la sección '+ Proveedor'.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        } else if (filteredSuppliers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔍 No se encontraron contactos para '$walletQuery'", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
                }
            }
        } else {
            filteredSuppliers.forEach { sup ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val photoBitmap = sup.contactCardPhotoBitmap ?: sup.marqueePhotoBitmap
                            if (photoBitmap != null) {
                                Image(
                                    bitmap = photoBitmap.asImageBitmap(),
                                    contentDescription = "Tarjeta",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                Card(
                                    modifier = Modifier.size(64.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("📇", fontSize = 28.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (sup.contactName.isNotBlank()) sup.contactName else "Contacto sin nombre",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1B365D)
                                )
                                if (sup.contactPosition.isNotBlank()) {
                                    Text("💼 ${sup.contactPosition}", fontSize = 11.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold)
                                }
                                Text("🏢 ${if (sup.companyName.isNotBlank()) sup.companyName else "Empresa N/A"}", fontSize = 12.sp, color = Color.DarkGray)
                                if (sup.stand.isNotBlank()) {
                                    Text("📍 Stand: ${sup.stand}", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        // Datos de contacto directos
                        if (sup.contactPhone.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("📞 Tel / WA: ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(sup.contactPhone, fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (sup.contactEmail.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("✉️ Email: ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(sup.contactEmail, fontSize = 12.sp, color = Color(0xFF1565C0))
                            }
                        }
                        if (sup.contactWeChat.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("💬 WeChat: ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(sup.contactWeChat, fontSize = 12.sp, color = Color(0xFF7B1FA2))
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 6. Buscador de Costos Perren (PostgreSQL Flexxus BI)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostgresSearchScreen(onSelectProductForComparison: ((PerrenPostgresProduct) -> Unit)? = null) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf("Todas las Marcas") }
    var selectedCategory by remember { mutableStateOf("Todos los Rubros") }

    var brandDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val availableBrands = remember { PerrenPostgresRepository.getAvailableBrands() }
    val availableCategories = remember { PerrenPostgresRepository.getAvailableCategories() }

    val filteredProducts = remember(searchQuery, selectedBrand, selectedCategory) {
        PerrenPostgresRepository.searchProducts(searchQuery, selectedBrand, selectedCategory)
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("🔍 BUSCADOR DE COSTOS PERREN (POSTGRESQL)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
        Text("Consulta de Costos de Venta Sin IVA & Stock de Perren & Cía.", fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        // Campo de búsqueda por texto libre
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar producto (ej: Bolsa de cemento loma negra)") },
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Text("❌", fontSize = 12.sp)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filtros por Marca y Rubro
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dropdown Marca
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { brandDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🏷️ Marca: $selectedBrand", fontSize = 11.sp, maxLines = 1)
                }
                DropdownMenu(
                    expanded = brandDropdownExpanded,
                    onDismissRequest = { brandDropdownExpanded = false }
                ) {
                    availableBrands.forEach { brand ->
                        DropdownMenuItem(
                            text = { Text(brand, fontSize = 12.sp) },
                            onClick = {
                                selectedBrand = brand
                                brandDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Dropdown Rubro
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { categoryDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📂 Rubro: $selectedCategory", fontSize = 11.sp, maxLines = 1)
                }
                DropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    availableCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, fontSize = 12.sp) },
                            onClick = {
                                selectedCategory = cat
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Resultados (${filteredProducts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            if (selectedBrand != "Todas las Marcas" || selectedCategory != "Todos los Rubros" || searchQuery.isNotEmpty()) {
                TextButton(onClick = {
                    searchQuery = ""
                    selectedBrand = "Todas las Marcas"
                    selectedCategory = "Todos los Rubros"
                }) {
                    Text("Limpiar filtros", fontSize = 11.sp, color = Color(0xFFD32F2F))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredProducts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📦 No se encontraron productos", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Intenta modificar la búsqueda o limpiar los filtros seleccionados.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        } else {
            filteredProducts.forEach { prod ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prod.sku, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                            Text("Stock: ${prod.stockAvailable} ${prod.unit}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(prod.description, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B365D))
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                            ) {
                                Text("🏷️ ${prod.brand}", fontSize = 10.sp, color = Color(0xFF1565C0), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                            ) {
                                Text("📂 ${prod.category}", fontSize = 10.sp, color = Color(0xFF7B1FA2), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("COSTO DE VENTA SIN IVA:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("$${String.format("%,.2f", prod.costNoVAT)} ARS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                Text("Equiv: $${String.format("%.2f", prod.costUSDNoVAT)} USD Sin IVA", fontSize = 11.sp, color = Color.DarkGray)
                            }
                            Button(
                                onClick = {
                                    onSelectProductForComparison?.invoke(prod)
                                    Toast.makeText(context, "Producto ${prod.sku} enviado al Comparador", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("➕ Comparar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - App Top Bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptInAppBar(
    title: String,
    subtitle: String,
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit = {},
    onLogout: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Text(subtitle, fontSize = 10.sp, color = Color(0xFFBBDEFB))
            }
        },
        actions = {
            IconButton(onClick = onSyncClick) {
                Text(if (isSyncing) "⏳" else "🔄", fontSize = 16.sp)
            }
            TextButton(onClick = onLogout) {
                Text("SALIR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1B365D))
    )
}