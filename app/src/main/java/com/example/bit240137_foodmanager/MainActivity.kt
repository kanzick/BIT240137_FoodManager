package com.example.bit240137_foodmanager

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.bit240137_foodmanager.data.CategoryDao
import com.example.bit240137_foodmanager.data.Dish
import com.example.bit240137_foodmanager.data.DishCategory
import com.example.bit240137_foodmanager.data.DishImage
import com.example.bit240137_foodmanager.data.DishWithDetails
import com.example.bit240137_foodmanager.data.FoodDatabase
import com.example.bit240137_foodmanager.ui.theme.BIT240137_FoodManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = FoodDatabase.get(applicationContext)
        setContent {
            val preferences = remember {
                getSharedPreferences("BIT240137_FoodManager", MODE_PRIVATE)
            }
            var themeChoiceName by rememberSaveable {
                mutableStateOf(preferences.getString("theme", ThemeChoice.SYSTEM.name) ?: ThemeChoice.SYSTEM.name)
            }
            val themeChoice = ThemeChoice.entries.firstOrNull { it.name == themeChoiceName }
                ?: ThemeChoice.SYSTEM
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = when (themeChoice) {
                ThemeChoice.SYSTEM -> systemDark
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            BIT240137_FoodManagerTheme(darkTheme = useDarkTheme) {
                FoodManagerApp(
                    database = database,
                    themeChoice = themeChoice,
                    onThemeChoice = { choice ->
                        themeChoiceName = choice.name
                        preferences.edit().putString("theme", choice.name).apply()
                    }
                )
            }
        }
    }
}

private enum class Screen { LIST, DETAIL, DISH_FORM, CATEGORIES, CATEGORY_FORM }
private enum class ThemeChoice { SYSTEM, LIGHT, DARK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodManagerApp(
    database: FoodDatabase,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("BIT240137_FoodManager", 0) }
    var isVietnamese by rememberSaveable {
        mutableStateOf(preferences.getBoolean("vietnamese", false))
    }
    val dishes by database.dishDao().observeAllWithDetails().collectAsState(initial = emptyList())
    val categories by database.categoryDao().observeAll().collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(Screen.LIST.name) }
    var selectedDishId by rememberSaveable { mutableStateOf(0L) }
    var selectedCategoryId by rememberSaveable { mutableStateOf(0L) }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf(0L) }
    var availabilityFilter by rememberSaveable { mutableStateOf("all") }
    var minimumPrice by rememberSaveable { mutableStateOf("") }
    var maximumPrice by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf("name") }
    var showDeleteDish by remember { mutableStateOf(false) }
    var showDeleteCategory by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }

    fun text(english: String, vietnamese: String) = if (isVietnamese) vietnamese else english
    fun showScreen(next: Screen) {
        errorMessage = ""
        screen = next.name
    }
    fun launchDatabaseAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                errorMessage = ""
            } catch (_: SQLiteConstraintException) {
                errorMessage = text(
                    "This name is already in use, or the record is still referenced.",
                    "Tên này đã được sử dụng hoặc dữ liệu vẫn đang được tham chiếu."
                )
            } catch (exception: IllegalArgumentException) {
                errorMessage = exception.message ?: text("Invalid data.", "Dữ liệu không hợp lệ.")
            } catch (exception: Exception) {
                Log.e("BIT240137_FoodManager", "Local database operation failed", exception)
                errorMessage = text(
                    "Could not complete the operation. Please try again.",
                    "Không thể hoàn tất thao tác. Vui lòng thử lại."
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        launchDatabaseAction { database.addSampleDataIfNeeded() }
    }

    val selectedDish = dishes.firstOrNull { it.dish.id == selectedDishId }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text("BIT240137 Food Manager", "BIT240137 · Quản lý món ăn"),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text(
                                "Local Room database",
                                "Dữ liệu Room lưu trên thiết bị"
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        isVietnamese = !isVietnamese
                        preferences.edit().putBoolean("vietnamese", isVietnamese).apply()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text(
                            "Language · English",
                            "Ngôn ngữ · Tiếng Việt"
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
                BoxMenu(
                    label = when (themeChoice) {
                        ThemeChoice.SYSTEM -> text("Theme · System", "Giao diện · Theo hệ thống")
                        ThemeChoice.LIGHT -> text("Theme · Light", "Giao diện · Sáng")
                        ThemeChoice.DARK -> text("Theme · Dark", "Giao diện · Tối")
                    },
                    expanded = themeMenuExpanded,
                    onExpandedChange = { themeMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    listOf(
                        ThemeChoice.SYSTEM to text("System default", "Theo hệ thống"),
                        ThemeChoice.LIGHT to text("Light", "Sáng"),
                        ThemeChoice.DARK to text("Dark", "Tối")
                    ).forEach { (choice, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onThemeChoice(choice)
                                themeMenuExpanded = false
                            }
                        )
                    }
                }
            }
            if (errorMessage.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(errorMessage, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { errorMessage = "" }) {
                            Text(text("Dismiss", "Đóng"))
                        }
                    }
                }
            }
            when (Screen.valueOf(screen)) {
                Screen.LIST -> DishListScreen(
                    dishes = dishes,
                    categories = categories,
                    search = search,
                    onSearch = { search = it },
                    categoryFilter = categoryFilter,
                    onCategoryFilter = { categoryFilter = it },
                    availabilityFilter = availabilityFilter,
                    onAvailabilityFilter = { availabilityFilter = it },
                    minimumPrice = minimumPrice,
                    onMinimumPrice = { minimumPrice = it },
                    maximumPrice = maximumPrice,
                    onMaximumPrice = { maximumPrice = it },
                    sortOrder = sortOrder,
                    onSortOrder = { sortOrder = it },
                    onAdd = { selectedDishId = 0L; showScreen(Screen.DISH_FORM) },
                    onManageCategories = { showScreen(Screen.CATEGORIES) },
                    onOpenDish = { selectedDishId = it; showScreen(Screen.DETAIL) },
                    isVietnamese = isVietnamese,
                    text = ::text,
                )

                Screen.DETAIL -> {
                    if (selectedDish == null) {
                        EmptyRecordScreen(
                            message = text("Dish not found.", "Không tìm thấy món ăn."),
                            backLabel = text("Back to dishes", "Về danh sách món"),
                            onBack = { showScreen(Screen.LIST) }
                        )
                    } else {
                        DishDetailScreen(
                            item = selectedDish,
                            isVietnamese = isVietnamese,
                            text = ::text,
                            onBack = { showScreen(Screen.LIST) },
                            onEdit = { showScreen(Screen.DISH_FORM) },
                            onDelete = { showDeleteDish = true },
                            onAddImage = { imageUrl ->
                                launchDatabaseAction {
                                    val uri = Uri.parse(imageUrl)
                                    require(
                                        uri.scheme == "content" ||
                                            uri.scheme == "file" ||
                                            (uri.scheme == "https" && !uri.host.isNullOrBlank())
                                    ) {
                                        text(
                                            "Enter a valid HTTPS image URL or choose a local image.",
                                            "Nhập URL ảnh HTTPS hợp lệ hoặc chọn ảnh trên thiết bị."
                                        )
                                    }
                                    if (uri.scheme == "content") {
                                        context.contentResolver.takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    }
                                    val existing = database.dishImageDao()
                                        .observeForDish(selectedDish.dish.id).first()
                                    database.addImage(
                                        DishImage(
                                            dishId = selectedDish.dish.id,
                                            imageUrl = imageUrl,
                                            isThumbnail = existing.isEmpty()
                                        )
                                    )
                                }
                            },
                            onSetThumbnail = { imageId ->
                                launchDatabaseAction {
                                    database.markThumbnail(selectedDish.dish.id, imageId)
                                }
                            },
                            onDeleteImage = { imageId ->
                                launchDatabaseAction {
                                    database.dishImageDao().delete(selectedDish.dish.id, imageId)
                                }
                            }
                        )
                    }
                }

                Screen.DISH_FORM -> DishFormScreen(
                    initial = selectedDish?.dish.takeIf { selectedDishId != 0L },
                    categories = categories,
                    text = ::text,
                    onCancel = { showScreen(if (selectedDishId == 0L) Screen.LIST else Screen.DETAIL) },
                    onSave = { dish ->
                        launchDatabaseAction {
                            val name = dish.name.trim()
                            require(name.isNotEmpty()) {
                                text("Dish name is required.", "Vui lòng nhập tên món ăn.")
                            }
                            require(dish.price > 0 && dish.price.isFinite() && dish.price % 1.0 == 0.0) {
                                text("Price must be a whole VND amount greater than zero.", "Giá VNĐ phải là số nguyên lớn hơn 0.")
                            }
                            require(dish.preparationTime > 0) {
                                text("Preparation time must be greater than zero.", "Thời gian chế biến phải lớn hơn 0.")
                            }
                            require(database.categoryDao().findById(dish.dishCategoryId) != null) {
                                text("Choose a valid category.", "Vui lòng chọn loại món hợp lệ.")
                            }
                            require(
                                database.dishDao().duplicateName(
                                    dish.dishCategoryId,
                                    name,
                                    dish.id
                                ) == 0
                            ) {
                                text("A dish with this name already exists in this category.", "Tên món đã tồn tại trong loại này.")
                            }
                            val cleanedDish = dish.copy(name = name, description = dish.description.trim())
                            if (cleanedDish.id == 0L) {
                                val newId = database.dishDao().insert(cleanedDish)
                                withContext(Dispatchers.Main) {
                                    selectedDishId = newId
                                    showScreen(Screen.DETAIL)
                                }
                            } else {
                                database.dishDao().update(cleanedDish)
                                withContext(Dispatchers.Main) { showScreen(Screen.DETAIL) }
                            }
                        }
                    }
                )

                Screen.CATEGORIES -> CategoryListScreen(
                    categories = categories,
                    text = ::text,
                    onBack = { showScreen(Screen.LIST) },
                    onAdd = { selectedCategoryId = 0L; showScreen(Screen.CATEGORY_FORM) },
                    onEdit = { selectedCategoryId = it; showScreen(Screen.CATEGORY_FORM) },
                    onDelete = { selectedCategoryId = it; showDeleteCategory = true },
                    dishCountForCategory = { id -> dishes.count { it.dish.dishCategoryId == id } }
                )

                Screen.CATEGORY_FORM -> CategoryFormScreen(
                    initial = selectedCategory.takeIf { selectedCategoryId != 0L },
                    text = ::text,
                    onCancel = { showScreen(Screen.CATEGORIES) },
                    onSave = { category ->
                        launchDatabaseAction {
                            val name = category.name.trim()
                            require(name.isNotEmpty()) {
                                text("Category name is required.", "Vui lòng nhập tên loại món.")
                            }
                            val dao: CategoryDao = database.categoryDao()
                            require(dao.duplicateName(name, category.id) == 0) {
                                text("A category with this name already exists.", "Tên loại món này đã tồn tại.")
                            }
                            if (category.id == 0L) {
                                dao.insert(category.copy(name = name, description = category.description.trim()))
                            } else {
                                dao.update(category.id, name, category.description.trim())
                            }
                            withContext(Dispatchers.Main) { showScreen(Screen.CATEGORIES) }
                        }
                    }
                )
            }
        }
    }

    if (showDeleteDish) {
        ConfirmDialog(
            title = text("Delete dish?", "Xóa món ăn?"),
            body = text("This also deletes its saved image records.", "Thao tác này cũng xóa các ảnh đã lưu của món."),
            confirm = text("Delete", "Xóa"),
            dismiss = text("Cancel", "Hủy"),
            onDismiss = { showDeleteDish = false },
            onConfirm = {
                showDeleteDish = false
                selectedDish?.let { item ->
                    launchDatabaseAction {
                        database.dishDao().delete(item.dish.id)
                        withContext(Dispatchers.Main) { showScreen(Screen.LIST) }
                    }
                }
            }
        )
    }
    if (showDeleteCategory) {
        ConfirmDialog(
            title = text("Delete category?", "Xóa loại món?"),
            body = text(
                "A category used by any dish cannot be deleted.",
                "Không thể xóa loại món đang được sử dụng."
            ),
            confirm = text("Delete", "Xóa"),
            dismiss = text("Cancel", "Hủy"),
            onDismiss = { showDeleteCategory = false },
            onConfirm = {
                showDeleteCategory = false
                launchDatabaseAction {
                    val dao = database.categoryDao()
                    require(dao.dishCount(selectedCategoryId) == 0) {
                        text("This category is used by a dish.", "Loại món này đang được sử dụng.")
                    }
                    dao.delete(selectedCategoryId)
                }
            }
        )
    }
}

@Composable
private fun DishListScreen(
    dishes: List<DishWithDetails>,
    categories: List<DishCategory>,
    search: String,
    onSearch: (String) -> Unit,
    categoryFilter: Long,
    onCategoryFilter: (Long) -> Unit,
    availabilityFilter: String,
    onAvailabilityFilter: (String) -> Unit,
    minimumPrice: String,
    onMinimumPrice: (String) -> Unit,
    maximumPrice: String,
    onMaximumPrice: (String) -> Unit,
    sortOrder: String,
    onSortOrder: (String) -> Unit,
    onAdd: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenDish: (Long) -> Unit,
    isVietnamese: Boolean,
    text: (String, String) -> String,
) {
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var availabilityMenu by remember { mutableStateOf(false) }
    val minValue = minimumPrice.toDoubleOrNull()
    val maxValue = maximumPrice.toDoubleOrNull()
    val invalidRange = (minimumPrice.isNotBlank() && (minValue == null || minValue < 0)) ||
        (maximumPrice.isNotBlank() && (maxValue == null || maxValue < 0)) ||
        (minValue != null && maxValue != null && minValue > maxValue)
    val filtered = if (invalidRange) emptyList() else dishes.asSequence()
        .filter { it.dish.name.contains(search.trim(), ignoreCase = true) }
        .filter { categoryFilter == 0L || it.dish.dishCategoryId == categoryFilter }
        .filter {
            when (availabilityFilter) {
                "available" -> it.dish.isAvailable
                "unavailable" -> !it.dish.isAvailable
                else -> true
            }
        }
        .filter { (minValue == null || it.dish.price >= minValue) && (maxValue == null || it.dish.price <= maxValue) }
        .let { sequence ->
            when (sortOrder) {
                "price_up" -> sequence.sortedBy { it.dish.price }
                "price_down" -> sequence.sortedByDescending { it.dish.price }
                "time" -> sequence.sortedBy { it.dish.preparationTime }
                else -> sequence.sortedBy { it.dish.name.lowercase(Locale.ROOT) }
            }
        }
        .toList()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Text(text("Add dish", "Thêm món"), textAlign = TextAlign.Center, maxLines = 2)
            }
            OutlinedButton(onClick = onManageCategories, modifier = Modifier.weight(1f)) {
                Text(text("Categories", "Loại món"), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text("Your menu", "Thực đơn"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text("${dishes.size} dishes", "${dishes.size} món ăn"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text("BIT240137 · Food Manager", "BIT240137 · Quản lý món ăn"),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text("Search dishes", "Tìm món ăn")) },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxMenu(
                label = categories.firstOrNull { it.id == categoryFilter }?.name
                    ?: text("All categories", "Tất cả loại"),
                expanded = categoryMenu,
                onExpandedChange = { categoryMenu = it },
                modifier = Modifier.weight(1f)
            ) {
                DropdownMenuItem(
                    text = { Text(text("All categories", "Tất cả loại")) },
                    onClick = { onCategoryFilter(0); categoryMenu = false }
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = { onCategoryFilter(category.id); categoryMenu = false }
                    )
                }
            }
            BoxMenu(
                label = when (availabilityFilter) {
                    "available" -> text("Available", "Đang bán")
                    "unavailable" -> text("Unavailable", "Ngừng bán")
                    else -> text("All status", "Mọi trạng thái")
                },
                expanded = availabilityMenu,
                onExpandedChange = { availabilityMenu = it },
                modifier = Modifier.weight(1f)
            ) {
                DropdownMenuItem(
                    text = { Text(text("All status", "Mọi trạng thái")) },
                    onClick = { onAvailabilityFilter("all"); availabilityMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(text("Available", "Đang bán")) },
                    onClick = { onAvailabilityFilter("available"); availabilityMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(text("Unavailable", "Ngừng bán")) },
                    onClick = { onAvailabilityFilter("unavailable"); availabilityMenu = false }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = minimumPrice,
                onValueChange = onMinimumPrice,
                modifier = Modifier.weight(1f),
                label = { Text(text("Min price (VND)", "Giá từ (VNĐ)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = maximumPrice,
                onValueChange = onMaximumPrice,
                modifier = Modifier.weight(1f),
                label = { Text(text("Max price (VND)", "Giá đến (VNĐ)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }
        if (invalidRange) {
            Text(
                text = "⚠ ${text("Invalid price range", "Khoảng giá không hợp lệ")}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text("Sort:", "Sắp xếp:"), modifier = Modifier.padding(end = 8.dp))
            BoxMenu(
                label = when (sortOrder) {
                    "price_up" -> text("Price: low to high", "Giá tăng dần")
                    "price_down" -> text("Price: high to low", "Giá giảm dần")
                    "time" -> text("Preparation time", "Thời gian chế biến")
                    else -> text("Name", "Tên")
                },
                expanded = sortMenu,
                onExpandedChange = { sortMenu = it }
            ) {
                listOf(
                    "name" to text("Name", "Tên"),
                    "price_up" to text("Price: low to high", "Giá tăng dần"),
                    "price_down" to text("Price: high to low", "Giá giảm dần"),
                    "time" to text("Preparation time", "Thời gian chế biến")
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSortOrder(value); sortMenu = false }
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            Text(
                text("No dishes match these filters.", "Không có món phù hợp với bộ lọc."),
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.dish.id }) { item ->
                    Card(onClick = { onOpenDish(item.dish.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${item.category.name}  •  ${item.dish.price.money(isVietnamese)}")
                            Text(
                                "${item.dish.preparationTime} ${text("min", "phút")}  •  " +
                                    if (item.dish.preparationTime <= 15) text("Quick", "Nhanh")
                                    else text("Regular", "Thông thường")
                            )
                            Text(
                                if (item.dish.isAvailable) text("Available", "Đang bán")
                                else text("Unavailable", "Ngừng bán"),
                                color = if (item.dish.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            item.images.firstOrNull { it.isThumbnail }?.let { image ->
                                LocalImagePreview(
                                    image.imageUrl,
                                        Modifier.fillMaxWidth().height(140.dp),
                                        { english, vietnamese -> text(english, vietnamese) }
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxMenu(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        OutlinedButton(onClick = {
            onExpandedChange(!expanded)
        },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(label, maxLines = 2, textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = {
            onExpandedChange(false)
        }, content = content)
    }
}

@Composable
private fun DishDetailScreen(
    item: DishWithDetails,
    isVietnamese: Boolean,
    text: (String, String) -> String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddImage: (String) -> Unit,
    onSetThumbnail: (Long) -> Unit,
    onDeleteImage: (Long) -> Unit
) {
    var imageUrl by rememberSaveable(item.dish.id) { mutableStateOf("") }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onAddImage(it.toString()) }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← ${text("Back to dishes", "Về danh sách món")}", textAlign = TextAlign.Start)
        }
        Text(item.dish.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${item.category.name}  •  ${item.dish.price.money(isVietnamese)}")
        Text("${text("Preparation", "Thời gian chế biến")}: ${item.dish.preparationTime} ${text("min", "phút")}")
        Text(if (item.dish.preparationTime <= 15) text("Quick", "Nhanh") else text("Regular", "Thông thường"))
        Text(if (item.dish.isAvailable) text("Available", "Đang bán") else text("Unavailable", "Ngừng bán"))
        Text(item.dish.description.ifBlank { text("No description.", "Chưa có mô tả.") })
        Text(text("Images", "Hình ảnh"), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { imagePicker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text("Choose image from device", "Chọn ảnh trên thiết bị"))
        }
        Text(
            text(
                "Images are saved locally. HTTPS URLs are loaded from the web when online.",
                "Thông tin ảnh lưu cục bộ. URL HTTPS sẽ tải ảnh từ Internet khi có mạng."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (item.images.isEmpty()) {
            Text(text("No images added yet.", "Chưa có ảnh nào."))
        } else {
            item.images.forEach { image ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        LocalImagePreview(
                            image.imageUrl,
                            Modifier.fillMaxWidth().height(160.dp),
                            { english, vietnamese -> text(english, vietnamese) }
                        )
                        Text(image.imageUrl)
                        Text(if (image.isThumbnail) text("Thumbnail", "Ảnh đại diện") else text("Additional image", "Ảnh bổ sung"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!image.isThumbnail) {
                                TextButton(
                                    onClick = { onSetThumbnail(image.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text("Make thumbnail", "Đặt làm ảnh đại diện"),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            }
                            TextButton(
                                onClick = { onDeleteImage(image.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text("Remove image", "Xóa ảnh"), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text("Image URL / local URI", "Đường dẫn ảnh / URI nội bộ")) },
            supportingText = {
                Text(
                    text(
                        "Paste an HTTPS image URL or select a local image above.",
                        "Dán URL ảnh HTTPS hoặc chọn ảnh trên thiết bị ở trên."
                    )
                )
            },
            singleLine = true
        )
        Button(
            onClick = {
                if (imageUrl.isNotBlank()) {
                    onAddImage(imageUrl.trim())
                    imageUrl = ""
                }
            },
            enabled = imageUrl.isNotBlank()
        ) { Text(text("Add image", "Thêm ảnh")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Text(text("Edit", "Sửa"), textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text(text("Delete", "Xóa"), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DishFormScreen(
    initial: Dish?,
    categories: List<DishCategory>,
    text: (String, String) -> String,
    onCancel: () -> Unit,
    onSave: (Dish) -> Unit
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var price by rememberSaveable(initial?.id) { mutableStateOf(initial?.price?.toString().orEmpty()) }
    var preparationTime by rememberSaveable(initial?.id) { mutableStateOf(initial?.preparationTime?.toString().orEmpty()) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var available by rememberSaveable(initial?.id) { mutableStateOf(initial?.isAvailable ?: true) }
    var categoryId by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.dishCategoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var categoryPickerOpen by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf("") }
    LaunchedEffect(categories, initial?.id) {
        if (categories.isNotEmpty() && categories.none { it.id == categoryId }) {
            categoryId = initial?.dishCategoryId
                ?.takeIf { id -> categories.any { it.id == id } }
                ?: categories.first().id
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text(if (initial == null) "Add dish" else "Edit dish", if (initial == null) "Thêm món" else "Sửa món"),
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(name, { name = it; validationMessage = "" }, Modifier.fillMaxWidth(), label = { Text(text("Name", "Tên món")) })
        OutlinedTextField(
            price,
            { price = it; validationMessage = "" },
            Modifier.fillMaxWidth(),
            label = { Text(text("Price (VND)", "Giá (VNĐ)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        OutlinedTextField(
            preparationTime,
            { preparationTime = it; validationMessage = "" },
            Modifier.fillMaxWidth(),
            label = { Text(text("Preparation time (minutes)", "Thời gian chế biến (phút)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        OutlinedButton(
            onClick = { categoryPickerOpen = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                categories.firstOrNull { it.id == categoryId }?.name
                    ?: text("Choose a category", "Chọn loại món")
            )
        }
        OutlinedTextField(
            description,
            { description = it },
            Modifier.fillMaxWidth(),
            label = { Text(text("Description", "Mô tả")) },
            minLines = 3
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = available, onCheckedChange = { available = it })
            Text(text("Available for sale", "Đang bán"))
        }
        if (validationMessage.isNotBlank()) {
            Text(validationMessage, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(text("Cancel", "Hủy"), textAlign = TextAlign.Center, maxLines = 2)
            }
            Button(
                onClick = {
                    val parsedPrice = price.toDoubleOrNull()
                    val parsedTime = preparationTime.toIntOrNull()
                    validationMessage = when {
                        name.isBlank() -> text("Enter a dish name.", "Vui lòng nhập tên món.")
                        parsedPrice == null || parsedPrice <= 0 || !parsedPrice.isFinite() ||
                            parsedPrice % 1.0 != 0.0 ->
                            text("Enter a whole VND amount greater than zero.", "Giá VNĐ phải là số nguyên lớn hơn 0.")
                        parsedTime == null || parsedTime <= 0 ->
                            text("Preparation time must be greater than zero.", "Thời gian chế biến phải lớn hơn 0.")
                        categoryId == 0L -> text("Add or choose a category first.", "Hãy thêm hoặc chọn loại món.")
                        else -> ""
                    }
                    if (validationMessage.isBlank()) {
                        onSave(
                            Dish(
                                id = initial?.id ?: 0,
                                name = name,
                                price = parsedPrice!!,
                                preparationTime = parsedTime!!,
                                isAvailable = available,
                                description = description,
                                dishCategoryId = categoryId
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(text("Save", "Lưu"), textAlign = TextAlign.Center, maxLines = 2) }
        }
    }

    if (categoryPickerOpen) {
        AlertDialog(
            onDismissRequest = { categoryPickerOpen = false },
            title = { Text(text("Choose a category", "Chọn loại món")) },
            text = {
                if (categories.isEmpty()) {
                    Text(
                        text(
                            "No categories are available. Go back and add a category first.",
                            "Chưa có loại món. Hãy quay lại và thêm loại món trước."
                        )
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        categories.forEach { category ->
                            TextButton(
                                onClick = {
                                    categoryId = category.id
                                    validationMessage = ""
                                    categoryPickerOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(category.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { categoryPickerOpen = false }) {
                    Text(text("Done", "Xong"))
                }
            }
        )
    }
}

@Composable
private fun CategoryListScreen(
    categories: List<DishCategory>,
    text: (String, String) -> String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    dishCountForCategory: (Long) -> Int
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("← ${text("Dishes", "Danh sách món")}", textAlign = TextAlign.Center, maxLines = 2)
            }
            Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Text(text("Add category", "Thêm loại"), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        if (categories.isEmpty()) {
            Text(text("No categories yet.", "Chưa có loại món nào."))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            Text(category.description.ifBlank { text("No description.", "Chưa có mô tả.") })
                            Text("${dishCountForCategory(category.id)} ${text("dishes", "món")}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onEdit(category.id) }, modifier = Modifier.weight(1f)) {
                                    Text(text("Edit", "Sửa"), textAlign = TextAlign.Center)
                                }
                                TextButton(onClick = { onDelete(category.id) }, modifier = Modifier.weight(1f)) {
                                    Text(text("Delete", "Xóa"), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFormScreen(
    initial: DishCategory?,
    text: (String, String) -> String,
    onCancel: () -> Unit,
    onSave: (DishCategory) -> Unit
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var validationMessage by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text(if (initial == null) "Add category" else "Edit category", if (initial == null) "Thêm loại món" else "Sửa loại món"),
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(text("Name", "Tên loại")) })
        OutlinedTextField(
            description,
            { description = it },
            Modifier.fillMaxWidth(),
            label = { Text(text("Description", "Mô tả")) },
            minLines = 3
        )
        if (validationMessage.isNotBlank()) Text(validationMessage, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(text("Cancel", "Hủy"), textAlign = TextAlign.Center, maxLines = 2)
            }
            Button(onClick = {
                if (name.isBlank()) {
                    validationMessage = text("Enter a category name.", "Vui lòng nhập tên loại món.")
                } else {
                    onSave(DishCategory(id = initial?.id ?: 0L, name = name, description = description))
                }
            }, modifier = Modifier.weight(1f)) {
                Text(text("Save", "Lưu"), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
private fun LocalImagePreview(
    url: String,
    modifier: Modifier = Modifier,
    text: (String, String) -> String
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = text("Dish image", "Ảnh món ăn"),
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text("Loading image…", "Đang tải ảnh…"))
            }
        },
        error = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text("Image unavailable. Check the URL or connection.", "Không tải được ảnh. Hãy kiểm tra URL hoặc kết nối."),
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}

@Composable
private fun EmptyRecordScreen(message: String, backLabel: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, modifier = Modifier.padding(24.dp))
        Button(onClick = onBack) { Text(backLabel) }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } }
    )
}

private fun Double.money(isVietnamese: Boolean): String =
    if (isVietnamese) {
        java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
            .format(this) + " VNĐ"
    } else {
        String.format(Locale.US, "VND %,.0f", this)
    }
