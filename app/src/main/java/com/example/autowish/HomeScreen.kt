package com.example.autowish

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavController, isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    val context = LocalContext.current
    val db = BirthdayDatabase.getInstance(context)
    var todayBirthdays by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var upcomingBirthdays by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var hasData by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showCsvDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Date formatters
    val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Function to update birthdays
    suspend fun updateBirthdays() {
        try {
            val today = Calendar.getInstance()
            val currentYear = yearFormat.format(today.time).toInt()
            val todayMMdd = dateFormat.format(today.time)
            val sevenDaysLater = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 7) }
            val sevenDaysTime = sevenDaysLater.timeInMillis

            val allBirthdays = db.birthdayDao().getAll()
            hasData = allBirthdays.isNotEmpty()

            // Today's birthdays
            val todayList = allBirthdays
                .filter { entry ->
                    try {
                        val parts = entry.birthDate.split("-")
                        if (parts.size == 2) {
                            val birthMMdd = "${parts[0]}-${parts[1]}"
                            birthMMdd == todayMMdd
                        } else false
                    } catch (e: Exception) {
                        false
                    }
                }

            // Upcoming birthdays (next 7 days, excluding today)
            val upcomingList = allBirthdays
                .mapNotNull { entry ->
                    try {
                        val parts = entry.birthDate.split("-")
                        if (parts.size == 2) {
                            val month = parts[0].toIntOrNull()
                            val day = parts[1].toIntOrNull()
                            val birthMMdd = "${parts[0]}-${parts[1]}"
                            if (month != null && day != null && birthMMdd != todayMMdd) {
                                val nextBirthday = Calendar.getInstance().apply {
                                    set(Calendar.MONTH, month - 1)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.YEAR, currentYear)
                                    if (timeInMillis <= today.timeInMillis) {
                                        add(Calendar.YEAR, 1)
                                    }
                                }
                                Pair(entry, nextBirthday.timeInMillis)
                            } else null
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                .filter { (_, birthTime) ->
                    birthTime > today.timeInMillis && birthTime <= sevenDaysTime
                }
                .sortedBy { it.second }
                .map { it.first }
                .take(7)

            withContext(Dispatchers.Main) {
                todayBirthdays = todayList
                upcomingBirthdays = upcomingList
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                errorMessage = "Failed to load birthdays: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            updateBirthdays()
        }
    }

    // CSV import
    val importCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entries = parseCsv(context, uri)
                    db.birthdayDao().deleteAll()
                    entries.forEach { db.birthdayDao().insert(it) }
                    updateBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${entries.size} birthdays imported successfully!")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "CSV import failed: ${e.message}"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("CSV import failed: ${e.message}")
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    // CSV merge
    val mergeCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entries = parseCsv(context, uri)
                    var insertedCount = 0
                    entries.forEach { entry ->
                        val existing = db.birthdayDao().getByNameAndPhone(entry.name, entry.phoneNumber)
                        if (existing.isEmpty()) {
                            db.birthdayDao().insert(entry)
                            insertedCount++
                        }
                    }
                    updateBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("$insertedCount birthdays merged successfully!")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "CSV merge failed: ${e.message}"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("CSV merge failed: ${e.message}")
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AutoWish",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 28.sp,
                            letterSpacing = 4.sp
                        )
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ThemeSwitch(
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = onThemeToggle
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                isHomeSelected = true
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    Text(
                        text = "Upload CSV file or add data manually",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(bottom = 18.dp)
                            .fillMaxWidth()
                    )
                }
                item {
                    UploadAddCards(
                        onShowCsvDialog = { showCsvDialog = true },
                        onShowForm = { showForm = true }
                    )
                }
                item {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Text(
                        text = "Today's Birthdays",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }
                item {
                    if (todayBirthdays.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "No birthdays today",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        HeroBirthdayCarousel(birthdays = todayBirthdays)
                    }
                }
                item {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                }
                item {
                    Text(
                        text = "Upcoming Birthdays",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                    )
                }
                if (upcomingBirthdays.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No upcoming birthdays.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    items(upcomingBirthdays, key = { it.id }) { entry ->
                        BirthdayCard(entry)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                errorMessage?.let {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("Upload CSV") },
            text = { Text("Importing a new file will delete all existing entries. To keep existing entries and add new ones, select 'Merge CSV' instead.") },
            confirmButton = {
                TextButton(onClick = {
                    showCsvDialog = false
                    importCsvPicker.launch("text/*")
                }) {
                    Text("New CSV File")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCsvDialog = false
                        mergeCsvPicker.launch("text/*")
                    },
                    enabled = hasData
                ) {
                    Text("Merge CSV File")
                }
            }
        )
    }

    if (showForm) {
        BirthdayEntryForm(
            onSave = { entry ->
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                    db.birthdayDao().insert(entry)
                    updateBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Birthday for ${entry.name} saved!")
                        }
                        showForm = false
                        isLoading = false
                    }
                }
            },
            onCancel = { showForm = false },
            initialEntry = null,
            database = db,
            coroutineScope = coroutineScope,
            setErrorMessage = { errorMessage = it }
        )
    }
}

@Composable
fun ThemeSwitch(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateFloatAsState(
        targetValue = if (isDarkTheme) 24f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ThumbOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (isDarkTheme) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.primary,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "TrackColor"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isDarkTheme) 360f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "IconRotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDarkTheme) 1f else 1f,
        animationSpec = keyframes {
            durationMillis = 300
            0.8f at 0 with LinearEasing
            1.2f at 150 with LinearEasing
            1f at 300 with LinearEasing
        },
        label = "IconScale"
    )

    // Particle effect for transition
    val infiniteTransition = rememberInfiniteTransition(label = "ParticleAnimation")
    val particleAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticleAlpha"
    )

    // Resolve color outside Canvas
    val particleColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .clickable(onClick = onThemeToggle),
        contentAlignment = Alignment.CenterStart
    ) {
        // Particle effect (small stars/dots)
        if (particleAlpha > 0f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = (thumbOffset + 12f).dp, y = 12.dp)
            ) {
                drawCircle(
                    color = particleColor.copy(alpha = particleAlpha * 0.5f),
                    radius = 2.dp.toPx(),
                    center = center.copy(x = center.x + 8.dp.toPx(), y = center.y - 4.dp.toPx())
                )
                drawCircle(
                    color = particleColor.copy(alpha = particleAlpha * 0.3f),
                    radius = 1.5.dp.toPx(),
                    center = center.copy(x = center.x - 6.dp.toPx(), y = center.y + 4.dp.toPx())
                )
            }
        }

        // Thumb with icon
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Default.Nightlight else Icons.Default.WbSunny,
                contentDescription = "Toggle ${if (isDarkTheme) "light" else "dark"} mode",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroBirthdayCarousel(birthdays: List<BirthdayEntry>) {
    val pagerState = rememberPagerState(pageCount = { birthdays.size })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(bottom = 16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 8.dp
    ) { page ->
        val entry = birthdays[page]
        val isSelected = page == currentPage
        val iconScale by animateFloatAsState(
            targetValue = if (isSelected) 1f else 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "IconScale"
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 🎉 Background image (birthday-themed)
                Image(
                    painter = painterResource(id = R.drawable.birthday2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.10f)
                        .clip(RoundedCornerShape(24.dp))
                        .align(Alignment.BottomCenter),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                )

                // Icon tag for Student, Staff, or HOD
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = (-16).dp, y = 16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            id = when {
                                entry.personType == "Staff" && entry.isHod -> R.drawable.hod
                                entry.personType == "Staff" -> R.drawable.staff
                                else -> R.drawable.student
                            }
                        ),
                        contentDescription = when {
                            entry.personType == "Staff" && entry.isHod -> "HOD"
                            entry.personType == "Staff" -> "Staff"
                            else -> "Student"
                        },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(30.dp)
                            .scale(iconScale),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Name — bigger
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            letterSpacing = 1.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Birth date
                    Text(
                        text = "Date: ${entry.birthDate}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Department and Year in one line
                    val departmentAndYear = buildString {
                        append(entry.department)
                        if (!entry.year.isNullOrBlank()) {
                            append("    ") // Add spacing between department and year
                            append(entry.year)
                        }
                    }

                    Text(
                        text = departmentAndYear,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Phone number
                    Text(
                        text = "Phone: ${entry.phoneNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun UploadAddCards(
    onShowCsvDialog: () -> Unit,
    onShowForm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier
                .size(width = 140.dp, height = 110.dp)
                .padding(end = 6.dp)
        ) {
            IconButton(
                onClick = { onShowCsvDialog() },
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.upload),
                    contentDescription = "Upload CSV",
                    modifier = Modifier.size(55.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier
                .size(width = 140.dp, height = 110.dp)
                .padding(start = 6.dp)
        ) {
            IconButton(
                onClick = { onShowForm() },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Entry",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(55.dp)
                )
            }
        }
    }
}