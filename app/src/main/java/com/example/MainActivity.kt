package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.data.AgentEntity
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.SavedContentEntity
import com.example.data.VoiceNoteEntity
import com.example.data.SocialAgentRepository
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.util.AudioRecorderManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.widget.MediaController
import androidx.core.content.FileProvider
import android.os.Environment
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize DB and Repository
        val db = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = SocialAgentRepository(db.socialAgentDao())
        val factory = MainViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = SpaceDark
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State bindings
    val agents by viewModel.allAgents.collectAsStateWithLifecycle()
    val savedContent by viewModel.allSavedContent.collectAsStateWithLifecycle()
    val voiceNotes by viewModel.allVoiceNotes.collectAsStateWithLifecycle()
    val selectedAgent by viewModel.selectedAgent.collectAsStateWithLifecycle()
    val chatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val chatLoading by viewModel.chatLoading.collectAsStateWithLifecycle()
    val generatorLoading by viewModel.generatorLoading.collectAsStateWithLifecycle()
    val generatedResult by viewModel.generatedContentResult.collectAsStateWithLifecycle()

    // Local state for localization/language
    var isArabic by remember { mutableStateOf(true) }

    // Audio recorder manager
    val audioManager = remember { AudioRecorderManager(context) }
    DisposableEffect(Unit) {
        onDispose { audioManager.release() }
    }

    // Mic permission launcher
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, if (isArabic) "تم تفعيل إذن الميكروفون!" else "Microphone permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, if (isArabic) "يرجى منح إذن الميكروفون لتسجيل الصوت" else "Microphone permission is required to record voice", Toast.LENGTH_SHORT).show()
        }
    }

    // Navigation tab
    var activeTab by remember { mutableStateOf(0) } // 0: Chat, 1: Studio, 2: Vault, 3: Academy, 4: Voice Account

    // Custom Agent Creation Dialog
    var showCreateAgentDialog by remember { mutableStateOf(false) }
    var showLogViewerDialog by remember { mutableStateOf(false) }
    var previewVideoPath by remember { mutableStateOf<String?>(null) }

    // Quick trigger to switch to Chat tab when an agent is selected
    LaunchedEffect(selectedAgent) {
        if (selectedAgent != null) {
            activeTab = 0
        }
    }

    // Interactive helper strings based on active language
    val tTitle = if (isArabic) "مدير حسابات السوشيال ميديا الذكي" else "Social Agent AI Terminal"
    val tSubtitle = if (isArabic) "خبراء تحليل البيانات، صناعة المحتوى وضوابط المنصات" else "Expert Data Analysts, Publishers & Policy Guardians"
    val tTab1 = if (isArabic) "العملاء والدردشة" else "Agents & Chat"
    val tTab2 = if (isArabic) "استوديو المحتوى" else "Content Studio"
    val tTab3 = if (isArabic) "الخزنة المحفوظة" else "Saved Vault"
    val tTab4 = if (isArabic) "دليل الضوابط" else "Academy"
    val tTab5 = if (isArabic) "حسابي وبصمتي" else "Voice Account"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceDark)
    ) {
        // App Premium Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentTeal)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextBright
                    )
                }
                Text(
                    text = tSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Log Viewer Button
                IconButton(
                    onClick = { showLogViewerDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpaceSurface)
                        .size(40.dp)
                        .testTag("log_viewer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Logs",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Language Switcher Button
                IconButton(
                    onClick = { isArabic = !isArabic },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpaceSurface)
                        .size(40.dp)
                        .testTag("lang_toggle")
                ) {
                    Text(
                        text = if (isArabic) "EN" else "AR",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan
                    )
                }
            }
        }

        // Warning if Gemini API Key is missing
        if (!viewModel.isApiKeyAvailable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FF3B30)),
                border = BorderStroke(1.dp, Color(0xFFFF3B30)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isArabic) "مفتاح API مفقود!" else "API Key Missing!",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isArabic)
                                "يرجى تكوين GEMINI_API_KEY في لوحة أسرار AI Studio لتنشيط عملاء الذكاء الاصطناعي."
                            else
                                "Please configure GEMINI_API_KEY in the AI Studio Secrets panel to enable AI replies.",
                            color = TextBright,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Custom Navigation Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SpaceSurface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tabs = listOf(
                TabItem(tTab1, Icons.Default.Chat, 0),
                TabItem(tTab2, Icons.Default.Create, 1),
                TabItem(tTab3, Icons.Default.Inventory, 2),
                TabItem(tTab4, Icons.Default.Analytics, 3),
                TabItem(tTab5, Icons.Default.Mic, 4)
            )

            tabs.forEach { tab ->
                val selected = activeTab == tab.index
                val bgSelected = if (selected) Brush.horizontalGradient(listOf(NeonCyan.copy(0.15f), NeonPurple.copy(0.15f))) else null
                val borderSelected = if (selected) BorderStroke(1.dp, NeonCyan.copy(0.3f)) else null
                val textColor = if (selected) NeonCyan else TextMuted

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { activeTab = tab.index }
                        .then(if (bgSelected != null) Modifier.background(bgSelected) else Modifier)
                        .then(if (borderSelected != null) Modifier.border(borderSelected, RoundedCornerShape(8.dp)) else Modifier)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tab.title,
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Active Tab Screen Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                0 -> AgentsChatTab(
                    viewModel = viewModel,
                    agents = agents,
                    selectedAgent = selectedAgent,
                    chatMessages = chatMessages,
                    chatLoading = chatLoading,
                    audioManager = audioManager,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    isArabic = isArabic,
                    onCreateAgentClick = { showCreateAgentDialog = true }
                )
                1 -> ContentStudioTab(
                    viewModel = viewModel,
                    agents = agents,
                    generatorLoading = generatorLoading,
                    generatedResult = generatedResult,
                    isArabic = isArabic,
                    onPreviewVideo = { path -> previewVideoPath = path }
                )
                2 -> SavedVaultTab(
                    viewModel = viewModel,
                    savedContent = savedContent,
                    isArabic = isArabic,
                    onPreviewVideo = { path -> previewVideoPath = path }
                )
                3 -> GuidelinesAcademyTab(isArabic = isArabic)
                4 -> VoiceAccountTab(
                    viewModel = viewModel,
                    voiceNotes = voiceNotes,
                    audioManager = audioManager,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    isArabic = isArabic
                )
            }
        }
    }

    // Dialog to create custom agent
    if (showCreateAgentDialog) {
        CreateAgentDialog(
            isArabic = isArabic,
            onDismiss = { showCreateAgentDialog = false },
            onSave = { name, platform, personality, description, systemInstruction ->
                viewModel.createCustomAgent(name, platform, personality, description, systemInstruction)
                showCreateAgentDialog = false
                Toast.makeText(context, if (isArabic) "تم إنشاء العميل بنجاح!" else "Agent created successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // System Log Viewer Dialog
    if (showLogViewerDialog) {
        LogViewerDialog(
            viewModel = viewModel,
            onDismiss = { showLogViewerDialog = false }
        )
    }

    // Video Preview Player Dialog
    if (previewVideoPath != null) {
        VideoPreviewDialog(
            videoPath = previewVideoPath!!,
            onDismiss = { previewVideoPath = null }
        )
    }
}

data class TabItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val index: Int)

// --- CHAT & AGENTS TERMINAL TAB ---
@Composable
fun AgentsChatTab(
    viewModel: MainViewModel,
    agents: List<AgentEntity>,
    selectedAgent: AgentEntity?,
    chatMessages: List<ChatMessageEntity>,
    chatLoading: Boolean,
    audioManager: AudioRecorderManager,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    isArabic: Boolean,
    onCreateAgentClick: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputMessageText by remember { mutableStateOf("") }
    val isRecording by audioManager.isRecording.collectAsStateWithLifecycle()
    val recordingTimer by audioManager.recordingTimer.collectAsStateWithLifecycle()
    var chatAudioFile by remember { mutableStateOf<File?>(null) }

    // Scroll chat to bottom on new message
    LaunchedEffect(chatMessages.size, chatLoading) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AlMalaf 71 Channel Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, SpaceCard), RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.img_social_agent_hero),
                    contentDescription = "AlMalaf 71 Studio Hero",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Dark Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, SpaceDark.copy(0.95f))
                            )
                        )
                )

                // Overlay Text content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isArabic) "🎬 منظومة وكلاء قناة (الملف 71)" else "🎬 AlMalaf 71 Multi-Agent System",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HotPink)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "5 AI AGENTS",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = if (isArabic)
                            "فريق متكامل لقناة الملف 71: صياد التريندات، الكاتب ياسر، المراجعة أمينة، المونتير، ومسؤول السيو"
                        else
                            "5 specialized AI agents working together for AlMalaf 71 crime channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBright.copy(0.9f),
                        maxLines = 2
                    )
                }
            }
        }

        // Big Red Button Card for Immediate Episode Generation & Preview
        item {
            val pipelineLoading by viewModel.pipelineLoading.collectAsStateWithLifecycle()
            val context = LocalContext.current

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0808)),
                border = BorderStroke(2.dp, Color(0xFFFF3B30)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test Episode",
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isArabic) "🔴 تجربة فورية - توليد أول حلقة للمعاينة" else "🔴 Instant Preview - Generate First Episode",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isArabic) "ينشئ فيديو سينمائي تجريبي بالسيناريو والصوت فوراً ويحفظه في /Movies/AlMalaf71 والخزانة!" else "Generates immediate test episode video, saves to Movies & Room DB",
                                fontSize = 10.5.sp,
                                color = Color.White.copy(0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.generateTestEpisode(context) { generatedVideoPath ->
                                Toast.makeText(context, if (isArabic) "تم إنشاء حلقة المعاينة وتصديرها إلى /Movies/AlMalaf71 بنجاح!" else "Preview episode created!", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !pipelineLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_test_episode_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3B30),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (pipelineLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isArabic) "جاري تصنيع فيديو المعاينة..." else "Generating preview...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Movie, contentDescription = "Generate", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isArabic) "🎬 توليد أول حلقة الآن للمعاينة" else "🎬 Generate First Episode Now for Preview",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: AlMalaf 71 Team Production Pipeline (خط الإنتاج الجماعي)
        item {
            val pipelineLoading by viewModel.pipelineLoading.collectAsStateWithLifecycle()
            val pipelineStep by viewModel.pipelineStep.collectAsStateWithLifecycle()
            val pipelineResult by viewModel.pipelineResult.collectAsStateWithLifecycle()
            var topicInput by remember { mutableStateOf("") }
            var activePipelineTab by remember { mutableStateOf(0) } // 0: Trend, 1: Script, 2: Hook, 3: Editor, 4: SEO

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.5.dp, NeonPurple.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Team Workflow",
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isArabic) "خط الإنتاج الجماعي (فريق الملف 71)" else "AlMalaf 71 Team Pipeline",
                                fontWeight = FontWeight.ExtraBold,
                                color = TextBright,
                                fontSize = 15.sp
                            )
                        }

                        if (pipelineResult != null) {
                            TextButton(onClick = { viewModel.resetPipeline() }) {
                                Text(
                                    text = if (isArabic) "إعادة تعيين" else "Reset",
                                    color = Color.Red.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isArabic)
                            "أدخل قضية أو تريند جريمة، وسيقوم الوكلاء الخمسة بالعمل كفريق متكامل لإخراج الحلقة بالكامل!"
                        else
                            "Enter a crime topic. The 5 agents will process it sequentially from trend to SEO!",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Topic Input
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        placeholder = {
                            Text(
                                text = if (isArabic) "مثال: قضية سفاح التجمع / لغز مقتل نيرة أصلان..." else "e.g., Real crime trend case...",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SpaceCard,
                            focusedContainerColor = SpaceDark,
                            unfocusedContainerColor = SpaceDark,
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextBright
                        )
                    )

                    // Quick topic chips
                    Spacer(modifier = Modifier.height(6.dp))
                    val quickTopics = if (isArabic) listOf(
                        "قضية سفاح التجمع والضحايا المفاجئة",
                        "لغز اختفاء السفينة الجنائية في البحر الأحمر",
                        "أخطر قضية احتيال في الصعيد",
                        "جريمة القرية الغامضة وعودة الحقائق"
                    ) else listOf(
                        "Tagamoa Serial Crime Case",
                        "Red Sea Cargo Ship Mystery",
                        "Upper Egypt Heist Trial",
                        "Unsolved Desert Mystery"
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickTopics) { t ->
                            FilterChip(
                                selected = topicInput == t,
                                onClick = { topicInput = t },
                                label = { Text(text = t, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = SpaceDark,
                                    labelColor = TextBright,
                                    selectedContainerColor = NeonPurple.copy(0.4f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Start Team Pipeline Button
                    Button(
                        onClick = { viewModel.runAlMalafTeamPipeline(topicInput) },
                        enabled = !pipelineLoading,
                        modifier = Modifier.fillMaxWidth().testTag("run_team_pipeline_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        if (pipelineLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isArabic) "الوكلاء الـ 8 يعملون تلقائياً الآن (خطوة $pipelineStep من 8)..." else "8 Agents running autonomously (Step $pipelineStep of 8)...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run Team")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "🚀 تشغيل الوضع الجماعي التلقائي (8 وكلاء)" else "🚀 Run Autonomous 8-Agent Team Pipeline",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Live Pipeline Progress Tracker & Agent Task Log
                    if (pipelineLoading || pipelineStep > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (isArabic) "مراحل خط الإنتاج التلقائي لقناة الملف 71 (8 وكلاء):" else "AlMalaf 71 Autonomous 8-Agent Workflow:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val stepsList = listOf(
                            "1. TrendHunter" to "صيد التريند عبر Google Search Grounding",
                            "2. الكاتب ياسر" to "كتابة سيناريو وثائقي 5 دقائق بالعامية المصرية",
                            "3. المراجعة أمينة" to "هوك 7 ثوانٍ وتدقيق سياسات يوتيوب والأمان",
                            "4. VoiceClone Agent" to "توليد التعليق الصوتي المصري بـ Fish Audio API",
                            "5. Montage Agent" to "تركيب المشاهد البصرية والصوت وإنشاء الفيديو",
                            "6. صانع الشورتس والبوستر" to "إنتاج 2 شورتس قصيرة + برومبت البوستر الصادم",
                            "7. مسؤول النشر والسيو" to "حفظ حزمة الفيديو و5 عناوين كليك بيت والسيو",
                            "8. خبير التحليلات والجمهور" to "تقييم استبقاء المشاهدين واستراتيجية التفاعل"
                        )

                        stepsList.forEachIndexed { index, (agentStepName, taskDesc) ->
                            val stepNumber = index + 1
                            val isCurrent = pipelineStep == stepNumber && pipelineLoading
                            val isCompleted = pipelineStep > stepNumber || pipelineStep >= 9
                            val bg = when {
                                isCurrent -> NeonCyan.copy(0.2f)
                                isCompleted -> AccentTeal.copy(0.15f)
                                else -> SpaceDark
                            }
                            val border = when {
                                isCurrent -> BorderStroke(1.dp, NeonCyan)
                                isCompleted -> BorderStroke(1.dp, AccentTeal)
                                else -> BorderStroke(1.dp, SpaceCard)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.5.dp),
                                colors = CardDefaults.cardColors(containerColor = bg),
                                border = border
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isCompleted) "✅" else if (isCurrent) "⏳" else "⚪",
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = agentStepName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isCurrent) NeonCyan else TextBright
                                            )
                                            Text(
                                                text = taskDesc,
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isCompleted) AccentTeal.copy(0.3f)
                                                else if (isCurrent) NeonCyan.copy(0.3f)
                                                else SpaceDark
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isCompleted) "مكتمل" else if (isCurrent) "جاري التنفيذ" else "في الانتظار",
                                            fontSize = 9.sp,
                                            color = if (isCompleted) AccentTeal else if (isCurrent) NeonCyan else TextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Pipeline Output Display & Agent Task Logs
                    pipelineResult?.let { res ->
                        // Live Agent Task Log Box
                        if (res.taskLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (isArabic) "📜 سجل مهام الوكلاء المباشر (Agent Task Log):" else "📜 Live Agent Task Log:",
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonCyan,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp),
                                colors = CardDefaults.cardColors(containerColor = SpaceDark),
                                border = BorderStroke(1.dp, NeonCyan.copy(0.4f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    res.taskLogs.forEach { log ->
                                        Text(
                                            text = log,
                                            fontSize = 10.5.sp,
                                            color = if (log.contains("✅") || log.contains("🎉")) AccentTeal else if (log.contains("🚀") || log.contains("🔍")) NeonCyan else TextBright,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (res.trendData.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (isArabic) "🎬 حزمة إنتاج فريق الملف 71 لحلقة: ${res.topic}" else "Team Output for Episode: ${res.topic}",
                                fontWeight = FontWeight.Bold,
                                color = TextBright,
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action buttons: Play Voiceover & Download Video
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Play Human Voiceover Button
                                Button(
                                    onClick = {
                                        if (res.audioFilePath.isNotEmpty()) {
                                            audioManager.playAudio(res.audioFilePath)
                                            Toast.makeText(context, if (isArabic) "جاري تشغيل التعليق الصوتي البشري..." else "Playing human voiceover...", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, if (isArabic) "ملف التعليق الصوتي غير جاهز بـ Fish Audio" else "Voice file not ready", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("play_human_voiceover_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Play Voice", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isArabic) "▶️ تشغيل الصوت البشري" else "▶️ Play Voiceover",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                // Download Video Button
                                Button(
                                    onClick = {
                                        Toast.makeText(context, if (isArabic) "تم حفظ وتنزيل فيديو الحلقة بنجاح! المسار: ${res.videoFilePath}" else "Video saved to downloads!", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f).testTag("download_video_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = "Download Video", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isArabic) "⬇️ تحميل الفيديو النهائي" else "⬇️ Download Video",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Tabs to switch outputs
                            ScrollableTabRow(
                                selectedTabIndex = activePipelineTab,
                                edgePadding = 0.dp,
                                containerColor = SpaceDark,
                                contentColor = NeonCyan
                            ) {
                                Tab(
                                    selected = activePipelineTab == 0,
                                    onClick = { activePipelineTab = 0 },
                                    text = { Text("1. التريند", fontSize = 11.sp) }
                                )
                                Tab(
                                    selected = activePipelineTab == 1,
                                    onClick = { activePipelineTab = 1 },
                                    text = { Text("2. السيناريو", fontSize = 11.sp) }
                                )
                                Tab(
                                    selected = activePipelineTab == 2,
                                    onClick = { activePipelineTab = 2 },
                                    text = { Text("3. الهوك", fontSize = 11.sp) }
                                )
                                Tab(
                                    selected = activePipelineTab == 3,
                                    onClick = { activePipelineTab = 3 },
                                    text = { Text("4. التعليق الصوتي", fontSize = 11.sp) }
                                )
                                Tab(
                                    selected = activePipelineTab == 4,
                                    onClick = { activePipelineTab = 4 },
                                    text = { Text("5. المونتاج والفيديو", fontSize = 11.sp) }
                                )
                                Tab(
                                    selected = activePipelineTab == 5,
                                    onClick = { activePipelineTab = 5 },
                                    text = { Text("6. الشورتس والبوستر", fontSize = 11.sp) }
                                )
                                Tab(
                                    selected = activePipelineTab == 6,
                                    onClick = { activePipelineTab = 6 },
                                    text = { Text("7. السيو والنشر", fontSize = 11.sp) }
                                )
                                Tab(
                                    selected = activePipelineTab == 7,
                                    onClick = { activePipelineTab = 7 },
                                    text = { Text("8. التحليلات والجمهور", fontSize = 11.sp) }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val contentToShow = when (activePipelineTab) {
                                0 -> "🔍 [1- صياد التريندات - TrendHunter]:\n\n" + res.trendData
                                1 -> "✍️ [2- الكاتب ياسر - 5-Minute Script]:\n\n" + res.scriptData
                                2 -> "🕵️‍♀️ [3- المراجعة أمينة - 7-Second Hook Audit]:\n\n" + res.hookData
                                3 -> "🎙️ [4- VoiceClone Agent - Fish Audio Voice]:\n\n" + res.audioStatus + "\n\nمسار ملف الصوت: " + res.audioFilePath
                                4 -> "🎬 [5- Montage Agent - Video Timeline]:\n\n" + res.montageData + "\n\nمسار ملف الفيديو MP4: " + res.videoFilePath
                                5 -> "🎨 [6- صانع الشورتس والبوستر - Shorts & Thumbnail]:\n\n" + res.shortsData
                                6 -> "🚀 [7- مسؤول النشر والسيو - YouTube SEO]:\n\n" + res.seoData
                                else -> "📊 [8- خبير التحليلات والجمهور - Analytics Auditor]:\n\n" + res.analyticsData
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpaceDark)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    SelectionContainer {
                                        Text(
                                            text = contentToShow,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextBright,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveGeneratedContent(
                                            title = "حلقة الملف 71: ${res.topic}",
                                            platform = "YouTube",
                                            contentType = "Script",
                                            content = contentToShow,
                                            agentName = "فريق قناة الملف 71"
                                        )
                                        Toast.makeText(context, if (isArabic) "تم حفظ مخرجات الفريق في الخزنة!" else "Saved to Vault!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                                ) {
                                    Icon(imageVector = Icons.Default.Bookmark, contentDescription = "Save", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isArabic) "حفظ في الخزنة" else "Save to Vault", fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("AlMalaf71", contentToShow)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, if (isArabic) "تم نسخ النص!" else "Copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isArabic) "نسخ" else "Copy", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Agent List with Tasks & Status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isArabic) "قائمة وكلاء قناة الملف 71" else "AlMalaf 71 Agents Directory",
                        fontWeight = FontWeight.Bold,
                        color = TextBright,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isArabic) "المهام المخصصة والحالة المباشرة لكل عميل" else "Assigned tasks & live status of each agent",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                TextButton(
                    onClick = onCreateAgentClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentTeal)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isArabic) "عميل مخصص" else "Custom Agent", fontSize = 12.sp)
                }
            }
        }

        // List of All 5 Agents with Tasks and Status
        items(agents) { agent ->
            val isSelected = selectedAgent?.id == agent.id
            val cardBorderColor = if (isSelected) NeonCyan else SpaceCard
            val cardBg = if (isSelected) SpaceCard else SpaceSurface

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectAgent(agent) }
                    .testTag("agent_card_${agent.id}"),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.5.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (agent.id) {
                                            "almalaf_trend_hunter" -> NeonCyan.copy(0.2f)
                                            "almalaf_writer_yasser" -> NeonPurple.copy(0.2f)
                                            "almalaf_reviewer_amina" -> HotPink.copy(0.2f)
                                            "almalaf_editor_shorts" -> AccentTeal.copy(0.2f)
                                            else -> SpaceDark
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (agent.id) {
                                        "almalaf_trend_hunter" -> "🔍"
                                        "almalaf_writer_yasser" -> "✍️"
                                        "almalaf_reviewer_amina" -> "🕵️‍♀️"
                                        "almalaf_editor_shorts" -> "🎬"
                                        "almalaf_uploader_seo" -> "🚀"
                                        else -> "🤖"
                                    },
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = agent.name,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextBright,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = agent.personality,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) AccentTeal.copy(0.25f) else SpaceDark
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AccentTeal else SpaceCard,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AccentTeal else NeonCyan)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = agent.currentStatus,
                                    fontSize = 10.sp,
                                    color = if (isSelected) AccentTeal else TextBright,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Specialized Task Display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpaceDark)
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TaskAlt,
                                    contentDescription = "Task",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isArabic) "المهمة التخصصية:" else "Specialized Task:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = agent.specializedTask,
                                fontSize = 12.sp,
                                color = TextBright.copy(0.9f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Description & Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = agent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.selectAgent(agent) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) AccentTeal else SpaceCard
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Chat",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSelected) (if (isArabic) "الدردشة النشطة" else "Active Chat") else (if (isArabic) "بدء الدردشة" else "Start Chat"),
                                fontSize = 11.sp,
                                color = TextBright
                            )
                        }
                    }
                }
            }
        }

        // Details of selected agent & Chat window
        if (selectedAgent != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                    border = BorderStroke(1.dp, SpaceCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "Robot",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedAgent.name,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextBright
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedAgent.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextBright.copy(0.9f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isArabic) "التعليمات البرمجية للعميل:" else "Agent Guidelines Override:",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonPurple,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedAgent.systemInstruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Quick actions shortcuts
            item {
                Text(
                    text = if (isArabic) "اختصارات سريعة للمحادثة" else "Quick Prompts",
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val quickPrompts = if (isArabic) listOf(
                    "راجع منشوري بخصوص ضوابط منصة فيسبوك للربح",
                    "كيف أتجنب الباند وحظر المحتوى المكرر؟",
                    "حلل لي أرقام المنشور: CTR 3% و Retention 20%",
                    "اعطني 3 عناوين ذكية لفيديو يوتيوب ترند"
                ) else listOf(
                    "Review my draft post for FB policy compliance",
                    "How to prevent reused content flags on YT?",
                    "Analyze my data: 3.5% CTR, 40% retention",
                    "Give me a viral hooked caption for tech news"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        SuggestionChip(
                            onClick = { inputMessageText = prompt },
                            label = { Text(text = prompt, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = TextBright,
                                containerColor = SpaceCard
                            ),
                            border = BorderStroke(1.dp, SpaceCard)
                        )
                    }
                }
            }

            // Chat dialog screen list
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "نافذة المحاكاة" else "Simulation Window",
                        fontWeight = FontWeight.Bold,
                        color = TextBright,
                        fontSize = 14.sp
                    )
                    TextButton(
                        onClick = { viewModel.clearActiveChat() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(0.8f))
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isArabic) "مسح الدردشة" else "Clear Chat", fontSize = 11.sp)
                    }
                }
            }

            if (chatMessages.isEmpty() && !chatLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpaceSurface)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic)
                                "ابدأ المحادثة الآن! اسأل العميل عن خوارزميات المنصة، أو اكتب منشورا ليقوم بمراجعته وتدقيقه فوراً."
                            else
                                "Start chat! Ask this agent about terms of service, platform compliance, or supply copy to audit.",
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(chatMessages) { message ->
                    val isUser = message.sender == "user"
                    val bubbleBg = if (isUser) NeonPurple.copy(alpha = 0.25f) else SpaceSurface
                    val align = if (isUser) Alignment.End else Alignment.Start
                    val bubbleBorder = if (isUser) BorderStroke(1.dp, NeonPurple.copy(0.5f)) else BorderStroke(1.dp, SpaceCard)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalAlignment = align
                    ) {
                        Text(
                            text = if (isUser) (if (isArabic) "أنت" else "You") else selectedAgent.name,
                            fontSize = 10.sp,
                            color = if (isUser) NeonPurple else NeonCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isUser) 12.dp else 0.dp,
                                        bottomEnd = if (isUser) 0.dp else 12.dp
                                    )
                                )
                                .background(bubbleBg)
                                .border(
                                    bubbleBorder,
                                    RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isUser) 12.dp else 0.dp,
                                        bottomEnd = if (isUser) 0.dp else 12.dp
                                    )
                                )
                                .padding(12.dp)
                                .widthIn(max = 290.dp)
                        ) {
                            Text(
                                text = message.message,
                                color = TextBright,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (chatLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "${selectedAgent.name} يكتب الآن..." else "${selectedAgent.name} is typing...",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Chat input row
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 24.dp)
                ) {
                    if (isRecording) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                            border = BorderStroke(1.dp, Color.Red),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val formattedTime = String.format(Locale.US, "%02d:%02d", recordingTimer / 60, recordingTimer % 60)
                                    Text(
                                        text = if (isArabic) "جاري تسجيل صوتك... $formattedTime" else "Recording voice... $formattedTime",
                                        color = Color.Red,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { audioManager.stopRecording() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                                    ) {
                                        Text(text = if (isArabic) "إلغاء" else "Cancel", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val durationSec = audioManager.stopRecording()
                                            chatAudioFile?.let { file ->
                                                val msg = if (isArabic)
                                                    "🎙️ [رسالة صوتية مسجلة] (${durationSec} ثانية): هل يمكنك إعطائي مراجعة سريعة وإستراتيجية للهاشتاقات؟"
                                                else
                                                    "🎙️ [Recorded Voice Note] (${durationSec}s): Can you audit my strategy?"
                                                viewModel.sendChatMessage(msg)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = TextBright),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send Voice", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isArabic) "إرسال الصوت" else "Send Voice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputMessageText,
                            onValueChange = { inputMessageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_text_field"),
                            placeholder = {
                                Text(
                                    text = if (isArabic) "أرسل رسالة أو سجل صوتك للعميل..." else "Ask, paste content, or record voice...",
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SpaceSurface,
                                unfocusedContainerColor = SpaceSurface,
                                focusedTextColor = TextBright,
                                unfocusedTextColor = TextBright,
                                focusedIndicatorColor = NeonCyan,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (inputMessageText.isNotBlank()) {
                                    viewModel.sendChatMessage(inputMessageText)
                                    inputMessageText = ""
                                }
                            })
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Mic Button
                        IconButton(
                            onClick = {
                                if (!hasMicPermission) {
                                    onRequestMicPermission()
                                } else {
                                    if (isRecording) {
                                        audioManager.stopRecording()
                                    } else {
                                        val file = File(context.filesDir, "chat_voice_${System.currentTimeMillis()}.mp4")
                                        chatAudioFile = file
                                        audioManager.startRecording(file)
                                    }
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isRecording) Color.Red else SpaceSurface)
                                .border(BorderStroke(1.dp, if (isRecording) Color.Red else NeonCyan.copy(0.5f)), CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record Voice",
                                tint = if (isRecording) TextBright else NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        FloatingActionButton(
                            onClick = {
                                if (inputMessageText.isNotBlank()) {
                                    viewModel.sendChatMessage(inputMessageText)
                                    inputMessageText = ""
                                }
                            },
                            containerColor = NeonCyan,
                            contentColor = SpaceDark,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("send_chat_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else {
            // No agent selected state
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpaceSurface)
                        .border(BorderStroke(1.dp, SpaceCard), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = "Select Agent",
                            tint = NeonPurple,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isArabic) "اختر عميلاً لتفعيل محطة السوشيال ميديا" else "Ready to manage accounts?",
                            fontWeight = FontWeight.Bold,
                            color = TextBright,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isArabic)
                                "انقر على أي من خبراء السوشيال ميديا أعلاه لبدء الدردشة التفاعلية معهم ومراجعة سياسات فيسبوك ويوتيوب بالتفصيل."
                            else
                                "Tap an agent to open their live interactive terminal, audit scripts, check monetization rules and optimize CTR.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// --- TAB 2: AI CONTENT STUDIO ---
@Composable
fun ContentStudioTab(
    viewModel: MainViewModel,
    agents: List<AgentEntity>,
    generatorLoading: Boolean,
    generatedResult: String,
    isArabic: Boolean,
    onPreviewVideo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedAgentIndex by remember { mutableStateOf(0) }
    var selectedPlatformIndex by remember { mutableStateOf(0) }
    var selectedToolIndex by remember { mutableStateOf(0) }
    var topicText by remember { mutableStateOf("") }
    var additionalInputText by remember { mutableStateOf("") }

    val platforms = listOf("Facebook", "YouTube", "TikTok", "Instagram")
    val tools = if (isArabic) {
        listOf("Caption" to "منشور تفاعلي", "Script" to "سيناريو فيديو", "Video Ideas" to "أفكار فيديو ريادية", "Comment Reply" to "رد ذكي")
    } else {
        listOf("Caption" to "Social Caption", "Script" to "Video Script", "Video Ideas" to "Content Ideas", "Comment Reply" to "Smart Reply")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isArabic) "استوديو صناعة وتدقيق المحتوى" else "AI Copywriting & Audit Studio",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextBright,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = if (isArabic) "اكتب الموضوع، اختر عميلك واصنع المحتوى في ثوانٍ مع تجنب مخالفات سياسات النشر" else "Write ideas, pick your specialized agent, and create compliant content.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        // Card 1: Setup Wizard
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Choose Agent
                    Text(
                        text = if (isArabic) "1. اختر العميل الخبير:" else "1. Choose Expert Agent:",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (agents.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(agents.size) { index ->
                                val agent = agents[index]
                                val selected = selectedAgentIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) NeonPurple.copy(0.25f) else SpaceCard)
                                        .border(BorderStroke(1.dp, if (selected) NeonPurple else Color.Transparent), RoundedCornerShape(8.dp))
                                        .clickable { selectedAgentIndex = index }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = agent.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) TextBright else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Choose Platform
                    Text(
                        text = if (isArabic) "2. اختر المنصة المستهدفة:" else "2. Choose Target Platform:",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        platforms.forEachIndexed { index, platform ->
                            val selected = selectedPlatformIndex == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AccentTeal.copy(0.2f) else SpaceCard)
                                    .border(BorderStroke(1.dp, if (selected) AccentTeal else Color.Transparent), RoundedCornerShape(8.dp))
                                    .clickable { selectedPlatformIndex = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = platform,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) TextBright else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Choose Output Format
                    Text(
                        text = if (isArabic) "3. نوع المحتوى:" else "3. Format of Output:",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tools.size) { index ->
                            val (key, value) = tools[index]
                            val selected = selectedToolIndex == index
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) HotPink.copy(0.2f) else SpaceCard)
                                    .border(BorderStroke(1.dp, if (selected) HotPink else Color.Transparent), RoundedCornerShape(8.dp))
                                    .clickable { selectedToolIndex = index }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = value,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) TextBright else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Topic Input
                    Text(
                        text = if (isArabic) "4. تفاصيل الموضوع والكلمات المفتاحية:" else "4. Core Topic or Draft Copy:",
                        fontWeight = FontWeight.Bold,
                        color = TextBright,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextField(
                        value = topicText,
                        onValueChange = { topicText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .testTag("studio_topic_input"),
                        placeholder = {
                            Text(
                                text = if (isArabic)
                                    "مثال: ميزات التحديث الجديد، أو الصق منشورك لتدقيقه..."
                                else
                                    "E.g., new product features, or paste copy to audit...",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SpaceCard,
                            unfocusedContainerColor = SpaceCard,
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextBright,
                            focusedIndicatorColor = NeonCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Secondary field conditionally shown for Comment Reply
                    if (tools[selectedToolIndex].first == "Comment Reply") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isArabic) "الصق تعليق المتابع هنا:" else "Paste the User Comment here:",
                            fontWeight = FontWeight.Bold,
                            color = TextBright,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = additionalInputText,
                            onValueChange = { additionalInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            placeholder = {
                                Text(
                                    text = if (isArabic) "كيف أستطيع الربح من هذه الصفحة؟" else "How can I make money with this page?",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SpaceCard,
                                unfocusedContainerColor = SpaceCard,
                                focusedTextColor = TextBright,
                                unfocusedTextColor = TextBright,
                                focusedIndicatorColor = NeonCyan,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button
                    Button(
                        onClick = {
                            if (agents.isNotEmpty() && topicText.isNotBlank()) {
                                viewModel.quickGenerate(
                                    agent = agents[selectedAgentIndex],
                                    topic = topicText,
                                    platform = platforms[selectedPlatformIndex],
                                    toolType = tools[selectedToolIndex].first,
                                    additionalInput = additionalInputText
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_content_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = SpaceDark
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !generatorLoading && topicText.isNotBlank() && agents.isNotEmpty()
                    ) {
                        if (generatorLoading) {
                            CircularProgressIndicator(color = SpaceDark, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Sparkle")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isArabic) "توليد المحتوى بالذكاء الاصطناعي" else "Generate Creative Draft",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Card 2: Response Output
        if (generatedResult.isNotBlank() || generatorLoading) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                    border = BorderStroke(1.5.dp, if (generatorLoading) NeonPurple else AccentTeal)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (generatorLoading) NeonPurple else AccentTeal)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (generatorLoading) (if (isArabic) "جاري التوليد والتدقيق..." else "Generating & Auditing...") else (if (isArabic) "النتيجة المقترحة والتدقيق" else "Generated Proposal"),
                                    fontWeight = FontWeight.Bold,
                                    color = TextBright
                                )
                            }

                            if (!generatorLoading && generatedResult.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Save Button
                                    IconButton(
                                        onClick = {
                                            if (agents.isNotEmpty()) {
                                                viewModel.saveGeneratedContent(
                                                    title = if (topicText.length > 25) topicText.take(22) + "..." else topicText,
                                                    platform = platforms[selectedPlatformIndex],
                                                    contentType = tools[selectedToolIndex].first,
                                                    content = generatedResult,
                                                    agentName = agents[selectedAgentIndex].name
                                                )
                                                Toast.makeText(context, if (isArabic) "تم الحفظ في الخزانة!" else "Saved to Vault!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save", tint = AccentTeal)
                                    }

                                    // Copy Button
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Generated Content", generatedResult)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, if (isArabic) "تم النسخ!" else "Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan)
                                    }

                                    // Share Button
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, generatedResult)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Content"))
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = NeonPurple)
                                    }
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = SpaceCard)

                        if (generatorLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = NeonPurple)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isArabic) "يتأكد الذكاء الاصطناعي من خوارزميات المنصة وضوابطها..." else "Checking social media policies and optimization formulas...",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Text(
                                text = generatedResult,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = TextBright
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 3: SAVED VAULT ---
@Composable
fun SavedVaultTab(
    viewModel: MainViewModel,
    savedContent: List<SavedContentEntity>,
    isArabic: Boolean,
    onPreviewVideo: (String) -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = if (isArabic) "خزانة المحتوى المعتمد" else "Approved Content Vault",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextBright,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = if (isArabic) "جميع المسودات والأفكار ومقاطع الفيديو المحفوظة في المجلد العام /Movies/AlMalaf71." else "All saved ideas, optimization scripts, and video exports archived.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        if (savedContent.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceSurface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Empty",
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isArabic) "الخزانة فارغة حالياً" else "Your vault is empty",
                            fontWeight = FontWeight.Bold,
                            color = TextBright
                        )
                        Text(
                            text = if (isArabic) "اضغط زر (توليد أول حلقة الآن للمعاينة) ليظهر فيديو الحلقة هنا فوراً!" else "Click 'Generate First Episode' to test and preview here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(savedContent) { item ->
                var expanded by remember { mutableStateOf(true) }

                var vPath = ""
                if (item.content.contains("مسار الفيديو:")) {
                    vPath = item.content.substringAfter("مسار الفيديو:").substringBefore("\n").trim()
                }
                if (vPath.isBlank()) {
                    val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AlMalaf71")
                    val latestFile = publicDir.listFiles()?.filter { it.extension == "mp4" }?.maxByOrNull { it.lastModified() }
                    if (latestFile != null) {
                        vPath = latestFile.absolutePath
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .testTag("saved_item_${item.id}"),
                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                    border = BorderStroke(1.dp, SpaceCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    color = TextBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.platform} • ${item.contentType}",
                                        fontSize = 11.sp,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "by ${item.agentName}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Row {
                                // Copy
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Saved Content", item.content)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, if (isArabic) "تم نسخ المحتوى!" else "Copied content!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan, modifier = Modifier.size(18.dp))
                                }

                                // Delete
                                IconButton(
                                    onClick = { viewModel.deleteSavedContent(item.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (vPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onPreviewVideo(vPath) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("preview_video_button_${item.id}"),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isArabic) "▶️ معاينة الفيديو المكتمل" else "▶️ Preview Completed Video", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        AnimatedVisibility(visible = expanded) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Divider(color = SpaceCard)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = TextBright
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 4: PLATFORM ACADEMY & POLICY ACCORD ---
@Composable
fun GuidelinesAcademyTab(isArabic: Boolean) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (isArabic) "أكاديمية المنصات والبيانات الذكية" else "Platform Guidelines & SMM Academy",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextBright,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = if (isArabic) "دليلك السريع كخبير ومُدير حسابات مهووس بالأرقام وسياسات الربح وقوانين المحتوى." else "Your cheat sheet on platform guidelines, metrics, and avoiding monetization bans.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        // Section: Facebook Guidelines
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Facebook, contentDescription = "FB", tint = AccentTeal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "ضوابط وسياسات فيسبوك للربح" else "Facebook Monetization Rules",
                            fontWeight = FontWeight.Bold,
                            color = TextBright
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isArabic) {
                            "1. المحتوى الأصلي: يجب أن تنشر محتوى قمت بصنعه أو تعديله بشكل كبير لإضافة قيمة إبداعية ملموسة لتفادي حظر 'المحتوى غير الأصلي بدرجة كبيرة'.\n" +
                                    "2. معايير المجتمع: تجنب العبارات المحفزة للكراهية، التضليل، أو التفاعل الزائف (مثل طلب التفاعل المباشر 'اضغط لايك واكتب تم').\n" +
                                    "3. حقوق النشر والموسيقى: استخدم الموسيقى من مكتبة الأصوات المجانية المخصصة لفيسبوك فقط لتفادي حظر أرباح الفيديو."
                        } else {
                            "1. Originality: Ensure significant creative edit or value-add to prevent 'Limited Originality' flags.\n" +
                                    "2. Engagement Bait: Never ask viewers directly to 'Like, comment and share' in an inorganic spammy way.\n" +
                                    "3. Sound Collection: Use Facebook's Creator Sound Library for reels and videos to keep copyright claims at 0%."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBright.copy(0.85f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Section: YouTube Guidelines
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.OndemandVideo, contentDescription = "YT", tint = HotPink)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "قوانين يوتيوب الصارمة للأرباح" else "YouTube Partner Program Policies",
                            fontWeight = FontWeight.Bold,
                            color = TextBright
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isArabic) {
                            "1. المحتوى المعاد استخدامه: يوتيوب يرفض تفعيل الربح على قنوات تقوم فقط بإعادة رفع محتوى آخرين دون تعليق صوتي واضح أو قيمة تعليمية مضافة.\n" +
                                    "2. إرشادات المحتوى المناسب للمعلنين: تجنب استخدام الكلمات النابية في أول 30 ثانية من الفيديو لتجنب ظهور 'الدولار الأصفر' وضمان تحقيق الربح الكامل.\n" +
                                    "3. الخدع التضليلية للعناوين: احرص على أن يطابق العنوان محتوى الفيديو الفعلي لمنع خفض نسبة الظهور بواسطة خوارزميات يوتيوب."
                        } else {
                            "1. Reused Content: Merely compiling others' videos without commentary or heavy edits results in monetization rejection.\n" +
                                    "2. Advertiser Friendly: Avoid harsh language/controversial topics in the first 30 seconds to bypass yellow-dollar flags.\n" +
                                    "3. Metadata Spam: Ensure titles match actual video visuals to prevent algorithmic demotions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBright.copy(0.85f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Section: Social Media Data Obsessive Checklist
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, NeonPurple.copy(0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Analytics, contentDescription = "Analytics", tint = NeonPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "قاموس مهووس التحليلات والأرقام" else "SMM Analytical Cheat Sheet",
                            fontWeight = FontWeight.Bold,
                            color = TextBright
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isArabic) {
                            "• نسبة النقر إلى الظهور (CTR): النسبة المئوية للمشاهدين الذين ينقرون على الفيديو بعد رؤية الغلاف. الهدف الممتاز: 6% - 10%.\n" +
                                    "• نسبة الاحتفاظ بالجمهور (Retention): مقياس تماسك الفيديو. إذا تجاوزت 50% في أول دقيقة، فالفيديو مرشح لانتشار فيروسي هائل (Viral).\n" +
                                    "• العائد لكل ألف ظهور (RPM): صافي الأرباح التي تحققها لكل 1000 مشاهدة بعد مشاركة المنصة للأرباح.\n" +
                                    "• خوارزمية التفاعل الأولي (Velocity): سرعة تفاعل أول 100 مشاهد مع منشورك يحدد مصيره بالانتشار لباقي المشتركين."
                        } else {
                            "• CTR (Click-Through Rate): Percent of impressions turning to views. Golden Standard: 6% to 10%.\n" +
                                    "• Retention Rate: Measures viewer hold. Keeping >50% at the 1-minute mark guarantees algorithmic push.\n" +
                                    "• RPM (Revenue Per Mille): Your actual net payout per 1,000 views after YouTube's split.\n" +
                                    "• Velocity: The engagement speed of the first 100 users, which determines wider recommendation pooling."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBright.copy(0.85f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// --- CREATE CUSTOM AGENT DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgentDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, platform: String, personality: String, description: String, systemInstruction: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("Facebook") }
    var personality by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var systemInstruction by remember { mutableStateOf("") }

    val platformOptions = listOf("Facebook", "YouTube", "Both")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("create_agent_dialog"),
            colors = CardDefaults.cardColors(containerColor = SpaceSurface),
            border = BorderStroke(1.dp, NeonCyan.copy(0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isArabic) "إنشاء عميل ذكاء اصطناعي مخصص" else "Create Custom Social Agent",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextBright
                )

                // Name Input
                Text(text = if (isArabic) "اسم العميل:" else "Agent Name:", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_name_input"),
                    placeholder = { Text(if (isArabic) "مثال: مروان خبير الريلز" else "E.g., Marwan Reels Expert") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpaceCard,
                        unfocusedContainerColor = SpaceCard,
                        focusedTextColor = TextBright,
                        unfocusedTextColor = TextBright
                    ),
                    singleLine = true
                )

                // Platform Dropdown Selection
                Text(text = if (isArabic) "المنصة المستهدفة:" else "Target Platform:", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    platformOptions.forEach { option ->
                        val selected = platform == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NeonPurple.copy(0.25f) else SpaceCard)
                                .border(BorderStroke(1.dp, if (selected) NeonPurple else Color.Transparent), RoundedCornerShape(8.dp))
                                .clickable { platform = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = option, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) TextBright else TextMuted)
                        }
                    }
                }

                // Personality Input
                Text(text = if (isArabic) "السمة الشخصية وهوس السوشيال ميديا:" else "Personality & Obsession style:", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                TextField(
                    value = personality,
                    onValueChange = { personality = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isArabic) "مهووس بالهاشتاقات والتفاعل" else "Growth Obsessed Hashtag Nerd") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpaceCard,
                        unfocusedContainerColor = SpaceCard,
                        focusedTextColor = TextBright,
                        unfocusedTextColor = TextBright
                    ),
                    singleLine = true
                )

                // Brief Description Input
                Text(text = if (isArabic) "الوصف المختصر:" else "Short Description:", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isArabic) "يساعدك على صياغة عناوين تضمن نقرات عالية" else "Helps you draft clickable high-CTR titles.") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpaceCard,
                        unfocusedContainerColor = SpaceCard,
                        focusedTextColor = TextBright,
                        unfocusedTextColor = TextBright
                    ),
                    singleLine = true
                )

                // System Instruction Input
                Text(text = if (isArabic) "التعليمات البرمجية الدقيقة (System Instruction):" else "Underlying System Instruction:", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                TextField(
                    value = systemInstruction,
                    onValueChange = { systemInstruction = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("agent_sys_input"),
                    placeholder = {
                        Text(
                            if (isArabic)
                                "اكتب هنا الأسلوب والتحذيرات والمنصات التي يحاكيها، مثل: أنت خبير مالي تقدم نصائح حذرة مع الالتزام بقواعد النشر..."
                            else
                                "Write how this agent acts, constraints, guidelines..."
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpaceCard,
                        unfocusedContainerColor = SpaceCard,
                        focusedTextColor = TextBright,
                        unfocusedTextColor = TextBright
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = if (isArabic) "إلغاء" else "Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && systemInstruction.isNotBlank()) {
                                onSave(name, platform, personality, description, systemInstruction)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceDark),
                        enabled = name.isNotBlank() && systemInstruction.isNotBlank(),
                        modifier = Modifier.testTag("save_custom_agent_button")
                    ) {
                        Text(text = if (isArabic) "حفظ وإطلاق العميل" else "Launch Agent", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- VOICE ACCOUNT & PROFILE TAB ---
@Composable
fun VoiceAccountTab(
    viewModel: MainViewModel,
    voiceNotes: List<VoiceNoteEntity>,
    audioManager: AudioRecorderManager,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val isRecording by audioManager.isRecording.collectAsStateWithLifecycle()
    val isPlaying by audioManager.isPlaying.collectAsStateWithLifecycle()
    val currentPlayingPath by audioManager.currentPlayingPath.collectAsStateWithLifecycle()
    val recordingTimer by audioManager.recordingTimer.collectAsStateWithLifecycle()

    var noteTitle by remember { mutableStateOf("") }
    var currentOutputFile by remember { mutableStateOf<File?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Info Header Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(NeonCyan, NeonPurple))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "User Avatar",
                            tint = SpaceDark,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) "حساب المستخدِم والبصمة الصوتية" else "User Account & Voice Signature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextBright
                        )
                        Text(
                            text = "ahmedelaswany774@gmail.com",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (voiceNotes.isNotEmpty()) AccentTeal else HotPink)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (voiceNotes.isNotEmpty())
                                    (if (isArabic) "البصمة الصوتية مفعّلة (${voiceNotes.size} تسجيل)" else "Voice Profile Active (${voiceNotes.size} recordings)")
                                else
                                    (if (isArabic) "لم يتم تسجيل بصمة صوتية بعد" else "No voice signature recorded yet"),
                                fontSize = 11.sp,
                                color = if (voiceNotes.isNotEmpty()) AccentTeal else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Fish Audio API & Voice Model ID Settings Card
        item {
            val fishApiKey by viewModel.fishApiKey.collectAsStateWithLifecycle()
            val fishVoiceModelId by viewModel.fishVoiceModelId.collectAsStateWithLifecycle()
            val fishTtsLoading by viewModel.fishTtsLoading.collectAsStateWithLifecycle()
            val userFishModels by viewModel.userFishModels.collectAsStateWithLifecycle()
            val fetchingModels by viewModel.fetchingModels.collectAsStateWithLifecycle()
            val fetchModelsError by viewModel.fetchModelsError.collectAsStateWithLifecycle()

            var apiKeyInput by remember(fishApiKey) { mutableStateOf(fishApiKey) }
            var modelIdInput by remember(fishVoiceModelId) { mutableStateOf(fishVoiceModelId) }
            var fishErrorDialogText by remember { mutableStateOf<String?>(null) }

            fishErrorDialogText?.let { dialogMsg ->
                AlertDialog(
                    onDismissRequest = { fishErrorDialogText = null },
                    title = {
                        Text(
                            text = if (isArabic) "⚠️ تنبيه حساب Fish Audio" else "⚠️ Fish Audio Notice",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                    },
                    text = {
                        Text(
                            text = dialogMsg,
                            fontSize = 13.sp,
                            color = TextBright
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { fishErrorDialogText = null },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Text(if (isArabic) "حسناً" else "OK")
                        }
                    },
                    containerColor = SpaceDark,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.5.dp, NeonPurple.copy(0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Fish Audio Settings",
                            tint = NeonPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "إعدادات Fish Audio TTS (التعليق الصوتي البشري)" else "Fish Audio TTS Engine Settings",
                            fontWeight = FontWeight.ExtraBold,
                            color = TextBright,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isArabic)
                            "أدخل مفتاح Fish Audio API ورمز موديل الصوت الخاص بك (Voice Model ID / reference_id) لتحويل السيناريو إلى تعليق صوتي بصوتك المستنسخ."
                        else
                            "Configure your Fish Audio API key & Voice Model ID (reference_id) to generate custom cloned voiceovers.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fish Audio API Key Field
                    Text(
                        text = if (isArabic) "Fish Audio API Key:" else "Fish Audio API Key:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("e.g. 8024250e7a2542a99d3455243b91950e...", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().testTag("fish_api_key_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SpaceCard,
                            focusedContainerColor = SpaceDark,
                            unfocusedContainerColor = SpaceDark,
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextBright
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Voice Model ID Field (FISH_MODEL_ID / reference_id)
                    Text(
                        text = if (isArabic) "FISH_MODEL_ID (رمز موديل الصوت reference_id):" else "FISH_MODEL_ID (Voice reference_id):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = modelIdInput,
                        onValueChange = { modelIdInput = it },
                        placeholder = { Text("FISH_MODEL_ID e.g. 8024250e7a2542a99d3455243b91950e", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().testTag("fish_model_id_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SpaceCard,
                            focusedContainerColor = SpaceDark,
                            unfocusedContainerColor = SpaceDark,
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextBright
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Save Settings Button
                        Button(
                            onClick = {
                                if (apiKeyInput.trim().isEmpty()) {
                                    Toast.makeText(context, if (isArabic) "يرجى إدخال Fish Audio API Key - راجع fish.audio" else "Please enter Fish Audio API Key", Toast.LENGTH_LONG).show()
                                } else {
                                    val finalModelId = if (modelIdInput.trim().isEmpty()) "98c1f6dca0614f679046c5a67eb1a27d" else modelIdInput.trim()
                                    modelIdInput = finalModelId
                                    viewModel.updateFishSettings(apiKeyInput, finalModelId)
                                    Toast.makeText(context, if (isArabic) "تم حفظ صوتك بصورة دائمة في التطبيق! 🎙️" else "Your voice model saved permanently! 🎙️", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("save_fish_settings_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "حفظ الإعدادات" else "Save Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Test Voice Button
                        Button(
                            onClick = {
                                viewModel.updateFishSettings(apiKeyInput, modelIdInput)
                                val sampleText = if (isArabic)
                                    "أهلاً بكم في قناة الملف 71.. معكم التعليق الصوتي الدرامي لأخطر قضايا الجريمة والتحقيقات."
                                else
                                    "Welcome to AlMalaf 71 crime channel. This is the human drama voice test."

                                viewModel.generateFishAudioTts(
                                    textToSpeech = sampleText,
                                    context = context,
                                    onStart = {
                                        Toast.makeText(context, if (isArabic) "جاري استدعاء Fish Audio API للتعليق الصوتي..." else "Calling Fish Audio API...", Toast.LENGTH_SHORT).show()
                                    },
                                    onSuccess = { generatedPath ->
                                        Toast.makeText(context, if (isArabic) "تم توليد الصوت بنجاح! جاري التشغيل..." else "Voice generated! Playing now...", Toast.LENGTH_SHORT).show()
                                        audioManager.playAudio(generatedPath)
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                        fishErrorDialogText = err
                                    }
                                )
                            },
                            enabled = !fishTtsLoading,
                            modifier = Modifier.weight(1.2f).testTag("test_voice_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                        ) {
                            if (fishTtsLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SpaceDark, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isArabic) "جاري التوليد..." else "Generating...", fontSize = 11.sp)
                            } else {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Test Voice", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isArabic) "🔊 اختبار الصوت" else "🔊 Test Voice", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // "تحديث أصواتي" Refresh My Voices Button
                    Button(
                        onClick = {
                            if (apiKeyInput.trim().isEmpty()) {
                                Toast.makeText(context, if (isArabic) "يرجى إدخال Fish Audio API Key أولاً" else "Please enter Fish Audio API Key first", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateFishSettings(apiKeyInput, modelIdInput)
                                viewModel.fetchUserFishModels(apiKeyInput) { models, err ->
                                    if (err != null) {
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    } else if (models.isEmpty()) {
                                        Toast.makeText(context, if (isArabic) "لم يتم العثور على أصوات مستنسخة خاصة بك في حسابك." else "No cloned voices found in your account.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, if (isArabic) "تم جلب ${models.size} صوت مستنسخ بنجاح!" else "Fetched ${models.size} cloned voices!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !fetchingModels,
                        modifier = Modifier.fillMaxWidth().testTag("refresh_my_voices_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        if (fetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SpaceDark, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isArabic) "جاري جلب أصواتي من Fish Audio..." else "Fetching voices...", fontSize = 11.sp, color = SpaceDark)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Voices", tint = SpaceDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isArabic) "🔄 تحديث أصواتي (جلب الأصوات المستنسخة)" else "🔄 Refresh My Voices", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SpaceDark)
                        }
                    }

                    fetchModelsError?.let { errStr ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "❌ $errStr", fontSize = 11.sp, color = Color(0xFFFF5252))
                    }

                    if (userFishModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isArabic) "🎙️ الأصوات المستنسخة بحسابك (${userFishModels.size}): (اختر صوتك للتحويل إليه)" else "🎙️ Cloned Voices (${userFishModels.size}): (Tap to select voice)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            userFishModels.forEach { model ->
                                val isSelected = (model.modelId == modelIdInput.trim())
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            modelIdInput = model.modelId
                                            viewModel.updateFishSettings(apiKeyInput, model.modelId)
                                            Toast.makeText(
                                                context,
                                                if (isArabic) "تم اختيار الصوت المرجعي: ${model.displayName}" else "Selected voice: ${model.displayName}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) NeonPurple.copy(0.25f) else SpaceDark
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) NeonCyan else SpaceCard
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                                            contentDescription = "Voice",
                                            tint = if (isSelected) NeonCyan else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = model.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) TextBright else TextMuted
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "reference_id: ${model.modelId}",
                                                fontSize = 10.sp,
                                                color = NeonCyan
                                            )
                                            if (!model.description.isNullOrBlank()) {
                                                Text(
                                                    text = model.description,
                                                    fontSize = 10.sp,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = NeonCyan.copy(0.2f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, NeonCyan)
                                            ) {
                                                Text(
                                                    text = if (isArabic) "صوتك المُنقّى" else "Selected",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeonCyan,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Google Command Code & Software Keys Manager Card
        item {
            val savedGoogleCode by viewModel.googleCommandCode.collectAsStateWithLifecycle()
            val savedSoftwareKeys by viewModel.softwareKeysCode.collectAsStateWithLifecycle()

            var googleCodeInput by remember(savedGoogleCode) { mutableStateOf(savedGoogleCode) }
            var softwareKeysInput by remember(savedSoftwareKeys) { mutableStateOf(savedSoftwareKeys) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.5.dp, NeonCyan.copy(0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Keys Manager",
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isArabic) "إدارة أكواد ومفاتيح Google والبرامج" else "Google & Software Keys Manager",
                                fontWeight = FontWeight.ExtraBold,
                                color = TextBright,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isArabic) "حفظ دائم للأكواد بدون الحاجة لإعادة تسجيل الدخول بالمتصفح" else "Persistent keys & command tokens stored locally",
                                fontSize = 10.5.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isArabic) "كود أوامر Google (Google Command / Auth Token):" else "Google Command Code / Auth Token:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = googleCodeInput,
                        onValueChange = { googleCodeInput = it },
                        placeholder = { Text("أدخل كود Google هنا للتنفيذ الفوري...", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().testTag("google_command_code_input"),
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SpaceCard,
                            focusedContainerColor = SpaceDark,
                            unfocusedContainerColor = SpaceDark,
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextBright
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isArabic) "مفاتيح وأكواد البرامج (Software Keys):" else "Software Keys & Codes:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = softwareKeysInput,
                        onValueChange = { softwareKeysInput = it },
                        placeholder = { Text("انسخ أو أدخل مفاتيح وأكواد البرامج هنا لنسخها فوراً...", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().testTag("software_keys_input"),
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SpaceCard,
                            focusedContainerColor = SpaceDark,
                            unfocusedContainerColor = SpaceDark,
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextBright
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.updateGoogleAndSoftwareKeys(googleCodeInput, softwareKeysInput)
                                Toast.makeText(context, if (isArabic) "تم حفظ الأكواد والمفاتيح في ذاكرة التطبيق الدائمة!" else "Keys saved to persistent memory!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("save_keys_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Save Keys", tint = SpaceDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "حفظ في المفاتيح" else "Save Keys", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpaceDark)
                        }

                        Button(
                            onClick = {
                                val textToCopy = if (googleCodeInput.isNotBlank()) googleCodeInput else softwareKeysInput
                                if (textToCopy.isBlank()) {
                                    Toast.makeText(context, if (isArabic) "لا يوجد كود أو مفتاح للنسخ" else "No code to copy", Toast.LENGTH_SHORT).show()
                                } else {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Software Code", textToCopy)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, if (isArabic) "تم نسخ الكود للحافظة بنجاح! 📋" else "Copied code to clipboard! 📋", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("copy_keys_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "نسخ الكود 📋" else "Copy Code 📋", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Autonomous Pipeline Worker (WorkManager) Settings Card
        item {
            val autoModeEnabled by viewModel.autonomousModeEnabled.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.5.dp, AccentTeal.copy(0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "Autonomous Mode",
                                tint = AccentTeal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isArabic) "الوضع التلقائي - خلي الموظفين يعيشوا" else "Autonomous Mode - Let Agents Live",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextBright,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = if (isArabic) "تشغيل خط إنتاج الوكلاء كل 6 ساعات في الخلفية (WorkManager)" else "Auto-run 8-agent pipeline every 6 hours in background",
                                    fontSize = 10.5.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Switch(
                            checked = autoModeEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.toggleAutonomousMode(enabled, context)
                                val msg = if (enabled) {
                                    if (isArabic) "تم تفعيل الوضع التلقائي! سيعمل الوكلاء الـ 8 كل 6 ساعات وسيصدر إشعار 'الملف 71 الجديد جاهز'." else "Autonomous mode activated! Running every 6h."
                                } else {
                                    if (isArabic) "تم إيقاف الوضع التلقائي." else "Autonomous mode disabled."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.testTag("autonomous_mode_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SpaceDark,
                                checkedTrackColor = AccentTeal,
                                uncheckedTrackColor = SpaceDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (autoModeEnabled) {
                            if (isArabic)
                                "⚡ الوضع مفعّل الآن: يقوم WorkManager بتشغيل الوكلاء الـ 8 تلقائياً في الخلفية لحفظ حلقة جديدة واستبدال الصوت والفيديو، وإرسال إشعار \"الملف 71 الجديد جاهز\" عند الاكتمل."
                            else
                                "⚡ Active: 8 agents run autonomously in the background every 6 hours and notify 'الملف 71 الجديد جاهز' when done."
                        } else {
                            if (isArabic)
                                "💡 قم بتشغيل المفتاح أعلاه للبدء التلقائي المستمر كل 6 ساعات وتلقي الإشعارات عند جاهزية الحلقات الجديدة حتى والتطبيق مغلق."
                            else
                                "💡 Enable the toggle above to start background automatic production every 6 hours."
                        },
                        fontSize = 11.sp,
                        color = if (autoModeEnabled) AccentTeal else TextMuted
                    )
                }
            }
        }

        // Voice Recording Studio Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.5.dp, if (isRecording) Color.Red else NeonCyan.copy(0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = if (isRecording) Color.Red else NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "استوديو تسجيل بصمة الصوت للحساب" else "Voice Signature Recorder",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextBright
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isArabic)
                            "سجل بصمتك الصوتية أو ملاحظاتك لربطها بحسابك ومشاركتها مع المساعد الذكي."
                        else
                            "Record your voice note or vocal profile to store securely in your account.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!hasMicPermission) {
                        Button(
                            onClick = onRequestMicPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = TextBright),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Grant")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isArabic) "السماح باستخدام الميكروفون" else "Grant Microphone Access", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Title Input
                        TextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            placeholder = { Text(if (isArabic) "عنوان البصمة (مثال: بصمة صوتي للحساب)" else "Recording title (e.g. Profile Voice Note)", fontSize = 12.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SpaceCard,
                                unfocusedContainerColor = SpaceCard,
                                focusedTextColor = TextBright,
                                unfocusedTextColor = TextBright,
                                focusedIndicatorColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Recording status / timer
                        if (isRecording) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val formattedTime = String.format(Locale.US, "%02d:%02d", recordingTimer / 60, recordingTimer % 60)
                                    Text(
                                        text = if (isArabic) "جاري التسجيل الآن... $formattedTime" else "Recording in progress... $formattedTime",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                VoiceWaveformAnimation(isRecording = true)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Record / Stop Control Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) Color.Red else NeonCyan)
                                .clickable {
                                    if (isRecording) {
                                        val durationSec = audioManager.stopRecording()
                                        currentOutputFile?.let { file ->
                                            val titleToSave = noteTitle.ifBlank {
                                                if (isArabic) "بصمة صوتية ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())}"
                                                else "Voice Note ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date())}"
                                            }
                                            viewModel.addVoiceNote(titleToSave, file.absolutePath, durationSec)
                                            Toast
                                                .makeText(
                                                    context,
                                                    if (isArabic) "تم حفظ البصمة الصوتية بنجاح!" else "Voice note saved successfully!",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                        noteTitle = ""
                                        currentOutputFile = null
                                    } else {
                                        val file = File(
                                            context.filesDir,
                                            "voice_note_${System.currentTimeMillis()}.mp4"
                                        )
                                        currentOutputFile = file
                                        val started = audioManager.startRecording(file)
                                        if (!started) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    if (isArabic) "تعذر بدء التسجيل" else "Failed to start recording",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record/Stop",
                                tint = SpaceDark,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRecording)
                                (if (isArabic) "اضغط لإيقاف وحفظ التسجيل" else "Tap to stop & save recording")
                            else
                                (if (isArabic) "اضغط للبدء بتسجيل صوتك" else "Tap icon to start recording voice"),
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // List of Saved Voice Notes
        item {
            Text(
                text = if (isArabic) "سجل البصمات الصوتية المسجلة بحسابك (${voiceNotes.size})" else "Recorded Voice Notes Archive (${voiceNotes.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextBright,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (voiceNotes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceSurface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = "Eq", tint = TextMuted, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isArabic) "لا توجد بصمات صوتية مسجلة بعد" else "No voice notes saved yet",
                            color = TextBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isArabic) "استخدم الاستوديو أعلاه لتسجيل أول بصمة صوتية لحسابك." else "Use the recorder above to capture your voice signature.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(voiceNotes) { note ->
                val isThisPlaying = isPlaying && currentPlayingPath == note.filePath
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                    border = BorderStroke(1.dp, if (isThisPlaying) AccentTeal else SpaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = {
                                        if (isThisPlaying) {
                                            audioManager.stopAudio()
                                        } else {
                                            audioManager.playAudio(note.filePath)
                                        }
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isThisPlaying) AccentTeal else SpaceCard)
                                        .size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = if (isThisPlaying) SpaceDark else NeonCyan
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = note.title,
                                        fontWeight = FontWeight.Bold,
                                        color = TextBright,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val formattedDuration = String.format(Locale.US, "%02d:%02d", note.durationSeconds / 60, note.durationSeconds % 60)
                                    val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(note.dateRecorded))
                                    Text(
                                        text = "⏱️ $formattedDuration • $dateStr",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    audioManager.stopAudio()
                                    viewModel.deleteVoiceNote(note.id)
                                    Toast.makeText(context, if (isArabic) "تم حذف التسجيل" else "Recording deleted", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(0.7f))
                            }
                        }

                        if (isThisPlaying) {
                            Spacer(modifier = Modifier.height(8.dp))
                            VoiceWaveformAnimation(isRecording = false)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Button to send note to active agent chat
                        OutlinedButton(
                            onClick = {
                                val agent = viewModel.selectedAgent.value
                                if (agent != null) {
                                    viewModel.sendChatMessage("🎙️ [ملاحظة صوتية من حسابي]: \"${note.title}\" (المدة: ${note.durationSeconds} ثانية)")
                                    Toast.makeText(context, if (isArabic) "تم إرسال الملاحظة الصوتية إلى ${agent.name}!" else "Voice note sent to ${agent.name}!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, if (isArabic) "يرجى اختيار عميل ذكي أولاً من تبويب الدردشة" else "Select an agent first in Chat tab", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, NeonPurple.copy(0.6f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send to Agent", tint = NeonPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "إرسال البصمة الصوتية للعميل الذكي" else "Send Voice Note to Selected Agent",
                                fontSize = 11.sp,
                                color = NeonPurple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceWaveformAnimation(isRecording: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(30.dp)
    ) {
        val heights = listOf(14.dp, 26.dp, 10.dp, 28.dp, 18.dp, 24.dp, 12.dp, 26.dp, 16.dp)
        heights.forEachIndexed { index, h ->
            val barColor = if (isRecording) {
                if (index % 2 == 0) Color.Red else HotPink
            } else {
                if (index % 2 == 0) AccentTeal else NeonCyan
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun VideoPreviewDialog(
    videoPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = SpaceSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PlayCircle, contentDescription = "Video", tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🎬 معاينة حلقة الفيديو (الملف 71)", fontWeight = FontWeight.Bold, color = TextBright, fontSize = 14.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val videoFile = File(videoPath)
                if (videoFile.exists() && videoFile.length() > 0) {
                    val exoPlayer = remember(context, videoPath) {
                        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(videoFile))
                            setMediaItem(mediaItem)
                            prepare()
                            playWhenReady = true
                        }
                    }

                    DisposableEffect(exoPlayer) {
                        onDispose {
                            exoPlayer.release()
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "📁 المسار العام: ${videoFile.absolutePath}",
                        fontSize = 10.sp,
                        color = AccentTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("⚠️ ملف الفيديو غير متوفر على المسار المحدد", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(videoPath, color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    videoFile
                                )
                                setDataAndType(uri, "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "يمكنك فتح الفيديو من مجلد Movies/AlMalaf71 بمشغل جهازك", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "External", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشغل الجهاز الخارجي", fontSize = 11.sp, color = SpaceDark, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceCard)
                    ) {
                        Text("إغلاق", fontSize = 11.sp, color = TextBright)
                    }
                }
            }
        }
    }
}

@Composable
fun LogViewerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val logs by viewModel.globalLogs.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = SpaceDark),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, NeonCyan)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📜 سجل مراقبة النظام والأخطاء", fontWeight = FontWeight.ExtraBold, color = TextBright, fontSize = 14.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy Logs
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("AlMalaf71 Logs", logs.joinToString("\n"))
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ السجل بالكامل!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceSurface)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ السجل", fontSize = 10.5.sp, color = TextBright)
                    }

                    // Clear Logs
                    Button(
                        onClick = { viewModel.clearSystemLogs() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceSurface)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مسح السجل", fontSize = 10.5.sp, color = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = SpaceCard)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { logLine ->
                        val isErr = logLine.contains("❌") || logLine.contains("⚠️")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isErr) Color(0x33FF3B30) else SpaceSurface)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = logLine,
                                fontSize = 10.5.sp,
                                color = if (isErr) Color(0xFFFF6B6B) else TextBright,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("إغلاق السجل", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
