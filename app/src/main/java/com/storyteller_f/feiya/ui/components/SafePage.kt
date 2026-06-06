package com.storyteller_f.feiya.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import com.jamal.composeprefs3.ui.PrefsScreen
import com.jamal.composeprefs3.ui.prefs.EditTextPref
import com.storyteller_f.feiya.dataStore


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SafePage() {
    PrefsScreen(dataStore = LocalContext.current.dataStore) {
        prefsItem {
            EditTextPref(key = "password", title = "password", summary = "想要访问内容必需输入此密码，为空代表不需要密码")
        }
    }
}
