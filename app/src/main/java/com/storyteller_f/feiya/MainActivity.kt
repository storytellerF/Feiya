package com.storyteller_f.feiya

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.service.AppService
import com.storyteller_f.feiya.service.Message
import com.storyteller_f.feiya.service.ServerState
import com.storyteller_f.feiya.service.SharedFileInfo
import com.storyteller_f.feiya.service.portFlow
import com.storyteller_f.feiya.ui.components.MainToolbar
import com.storyteller_f.feiya.ui.components.HidScreen
import com.storyteller_f.feiya.ui.components.SharedFiles
import com.storyteller_f.feiya.ui.components.MessagePage
import com.storyteller_f.feiya.ui.components.AppShell
import com.storyteller_f.feiya.ui.components.FormPane
import com.storyteller_f.feiya.ui.components.destinationTitle
import com.storyteller_f.feiya.ui.components.SafePage
import com.storyteller_f.feiya.ui.components.SettingPage
import com.storyteller_f.feiya.ui.components.ShowQrCode
import com.storyteller_f.feiya.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.concurrent.thread


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    private val serviceBinder = MutableStateFlow<AppService.ServiceBinder?>(null)
    private val currentServiceBinder get() = serviceBinder.value
    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uri ->
            addUri(uri)
        }

    private val request =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            bluetoothController.retry()
        }
    private val bluetoothController by lazy {
        (application as FeiyaApplication).hidConnectionHost
    }
    private val hidClient = Any()

    private val bootSettingsHost by lazy {
        BootSettingsHost(
            (application as FeiyaApplication).settingsCoordination,
            DataStoreBootSettings(dataStore),
        )
    }

    private val keyboardHost by lazy {
        HidKeyboardHost(
            (application as FeiyaApplication).keyboardCoordination,
            bluetoothController.keyboardConnection,
        )
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            currentServiceBinder?.service?.onUserGrantNotificationPermission()
        }
    }

    private val requestLocalNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startAppService()
        } else {
            Toast.makeText(this, "需要局域网访问权限才能启动服务", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val serverState = serviceBinder.flatMapLatest {
        it?.service?.server?.state ?: MutableStateFlow(ServerState.Init)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messageFlow = serviceBinder.flatMapLatest {
        it?.service?.server?.messagesCache ?: MutableStateFlow(
            listOf(
                Message(
                    "system",
                    "Server stopped."
                )
            )
        )
    }

    private val serviceConnection by lazy { HidConnection(this) }

    var newSession: CustomTabsSession? = null
    private val chromeConnection by lazy { CustomTabConnection(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by bluetoothController.state.collectAsStateWithLifecycle()
            val port by LocalContext.current.portFlow.collectAsStateWithLifecycle(initialValue = AppService.DEFAULT_PORT)
            val navController = rememberNavController()
            val density = LocalDensity.current
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val screenHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
            val currentBackStackEntryAsState by navController.currentBackStackEntryAsState()

            LaunchedEffect(keyboardHost, navController) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    keyboardHost.effects.collect { effect ->
                        val message = when (effect) {
                            HidKeyboardEffect.UNSUPPORTED_TEXT -> R.string.hid_unsupported_text
                            HidKeyboardEffect.SEND_FAILED -> R.string.hid_send_failed
                            HidKeyboardEffect.CONNECTION_CHANGED -> R.string.hid_connection_changed
                            HidKeyboardEffect.SENT -> R.string.hid_task_sent
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (effect == HidKeyboardEffect.SEND_FAILED || effect == HidKeyboardEffect.CONNECTION_CHANGED) {
                            navController.navigate("hid") { launchSingleTop = true }
                        }
                    }
                }
            }
            val sendText: (String) -> Unit = keyboardHost::sendText
            val projectUrl = stringResource(R.string.project_url)
            val showAboutWebView = {
                val builder = CustomTabsIntent.Builder()
                    .setInitialActivityHeightPx((screenHeight * 0.7).toInt())
                val session = newSession
                if (session != null) builder.setSession(session)
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(context, Uri.parse(projectUrl))
            }

            AppTheme {
                val route = currentBackStackEntryAsState?.destination?.route ?: "main"
                val activeRoute = if (route.startsWith("info/")) "main" else route
                val serviceState by serverState.collectAsStateWithLifecycle(initialValue = ServerState.Init)
                AppShell(
                    route = activeRoute,
                    navigate = { destination ->
                        navController.navigate(destination) {
                            popUpTo("main") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    about = showAboutWebView,
                    topBar = {
                        MainToolbar(
                            title = destinationTitle(activeRoute),
                            port = ((serviceState as? ServerState.Started)?.port ?: port).toString(),
                            state = serviceState,
                            restartService = { currentServiceBinder?.restart() },
                            stopService = { currentServiceBinder?.stop() },
                            sendText = sendText,
                            deleteAll = { shares.value.forEach(::deleteItem) },
                            showFileActions = activeRoute == "main",
                            about = showAboutWebView,
                        )
                    },
                ) { paddingValues ->
                    MainContent(paddingValues, navController, (serviceState as? ServerState.Started)?.port ?: port, sendText, state)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    NotificationRequest()
                }

            }
        }
        startAppServiceWhenPermitted()
        CustomTabsClient.bindCustomTabsService(this, CUSTOM_TAB_PACKAGE_NAME, chromeConnection)
    }

    private fun startAppServiceWhenPermitted() {
        if (
            Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocalNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            startAppService()
        }
    }

    private fun startAppService() {
        val intent = Intent(this, AppService::class.java)
        startService(intent)
        if (currentServiceBinder == null) bindService(intent, serviceConnection, 0)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    @OptIn(ExperimentalPermissionsApi::class)
    private fun NotificationRequest() {
        var showRationaleDialog by remember {
            mutableStateOf(false)
        }
        val postNotificationPermission =
            rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(key1 = postNotificationPermission) {
            if (!postNotificationPermission.status.isGranted) {
                if (postNotificationPermission.status.shouldShowRationale) {
                    showRationaleDialog = true
                } else
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (showRationaleDialog) {
            AlertDialog(onDismissRequest = {
                showRationaleDialog = false
            }, confirmButton = {
                showRationaleDialog = false
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, text = {
                Text(text = "为了能够保证在后台正常运行，需要”Post Notification“ 权限")
            })
        }
    }

    @Composable
    private fun MainContent(
        paddingValues: PaddingValues,
        navController: NavHostController,
        port: Int,
        sendText: (String) -> Unit,
        state: HidState
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues).consumeWindowInsets(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(navController = navController, startDestination = "main") {
                navPages(
                    port,
                    state,
                    ::saveToLocal,
                    ::deleteItem,
                    sendText,
                    ::requestPermission
                )
            }
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            request.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            request.launch(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    private fun saveToLocal(it: SharedFileInfo) {
        val uri = Uri.parse(it.uri)
        assert(uri.scheme != "file")
        currentServiceBinder?.saveToLocal(uri, it)
    }

    private fun deleteItem(info: SharedFileInfo) {
        val uri = Uri.parse(info.uri)
        currentServiceBinder?.deleteUri(uri, info)
    }

    private fun NavGraphBuilder.navPages(
        port: Int,
        state: HidState,
        saveToLocal: (SharedFileInfo) -> Unit,
        deleteItem: (SharedFileInfo) -> Unit,
        sendText: (String) -> Unit,
        requestPermission: () -> Unit
    ) {
        composable("main") {
            val infoList by shares.collectAsStateWithLifecycle()
            var qrUri by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
            SharedFiles(infoList, deleteItem, saveToLocal,
                viewInfo = { qrUri = it.uri },
                addFiles = { pickFile.launch(arrayOf("*/*")) },
            )
            val qrIndex = infoList.indexOfFirst { it.uri == qrUri }
            if (qrUri != null && qrIndex >= 0) AlertDialog(
                onDismissRequest = { qrUri = null },
                title = { Text(getString(R.string.file_share_code)) },
                text = { ShowQrCode("shares/$qrIndex", port.toString(), sendText = sendText) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { qrUri = null }) { Text(getString(android.R.string.ok)) } },
            )
            LaunchedEffect(qrIndex) { if (qrIndex < 0) qrUri = null }
        }
        composable("settings") {
            val bootSettings by bootSettingsHost.state.collectAsStateWithLifecycle()
            FormPane { SettingPage(
                port.toString(),
                bootSettings,
                bootSettingsHost::setEnabled,
                bootSettingsHost::reload,
            ) }
        }
        composable("messages") {
            Messages()
        }
        composable("hid") {
            val keyboardState by keyboardHost.state.collectAsStateWithLifecycle()
            FormPane { HidScreen(
                bluetoothState = state,
                requestPermission = requestPermission,
                connectDevice = bluetoothController::connectDevice,
                retryConnection = bluetoothController::retry,
                cancelConnection = bluetoothController::cancelConnectionAttempt,
                sendText = sendText,
                keyboardState = keyboardState,
                cancelTask = keyboardHost::cancelTask,
                cancelAll = keyboardHost::cancelAll,
                selectLayout = keyboardHost::selectLayout,
                selectCalibration = keyboardHost::selectCalibration,
                sendLeftCalibrationKey = keyboardHost::sendLeftCalibrationKey,
                sendRightCalibrationKey = keyboardHost::sendRightCalibrationKey,
                disconnect = { bluetoothController.disconnectDevice(it) },
            ) }
        }
        composable("safe") {
            FormPane { SafePage() }
        }
    }

    override fun onDestroy() {
        bluetoothController.background(hidClient)
        keyboardHost.close()
        bootSettingsHost.close()
        super.onDestroy()
        unbindService(serviceConnection)
        unbindService(chromeConnection)
    }

    @Composable
    fun Messages() {
        val messageList by messageFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        MessagePage(messageList) {
            currentServiceBinder?.sendMessage(it)
        }
    }

    private fun addUri(uri: List<Uri>) {
        uri.forEach {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            currentServiceBinder?.appendUri(it)
        }
    }

    override fun onResume() {
        super.onResume()
        bluetoothController.foreground(hidClient)
        lifecycleScope.launch {
            cacheInvalid()//when resume
        }
    }

    fun bindServiceBinder(binder: AppService.ServiceBinder) {
        serviceBinder.value = binder
    }

    override fun onPause() {
        bluetoothController.background(hidClient)
        super.onPause()
    }

    fun unbindServiceBinder() {
        serviceBinder.value = null
    }

    companion object {
        private const val CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome" // Change when in stable
    }
}

class HidConnection(activity: MainActivity) : ServiceConnection {
    private val activityRef = WeakReference(activity)
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val activity = activityRef.get() ?: return
        Toast.makeText(
            activity,
            activity.getString(R.string.service_connected),
            Toast.LENGTH_SHORT
        ).show()
        val serviceBinder = service as AppService.ServiceBinder
        Log.i("HidConnection", "onServiceConnected: $serviceBinder")
        activity.bindServiceBinder(serviceBinder)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        val activity = activityRef.get() ?: return
        activity.unbindServiceBinder()
        Toast.makeText(
            activity,
            activity.getString(R.string.service_closed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

class CustomTabConnection(activity: MainActivity) : CustomTabsServiceConnection() {
    private val activityRef = WeakReference(activity)
    override fun onCustomTabsServiceConnected(
        name: ComponentName,
        client: CustomTabsClient
    ) {
        thread(name = "warmup chrome tab") {
            val activity = activityRef.get() ?: return@thread
            val warmup = client.warmup(0)
            Log.i("CustomTabConnection", "onCustomTabsServiceConnected: warmup $warmup")
            val newSession = client.newSession(object : CustomTabsCallback() {

            })
            activity.newSession = newSession
            newSession?.mayLaunchUrl(
                Uri.parse(activity.getString(R.string.project_url)),
                null,
                null
            )
        }

    }

    override fun onServiceDisconnected(name: ComponentName) {}
}
