package com.storyteller_f.feiya.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.jamal.composeprefs3.ui.PrefsScreen
import com.jamal.composeprefs3.ui.prefs.EditTextPref
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.dataStore
import com.storyteller_f.feiya.service.AppService
import com.storyteller_f.feiya.service.ServerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Preview
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainToolbar(
    port: String = AppService.DEFAULT_PORT.toString(),
    restartService: () -> Unit = {},
    stopService: () -> Unit = {},
    sendText: (String) -> Unit = {},
    openDrawer: () -> Unit = {},
    deleteAll: () -> Unit = {},
    stateFlow: Flow<ServerState> = MutableStateFlow(ServerState.Init),
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var errorDialogContent by remember {
        mutableStateOf("")
    }
    val state by stateFlow.collectAsState(initial = ServerState.Init)

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name))
                Text(
                    text = port,
                    fontSize = 10.sp,
                    color = when (state) {
                        is ServerState.Error -> MaterialTheme.colorScheme.onError
                        is ServerState.Stopped -> MaterialTheme.colorScheme.onTertiary
                        is ServerState.Init -> MaterialTheme.colorScheme.onSecondary
                        else -> MaterialTheme.colorScheme.onPrimary
                    },
                    modifier = Modifier
                        .padding(8.dp)
                        .background(
                            when (state) {
                                is ServerState.Error -> MaterialTheme.colorScheme.error
                                is ServerState.Stopped -> MaterialTheme.colorScheme.tertiary
                                is ServerState.Init -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp, 2.dp)
                        .clickable {
                            val currentState = state
                            if (currentState is ServerState.Error) {
                                errorDialogContent = currentState.cause.stackTraceToString()
                            } else if (currentState is ServerState.Started) {
                                showDialog = true
                            }
                        }
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = {
                openDrawer()
            }) {
                Icon(Icons.Filled.Menu, contentDescription = null)
            }
        },
        actions = {
            IconButton(onClick = {
                restartService()
            }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.restart_service)
                )
            }
            IconButton(onClick = {
                stopService()
            }) {
                Icon(
                    ImageVector.vectorResource(id = R.drawable.baseline_stop_24),
                    contentDescription = stringResource(R.string.stop_service)
                )
            }
            IconButton(onClick = {
                deleteAll()
            }) {
                Icon(
                    ImageVector.vectorResource(id = R.drawable.baseline_delete_24),
                    contentDescription = stringResource(
                        R.string.delete_all
                    )
                )
            }
        },

        )
    val (_, qrcodeHorLayout) = computeQrcodeLayout()
    if (showDialog) {
        BasicAlertDialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = !qrcodeHorLayout
            ),
        ) {
            Surface(shape = RoundedCornerShape(8.dp)) {
                ShowQrCode(sub = "", port = port, modifier = Modifier.padding(20.dp)) {
                    showDialog = false
                    sendText(it)
                }
            }
        }
    }
    if (errorDialogContent.isNotEmpty()) {
        val scrollState = rememberScrollState()
        AlertDialog(onDismissRequest = { errorDialogContent = "" }, confirmButton = {
            Text(text = "Close", modifier = Modifier
                .clickable {
                    errorDialogContent = ""
                }
            )
        }, text = {
            Text(text = errorDialogContent, modifier = Modifier.verticalScroll(scrollState))
        })
    }
}

@Preview
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SettingPage(port: String = AppService.DEFAULT_PORT.toString()) {
    PrefsScreen(dataStore = LocalContext.current.dataStore) {
        prefsItem {
            EditTextPref(
                key = "port",
                title = stringResource(R.string.port),
                summary = "server listen on $port",
                dialogTitle = stringResource(R.string.port_setting),
                dialogMessage = stringResource(R.string.please_input_a_valid_port),
                defaultValue = AppService.DEFAULT_PORT.toString()
            )
        }
    }
}

@Preview
@Composable
fun NavDrawer(
    closeDrawer: () -> Unit = {},
    navigateTo: (String) -> Unit = {},
    openAboutPage: () -> Unit = {}
) {

    NavigationDrawerItem(
        label = {
            Text(text = stringResource(R.string.home))
        },
        icon = {
            Icon(Icons.Filled.Home, contentDescription = stringResource(id = R.string.home))
        },
        selected = false,
        onClick = {
            navigateTo("main")
            closeDrawer()
        })

    NavigationDrawerItem(label = { Text(text = stringResource(R.string.messages)) }, icon = {
        Icon(Icons.Filled.AccountBox, contentDescription = stringResource(R.string.messages))
    }, selected = false, onClick = {
        navigateTo("messages")
        closeDrawer()
    })

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        NavigationDrawerItem(label = { Text(text = "hid") }, icon = {
            Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.messages))
        }, selected = false, onClick = {
            navigateTo("hid")
            closeDrawer()
        })
    }

    NavigationDrawerItem(label = { Text(text = "保护措施") }, icon = {
        Icon(
            ImageVector.vectorResource(R.drawable.baseline_password_24),
            contentDescription = "safe"
        )
    }, selected = false, onClick = {
        navigateTo("safe")
        closeDrawer()
    })

    NavigationDrawerItem(
        label = {
            Text(text = stringResource(R.string.about))
        },
        icon = {
            Icon(Icons.Filled.Info, contentDescription = stringResource(id = R.string.about))
        },
        selected = false,
        onClick = {
            openAboutPage()
            closeDrawer()
        })
    NavigationDrawerItem(
        label = {
            Text(text = stringResource(R.string.settings))
        },
        icon = {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(id = R.string.settings)
            )
        },
        selected = false,
        onClick = {
            navigateTo("settings")
            closeDrawer()
        })
}

@Composable
fun OneCenter(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(), contentAlignment = Alignment.Center, content = content
    )
}
