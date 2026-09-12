package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.appTempFolder
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.useCropLauncher
import me.rerere.rikkahub.ui.components.ui.UpdateCard
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.rememberIsPlayStoreVersion
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.isAllowedFileType
import me.rerere.rikkahub.utils.toAndroidUri
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.ui.components.ai.WorkspaceCwdPickerSheet
import me.rerere.rikkahub.ui.components.ai.WorkspaceSelectSheet
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.workspace.WorkspaceShellStatus
import org.jetbrains.compose.resources.stringResource

class AndroidChatPagePlatformContent(
    private val filesManager: FilesManager,
    private val workspaceRepository: WorkspaceRepository,
    private val buildInfo: PlatformBuildInfo,
) : ChatPagePlatformContent {
    override suspend fun importInitialFiles(files: List<String>): List<UIMessagePart> {
        val uris = files.map(Uri::parse)
        val localFiles = filesManager.createChatFilesByContents(uris)
        val contentTypes = uris.mapNotNull(filesManager::getFileMimeType)
        return buildList {
            localFiles.forEachIndexed { index, file ->
                when {
                    contentTypes.getOrNull(index)?.startsWith("image/") == true ->
                        add(UIMessagePart.Image(url = file.toString()))
                    contentTypes.getOrNull(index)?.startsWith("video/") == true ->
                        add(UIMessagePart.Video(url = file.toString()))
                    contentTypes.getOrNull(index)?.startsWith("audio/") == true ->
                        add(UIMessagePart.Audio(url = file.toString()))
                }
            }
        }
    }

    override fun completionProviders(
        assistant: Assistant,
        conversation: Conversation,
    ): List<ChatCompletionProvider> = assistant.workspaceId?.let { workspaceId ->
        listOf(
            WorkspaceCompletionProvider(
                workspaceId = workspaceId.toString(),
                repository = workspaceRepository,
                currentCwd = conversation.workspaceCwd,
            ),
        )
    }.orEmpty()

    @Composable
    override fun RegisterBackHandler(enabled: Boolean, onBack: () -> Unit) {
        BackHandler(enabled = enabled, onBack = onBack)
    }

    @Composable
    override fun volumeKeyEventSource(): VolumeKeyEventSource? {
        val routeActivity = LocalContext.current as? RouteActivity
        return remember(routeActivity) {
            routeActivity?.let { activity ->
                object : VolumeKeyEventSource {
                    override fun addListener(listener: (isVolumeUp: Boolean) -> Boolean) {
                        activity.volumeKeyListeners.add(listener)
                    }

                    override fun removeListener(listener: (isVolumeUp: Boolean) -> Boolean) {
                        activity.volumeKeyListeners.remove(listener)
                    }
                }
            }
        }
    }

    @Composable
    override fun isScrollCaptureInProgress(): Boolean = LocalScrollCaptureInProgress.current

    @Composable
    override fun RenderDrawerHeader(vm: ChatVM, settings: Settings) {
        if (settings.displaySetting.showUpdates && !rememberIsPlayStoreVersion()) {
            UpdateCard(vm, buildInfo)
        }
    }

    @Composable
    override fun RenderFilesPicker(
        inputState: ChatInputState,
        setting: Settings,
        conversation: Conversation,
        assistant: Assistant,
        vm: ChatVM,
        onDismiss: () -> Unit,
    ) {
        AndroidChatFilesPickerSheet(inputState, setting, conversation, assistant, vm, onDismiss)
    }

    @Composable
    override fun RenderExport(
        visible: Boolean,
        onDismissRequest: () -> Unit,
        conversation: Conversation,
        selectedMessages: List<UIMessage>,
    ) {
        ChatExportSheet(
            visible = visible,
            onDismissRequest = onDismissRequest,
            conversation = conversation,
            selectedMessages = selectedMessages,
        )
    }
}

@Composable
private fun AndroidChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val mcpManager: McpManager = koinInject()
    val navController = LocalNavController.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val onUpdateAssistant: (Assistant) -> Unit = {
        vm.updateSettings(
            setting.copy(
                assistants = setting.assistants.map { assistant ->
                    if (assistant.id == it.id) {
                        it
                    } else {
                        assistant
                    }
                }
            )
        )
    }
    val onUpdateConversation: (Conversation) -> Unit = {
        vm.updateConversation(it)
        vm.saveConversationAsync()
    }

    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val launchCameraCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)).map(Uri::toString))
            dismissAll()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                inputState.addImages(
                    filesManager.createChatFilesByContents(listOf(cameraOutputUri!!)).map(Uri::toString)
                )
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
                dismissAll()
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onLaunchCamera: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    var preCropTempFile by remember { mutableStateOf<File?>(null) }
    val launchImageCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)).map(Uri::toString))
            dismissAll()
        },
        onCleanup = {
            preCropTempFile?.delete()
            preCropTempFile = null
        }
    )
    val imagePickerLauncher =
        rememberFilePickerLauncher(
            type = FileKitType.Image,
            mode = FileKitMode.Multiple(),
        ) { selectedFiles ->
            val selectedUris = selectedFiles.orEmpty().map { it.toAndroidUri() }
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (setting.displaySetting.skipCropImage) {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris).map(Uri::toString))
                    dismissAll()
                } else if (selectedUris.size == 1) {
                    val tempFile = File(context.appTempFolder, "pick_temp_${System.currentTimeMillis()}.jpg")
                    runCatching {
                        val source = selectedUris.first()
                        // HEIF/HEIC（尤其 HDR HEIF）交给 UCrop 前先解码转为 JPEG，规避裁剪解码失败
                        val converted = ImageUtils.isHeifImage(context, source) &&
                            ImageUtils.convertHeifToJpeg(context, source, tempFile)
                        if (!converted) {
                            context.contentResolver.openInputStream(source)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        preCropTempFile = tempFile
                        launchImageCrop(tempFile.toUri())
                    }.onFailure {
                        Log.e("ImagePickButton", "Failed to copy image to temp, falling back", it)
                        launchImageCrop(selectedUris.first())
                    }
                } else {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris).map(Uri::toString))
                    dismissAll()
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberFilePickerLauncher(
            type = FileKitType.Video,
            mode = FileKitMode.Multiple(),
        ) { selectedFiles ->
            val selectedUris = selectedFiles.orEmpty().map { it.toAndroidUri() }
            if (selectedUris.isNotEmpty()) {
                inputState.addVideos(filesManager.createChatFilesByContents(selectedUris).map(Uri::toString))
                dismissAll()
            }
        }

    val audioPickerLauncher =
        rememberFilePickerLauncher(
            type = FileKitType.File("mp3", "m4a", "wav", "ogg", "aac", "flac", "opus", "aiff", "amr"),
            mode = FileKitMode.Multiple(),
        ) { selectedFiles ->
            val selectedUris = selectedFiles.orEmpty().map { it.toAndroidUri() }
            if (selectedUris.isNotEmpty()) {
                inputState.addAudios(filesManager.createChatFilesByContents(selectedUris).map(Uri::toString))
                dismissAll()
            }
        }

    val filePickerLauncher =
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = null),
            mode = FileKitMode.Multiple(),
        ) { selectedFiles ->
            val uris = selectedFiles.orEmpty().map { it.toAndroidUri() }
            if (uris.isNotEmpty()) {
                val documents = uris.mapNotNull { uri ->
                    val fileName = filesManager.getFileNameFromUri(uri) ?: "file"
                    val mime = filesManager.getFileMimeType(uri) ?: "text/plain"
                    if (isAllowedFileType(fileName, mime)) {
                        val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
                            ?: run {
                                toaster.show(
                                    context.getString(R.string.chat_input_file_read_failed, fileName),
                                    type = ToastType.Error
                                )
                                return@mapNotNull null
                            }
                        UIMessagePart.Document(url = localUri.toString(), fileName = fileName, mime = mime)
                    } else {
                        toaster.show(
                            context.getString(R.string.chat_input_unsupported_file_type, fileName),
                            type = ToastType.Error
                        )
                        null
                    }
                }
                if (documents.isNotEmpty()) {
                    inputState.addFiles(documents)
                    dismissAll()
                }
            }
        }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
            },
            onUpdateAssistant = onUpdateAssistant,
            onUpdateConversation = onUpdateConversation,
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = onLaunchCamera,
            onPickImage = { imagePickerLauncher.launch() },
            onPickVideo = { videoPickerLauncher.launch() },
            onPickAudio = { audioPickerLauncher.launch() },
            onPickFile = { filePickerLauncher.launch() },
            workspacePicker = {
                if (workspaces.isNotEmpty()) {
                    WorkspacePickerListItem(
                        assistant = assistant,
                        conversation = conversation,
                        workspaces = workspaces,
                        onUpdateAssistant = onUpdateAssistant,
                        onUpdateConversation = onUpdateConversation,
                        onNavigateToDetail = { id ->
                            dismissAll()
                            navController.navigate(Screen.WorkspaceDetail(id))
                        },
                        onNavigateToTerminal = { id ->
                            dismissAll()
                            navController.navigate(Screen.WorkspaceTerminal(id))
                        },
                        onNavigateToManage = {
                            dismissAll()
                            navController.navigate(Screen.Workspaces)
                        },
                    )
                }
            },
            workspaceCwdPicker = {
                // Workspace CWD
                val boundWorkspace = remember(workspaces, assistant.workspaceId) {
                    workspaces.find { it.id == assistant.workspaceId?.toString() }
                }
                if (boundWorkspace != null && boundWorkspace.shellStatus == WorkspaceShellStatus.READY.name) {
                    var showCwdSheet by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showCwdSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Folder01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = conversation.workspaceCwd ?: "/workspace",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showCwdSheet) {
                        WorkspaceCwdPickerSheet(
                            workspaceId = boundWorkspace.id,
                            currentCwd = conversation.workspaceCwd,
                            onSelectCwd = { newCwd ->
                                onUpdateConversation(conversation.copy(workspaceCwd = newCwd))
                            },
                            onDismiss = { showCwdSheet = false },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun WorkspacePickerListItem(
    assistant: Assistant,
    conversation: Conversation,
    workspaces: List<WorkspaceEntity>,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToManage: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = HugeIcons.Codesandbox,
                contentDescription = stringResource(Res.string.assistant_page_workspace),
            )
        },
        headlineContent = {
            Text(stringResource(Res.string.assistant_page_workspace))
        },
        supportingContent = {
            Text(
                text = boundWorkspace?.name ?: stringResource(Res.string.assistant_page_workspace_unbound),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (boundWorkspace != null) {
                    IconButton(onClick = { onNavigateToDetail(boundWorkspace.id) }) {
                        Icon(
                            imageVector = HugeIcons.Settings02,
                            contentDescription = stringResource(Res.string.workspace_detail),
                        )
                    }
                    if (boundWorkspace.shellStatus != WorkspaceShellStatus.DISABLED.name) {
                        IconButton(onClick = { onNavigateToTerminal(boundWorkspace.id) }) {
                            Icon(
                                imageVector = HugeIcons.ComputerTerminal01,
                                contentDescription = stringResource(Res.string.workspace_terminal),
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { showSheet = true },
    )

    if (showSheet) {
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = workspaces,
            onSelect = { workspaceId ->
                val newId = workspaceId?.let { Uuid.parse(it) }
                if (newId != assistant.workspaceId) {
                    onUpdateAssistant(assistant.copy(workspaceId = newId))
                    if (conversation.workspaceCwd != null) {
                        onUpdateConversation(conversation.copy(workspaceCwd = null))
                    }
                }
                showSheet = false
            },
            onManage = {
                showSheet = false
                onNavigateToManage()
            },
            onDismiss = { showSheet = false },
        )
    }
}
