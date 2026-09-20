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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcanton.ui.theme.AppCantonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.MediaStore
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.compose.ui.window.Dialog

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

data class ImportRow(
    val concept: String,
    val pctStr: String,
    val unitUSD: Double,
    val totalUSD: Double,
    val isHeaderOrSubtotal: Boolean = false,
    val isTotal: Boolean = false,
    val isCostIncidido: Boolean = false
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
    val sku: String,             // Codigo / SKU
    val description: String,     // Descripcion
    val brand: String,           // Marca
    val category: String,        // Rubro
    val subcategory: String = "",// Subrubro
    val classification: String = "", // Categoria
    val abcClass: String = "A",   // Clase ABC (A, B, C) Flexxus BI
    val costNoVAT: Double,       // Costo de venta sin IVA en ARS ($)
    val costUSDNoVAT: Double,     // Costo de venta sin IVA en USD ($)
    val stockAvailable: Int,      // Stock disponible en depósito
    val unit: String = "Unidad",  // Unidad
    val priceVAT: Double = 0.0,   // Precio de venta público con IVA
    val salePriceNoVAT: Double = 0.0 // Precio de venta público sin IVA
)

// MARK: - SQLite Local Database Helper (Almacenamiento Offline Nativo)
class CantonSQLiteHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "canton_local.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_SUPPLIERS = "suppliers"
        const val TABLE_FAVORITES = "favorites"
        const val TABLE_FLEXXUS_PRODUCTS = "flexxus_products"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SUPPLIERS (
                id TEXT PRIMARY KEY,
                supplierCode TEXT,
                companyName TEXT,
                companyChinese TEXT,
                stand TEXT,
                category TEXT,
                gpsCoordinates TEXT,
                contactName TEXT,
                contactPosition TEXT,
                contactWeChat TEXT,
                contactPhone TEXT,
                contactEmail TEXT,
                createdAt TEXT,
                articlesJson TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                sku TEXT PRIMARY KEY
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_FLEXXUS_PRODUCTS (
                sku TEXT PRIMARY KEY,
                description TEXT,
                brand TEXT,
                category TEXT,
                subcategory TEXT,
                classification TEXT,
                abcClass TEXT,
                costNoVAT REAL,
                costUSDNoVAT REAL,
                stockAvailable INTEGER,
                unit TEXT,
                priceVAT REAL,
                salePriceNoVAT REAL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SUPPLIERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FLEXXUS_PRODUCTS")
        onCreate(db)
    }

    fun saveSupplier(supplier: LocalSupplier) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("id", supplier.id)
                put("supplierCode", supplier.supplierCode)
                put("companyName", supplier.companyName)
                put("companyChinese", supplier.companyChinese)
                put("stand", supplier.stand)
                put("category", supplier.category)
                put("gpsCoordinates", supplier.gpsCoordinates)
                put("contactName", supplier.contactName)
                put("contactPosition", supplier.contactPosition)
                put("contactWeChat", supplier.contactWeChat)
                put("contactPhone", supplier.contactPhone)
                put("contactEmail", supplier.contactEmail)
                put("createdAt", supplier.createdAt)

                val articlesArray = JSONArray()
                supplier.articles.forEach { art ->
                    val obj = JSONObject().apply {
                        put("code", art.code)
                        put("name", art.name)
                        put("fobPriceUSD", art.fobPriceUSD)
                        put("moq", art.moq)
                        put("port", art.port)
                        put("leadTime", art.leadTime)
                        put("note", art.note)
                        put("audioPath", art.audioPath ?: "")
                    }
                    articlesArray.put(obj)
                }
                put("articlesJson", articlesArray.toString())
            }
            db.insertWithOnConflict(TABLE_SUPPLIERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {}
    }

    fun getAllSuppliers(): List<LocalSupplier> {
        val list = mutableListOf<LocalSupplier>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_SUPPLIERS, null, null, null, null, null, null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(c.getColumnIndexOrThrow("id"))
                    val code = c.getString(c.getColumnIndexOrThrow("supplierCode"))
                    val company = c.getString(c.getColumnIndexOrThrow("companyName"))
                    val companyChi = c.getString(c.getColumnIndexOrThrow("companyChinese"))
                    val stand = c.getString(c.getColumnIndexOrThrow("stand"))
                    val cat = c.getString(c.getColumnIndexOrThrow("category"))
                    val gps = c.getString(c.getColumnIndexOrThrow("gpsCoordinates"))
                    val cName = c.getString(c.getColumnIndexOrThrow("contactName"))
                    val cPos = c.getString(c.getColumnIndexOrThrow("contactPosition"))
                    val cWeChat = c.getString(c.getColumnIndexOrThrow("contactWeChat"))
                    val cPhone = c.getString(c.getColumnIndexOrThrow("contactPhone"))
                    val cEmail = c.getString(c.getColumnIndexOrThrow("contactEmail"))
                    val createdAt = c.getString(c.getColumnIndexOrThrow("createdAt"))
                    val articlesJsonStr = c.getString(c.getColumnIndexOrThrow("articlesJson"))

                    val articlesList = mutableListOf<ArticleItem>()
                    if (!articlesJsonStr.isNullOrBlank()) {
                        try {
                            val arr = JSONArray(articlesJsonStr)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                articlesList.add(
                                    ArticleItem(
                                        code = obj.optString("code", ""),
                                        name = obj.optString("name", ""),
                                        fobPriceUSD = obj.optDouble("fobPriceUSD", 0.0),
                                        moq = obj.optInt("moq", 0),
                                        port = obj.optString("port", "Shenzhen"),
                                        leadTime = obj.optString("leadTime", "30 días"),
                                        note = obj.optString("note", ""),
                                        audioPath = obj.optString("audioPath", "").ifBlank { null }
                                    )
                                )
                            }
                        } catch (_: Exception) {}
                    }

                    list.add(
                        LocalSupplier(
                            id = id,
                            supplierCode = code,
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
                            articles = articlesList,
                            createdAt = createdAt
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun toggleFavorite(sku: String): Boolean {
        return try {
            val db = writableDatabase
            val cursor = db.query(TABLE_FAVORITES, arrayOf("sku"), "sku = ?", arrayOf(sku), null, null, null)
            val exists = cursor.use { it.moveToFirst() }
            if (exists) {
                db.delete(TABLE_FAVORITES, "sku = ?", arrayOf(sku))
                false
            } else {
                val values = ContentValues().apply { put("sku", sku) }
                db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getFavoriteSkus(): Set<String> {
        val set = mutableSetOf<String>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_FAVORITES, arrayOf("sku"), null, null, null, null, null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    set.add(c.getString(0))
                }
            }
        } catch (_: Exception) {}
        return set
    }

    fun getFlexxusProductCount(): Int {
        var count = 0
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_FLEXXUS_PRODUCTS", null)
            cursor.use { c ->
                if (c.moveToFirst()) {
                    count = c.getInt(0)
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun clearFlexxusProducts() {
        try {
            val db = writableDatabase
            db.execSQL("DELETE FROM $TABLE_FLEXXUS_PRODUCTS")
        } catch (_: Exception) {}
    }

    fun saveFlexxusProductsWithProgress(products: List<PerrenPostgresProduct>, onProgress: ((inserted: Int, total: Int) -> Unit)? = null) {
        try {
            val db = writableDatabase
            val total = products.size
            var inserted = 0
            products.chunked(500).forEach { chunk ->
                db.beginTransaction()
                try {
                    chunk.forEach { prod ->
                        val values = ContentValues().apply {
                            put("sku", prod.sku)
                            put("description", prod.description)
                            put("brand", prod.brand)
                            put("category", prod.category)
                            put("subcategory", prod.subcategory)
                            put("classification", prod.classification)
                            put("abcClass", prod.abcClass)
                            put("costNoVAT", prod.costNoVAT)
                            put("costUSDNoVAT", prod.costUSDNoVAT)
                            put("stockAvailable", prod.stockAvailable)
                            put("unit", prod.unit)
                            put("priceVAT", prod.priceVAT)
                            put("salePriceNoVAT", prod.salePriceNoVAT)
                        }
                        db.insertWithOnConflict(TABLE_FLEXXUS_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        inserted++
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                onProgress?.invoke(inserted, total)
            }
        } catch (_: Exception) {}
    }


    fun getFlexxusProducts(): List<PerrenPostgresProduct> {
        val list = mutableListOf<PerrenPostgresProduct>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_FLEXXUS_PRODUCTS, null, null, null, null, null, null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(
                        PerrenPostgresProduct(
                            sku = c.getString(c.getColumnIndexOrThrow("sku")),
                            description = c.getString(c.getColumnIndexOrThrow("description")),
                            brand = c.getString(c.getColumnIndexOrThrow("brand")),
                            category = c.getString(c.getColumnIndexOrThrow("category")),
                            subcategory = c.getString(c.getColumnIndexOrThrow("subcategory")),
                            classification = c.getString(c.getColumnIndexOrThrow("classification")),
                            abcClass = try { c.getString(c.getColumnIndexOrThrow("abcClass")) } catch(_: Exception) { "A" } ?: "A",
                            costNoVAT = c.getDouble(c.getColumnIndexOrThrow("costNoVAT")),
                            costUSDNoVAT = c.getDouble(c.getColumnIndexOrThrow("costUSDNoVAT")),
                            stockAvailable = c.getInt(c.getColumnIndexOrThrow("stockAvailable")),
                            unit = c.getString(c.getColumnIndexOrThrow("unit")),
                            priceVAT = c.getDouble(c.getColumnIndexOrThrow("priceVAT")),
                            salePriceNoVAT = c.getDouble(c.getColumnIndexOrThrow("salePriceNoVAT"))
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun searchProductsInDb(query: String, selectedBrand: String, selectedCategory: String, selectedAbc: String = "Todas las Clases", limit: Int = 50): List<PerrenPostgresProduct> {
        val list = mutableListOf<PerrenPostgresProduct>()
        try {
            val db = readableDatabase
            val selectionClauses = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (query.trim().length >= 2) {
                val q = "%${query.trim()}%"
                selectionClauses.add("(sku LIKE ? OR description LIKE ? OR brand LIKE ? OR category LIKE ? OR subcategory LIKE ?)")
                args.add(q)
                args.add(q)
                args.add(q)
                args.add(q)
                args.add(q)
            }

            if (selectedBrand.isNotBlank() && selectedBrand != "Todas las Marcas") {
                selectionClauses.add("brand = ?")
                args.add(selectedBrand)
            }

            if (selectedCategory.isNotBlank() && selectedCategory != "Todos los Rubros") {
                selectionClauses.add("category = ?")
                args.add(selectedCategory)
            }

            if (selectedAbc.isNotBlank() && selectedAbc != "Todas las Clases") {
                val letter = when {
                    selectedAbc.contains("Clase A") -> "A"
                    selectedAbc.contains("Clase B") -> "B"
                    selectedAbc.contains("Clase C") -> "C"
                    else -> selectedAbc
                }
                selectionClauses.add("abcClass = ?")
                args.add(letter)
            }

            if (selectionClauses.isEmpty()) {
                return emptyList()
            }

            val selection = selectionClauses.joinToString(" AND ")
            val selectionArgs = args.toTypedArray()

            val cursor = db.query(TABLE_FLEXXUS_PRODUCTS, null, selection, selectionArgs, null, null, null, limit.toString())
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(
                        PerrenPostgresProduct(
                            sku = c.getString(c.getColumnIndexOrThrow("sku")),
                            description = c.getString(c.getColumnIndexOrThrow("description")),
                            brand = c.getString(c.getColumnIndexOrThrow("brand")),
                            category = c.getString(c.getColumnIndexOrThrow("category")),
                            subcategory = c.getString(c.getColumnIndexOrThrow("subcategory")),
                            classification = c.getString(c.getColumnIndexOrThrow("classification")),
                            abcClass = try { c.getString(c.getColumnIndexOrThrow("abcClass")) } catch(_: Exception) { "A" } ?: "A",
                            costNoVAT = c.getDouble(c.getColumnIndexOrThrow("costNoVAT")),
                            costUSDNoVAT = c.getDouble(c.getColumnIndexOrThrow("costUSDNoVAT")),
                            stockAvailable = c.getInt(c.getColumnIndexOrThrow("stockAvailable")),
                            unit = c.getString(c.getColumnIndexOrThrow("unit")),
                            priceVAT = c.getDouble(c.getColumnIndexOrThrow("priceVAT")),
                            salePriceNoVAT = c.getDouble(c.getColumnIndexOrThrow("salePriceNoVAT"))
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun getDistinctBrands(): List<String> {
        val list = mutableListOf<String>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT DISTINCT brand FROM $TABLE_FLEXXUS_PRODUCTS WHERE brand IS NOT NULL AND brand != '' ORDER BY brand ASC", null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(c.getString(0))
                }
            }
        } catch (_: Exception) {}
        return listOf("Todas las Marcas") + list
    }

    fun getDistinctCategories(): List<String> {
        val list = mutableListOf<String>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT DISTINCT category FROM $TABLE_FLEXXUS_PRODUCTS WHERE category IS NOT NULL AND category != '' ORDER BY category ASC", null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(c.getString(0))
                }
            }
        } catch (_: Exception) {}
        return listOf("Todos los Rubros") + list
    }
}

object PerrenPostgresRepository {
    // Conexión en Vivo a través del Gateway / API de Flexxus BI (PostgreSQL)
    var isCrmConnected by mutableStateOf(false)
    var lastConnectionStatus by mutableStateOf("🟢 Base de Datos lista (Catálogo SQLite Flexxus BI Cargado)")

    val loadedCsvProducts = mutableStateListOf<PerrenPostgresProduct>()
    var totalProductCount by mutableStateOf(0)
    var csvLoadedFileName by mutableStateOf("articulos_flexxus_perren.csv")

    var isSyncingProducts by mutableStateOf(false)
    var syncProgressPercentage by mutableStateOf(0)
    var syncProgressText by mutableStateOf("")

    val favoriteProductSkus = mutableStateListOf<String>()

    fun toggleFavorite(sku: String, context: Context) {
        val dbHelper = CantonSQLiteHelper(context)
        val isFav = dbHelper.toggleFavorite(sku)
        if (isFav) {
            if (!favoriteProductSkus.contains(sku)) favoriteProductSkus.add(sku)
        } else {
            favoriteProductSkus.remove(sku)
        }
        val prefs = context.getSharedPreferences("perren_fav_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("fav_skus", favoriteProductSkus.toSet()).apply()
    }

    fun loadFavorites(context: Context) {
        val dbHelper = CantonSQLiteHelper(context)
        val sqliteFavs = dbHelper.getFavoriteSkus()
        favoriteProductSkus.clear()
        if (sqliteFavs.isNotEmpty()) {
            favoriteProductSkus.addAll(sqliteFavs)
        } else {
            val prefs = context.getSharedPreferences("perren_fav_prefs", Context.MODE_PRIVATE)
            val set = prefs.getStringSet("fav_skus", emptySet()) ?: emptySet()
            favoriteProductSkus.addAll(set)
        }
    }

    suspend fun ensureDatabaseLoaded(context: Context) = withContext(Dispatchers.IO) {
        val dbHelper = CantonSQLiteHelper(context)
        val count = dbHelper.getFlexxusProductCount()
        if (count > 0) {
            withContext(Dispatchers.Main) {
                totalProductCount = count
                lastConnectionStatus = "🟢 Base de Datos SQLite lista ($count artículos)"
            }
        } else {
            syncDatabaseFromOnline(context)
        }
    }

    suspend fun syncDatabaseFromOnline(context: Context, onComplete: ((Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            isSyncingProducts = true
            syncProgressPercentage = 0
            syncProgressText = "Leyendo catálogo Flexxus..."
        }
        try {
            val inputStream = context.assets.open("articulos_flexxus_perren.csv")
            val text = inputStream.bufferedReader().use { it.readText() }
            withContext(Dispatchers.Main) {
                syncProgressPercentage = 10
                syncProgressText = "Procesando 12.074 productos de Flexxus..."
            }
            val products = loadProductsFromCsvContent(text)
            if (products.isNotEmpty()) {
                val dbHelper = CantonSQLiteHelper(context)
                dbHelper.clearFlexxusProducts()
                dbHelper.saveFlexxusProductsWithProgress(products) { inserted, total ->
                    val pct = 10 + ((inserted.toFloat() / total.toFloat()) * 90f).toInt()
                    CoroutineScope(Dispatchers.Main).launch {
                        syncProgressPercentage = pct
                        syncProgressText = "$inserted / $total artículos guardados en SQLite..."
                    }
                }
                val count = dbHelper.getFlexxusProductCount()
                withContext(Dispatchers.Main) {
                    totalProductCount = count
                    lastConnectionStatus = "🟢 Base de Datos SQLite sincronizada ($count artículos)"
                    isSyncingProducts = false
                    onComplete?.invoke(count)
                }
            } else {
                withContext(Dispatchers.Main) {
                    isSyncingProducts = false
                    onComplete?.invoke(0)
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                isSyncingProducts = false
                onComplete?.invoke(0)
            }
        }
    }

    fun loadProductsFromCsvContent(csvText: String): List<PerrenPostgresProduct> {
        val list = mutableListOf<PerrenPostgresProduct>()
        val lines = csvText.lines()
        if (lines.size <= 1) return list

        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val tokens = parseCsvLine(line)
            if (tokens.size >= 5) {
                val sku = tokens.getOrElse(0) { "" }.trim()
                val desc = tokens.getOrElse(1) { "" }.trim()
                val brand = tokens.getOrElse(2) { "" }.trim()
                val cat = tokens.getOrElse(3) { "" }.trim()
                val subcat = if (tokens.size >= 10) tokens.getOrElse(4) { "" }.trim() else ""
                val classification = if (tokens.size >= 10) tokens.getOrElse(5) { "" }.trim() else ""

                val hasAbc = tokens.size >= 11
                val abcClass = if (hasAbc) tokens.getOrElse(6) { "A" }.trim().ifBlank { "A" } else "A"

                val costIdx = if (hasAbc) 7 else (if (tokens.size >= 10) 6 else 4)
                val saleIdx = if (hasAbc) 8 else (if (tokens.size >= 10) 7 else 5)
                val stockIdx = if (hasAbc) 9 else (if (tokens.size >= 10) 8 else 6)
                val unitIdx = if (hasAbc) 10 else (if (tokens.size >= 10) 9 else 7)

                val costNoVat = tokens.getOrElse(costIdx) { "0" }.replace("$", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                val saleNoVat = tokens.getOrElse(saleIdx) { "0" }.replace("$", "").replace(",", "").trim().toDoubleOrNull() ?: (costNoVat * 1.40)
                val stock = tokens.getOrElse(stockIdx) { "100" }.trim().toIntOrNull() ?: 100
                val unit = tokens.getOrElse(unitIdx) { "Unidad" }.trim().ifBlank { "Unidad" }

                list.add(
                    PerrenPostgresProduct(
                        sku = sku,
                        description = desc,
                        brand = brand,
                        category = cat,
                        subcategory = subcat,
                        classification = classification,
                        abcClass = abcClass,
                        costNoVAT = costNoVat,
                        costUSDNoVAT = costNoVat / 1350.0,
                        stockAvailable = stock,
                        unit = unit,
                        priceVAT = saleNoVat * 1.21,
                        salePriceNoVAT = saleNoVat
                    )
                )
            }
        }
        return list
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString())
                cur.clear()
            } else {
                cur.append(ch)
            }
        }
        result.add(cur.toString())
        return result
    }

    fun syncWithCrmBackend(onComplete: (Boolean) -> Unit = {}) {
        onComplete(true)
    }

    private val perrenMasterCatalog = listOf(
        // CEMENTOS & CONSTRUCCIÓN (LOMA NEGRA & AVELLANEDA & BRICKS)
        PerrenPostgresProduct(sku = "LADR-HUE-12", description = "Ladrillo Cerámico Hueco 12x18x33 (6 Agujeros)", brand = "Palmar", category = "Construcción", subcategory = "Ladrillos", classification = "Materiales Obra Gruesa", costNoVAT = 480.0, costUSDNoVAT = 0.36, stockAvailable = 15000, unit = "Unidad", priceVAT = 850.0, salePriceNoVAT = 702.48),
        PerrenPostgresProduct(sku = "LADR-HUE-18", description = "Ladrillo Cerámico Hueco 18x18x33 Portante (9 Agujeros)", brand = "Palmar", category = "Construcción", subcategory = "Ladrillos", classification = "Materiales Obra Gruesa", costNoVAT = 720.0, costUSDNoVAT = 0.53, stockAvailable = 12000, unit = "Unidad", priceVAT = 1250.0, salePriceNoVAT = 1033.06),
        PerrenPostgresProduct(sku = "LADR-COM-01", description = "Ladrillo Común de Campo 1ª Calidad (Construcción)", brand = "El Palmar", category = "Construcción", subcategory = "Ladrillos", classification = "Materiales Obra Gruesa", costNoVAT = 180.0, costUSDNoVAT = 0.13, stockAvailable = 35000, unit = "Unidad", priceVAT = 320.0, salePriceNoVAT = 264.46),
        PerrenPostgresProduct(sku = "LADR-RET-10", description = "Ladrillo Retak HCCA 10x25x50cm Concreto Celular", brand = "Retak", category = "Construcción", subcategory = "Ladrillos", classification = "Materiales Obra Gruesa", costNoVAT = 2900.0, costUSDNoVAT = 2.15, stockAvailable = 3400, unit = "Unidad", priceVAT = 5100.0, salePriceNoVAT = 4214.88),
        PerrenPostgresProduct(sku = "LADR-REF-01", description = "Ladrillo Refractario 22.9x11.4x6.3cm para Parrilla", brand = "Refractarios BI", category = "Construcción", subcategory = "Ladrillos", classification = "Materiales Obra Gruesa", costNoVAT = 1450.0, costUSDNoVAT = 1.07, stockAvailable = 2800, unit = "Unidad", priceVAT = 2600.0, salePriceNoVAT = 2148.76),
        PerrenPostgresProduct(sku = "LN-CEM-50", description = "Bolsa de Cemento Loma Negra 50kg Estándar", brand = "Loma Negra", category = "Construcción", subcategory = "Cementos y Cales", classification = "Materiales Obra Gruesa", costNoVAT = 7850.0, costUSDNoVAT = 5.81, stockAvailable = 2400, unit = "Bolsa", priceVAT = 13800.0, salePriceNoVAT = 11404.96),
        PerrenPostgresProduct(sku = "LN-CEM-25", description = "Bolsa de Cemento Loma Negra 25kg Rapidito", brand = "Loma Negra", category = "Construcción", subcategory = "Cementos y Cales", classification = "Materiales Obra Gruesa", costNoVAT = 4200.0, costUSDNoVAT = 3.11, stockAvailable = 1150, unit = "Bolsa", priceVAT = 7400.0, salePriceNoVAT = 6115.70),
        PerrenPostgresProduct(sku = "LN-CAL-25", description = "Cal Hidratada Loma Negra Calsid 25kg", brand = "Loma Negra", category = "Construcción", subcategory = "Cementos y Cales", classification = "Materiales Obra Gruesa", costNoVAT = 3100.0, costUSDNoVAT = 2.30, stockAvailable = 890, unit = "Bolsa", priceVAT = 5500.0, salePriceNoVAT = 4545.45),
        PerrenPostgresProduct(sku = "LN-MAS-30", description = "Pastina Loma Negra Plastocor 30kg", brand = "Loma Negra", category = "Construcción", subcategory = "Cementos y Cales", classification = "Materiales Obra Gruesa", costNoVAT = 5200.0, costUSDNoVAT = 3.85, stockAvailable = 620, unit = "Bolsa", priceVAT = 9100.0, salePriceNoVAT = 7520.66),
        PerrenPostgresProduct(sku = "TUY-YES-30", description = "Bolsa de Yeso Tuyango Tradicional 30kg", brand = "Tuyango", category = "Construcción", subcategory = "Cementos y Cales", classification = "Materiales Obra Gruesa", costNoVAT = 6400.0, costUSDNoVAT = 4.74, stockAvailable = 450, unit = "Bolsa", priceVAT = 11200.0, salePriceNoVAT = 9256.20),

        // ESPEJOS, ABERTURAS & SANITARIOS (MODENA, OBLAK, FERRUM, ROCA)
        PerrenPostgresProduct(sku = "ESP-BOT-5070", description = "Espejo Botiquín Biselado Flotante 50x70cm para Baño", brand = "Ferrum", category = "Sanitarios", subcategory = "Espejos y Accesorios", classification = "Equipamiento Baño", costNoVAT = 32000.0, costUSDNoVAT = 23.70, stockAvailable = 140, unit = "Unidad", priceVAT = 56000.0, salePriceNoVAT = 46280.99),
        PerrenPostgresProduct(sku = "ESP-LED-6080", description = "Espejo Touch con Luz LED Marco de Aluminio 60x80cm", brand = "Ferrum", category = "Sanitarios", subcategory = "Espejos y Accesorios", classification = "Equipamiento Baño", costNoVAT = 58000.0, costUSDNoVAT = 42.96, stockAvailable = 95, unit = "Unidad", priceVAT = 102000.0, salePriceNoVAT = 84297.52),
        PerrenPostgresProduct(sku = "VENT-ALU-120", description = "Ventana de Aluminio Blanco Modena 120x100cm Vidrio Entero", brand = "Modena", category = "Aberturas", subcategory = "Ventanas", classification = "Aberturas Aluminio", costNoVAT = 95000.0, costUSDNoVAT = 70.37, stockAvailable = 85, unit = "Unidad", priceVAT = 168000.0, salePriceNoVAT = 138842.98),
        PerrenPostgresProduct(sku = "VENT-GUI-60", description = "Ventana Guillotina 60x100cm Aluminio Anodizado", brand = "Modena", category = "Aberturas", subcategory = "Ventanas", classification = "Aberturas Aluminio", costNoVAT = 68000.0, costUSDNoVAT = 50.37, stockAvailable = 120, unit = "Unidad", priceVAT = 120000.0, salePriceNoVAT = 99173.55),
        PerrenPostgresProduct(sku = "PUE-INY-80200", description = "Puerta Exterior Inyectada Oblak 80x200cm con Barral", brand = "Oblak", category = "Aberturas", subcategory = "Puertas", classification = "Aberturas Exterior", costNoVAT = 142000.0, costUSDNoVAT = 105.18, stockAvailable = 60, unit = "Unidad", priceVAT = 248000.0, salePriceNoVAT = 204958.68),
        PerrenPostgresProduct(sku = "FERRUM-BARI-INO", description = "Inodoro Blanco De Pie Ferrum Bari Short", brand = "Ferrum", category = "Sanitarios", subcategory = "Loza Sanitaria", classification = "Equipamiento Baño", costNoVAT = 55000.0, costUSDNoVAT = 40.74, stockAvailable = 320, unit = "Unidad", priceVAT = 97000.0, salePriceNoVAT = 80165.29),
        PerrenPostgresProduct(sku = "FERRUM-BARI-MOC", description = "Mochila Depósito Apoyo Ferrum Bari Dual 3/6L", brand = "Ferrum", category = "Sanitarios", subcategory = "Loza Sanitaria", classification = "Equipamiento Baño", costNoVAT = 28000.0, costUSDNoVAT = 20.74, stockAvailable = 280, unit = "Unidad", priceVAT = 49000.0, salePriceNoVAT = 40495.87),
        PerrenPostgresProduct(sku = "FERRUM-BARI-TAP", description = "Tapa Asiento Inodoro Ferrum Bari Cierre Suave", brand = "Ferrum", category = "Sanitarios", subcategory = "Loza Sanitaria", classification = "Equipamiento Baño", costNoVAT = 15000.0, costUSDNoVAT = 11.11, stockAvailable = 450, unit = "Unidad", priceVAT = 26500.0, salePriceNoVAT = 21900.83),
        PerrenPostgresProduct(sku = "FERRUM-VEN-INO", description = "Inodoro Blanco Largo Ferrum Venezia Premium", brand = "Ferrum", category = "Sanitarios", subcategory = "Loza Sanitaria", classification = "Equipamiento Baño", costNoVAT = 89000.0, costUSDNoVAT = 65.92, stockAvailable = 110, unit = "Unidad", priceVAT = 156000.0, salePriceNoVAT = 128925.62),
        PerrenPostgresProduct(sku = "FERRUM-MAYO-BID", description = "Bidé 1 Agujero Ferrum Mayo Blanco", brand = "Ferrum", category = "Sanitarios", subcategory = "Loza Sanitaria", classification = "Equipamiento Baño", costNoVAT = 38000.0, costUSDNoVAT = 28.14, stockAvailable = 190, unit = "Unidad", priceVAT = 67000.0, salePriceNoVAT = 55371.90),

        // GRIFERÍA, TERMOTANQUES & PLOMERÍA (FV, RHEEM, AQUA SYSTEM, TIGRE, IPS, AWADUCT)
        PerrenPostgresProduct(sku = "TER-ELE-85L", description = "Termotanque Eléctrico Señorial 85 Litros Alta Recuperación", brand = "Señorial", category = "Plomería y Agua", subcategory = "Termotanques", classification = "Instalaciones Agua", costNoVAT = 135000.0, costUSDNoVAT = 100.0, stockAvailable = 75, unit = "Unidad", priceVAT = 238000.0, salePriceNoVAT = 196694.21),
        PerrenPostgresProduct(sku = "TER-GAS-120L", description = "Termotanque a Gas Rheem 120 Litros Pie", brand = "Rheem", category = "Plomería y Agua", subcategory = "Termotanques", classification = "Instalaciones Agua", costNoVAT = 215000.0, costUSDNoVAT = 159.25, stockAvailable = 40, unit = "Unidad", priceVAT = 378000.0, salePriceNoVAT = 312396.69),
        PerrenPostgresProduct(sku = "FV-GRIF-LAV-01", description = "Monocomando Lavatorio FV Arizona Cromo", brand = "FV", category = "Grifería", subcategory = "Griferías Baño", classification = "Griferías Monocomando", costNoVAT = 42000.0, costUSDNoVAT = 31.11, stockAvailable = 530, unit = "Unidad", priceVAT = 74000.0, salePriceNoVAT = 61157.02),
        PerrenPostgresProduct(sku = "FV-GRIF-COC-02", description = "Monocomando Cocina Pico Alto FV Swing Cromo", brand = "FV", category = "Grifería", subcategory = "Griferías Cocina", classification = "Griferías Monocomando", costNoVAT = 64000.0, costUSDNoVAT = 47.40, stockAvailable = 210, unit = "Unidad", priceVAT = 112000.0, salePriceNoVAT = 92561.98),
        PerrenPostgresProduct(sku = "FV-GRIF-DUCH-03", description = "Juego de Ducha con Transferencia FV Temple", brand = "FV", category = "Grifería", subcategory = "Griferías Ducha", classification = "Griferías Monocomando", costNoVAT = 95000.0, costUSDNoVAT = 70.37, stockAvailable = 140, unit = "Unidad", priceVAT = 166000.0, salePriceNoVAT = 137190.08),
        PerrenPostgresProduct(sku = "TF-COD-90-20", description = "Codo 90° Termofusión 20mm Agua Fría / Caliente", brand = "Aqua System", category = "Plomería y Agua", subcategory = "Cañerías y Conexiones", classification = "Instalaciones Termofusión", costNoVAT = 1250.0, costUSDNoVAT = 0.92, stockAvailable = 1800, unit = "Unidad", priceVAT = 2200.0, salePriceNoVAT = 1818.18),
        PerrenPostgresProduct(sku = "TF-COD-90-25", description = "Codo 90° Termofusión 25mm Agua Fría / Caliente", brand = "Aqua System", category = "Plomería y Agua", subcategory = "Cañerías y Conexiones", classification = "Instalaciones Termofusión", costNoVAT = 1680.0, costUSDNoVAT = 1.24, stockAvailable = 1400, unit = "Unidad", priceVAT = 2950.0, salePriceNoVAT = 2438.02),
        PerrenPostgresProduct(sku = "PVC-COD-90-110", description = "Codo 90° PVC Sanitarios 110mm Roscado / Pegar", brand = "Tigre", category = "Plomería y Agua", subcategory = "Cañerías Cloacales", classification = "Instalaciones Sanitarias", costNoVAT = 3450.0, costUSDNoVAT = 2.55, stockAvailable = 950, unit = "Unidad", priceVAT = 6100.0, salePriceNoVAT = 5041.32),
        PerrenPostgresProduct(sku = "BRO-COD-12", description = "Codo de Bronce 1/2 H-H Rosca IPS", brand = "IPS", category = "Plomería y Agua", subcategory = "Cañerías y Conexiones", classification = "Instalaciones Bronce", costNoVAT = 2900.0, costUSDNoVAT = 2.14, stockAvailable = 620, unit = "Unidad", priceVAT = 5100.0, salePriceNoVAT = 4214.88),
        PerrenPostgresProduct(sku = "TF-CAN-20", description = "Caño Termofusión 20mm PN20 x 4 Metros", brand = "Aqua System", category = "Plomería y Agua", subcategory = "Cañerías y Conexiones", classification = "Instalaciones Termofusión", costNoVAT = 4800.0, costUSDNoVAT = 3.55, stockAvailable = 2100, unit = "Tira", priceVAT = 8450.0, salePriceNoVAT = 6983.47),
        PerrenPostgresProduct(sku = "PVC-CAN-110", description = "Caño PVC Cloacal 110mm x 4 Metros Sanitarios", brand = "Tigre", category = "Plomería y Agua", subcategory = "Cañerías Cloacales", classification = "Instalaciones Sanitarias", costNoVAT = 9800.0, costUSDNoVAT = 7.25, stockAvailable = 820, unit = "Tira", priceVAT = 17200.0, salePriceNoVAT = 14214.88),
        PerrenPostgresProduct(sku = "VALV-ESF-12", description = "Válvula de Esfera de Bronce 1/2 Paso Total", brand = "FV", category = "Plomería y Agua", subcategory = "Válvulas y Llaves", classification = "Instalaciones Bronce", costNoVAT = 8500.0, costUSDNoVAT = 6.29, stockAvailable = 740, unit = "Unidad", priceVAT = 14900.0, salePriceNoVAT = 12314.05),
        PerrenPostgresProduct(sku = "FLEX-INOX-12", description = "Flexible Acero Inoxidable 1/2 x 35cm M-H", brand = "FV", category = "Plomería y Agua", subcategory = "Flexibles y Conexiones", classification = "Instalaciones Agua", costNoVAT = 3200.0, costUSDNoVAT = 2.37, stockAvailable = 1100, unit = "Unidad", priceVAT = 5600.0, salePriceNoVAT = 4628.10),
        PerrenPostgresProduct(sku = "SIG-COD-110", description = "Codo 90° con Acometida Awaduct / Duratop 110mm", brand = "Awaduct", category = "Plomería y Agua", subcategory = "Cañerías Cloacales", classification = "Instalaciones Sanitarias", costNoVAT = 5600.0, costUSDNoVAT = 4.14, stockAvailable = 530, unit = "Unidad", priceVAT = 9850.0, salePriceNoVAT = 8140.50),
        PerrenPostgresProduct(sku = "IPS-TE-34", description = "Tee 90° Rosca 3/4 IPS Bronce Inserto", brand = "IPS", category = "Plomería y Agua", subcategory = "Cañerías y Conexiones", classification = "Instalaciones Bronce", costNoVAT = 3890.0, costUSDNoVAT = 2.88, stockAvailable = 410, unit = "Unidad", priceVAT = 6850.0, salePriceNoVAT = 5661.16),

        // CHAPAS & METALÚRGICA (TERNIUM & CINCALUM & ACINDAR)
        PerrenPostgresProduct(sku = "CHA-GAL-C27", description = "Chapa Galvanizada Acanalada C-27 1.10x6m", brand = "Ternium", category = "Metalúrgica", subcategory = "Chapas", classification = "Techado y Estructura", costNoVAT = 24500.0, costUSDNoVAT = 18.15, stockAvailable = 620, unit = "Hoja", priceVAT = 43000.0, salePriceNoVAT = 35537.19),
        PerrenPostgresProduct(sku = "CHA-CIN-T101", description = "Chapa Cincalum Trapezoidal T-101 C-25 1.10x6m", brand = "Ternium", category = "Metalúrgica", subcategory = "Chapas", classification = "Techado y Estructura", costNoVAT = 31200.0, costUSDNoVAT = 23.11, stockAvailable = 480, unit = "Hoja", priceVAT = 54800.0, salePriceNoVAT = 45289.26),
        PerrenPostgresProduct(sku = "CHA-PRE-NEG", description = "Chapa Prepintada Negra C-25 1.10x6m", brand = "Ternium", category = "Metalúrgica", subcategory = "Chapas", classification = "Techado y Estructura", costNoVAT = 38900.0, costUSDNoVAT = 28.81, stockAvailable = 310, unit = "Hoja", priceVAT = 68400.0, salePriceNoVAT = 56528.93),
        PerrenPostgresProduct(sku = "PER-C-100", description = "Perfil C Galvanizado 100x50x2mm x 6 metros", brand = "Acindar", category = "Metalúrgica", subcategory = "Perfiles", classification = "Techado y Estructura", costNoVAT = 28900.0, costUSDNoVAT = 21.40, stockAvailable = 850, unit = "Tira", priceVAT = 50800.0, salePriceNoVAT = 41983.47),
        PerrenPostgresProduct(sku = "HIE-ALE-8MM", description = "Hierro Aletado Acindar 8mm x 12 metros", brand = "Acindar", category = "Metalúrgica", subcategory = "Hierros y Mallas", classification = "Materiales Estructurales", costNoVAT = 9500.0, costUSDNoVAT = 7.03, stockAvailable = 1400, unit = "Varilla", priceVAT = 16700.0, salePriceNoVAT = 13801.65),
        PerrenPostgresProduct(sku = "HIE-ALE-10MM", description = "Hierro Aletado Acindar 10mm x 12 metros", brand = "Acindar", category = "Metalúrgica", subcategory = "Hierros y Mallas", classification = "Materiales Estructurales", costNoVAT = 14800.0, costUSDNoVAT = 10.96, stockAvailable = 1100, unit = "Varilla", priceVAT = 26000.0, salePriceNoVAT = 21487.60),
        PerrenPostgresProduct(sku = "MAL-SIM-15", description = "Malla Sima 15x15 5mm Panel 2.00x3.00m", brand = "Acindar", category = "Metalúrgica", subcategory = "Hierros y Mallas", classification = "Materiales Estructurales", costNoVAT = 22400.0, costUSDNoVAT = 16.59, stockAvailable = 520, unit = "Panel", priceVAT = 39400.0, salePriceNoVAT = 32561.98),

        // TANQUES, BOMBAS, PINTURAS, HERRAMIENTAS & ACCESORIOS (ROTOPLAS, CZERWENY, ALBA, ORMIFLEX)
        PerrenPostgresProduct(sku = "TAN-ROT-1000", description = "Tanque de Agua Rotoplas Multicapa 1000 Litros", brand = "Rotoplas", category = "Tanques y Bombas", subcategory = "Tanques", classification = "Almacenamiento Agua", costNoVAT = 115000.0, costUSDNoVAT = 85.18, stockAvailable = 120, unit = "Unidad", priceVAT = 202000.0, salePriceNoVAT = 166942.15),
        PerrenPostgresProduct(sku = "BOM-CZ-05HP", description = "Bomba Periférica Czerweny 1/2 HP Agua Elevación", brand = "Czerweny", category = "Tanques y Bombas", subcategory = "Bombas", classification = "Bombeo y Elevación", costNoVAT = 48000.0, costUSDNoVAT = 35.55, stockAvailable = 230, unit = "Unidad", priceVAT = 84500.0, salePriceNoVAT = 69834.71),
        PerrenPostgresProduct(sku = "ALB-LAT-20L", description = "Pintura Látex Interior Exterior Alba Albalatex 20L", brand = "Alba", category = "Pinturas", subcategory = "Látex Interior Exterior", classification = "Acabados y Pintura", costNoVAT = 62000.0, costUSDNoVAT = 45.92, stockAvailable = 340, unit = "Balde", priceVAT = 109000.0, salePriceNoVAT = 90082.64),
        PerrenPostgresProduct(sku = "MEM-ORM-4MM", description = "Membrana Asfáltica Ormiflex 40kg 4mm Aluminio 10m2", brand = "Ormiflex", category = "Aislaciones", subcategory = "Membranas Techos", classification = "Impermeabilizantes", costNoVAT = 42000.0, costUSDNoVAT = 31.11, stockAvailable = 410, unit = "Rollo", priceVAT = 73900.0, salePriceNoVAT = 61074.38),
        PerrenPostgresProduct(sku = "WEBER-COL-IMP", description = "Adhesivo Weber Impermeable para Cerámicos 30kg", brand = "Weber", category = "Adhesivos", subcategory = "Pegamentos Cerámicos", classification = "Adhesivos y Pastinas", costNoVAT = 8900.0, costUSDNoVAT = 6.59, stockAvailable = 1600, unit = "Bolsa", priceVAT = 15600.0, salePriceNoVAT = 12892.56),
        PerrenPostgresProduct(sku = "WEBER-PAS-BLA", description = "Pastina Weber Blanco Nieve 2kg Impermeable", brand = "Weber", category = "Adhesivos", subcategory = "Pastinas Impermeables", classification = "Adhesivos y Pastinas", costNoVAT = 2300.0, costUSDNoVAT = 1.70, stockAvailable = 940, unit = "Unidad", priceVAT = 4050.0, salePriceNoVAT = 3347.11),
        PerrenPostgresProduct(sku = "KLAU-ADH-POR", description = "Pegamento Klaukol Porcellanato Fluido 30kg", brand = "Klaukol", category = "Adhesivos", subcategory = "Pegamentos Porcellanato", classification = "Adhesivos y Pastinas", costNoVAT = 14500.0, costUSDNoVAT = 10.74, stockAvailable = 780, unit = "Bolsa", priceVAT = 25500.0, salePriceNoVAT = 21074.38),
        PerrenPostgresProduct(sku = "KLAU-ADH-STD", description = "Klaukol Tradicional Bolsa 30kg", brand = "Klaukol", category = "Adhesivos", subcategory = "Pegamentos Cerámicos", classification = "Adhesivos y Pastinas", costNoVAT = 9800.0, costUSDNoVAT = 7.25, stockAvailable = 1200, unit = "Bolsa", priceVAT = 17200.0, salePriceNoVAT = 14214.88),
        PerrenPostgresProduct(sku = "SP-PORC-60X60", description = "Porcellanato San Pietro Marmi Carrara 60x60 M2", brand = "San Pietro", category = "Revestimientos", subcategory = "Porcellanatos", classification = "Pisos y Revestimientos", costNoVAT = 18500.0, costUSDNoVAT = 13.70, stockAvailable = 3200, unit = "m²", priceVAT = 32500.0, salePriceNoVAT = 26859.50),
        PerrenPostgresProduct(sku = "SP-PORC-80X80", description = "Porcellanato San Pietro Concrete Grey 80x80 M2", brand = "San Pietro", category = "Revestimientos", subcategory = "Porcellanatos", classification = "Pisos y Revestimientos", costNoVAT = 24900.0, costUSDNoVAT = 18.44, stockAvailable = 1850, unit = "m²", priceVAT = 43800.0, salePriceNoVAT = 36198.35),
        PerrenPostgresProduct(sku = "CN-PORC-60X60", description = "Porcellanato Cerro Negro Madera Roble 60x60 M2", brand = "Cerro Negro", category = "Revestimientos", subcategory = "Porcellanatos", classification = "Pisos y Revestimientos", costNoVAT = 16800.0, costUSDNoVAT = 12.44, stockAvailable = 2100, unit = "m²", priceVAT = 29500.0, salePriceNoVAT = 24380.17),
        PerrenPostgresProduct(sku = "CORT-CER-40X40", description = "Cerámica Cortines Piedra Beige 40x40 M2", brand = "Cortines", category = "Revestimientos", subcategory = "Cerámicas", classification = "Pisos y Revestimientos", costNoVAT = 9200.0, costUSDNoVAT = 6.81, stockAvailable = 4500, unit = "m²", priceVAT = 16200.0, salePriceNoVAT = 13388.43),
        PerrenPostgresProduct(sku = "DURL-PLA-125", description = "Placa de Yeso Durlock Estándar 12.5mm 1.20x2.40", brand = "Durlock", category = "Construcción en Seco", subcategory = "Placas de Yeso", classification = "Cielorrasos y Tabiques", costNoVAT = 11500.0, costUSDNoVAT = 8.51, stockAvailable = 1400, unit = "Unidad", priceVAT = 20200.0, salePriceNoVAT = 16694.21),
        PerrenPostgresProduct(sku = "ISOV-LAN-50", description = "Lana de Vidrio Isover Rolac Plata 50mm 12m2", brand = "Isover", category = "Aislaciones", subcategory = "Aislación Térmica", classification = "Aislaciones Techo", costNoVAT = 28500.0, costUSDNoVAT = 21.11, stockAvailable = 480, unit = "Rollo", priceVAT = 50000.0, salePriceNoVAT = 41322.31)
    )

    fun getProductsFromSuppliers(suppliers: List<LocalSupplier>): List<PerrenPostgresProduct> {
        val supplierProducts = mutableListOf<PerrenPostgresProduct>()
        suppliers.forEach { sup ->
            sup.articles.forEachIndexed { idx, art ->
                val code = art.code.ifBlank { "${sup.supplierCode}-ART-${idx + 1}" }
                val desc = art.name.ifBlank { "Artículo ${code}" }
                val brand = sup.companyName.ifBlank { "Sin Marca" }
                val cat = sup.category.ifBlank { "General" }
                val costUSD = art.fobPriceUSD
                val costARS = costUSD * 1350.0
                val stock = if (art.moq > 0) art.moq else 100
                val priceVAT = costARS * 1.21 * 1.40

                supplierProducts.add(
                    PerrenPostgresProduct(
                        sku = code,
                        description = desc,
                        brand = brand,
                        category = cat,
                        costNoVAT = costARS,
                        costUSDNoVAT = costUSD,
                        stockAvailable = stock,
                        unit = "Unidad",
                        priceVAT = priceVAT
                    )
                )
            }
        }
        val masterList = if (loadedCsvProducts.isNotEmpty()) loadedCsvProducts else perrenMasterCatalog
        return masterList + supplierProducts
    }

    fun getAllProducts(suppliers: List<LocalSupplier> = emptyList()): List<PerrenPostgresProduct> {
        return getProductsFromSuppliers(suppliers)
    }

    fun getAvailableBrands(suppliers: List<LocalSupplier>): List<String> {
        val products = getProductsFromSuppliers(suppliers)
        val brands = products.map { it.brand }.filter { it.isNotBlank() }.distinct().sorted()
        return listOf("Todas las Marcas") + brands
    }

    fun getAvailableCategories(suppliers: List<LocalSupplier>): List<String> {
        val products = getProductsFromSuppliers(suppliers)
        val categories = products.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        return listOf("Todos los Rubros") + categories
    }

    private fun normalizeText(input: String): String {
        if (input.isBlank()) return ""
        val normalized = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
        return normalized
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9 ]"), " ")
    }

    fun searchProducts(suppliers: List<LocalSupplier>, query: String, selectedBrand: String, selectedCategory: String, selectedAbc: String = "Todas las Clases"): List<PerrenPostgresProduct> {
        val hasQuery = query.trim().isNotBlank()
        val hasBrand = selectedBrand.isNotBlank() && selectedBrand != "Todas las Marcas"
        val hasCategory = selectedCategory.isNotBlank() && selectedCategory != "Todos los Rubros"
        val hasAbc = selectedAbc.isNotBlank() && selectedAbc != "Todas las Clases"

        if (!hasQuery && !hasBrand && !hasCategory && !hasAbc) {
            return emptyList()
        }

        val allProducts = getProductsFromSuppliers(suppliers)
        val normQuery = normalizeText(query.replace("\n", " ").replace("\r", " "))
        val keywords = normQuery.split(" ").filter { it.isNotBlank() }

        val matches = allProducts.filter { prod ->
            val normSku = normalizeText(prod.sku)
            val normDesc = normalizeText(prod.description)
            val normBrand = normalizeText(prod.brand)
            val normCat = normalizeText(prod.category)
            val normSubcat = normalizeText(prod.subcategory)
            val normClass = normalizeText(prod.classification)
            val fullText = "$normSku $normDesc $normBrand $normCat $normSubcat $normClass"

            val matchQuery = if (keywords.isEmpty()) {
                true
            } else {
                keywords.all { kw -> fullText.contains(kw) }
            }

            val matchBrand = (selectedBrand == "Todas las Marcas" || selectedBrand.isEmpty()) ||
                    normalizeText(prod.brand) == normalizeText(selectedBrand)

            val matchCategory = (selectedCategory == "Todos los Rubros" || selectedCategory.isEmpty()) ||
                    normalizeText(prod.category) == normalizeText(selectedCategory)

            val matchAbc = (selectedAbc == "Todas las Clases" || selectedAbc.isEmpty()) ||
                    (selectedAbc.contains("Clase A") && prod.abcClass == "A") ||
                    (selectedAbc.contains("Clase B") && prod.abcClass == "B") ||
                    (selectedAbc.contains("Clase C") && prod.abcClass == "C") ||
                    prod.abcClass.equals(selectedAbc, ignoreCase = true)

            matchQuery && matchBrand && matchCategory && matchAbc
        }

        return matches
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

// MARK: - Gestor de Persistencia Local de Proveedores en Disco y SQLite
object LocalPersistenceManager {
    private const val FILE_NAME = "canton_suppliers.json"

    fun saveSuppliers(context: Context, suppliers: List<LocalSupplier>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbHelper = CantonSQLiteHelper(context)
                for (sup in suppliers) {
                    dbHelper.saveSupplier(sup)
                }

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
        try {
            val dbHelper = CantonSQLiteHelper(context)
            val sqliteSuppliers = dbHelper.getAllSuppliers()
            if (sqliteSuppliers.isNotEmpty()) {
                return sqliteSuppliers
            }
        } catch (_: Exception) {}

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
    var comparisonPerrenProduct by remember { mutableStateOf<PerrenPostgresProduct?>(null) }

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
        PerrenPostgresRepository.loadFavorites(context)
        withContext(Dispatchers.IO) {
            PerrenPostgresRepository.ensureDatabaseLoaded(context)
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

    if (PerrenPostgresRepository.isSyncingProducts) {
        SyncProgressDialog(
            progress = PerrenPostgresRepository.syncProgressPercentage,
            statusText = PerrenPostgresRepository.syncProgressText
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
                    currentScreen == "postgres_search" -> PostgresSearchScreen(
                        suppliers = savedSuppliers,
                        onSelectProductForComparison = { prod ->
                            comparisonPerrenProduct = prod
                            currentScreen = "comparison"
                        }
                    )
                    currentScreen == "comparison" -> ComparisonFlexxusScreen(
                        suppliers = savedSuppliers,
                        preselectedPerrenProduct = comparisonPerrenProduct
                    )
                    currentScreen == "wallet" -> CardWalletScreen(suppliers = savedSuppliers)
                }
            }
        }
    }
}

// MARK: - Diálogo de Progreso de Sincronización SQLite
@Composable
fun SyncProgressDialog(progress: Int, statusText: String) {
    Dialog(onDismissRequest = { /* Modal no cerrable */ }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔄 Sincronizando Base de Datos", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Cargando catálogo Flexxus BI a la base SQLite local para consulta offline super rápida.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Color(0xFF1976D2),
                    trackColor = Color(0xFFE3F2FD)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$progress %",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                if (statusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
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

// MARK: - Componente Dialog para Ampliar Fotos en Pantalla Completa HD
@Composable
fun FullImageZoomDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔍 FOTO AMPLIADA HD", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    IconButton(onClick = onDismiss) {
                        Text("❌", color = Color.White, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto Ampliada HD",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CERRAR VISTA HD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

    var selectedImageForZoom by remember { mutableStateOf<Bitmap?>(null) }

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

    // Estado reactivo inmediato para artículos del proveedor
    val localArticles = remember(supplier, supplier?.articles?.size) {
        mutableStateListOf<ArticleItem>().apply {
            supplier?.articles?.let { addAll(it) }
        }
    }

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
                            .clickable { selectedImageForZoom = capturedMarqueeBitmap }
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
                            .clickable { selectedImageForZoom = capturedContactCardBitmap }
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
                    contactCardPhotoBitmap = capturedContactCardBitmap,
                    articles = localArticles.toMutableList()
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
                    articles = localArticles.toMutableList(),
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
                                    .clickable { selectedImageForZoom = photo }
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
                                val artCode = editingArticleCode ?: "${supplier?.supplierCode ?: "CF26-P-0001"}-A${String.format("%02d", localArticles.size + 1)}"
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
                                    val idx = localArticles.indexOfFirst { it.code == editingArticleCode }
                                    if (idx >= 0) {
                                        localArticles[idx] = newArt
                                    }
                                } else {
                                    localArticles.add(newArt)
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
                                    articles = localArticles.toMutableList()
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

        Text("ARTÍCULOS REGISTRADOS EN ESTE PROVEEDOR (${localArticles.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))

        localArticles.forEach { art ->
            val isExpanded = expandedArticleCode == art.code
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { expandedArticleCode = if (isExpanded) null else art.code },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 1.5.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val firstPhoto = art.photos.firstOrNull() ?: art.photoBitmap
                        if (firstPhoto != null) {
                            Image(
                                bitmap = firstPhoto.asImageBitmap(),
                                contentDescription = "Foto Artículo",
                                modifier = Modifier
                                    .size(52.dp)
                                    .border(1.dp, Color(0xFF1976D2), RoundedCornerShape(8.dp))
                                    .clickable { selectedImageForZoom = firstPhoto }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(art.code, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1976D2))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(art.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B365D))
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("FOB: $${art.fobPriceUSD} USD", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                Text("MOQ: ${art.moq} u", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                            }
                        }
                        IconButton(onClick = { expandedArticleCode = if (isExpanded) null else art.code }) {
                            Text(if (isExpanded) "▲" else "▼", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Spacer(modifier = Modifier.height(10.dp))

                        if (art.photos.size > 1) {
                            Text("📷 GALERÍA DE FOTOS (${art.photos.size}) - Toca una foto para ampliar HD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                art.photos.forEach { p ->
                                    Image(
                                        bitmap = p.asImageBitmap(),
                                        contentDescription = "Foto ampliable",
                                        modifier = Modifier
                                            .size(76.dp)
                                            .border(1.5.dp, Color(0xFF1565C0), RoundedCornerShape(8.dp))
                                            .clickable { selectedImageForZoom = p }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        } else if (art.photos.size == 1) {
                            Text("🔍 Toca la foto de arriba para ampliar en pantalla completa HD", fontSize = 10.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("📦 Puerto Origen: ${art.port} | Tiempo Entrega: ${art.leadTime}", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)

                                if (art.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("📝 Observaciones: ${art.note}", fontSize = 11.sp, color = Color(0xFF212121), fontWeight = FontWeight.Normal)
                                }
                            }
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

                        Spacer(modifier = Modifier.height(12.dp))
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
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("✏️ EDITAR ARTÍCULO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    localArticles.remove(art)
                                    val updatedSupplier = (supplier ?: LocalSupplier(
                                        id = System.currentTimeMillis().toString(),
                                        supplierCode = "CF26-P-0001",
                                        companyName = companyName,
                                        companyChinese = companyChinese,
                                        stand = stand,
                                        category = category,
                                        gpsCoordinates = "GPS: Lat -43.2512, Long -65.3094",
                                        createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                    )).copy(
                                        companyName = companyName,
                                        companyChinese = companyChinese,
                                        stand = stand,
                                        articles = localArticles.toMutableList()
                                    )
                                    onSaveSupplier(updatedSupplier)
                                    Toast.makeText(context, "🗑️ Artículo eliminado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("🗑️ ELIMINAR", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedImageForZoom != null) {
        FullImageZoomDialog(bitmap = selectedImageForZoom!!, onDismiss = { selectedImageForZoom = null })
    }
}

@Composable
fun ImportBreakdownTableComposable(
    fobUnit: Double,
    containerQty: Int,
    fleteUSD: Double,
    seguroPct: Double,
    arancelPct: Double,
    tasaEstadPct: Double,
    ivaPct: Double,
    ivaAdicPct: Double,
    gananciasPct: Double,
    iibbPct: Double,
    despachantePct: Double,
    tc: Double,
    incFlete: Boolean = true,
    incSeguro: Boolean = true,
    incArancel: Boolean = true,
    incTasaEstad: Boolean = true,
    incIVA: Boolean = true,
    incIVAAdic: Boolean = true,
    incGanancias: Boolean = true,
    incIIBB: Boolean = true,
    incDespachante: Boolean = true,
    weightModeEnabled: Boolean = false,
    itemWeightKg: Double = 0.0,
    containerMaxWeightKg: Double = 26000.0,
    titleSuffix: String = ""
) {
    val qty = if (containerQty > 0) containerQty else 1
    val fobTotal = fobUnit * qty

    // Flete Efectivo según Toggles y Prorrateo por Peso
    val baseFlete = if (incFlete) fleteUSD else 0.0
    val effectiveFlete = if (incFlete && weightModeEnabled && itemWeightKg > 0 && containerMaxWeightKg > 0) {
        val totalLotWeightKg = itemWeightKg * qty
        val weightRatio = (totalLotWeightKg / containerMaxWeightKg).coerceAtMost(1.0)
        baseFlete * weightRatio
    } else {
        baseFlete
    }

    val effSeguroPct = if (incSeguro) seguroPct else 0.0
    val effArancelPct = if (incArancel) arancelPct else 0.0
    val effTasaEstadPct = if (incTasaEstad) tasaEstadPct else 0.0
    val effIvaPct = if (incIVA) ivaPct else 0.0
    val effIvaAdicPct = if (incIVAAdic) ivaAdicPct else 0.0
    val effGananciasPct = if (incGanancias) gananciasPct else 0.0
    val effIibbPct = if (incIIBB) iibbPct else 0.0
    val effDespachantePct = if (incDespachante) despachantePct else 0.0

    val seguroTotal = fobTotal * (effSeguroPct / 100.0)
    val cifTotal = fobTotal + effectiveFlete + seguroTotal
    val derechosTotal = cifTotal * (effArancelPct / 100.0)
    val tasaEstadTotal = cifTotal * (effTasaEstadPct / 100.0)
    val baseImponible = cifTotal + derechosTotal + tasaEstadTotal
    val ivaTotal = baseImponible * (effIvaPct / 100.0)
    val ivaAdicTotal = baseImponible * (effIvaAdicPct / 100.0)
    val gananciasTotal = baseImponible * (effGananciasPct / 100.0)
    val iibbTotal = baseImponible * (effIibbPct / 100.0)
    val gastosDespachante = baseImponible * (effDespachantePct / 100.0)

    val costoRealIncididoUSD = baseImponible + iibbTotal + gastosDespachante
    val unitCostoIncididoUSD = costoRealIncididoUSD / qty
    val unitCostoIncididoARS = unitCostoIncididoUSD * tc

    val totalLandedUSD = baseImponible + ivaTotal + ivaAdicTotal + gananciasTotal + iibbTotal + gastosDespachante
    val unitLandedUSD = totalLandedUSD / qty
    val unitLandedARS = unitLandedUSD * tc

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFB0BEC5))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📋 DESGLOSE COMPLETO DE IMPORTACIÓN (LANDED) $titleSuffix",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1B365D)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Lote: $qty u",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color(0xFF2E7D32),
                        maxLines = 1
                    )
                }
            }

            if (qty <= 1 && !weightModeEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Text(
                        "⚠️ Al colocar 1 sola unidad, el flete entero del contenedor ($${String.format(Locale.US, "%,.0f", fleteUSD)} USD) se asigna a esta unidad. Ingresa el MOQ del lote (ej: 1.000 u) o activa el prorrateo por peso para distribuir el flete adecuadamente.",
                        fontSize = 9.sp, color = Color(0xFFE65100), modifier = Modifier.padding(6.dp)
                    )
                }
            }

            if (weightModeEnabled && itemWeightKg > 0) {
                val totalLotWeight = itemWeightKg * qty
                val pctCap = (totalLotWeight / containerMaxWeightKg) * 100.0
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Text(
                        "⚖️ Prorrateo por Peso Activado: Peso Lote = ${String.format(Locale.US, "%.1f", totalLotWeight)} kg (${String.format(Locale.US, "%.1f", pctCap)}% del contenedor de ${containerMaxWeightKg.toInt()} kg). Flete asignado: $${String.format(Locale.US, "%,.2f", effectiveFlete)} USD.",
                        fontSize = 9.sp, color = Color(0xFF2E7D32), modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Encabezados de Columna
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE1F5FE))
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CONCEPTO", modifier = Modifier.weight(2.0f), fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF01579B))
                Text("%", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF01579B), textAlign = TextAlign.Center)
                Text("UNITARIO (USD)", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF01579B), textAlign = TextAlign.End)
                Text("TOTAL LOTE (USD)", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF01579B), textAlign = TextAlign.End)
            }
            HorizontalDivider()

            val fletePctLabel = if (!incFlete) "OFF" else if (weightModeEnabled) "${String.format(Locale.US, "%.1f", (itemWeightKg * qty / containerMaxWeightKg) * 100)}%" else ""

            val rows = listOf(
                ImportRow("Valor FOB", "", fobUnit, fobTotal),
                ImportRow(if (weightModeEnabled) "Flete (Prorr. Peso)" else "Flete Marítimo", fletePctLabel, effectiveFlete / qty, effectiveFlete),
                ImportRow("Seguro", if (incSeguro) "${String.format(Locale.US, "%.1f", seguroPct)}%" else "OFF", seguroTotal / qty, seguroTotal),
                ImportRow("CIF", "", cifTotal / qty, cifTotal, isHeaderOrSubtotal = true),
                ImportRow("Derecho Impo", if (incArancel) "${arancelPct.toInt()}%" else "OFF", derechosTotal / qty, derechosTotal),
                ImportRow("Tasa Estadística", if (incTasaEstad) "${tasaEstadPct.toInt()}%" else "OFF", tasaEstadTotal / qty, tasaEstadTotal),
                ImportRow("Base Imponible", "", baseImponible / qty, baseImponible, isHeaderOrSubtotal = true),
                ImportRow("IVA", if (incIVA) "${ivaPct.toInt()}%" else "OFF", ivaTotal / qty, ivaTotal),
                ImportRow("IVA Adicional", if (incIVAAdic) "${ivaAdicPct.toInt()}%" else "OFF", ivaAdicTotal / qty, ivaAdicTotal),
                ImportRow("Ganancias", if (incGanancias) "${gananciasPct.toInt()}%" else "OFF", gananciasTotal / qty, gananciasTotal),
                ImportRow("II.BB", if (incIIBB) "${String.format(Locale.US, "%.1f", iibbPct)}%" else "OFF", iibbTotal / qty, iibbTotal),
                ImportRow("Gastos Desp/Forw", if (incDespachante) "${despachantePct.toInt()}%" else "OFF", gastosDespachante / qty, gastosDespachante),
                ImportRow("COSTO INCIDIDO NETO", "", unitCostoIncididoUSD, costoRealIncididoUSD, isHeaderOrSubtotal = true, isCostIncidido = true),
                ImportRow("TOTAL DESEMBOLSO LOTE", "", unitLandedUSD, totalLandedUSD, isHeaderOrSubtotal = true, isTotal = true)
            )

            rows.forEach { r ->
                val bgColor = when {
                    r.isTotal -> Color(0xFFE8F5E9)
                    r.isCostIncidido -> Color(0xFFE3F2FD)
                    r.isHeaderOrSubtotal -> Color(0xFFFFF8E1)
                    else -> Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(vertical = 3.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        r.concept,
                        modifier = Modifier.weight(2.0f),
                        fontWeight = if (r.isHeaderOrSubtotal || r.isTotal || r.isCostIncidido) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 9.sp,
                        color = when {
                            r.isTotal -> Color(0xFF2E7D32)
                            r.isCostIncidido -> Color(0xFF1565C0)
                            else -> Color.DarkGray
                        }
                    )
                    Text(
                        r.pctStr,
                        modifier = Modifier.weight(0.6f),
                        fontSize = 8.5.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "$${String.format(Locale.US, "%,.2f", r.unitUSD)}",
                        modifier = Modifier.weight(1.5f),
                        fontWeight = if (r.isHeaderOrSubtotal || r.isTotal || r.isCostIncidido) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 9.sp,
                        color = if (r.isTotal) Color(0xFF2E7D32) else Color.Black,
                        textAlign = TextAlign.End
                    )
                    Text(
                        "$${String.format(Locale.US, "%,.2f", r.totalUSD)}",
                        modifier = Modifier.weight(1.6f),
                        fontWeight = if (r.isHeaderOrSubtotal || r.isTotal || r.isCostIncidido) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 9.sp,
                        color = if (r.isTotal) Color(0xFF2E7D32) else Color.Black,
                        textAlign = TextAlign.End
                    )
                }
                if (r.isHeaderOrSubtotal || r.isTotal || r.isCostIncidido) HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tarjetas de Resumen Unitario Puesto
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text("COSTO INCIDIDO NETO (Sin IVA/Gan)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        Text("$${String.format(Locale.US, "%.2f", unitCostoIncididoUSD)} USD / u", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        Text("$${String.format("%,.2f", unitCostoIncididoARS)} ARS / u", fontSize = 9.5.sp, color = Color.DarkGray)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text("DESEMBOLSO TOTAL (Con IVA/Gan)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text("$${String.format(Locale.US, "%.2f", unitLandedUSD)} USD / u", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text("$${String.format("%,.2f", unitLandedARS)} ARS / u", fontSize = 9.5.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}

// MARK: - 4. Comparador de Costos Flexxus BI & Combos (Interactivo de 3 Pestañas)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComparisonFlexxusScreen(
    suppliers: List<LocalSupplier> = emptyList(),
    preselectedPerrenProduct: PerrenPostgresProduct? = null
) {
    val context = LocalContext.current
    val dbHelper = remember(context) { CantonSQLiteHelper(context) }
    val scrollState = rememberScrollState()

    var selectedSubTab by remember { mutableStateOf(if (preselectedPerrenProduct != null) 2 else 0) }

    var selectedPerrenProduct by remember { mutableStateOf<PerrenPostgresProduct?>(preselectedPerrenProduct) }
    var selectedCantonArticle by remember { mutableStateOf<ArticleItem?>(null) }
    var selectedCantonSupplierName by remember { mutableStateOf("") }

    LaunchedEffect(preselectedPerrenProduct) {
        if (preselectedPerrenProduct != null) {
            selectedPerrenProduct = preselectedPerrenProduct
            selectedSubTab = 2
        }
    }

    var currencyMode by remember { mutableStateOf("ARS") } // "ARS" or "USD"
    var showPerrenSearchModal by remember { mutableStateOf(false) }
    var showCantonPickerModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }

    // Parámetros de Importación (Ajustes Configurables)
    var dolarTCSetting by remember { mutableStateOf("1350.00") }
    var fleteUSDSetting by remember { mutableStateOf("3400.00") }
    var seguroPercentSetting by remember { mutableStateOf("1.2") }
    var arancelPercentSetting by remember { mutableStateOf("20.0") }
    var tasaEstadPercentSetting by remember { mutableStateOf("3.0") }
    var ivaPercentSetting by remember { mutableStateOf("21.0") }
    var ivaAdicPercentSetting by remember { mutableStateOf("20.0") }
    var gananciasPercentSetting by remember { mutableStateOf("6.0") }
    var iibbPercentSetting by remember { mutableStateOf("2.5") }
    var despachantePercentSetting by remember { mutableStateOf("8.0") }

    // Toggles de Inclusión / Exclusión de Renglones
    var incFlete by remember { mutableStateOf(true) }
    var incSeguro by remember { mutableStateOf(true) }
    var incArancel by remember { mutableStateOf(true) }
    var incTasaEstad by remember { mutableStateOf(true) }
    var incIVA by remember { mutableStateOf(true) }
    var incIVAAdic by remember { mutableStateOf(true) }
    var incGanancias by remember { mutableStateOf(true) }
    var incIIBB by remember { mutableStateOf(true) }
    var incDespachante by remember { mutableStateOf(true) }

    // Prorrateo por Peso (kg)
    var weightModeEnabled by remember { mutableStateOf(false) }
    var itemWeightKgInput by remember { mutableStateOf("2.5") }
    var containerMaxWeightInput by remember { mutableStateOf("26000") }

    // Cotización China Manual (Tab 1)
    var manualArtName by remember { mutableStateOf("Artículo Importado") }
    var chinaFobUSDInput by remember { mutableStateOf("10.00") }
    var qtyInput by remember { mutableStateOf("1000") }
    var manualPortInput by remember { mutableStateOf("Shenzhen") }

    // Búsqueda en catálogo de guardados (Tab 2)
    var cantonCatalogQuery by remember { mutableStateOf("") }
    var expandedArticleCodes by remember { mutableStateOf(setOf<String>()) }

    // Parse variables
    val tc = dolarTCSetting.toDoubleOrNull() ?: 1350.0
    val flete = fleteUSDSetting.toDoubleOrNull() ?: 3400.0
    val seguroPct = seguroPercentSetting.toDoubleOrNull() ?: 1.2
    val arancel = arancelPercentSetting.toDoubleOrNull() ?: 20.0
    val tasaEstad = tasaEstadPercentSetting.toDoubleOrNull() ?: 3.0
    val ivaPct = ivaPercentSetting.toDoubleOrNull() ?: 21.0
    val ivaAdicPct = ivaAdicPercentSetting.toDoubleOrNull() ?: 20.0
    val gananciasPct = gananciasPercentSetting.toDoubleOrNull() ?: 6.0
    val iibb = iibbPercentSetting.toDoubleOrNull() ?: 2.5
    val despachante = despachantePercentSetting.toDoubleOrNull() ?: 8.0

    val fobUnit = selectedCantonArticle?.fobPriceUSD ?: (chinaFobUSDInput.toDoubleOrNull() ?: 10.0)
    val containerQty = if ((selectedCantonArticle?.moq ?: 0) > 0) selectedCantonArticle!!.moq else (qtyInput.toIntOrNull() ?: 1000)

    // Cálculo Landed China Completo
    val fobTotal = fobUnit * containerQty
    val seguroTotal = fobTotal * (seguroPct / 100.0)
    val cifTotal = fobTotal + flete + seguroTotal
    val derechosTotal = cifTotal * (arancel / 100.0)
    val tasaEstadTotal = cifTotal * (tasaEstad / 100.0)
    val baseImponible = cifTotal + derechosTotal + tasaEstadTotal
    val ivaTotal = baseImponible * (ivaPct / 100.0)
    val ivaAdicTotal = baseImponible * (ivaAdicPct / 100.0)
    val gananciasTotal = baseImponible * (gananciasPct / 100.0)
    val iibbTotal = baseImponible * (iibb / 100.0)
    val gastosDespachante = baseImponible * (despachante / 100.0)

    val costoLandedTotalUSD = baseImponible + ivaTotal + ivaAdicTotal + gananciasTotal + iibbTotal + gastosDespachante
    val unitLandedUSD = if (containerQty > 0) costoLandedTotalUSD / containerQty else 0.0
    val unitLandedARS = unitLandedUSD * tc

    // Datos Perren
    val perrenCostARS = selectedPerrenProduct?.costNoVAT ?: 0.0
    val perrenCostUSD = if (tc > 0) perrenCostARS / tc else 0.0
    val perrenSaleARS = if ((selectedPerrenProduct?.priceVAT ?: 0.0) > 0) selectedPerrenProduct!!.priceVAT / 1.21 else perrenCostARS * 1.40
    val perrenSaleUSD = if (tc > 0) perrenSaleARS / tc else 0.0

    // Diferencia y Ahorro
    val ahorroUSD = perrenCostUSD - unitLandedUSD
    val ahorroARS = perrenCostARS - unitLandedARS
    val ahorroPct = if (perrenCostUSD > 0) (ahorroUSD / perrenCostUSD) * 100.0 else 0.0

    val formatVal: (Double, Double) -> String = { ars, usd ->
        if (currencyMode == "USD") "$${String.format(Locale.US, "%.2f", usd)} USD"
        else "$${String.format("%,.2f", ars)} ARS"
    }

    val allSupplierArticles = remember(suppliers) {
        suppliers.flatMap { sup -> sup.articles.map { art -> sup to art } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // ENCABEZADO CON NAVEGACIÓN DE 3 TABS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            Text("⚖️ MODULO DE COMPARACIÓN FLEXXUS & CANTON", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
            Text("Cálculo Landed, Selección de Proveedores y Evaluación de Costos", fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.White,
                contentColor = Color(0xFF1B365D)
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("✍️ Tab 1: Carga Manual", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("📦 Tab 2: Guardados (${allSupplierArticles.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("⚖️ Tab 3: Compara", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            when (selectedSubTab) {
                // ==================== TAB 1: CARGA MANUAL DEL ARTÍCULO ====================
                0 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("✍️ TAB 1: CARGA MANUAL DEL ARTÍCULO (FOB CHINA)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B365D))
                            Text("Ingresa los datos de cotización directa para calcular el costo puesto landed:", fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = manualArtName,
                                onValueChange = { manualArtName = it },
                                label = { Text("Nombre / Descripción del Artículo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = chinaFobUSDInput,
                                    onValueChange = { chinaFobUSDInput = it },
                                    label = { Text("Precio FOB ($ USD / u)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = qtyInput,
                                    onValueChange = { qtyInput = it },
                                    label = { Text("Cantidad (MOQ)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = manualPortInput,
                                    onValueChange = { manualPortInput = it },
                                    label = { Text("Puerto de Embarque") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = itemWeightKgInput,
                                    onValueChange = { itemWeightKgInput = it },
                                    label = { Text("Peso Unit. (kg)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = weightModeEnabled,
                                    onClick = { weightModeEnabled = !weightModeEnabled },
                                    label = { Text(if (weightModeEnabled) "⚖️ Prorratear Flete por Peso (ACTIVADO)" else "⚖️ Prorratear Flete por Peso (DESACTIVADO)", fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // DESGLOSE COMPLETO EN TAB 1
                    ImportBreakdownTableComposable(
                        fobUnit = chinaFobUSDInput.toDoubleOrNull() ?: 10.0,
                        containerQty = qtyInput.toIntOrNull() ?: 1000,
                        fleteUSD = flete,
                        seguroPct = seguroPct,
                        arancelPct = arancel,
                        tasaEstadPct = tasaEstad,
                        ivaPct = ivaPct,
                        ivaAdicPct = ivaAdicPct,
                        gananciasPct = gananciasPct,
                        iibbPct = iibb,
                        despachantePct = despachante,
                        tc = tc,
                        incFlete = incFlete,
                        incSeguro = incSeguro,
                        incArancel = incArancel,
                        incTasaEstad = incTasaEstad,
                        incIVA = incIVA,
                        incIVAAdic = incIVAAdic,
                        incGanancias = incGanancias,
                        incIIBB = incIIBB,
                        incDespachante = incDespachante,
                        weightModeEnabled = weightModeEnabled,
                        itemWeightKg = itemWeightKgInput.toDoubleOrNull() ?: 0.0,
                        containerMaxWeightKg = containerMaxWeightInput.toDoubleOrNull() ?: 26000.0,
                        titleSuffix = "(TAB 1 MANUAL)"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            selectedCantonArticle = ArticleItem(
                                code = "MANUAL-${System.currentTimeMillis() % 10000}",
                                name = manualArtName.ifBlank { "Artículo Manual" },
                                fobPriceUSD = chinaFobUSDInput.toDoubleOrNull() ?: 10.0,
                                moq = qtyInput.toIntOrNull() ?: 1000,
                                port = manualPortInput,
                                leadTime = "30 días"
                            )
                            selectedCantonSupplierName = "Carga Manual"
                            selectedSubTab = 2
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("➡️ ENVIAR A TAB 3 (COMPARA ARTÍCULOS)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // ==================== TAB 2: CARGA ARTÍCULOS GUARDADOS ====================
                1 -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("📦 TAB 2: CATÁLOGOS Y ARTÍCULOS GUARDADOS POR PROVEEDORES", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B365D))
                        Text("Selecciona un artículo cotizado durante la feria para enviarlo al comparador:", fontSize = 10.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = cantonCatalogQuery,
                            onValueChange = { cantonCatalogQuery = it },
                            label = { Text("Filtrar por artículo o proveedor...") },
                            leadingIcon = { Text("🔍") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredArticles = allSupplierArticles.filter { (sup, art) ->
                            val q = cantonCatalogQuery.trim().lowercase()
                            q.isEmpty() || art.name.lowercase().contains(q) || art.code.lowercase().contains(q) || sup.companyName.lowercase().contains(q)
                        }

                        if (filteredArticles.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📦 No hay artículos cargados en proveedores", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Carga artículos desde la sección '+ Proveedor' para tenerlos guardados aquí.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            filteredArticles.forEach { (sup, art) ->
                                val artFob = art.fobPriceUSD
                                val artMoq = if (art.moq > 0) art.moq else 1000
                                val isExpanded = expandedArticleCodes.contains(art.code)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable {
                                            expandedArticleCodes = if (isExpanded) {
                                                expandedArticleCodes - art.code
                                            } else {
                                                expandedArticleCodes + art.code
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF1976D2) else Color(0xFFE0E0E0))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(art.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B365D))
                                                Text("Proveedor: ${sup.companyName} | Código: ${art.code}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFE3F2FD),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    "FOB: $${String.format(Locale.US, "%.2f", artFob)} USD",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1565C0)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Puerto: ${art.port} | MOQ: $artMoq u", fontSize = 10.sp, color = Color.DarkGray)
                                            Text(
                                                if (isExpanded) "🔼 Ocultar Desglose" else "🔽 Ver Desglose Landed",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1976D2)
                                            )
                                        }

                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider()
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // DESGLOSE COMPLETO AL EXPANDIR LA TARJETA
                                            ImportBreakdownTableComposable(
                                                fobUnit = artFob,
                                                containerQty = artMoq,
                                                fleteUSD = flete,
                                                seguroPct = seguroPct,
                                                arancelPct = arancel,
                                                tasaEstadPct = tasaEstad,
                                                ivaPct = ivaPct,
                                                ivaAdicPct = ivaAdicPct,
                                                gananciasPct = gananciasPct,
                                                iibbPct = iibb,
                                                despachantePct = despachante,
                                                tc = tc,
                                                incFlete = incFlete,
                                                incSeguro = incSeguro,
                                                incArancel = incArancel,
                                                incTasaEstad = incTasaEstad,
                                                incIVA = incIVA,
                                                incIVAAdic = incIVAAdic,
                                                incGanancias = incGanancias,
                                                incIIBB = incIIBB,
                                                incDespachante = incDespachante,
                                                weightModeEnabled = weightModeEnabled,
                                                itemWeightKg = itemWeightKgInput.toDoubleOrNull() ?: 0.0,
                                                containerMaxWeightKg = containerMaxWeightInput.toDoubleOrNull() ?: 26000.0,
                                                titleSuffix = "(${sup.companyName})"
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    selectedCantonArticle = art
                                                    selectedCantonSupplierName = sup.companyName
                                                    selectedSubTab = 2
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(vertical = 6.dp)
                                            ) {
                                                Text("📊 COMPARAR ESTE ARTÍCULO EN TAB 3", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    selectedCantonArticle = art
                                                    selectedCantonSupplierName = sup.companyName
                                                    selectedSubTab = 2
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                border = BorderStroke(1.dp, Color(0xFF2E7D32)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Text("📊 COMPARAR ESTE ARTÍCULO EN TAB 3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== TAB 3: COMPARA ARTÍCULOS ====================
                2 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚖️ TAB 3: COMPARA ARTÍCULOS EN TIEMPO REAL", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
                            Text("Costo Argentina vs Importado Landed", fontSize = 10.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = { showSettingsModal = !showSettingsModal },
                            colors = ButtonDefaults.buttonColors(containerColor = if (showSettingsModal) Color(0xFFD32F2F) else Color(0xFF1976D2)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (showSettingsModal) "✖️ Ajustes" else "⚙️ Ajustes Importación", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // BARRA SELECTORA DE MONEDA ($ Pesos / $ Dólares MEP)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("💱 VER EN MONEDA:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                Text("Dólar TC: $${String.format("%,.2f", tc)} ARS", fontSize = 9.sp, color = Color.Gray)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { currencyMode = "ARS" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (currencyMode == "ARS") Color(0xFF1B365D) else Color.LightGray
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("💵 Pesos ($ ARS)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Button(
                                    onClick = { currencyMode = "USD" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (currencyMode == "USD") Color(0xFF2E7D32) else Color.LightGray
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("💲 Dólares ($ USD MEP)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // PANEL DE AJUSTES & PARÁMETROS CONFIGURABLES
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
                                        value = arancelPercentSetting, onValueChange = { arancelPercentSetting = it },
                                        label = { Text("Arancel / Derechos %") }, modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = tasaEstadPercentSetting, onValueChange = { tasaEstadPercentSetting = it },
                                        label = { Text("Tasa Estadística %") }, modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = ivaPercentSetting, onValueChange = { ivaPercentSetting = it },
                                        label = { Text("IVA %") }, modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = ivaAdicPercentSetting, onValueChange = { ivaAdicPercentSetting = it },
                                        label = { Text("IVA Adicional %") }, modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = gananciasPercentSetting, onValueChange = { gananciasPercentSetting = it },
                                        label = { Text("Ganancias %") }, modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = iibbPercentSetting, onValueChange = { iibbPercentSetting = it },
                                        label = { Text("II.BB %") }, modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = despachantePercentSetting, onValueChange = { despachantePercentSetting = it },
                                        label = { Text("Despachante & Puerto %") }, modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = seguroPercentSetting, onValueChange = { seguroPercentSetting = it },
                                        label = { Text("Seguro %") }, modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("☑️ SELECCIONAR RENGLONES A CALCULAR", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE65100))
                                Text("Tilda o destilda los casilleros para incluir o excluir del cálculo landed:", fontSize = 9.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(6.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(selected = incFlete, onClick = { incFlete = !incFlete }, label = { Text("Flete", fontSize = 9.sp) })
                                    FilterChip(selected = incSeguro, onClick = { incSeguro = !incSeguro }, label = { Text("Seguro", fontSize = 9.sp) })
                                    FilterChip(selected = incArancel, onClick = { incArancel = !incArancel }, label = { Text("Arancel", fontSize = 9.sp) })
                                    FilterChip(selected = incTasaEstad, onClick = { incTasaEstad = !incTasaEstad }, label = { Text("Tasa Estad", fontSize = 9.sp) })
                                    FilterChip(selected = incIVA, onClick = { incIVA = !incIVA }, label = { Text("IVA 21%", fontSize = 9.sp) })
                                    FilterChip(selected = incIVAAdic, onClick = { incIVAAdic = !incIVAAdic }, label = { Text("IVA Adic 20%", fontSize = 9.sp) })
                                    FilterChip(selected = incGanancias, onClick = { incGanancias = !incGanancias }, label = { Text("Ganancias 6%", fontSize = 9.sp) })
                                    FilterChip(selected = incIIBB, onClick = { incIIBB = !incIIBB }, label = { Text("II.BB 2.5%", fontSize = 9.sp) })
                                    FilterChip(selected = incDespachante, onClick = { incDespachante = !incDespachante }, label = { Text("Despachante 8%", fontSize = 9.sp) })
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))

                                Text("⚖️ PRORRATEO POR PESO (KG) EN CONTENEDOR", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE65100))
                                FilterChip(
                                    selected = weightModeEnabled,
                                    onClick = { weightModeEnabled = !weightModeEnabled },
                                    label = { Text(if (weightModeEnabled) "✅ Prorratear Flete por Peso Activado" else "❌ Prorratear Flete por Unidades (Estándar)", fontSize = 9.5.sp) }
                                )

                                if (weightModeEnabled) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = itemWeightKgInput, onValueChange = { itemWeightKgInput = it },
                                            label = { Text("Peso Unitario (kg / u)") }, modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = containerMaxWeightInput, onValueChange = { containerMaxWeightInput = it },
                                            label = { Text("Capacidad Contenedor (kg)") }, modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("ℹ️ Capacidad Estructural & Carga Útil por Tipo de Contenedor:", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(0xFFE65100))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("• 20' DV (20 pies): Carga útil de 27 a 28 Tn (27.000-28.000 kg). Peso bruto max total ~30,4 Tn incl. tara.", fontSize = 8.5.sp, color = Color.DarkGray)
                                        Text("• 40' DV / HC (40 pies): Carga útil de 26 a 29 Tn (26.000-29.000 kg) por resistencia estructural del piso.", fontSize = 8.5.sp, color = Color.DarkGray)
                                        Text("• Límite Balanza Puerto AR: 26.000 kg netos recomendados sin sobretasa vial.", fontSize = 8.5.sp, color = Color(0xFFD84315), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // SELECCIÓN PERREN (SQLITE)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🇦🇷 ARTÍCULO PERREN (SQLITE LOCAL)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1976D2))
                                Button(
                                    onClick = { showPerrenSearchModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("🔍 BUSCAR PERREN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedPerrenProduct == null) {
                                Text("Ningún artículo Perren seleccionado. Toca 'BUSCAR PERREN' para elegir de la base de 12.074 artículos.", fontSize = 11.sp, color = Color.Gray)
                            } else {
                                val prod = selectedPerrenProduct!!
                                Text("${prod.sku} - ${prod.description}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B365D))
                                Text("Marca: ${prod.brand} | Rubro: ${prod.category} | Clase: ${prod.abcClass}", fontSize = 10.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("COSTO SIN IVA", fontSize = 9.sp, color = Color.Gray)
                                        Text(formatVal(perrenCostARS, perrenCostUSD), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("VENTA ESTIMADA SIN IVA", fontSize = 9.sp, color = Color.Gray)
                                        Text(formatVal(perrenSaleARS, perrenSaleUSD), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // SELECCIÓN CANTON FAIR (PROVEEDORES)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🇨🇳 ARTÍCULO CANTON FAIR (PROVEEDORES)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                Button(
                                    onClick = { showCantonPickerModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("📦 ELEGIR DE CANTON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedCantonArticle == null) {
                                Text("Puedes elegir de los artículos de proveedores guardados (Tab 2) o ingresar el FOB manual (Tab 1).", fontSize = 10.sp, color = Color.Gray)
                            } else {
                                val art = selectedCantonArticle
                                if (art != null) {
                                    Text("${art.name} (${selectedCantonSupplierName})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B365D))
                                    Text("Código: ${art.code} | Puerto: ${art.port} | MOQ: ${containerQty} Unidades", fontSize = 10.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("PRECIO FOB FÁBRICA", fontSize = 9.sp, color = Color.Gray)
                                        Text("$${String.format(Locale.US, "%.2f", fobUnit)} USD", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("COSTO LANDED PUESTO", fontSize = 9.sp, color = Color.Gray)
                                        Text(formatVal(unitLandedARS, unitLandedUSD), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // DESARROLLO VISUAL DE IMPORTACIÓN COMPLETO (TAB 3)
                    ImportBreakdownTableComposable(
                        fobUnit = fobUnit,
                        containerQty = containerQty,
                        fleteUSD = flete,
                        seguroPct = seguroPct,
                        arancelPct = arancel,
                        tasaEstadPct = tasaEstad,
                        ivaPct = ivaPct,
                        ivaAdicPct = ivaAdicPct,
                        gananciasPct = gananciasPct,
                        iibbPct = iibb,
                        despachantePct = despachante,
                        tc = tc,
                        incFlete = incFlete,
                        incSeguro = incSeguro,
                        incArancel = incArancel,
                        incTasaEstad = incTasaEstad,
                        incIVA = incIVA,
                        incIVAAdic = incIVAAdic,
                        incGanancias = incGanancias,
                        incIIBB = incIIBB,
                        incDespachante = incDespachante,
                        weightModeEnabled = weightModeEnabled,
                        itemWeightKg = itemWeightKgInput.toDoubleOrNull() ?: 0.0,
                        containerMaxWeightKg = containerMaxWeightInput.toDoubleOrNull() ?: 26000.0,
                        titleSuffix = if (selectedCantonArticle != null) "(${selectedCantonArticle!!.name})" else "(TAB 3 COMPARA)"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // TARJETAS COMPARATIVAS LADO A LADO
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Card(
                            modifier = Modifier.weight(1f).border(2.dp, Color(0xFFD32F2F), RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("🇦🇷 PERREN (ARGENTINA)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFD32F2F))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Costo Sin IVA", fontSize = 9.sp, color = Color.Gray)
                                Text(formatVal(perrenCostARS, perrenCostUSD), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFD32F2F))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Venta: ${formatVal(perrenSaleARS, perrenSaleUSD)}", fontSize = 9.sp, color = Color.DarkGray)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Card(
                            modifier = Modifier.weight(1f).border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("🇨🇳 CANTON FAIR (IMPORTADO)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Costo Puesto Depósito", fontSize = 9.sp, color = Color.Gray)
                                Text(formatVal(unitLandedARS, unitLandedUSD), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("FOB: $${String.format(Locale.US, "%.2f", fobUnit)} USD", fontSize = 9.sp, color = Color.DarkGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // AHORRO O DIFERENCIA FINAL
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (ahorroUSD > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("RESULTADO COMPARATIVO FINAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Diferencia / Ahorro Unitario:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${if (ahorroUSD > 0) "+" else ""}${formatVal(ahorroARS, ahorroUSD)} (${String.format(Locale.US, "%.1f", ahorroPct)}%)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (ahorroUSD > 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (selectedPerrenProduct == null) "Selecciona un artículo Perren para calcular la diferencia de costo exacta."
                                else if (ahorroUSD > 0) "🟢 Importar desde Canton Fair representa un ahorro de ${formatVal(ahorroARS, ahorroUSD)} (${String.format(Locale.US, "%.1f", ahorroPct)}%) por unidad vs Perren."
                                else "🔴 El producto local resulta de menor costo que la alternativa importada.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ahorroUSD > 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        }
    }

    // MODAL DE BÚSQUEDA PERREN (SQLITE)
    if (showPerrenSearchModal) {
        var dialogQuery by remember { mutableStateOf("") }
        var dialogResults by remember { mutableStateOf<List<PerrenPostgresProduct>>(emptyList()) }
        var isSearchingDialog by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPerrenSearchModal = false },
            title = { Text("🔍 Buscar Artículo Perren (SQLite)", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = dialogQuery,
                        onValueChange = { dialogQuery = it },
                        label = { Text("Buscar por código, descripción o marca...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (dialogQuery.trim().length >= 2) {
                                    isSearchingDialog = true
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val res = dbHelper.searchProductsInDb(dialogQuery.trim(), "Todas las Marcas", "Todos los Rubros", limit = 30)
                                        withContext(Dispatchers.Main) {
                                            dialogResults = res
                                            isSearchingDialog = false
                                        }
                                    }
                                }
                            }) {
                                Text("🔍")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isSearchingDialog) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (dialogResults.isEmpty()) {
                        Text("Escribe al menos 2 letras y toca 🔍 para buscar en SQLite.", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(dialogResults) { prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPerrenProduct = prod
                                            showPerrenSearchModal = false
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${prod.sku} - ${prod.description}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("${prod.brand} | Costo ARS: $${String.format("%,.2f", prod.costNoVAT)}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Button(
                                        onClick = {
                                            selectedPerrenProduct = prod
                                            showPerrenSearchModal = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Elegir", fontSize = 10.sp)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPerrenSearchModal = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // MODAL DE SELECCIÓN CANTON FAIR (ARTÍCULOS DE PROVEEDORES)
    if (showCantonPickerModal) {
        AlertDialog(
            onDismissRequest = { showCantonPickerModal = false },
            title = { Text("🇨🇳 Seleccionar Artículo Canton Fair", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (allSupplierArticles.isEmpty()) {
                        Text("No tienes artículos guardados en proveedores todavía. Puedes utilizar el ingreso FOB manual en el Tab 1.", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(allSupplierArticles) { (sup, art) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCantonArticle = art
                                            selectedCantonSupplierName = sup.companyName
                                            showCantonPickerModal = false
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(art.name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("${sup.companyName} | FOB: $${String.format(Locale.US, "%.2f", art.fobPriceUSD)} USD | MOQ: ${art.moq}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Button(
                                        onClick = {
                                            selectedCantonArticle = art
                                            selectedCantonSupplierName = sup.companyName
                                            showCantonPickerModal = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Elegir", fontSize = 10.sp)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCantonPickerModal = false }) {
                    Text("Cerrar")
                }
            }
        )
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostgresSearchScreen(
    suppliers: List<LocalSupplier> = emptyList(),
    onSelectProductForComparison: ((PerrenPostgresProduct) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf("Todas las Marcas") }
    var selectedCategory by remember { mutableStateOf("Todos los Rubros") }
    var selectedAbc by remember { mutableStateOf("Todas las Clases") }
    var itemsPerPage by remember { mutableStateOf(50) }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    var brandDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var abcDropdownExpanded by remember { mutableStateOf(false) }
    var limitDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val dbHelper = remember(context) { CantonSQLiteHelper(context) }

    var availableBrands by remember { mutableStateOf(listOf("Todas las Marcas")) }
    var availableCategories by remember { mutableStateOf(listOf("Todos los Rubros")) }
    val abcOptions = listOf("Todas las Clases", "🟢 Clase A", "🟡 Clase B", "🟠 Clase C")
    val itemsPerPageOptions = listOf(20, 50, 100, 200)

    var displayProducts by remember { mutableStateOf<List<PerrenPostgresProduct>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val cleanQuery = searchQuery.trim()
    val hasActiveFilter = cleanQuery.length >= 2 ||
            (selectedBrand.isNotBlank() && selectedBrand != "Todas las Marcas") ||
            (selectedCategory.isNotBlank() && selectedCategory != "Todos los Rubros") ||
            (selectedAbc.isNotBlank() && selectedAbc != "Todas las Clases") ||
            showOnlyFavorites

    val performSearch: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        val currentClean = searchQuery.trim()
        val currentActive = currentClean.length >= 2 ||
                (selectedBrand.isNotBlank() && selectedBrand != "Todas las Marcas") ||
                (selectedCategory.isNotBlank() && selectedCategory != "Todos los Rubros") ||
                (selectedAbc.isNotBlank() && selectedAbc != "Todas las Clases") ||
                showOnlyFavorites

        if (!currentActive) {
            displayProducts = emptyList()
            isSearching = false
        } else {
            isSearching = true
            CoroutineScope(Dispatchers.IO).launch {
                val dbResults = dbHelper.searchProductsInDb(currentClean, selectedBrand, selectedCategory, selectedAbc, itemsPerPage)
                val filtered = if (showOnlyFavorites) {
                    dbResults.filter { PerrenPostgresRepository.favoriteProductSkus.contains(it.sku) }
                } else {
                    dbResults
                }
                withContext(Dispatchers.Main) {
                    displayProducts = filtered.take(itemsPerPage)
                    isSearching = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        PerrenPostgresRepository.loadFavorites(context)
        withContext(Dispatchers.IO) {
            availableBrands = dbHelper.getDistinctBrands()
            availableCategories = dbHelper.getDistinctCategories()
            PerrenPostgresRepository.ensureDatabaseLoaded(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("🔍 BUSCADOR DE COSTOS & PRECIOS PERREN (SQLITE LOCAL / FLEXXUS BI)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
            Text("Consulta de Costos sin IVA, Costos con IVA (21%) y Precio Venta Público", fontSize = 10.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Card de Estado y Actualización de Base de Datos SQLite Local
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🟢 Base de Datos SQLite Local",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                    Text("Total: ${if (PerrenPostgresRepository.totalProductCount > 0) PerrenPostgresRepository.totalProductCount else 12074} artículos sincronizados", fontSize = 10.sp, color = Color.Gray)
                }
                Button(
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            PerrenPostgresRepository.syncDatabaseFromOnline(context) { count ->
                                Toast.makeText(context, "🔄 Base de Datos SQLite actualizada: $count artículos listos para consulta offline", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B365D)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    enabled = !PerrenPostgresRepository.isSyncingProducts
                ) {
                    Text(if (PerrenPostgresRepository.isSyncingProducts) "⏳ ACTUALIZANDO..." else "🔄 ACTUALIZAR BASE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Campo de búsqueda por texto libre con botón "BUSCAR" y tecla Enter configurada
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it.replace("\n", "").replace("\r", "")
                    displayProducts = emptyList()
                },
                label = { Text("Buscar por producto, marca o código...") },
                leadingIcon = { Text("🔍", fontSize = 16.sp) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            displayProducts = emptyList()
                        }) {
                            Text("❌", fontSize = 12.sp)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { performSearch() }
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = { performSearch() },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B365D)),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("🔍 BUSCAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filtros por Marca, Rubro
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Dropdown Marca
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { brandDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🏷️ $selectedBrand", fontSize = 10.sp, maxLines = 1)
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
                                displayProducts = emptyList()
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
                    Text("📂 $selectedCategory", fontSize = 10.sp, maxLines = 1)
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
                                displayProducts = emptyList()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Filtros por Clase ABC, Cantidad por Pantalla y Favoritos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Dropdown Clase ABC
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { abcDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📊 $selectedAbc", fontSize = 10.sp, maxLines = 1)
                }
                DropdownMenu(
                    expanded = abcDropdownExpanded,
                    onDismissRequest = { abcDropdownExpanded = false }
                ) {
                    abcOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, fontSize = 12.sp) },
                            onClick = {
                                selectedAbc = option
                                abcDropdownExpanded = false
                                displayProducts = emptyList()
                            }
                        )
                    }
                }
            }

            // Dropdown Cantidad por pantalla
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { limitDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📄 $itemsPerPage / pág", fontSize = 10.sp, maxLines = 1)
                }
                DropdownMenu(
                    expanded = limitDropdownExpanded,
                    onDismissRequest = { limitDropdownExpanded = false }
                ) {
                    itemsPerPageOptions.forEach { limit ->
                        DropdownMenuItem(
                            text = { Text("$limit artículos", fontSize = 12.sp) },
                            onClick = {
                                itemsPerPage = limit
                                limitDropdownExpanded = false
                                displayProducts = emptyList()
                            }
                        )
                    }
                }
            }

            // Botón Filtro Favoritos
            OutlinedButton(
                onClick = {
                    showOnlyFavorites = !showOnlyFavorites
                    displayProducts = emptyList()
                },
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (showOnlyFavorites) Color(0xFFFFF8E1) else Color.Transparent
                ),
                border = BorderStroke(1.dp, if (showOnlyFavorites) Color(0xFFFFA000) else Color.LightGray)
            ) {
                Text(
                    text = if (showOnlyFavorites) "⭐ Fav (${PerrenPostgresRepository.favoriteProductSkus.size})" else "☆ Fav (${PerrenPostgresRepository.favoriteProductSkus.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showOnlyFavorites) Color(0xFFE65100) else Color.DarkGray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Resultados (${displayProducts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            if (hasActiveFilter) {
                TextButton(onClick = {
                    searchQuery = ""
                    selectedBrand = "Todas las Marcas"
                    selectedCategory = "Todos los Rubros"
                    selectedAbc = "Todas las Clases"
                    itemsPerPage = 50
                    showOnlyFavorites = false
                    displayProducts = emptyList()
                }) {
                    Text("Limpiar filtros", fontSize = 11.sp, color = Color(0xFFD32F2F))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (displayProducts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!hasActiveFilter) {
                        Text("🔍 Consulta de Costos Flexxus", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B365D))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Escribe un término en el buscador arriba o selecciona una Marca, Rubro o Clase ABC para ver los resultados.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        Text("📦 No se encontraron productos", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (showOnlyFavorites)
                                "No tienes productos marcados como favoritos. Toca la estrella ⭐ en cualquier artículo para guardarlo en tus favoritos."
                            else if (searchQuery.isNotBlank())
                                "No se encontraron coincidencias para '$searchQuery' en la base de datos."
                            else
                                "Intenta modificar los filtros aplicados.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            displayProducts.forEach { prod ->
                val costWithVat = prod.costNoVAT * 1.21
                val salePriceVat = if (prod.priceVAT > 0) prod.priceVAT else (costWithVat * 1.40)
                val isFav = PerrenPostgresRepository.favoriteProductSkus.contains(prod.sku)

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        PerrenPostgresRepository.toggleFavorite(prod.sku, context)
                                        val msg = if (isFav) "Quitado de Favoritos" else "⭐ ¡Guardado en Favoritos!"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text(if (isFav) "⭐" else "☆", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(prod.sku, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                            }
                            Text("Stock: ${prod.stockAvailable} ${prod.unit}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(prod.description, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B365D))
                        Spacer(modifier = Modifier.height(4.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val abcBadge = when (prod.abcClass) {
                                "A" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32) to "🟢 Clase A"
                                "B" -> Color(0xFFFFF8E1) to Color(0xFFE65100) to "🟡 Clase B"
                                "C" -> Color(0xFFFFEBEE) to Color(0xFFD32F2F) to "🟠 Clase C"
                                else -> Color(0xFFE8F5E9) to Color(0xFF2E7D32) to "🟢 Clase A"
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = abcBadge.first.first)
                            ) {
                                Text(abcBadge.second, fontSize = 10.sp, color = abcBadge.first.second, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
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
                            if (prod.subcategory.isNotBlank()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                                ) {
                                    Text("📌 ${prod.subcategory}", fontSize = 10.sp, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (prod.classification.isNotBlank()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                                ) {
                                    Text("🗂️ ${prod.classification}", fontSize = 10.sp, color = Color(0xFFE65100), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (isFav) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                                ) {
                                    Text("⭐ Favorito", fontSize = 10.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val salePriceNoVAT = if (prod.priceVAT > 0) prod.priceVAT / 1.21 else (prod.costNoVAT * 1.40)

                            Column {
                                Text("COSTO SIN IVA: $${String.format("%,.2f", prod.costNoVAT)} ARS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                Text("VENTA SIN IVA: $${String.format("%,.2f", salePriceNoVAT)} ARS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
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