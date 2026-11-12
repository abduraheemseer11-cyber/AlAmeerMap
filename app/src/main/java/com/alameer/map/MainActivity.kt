package com.alameer.map

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alameer.map.ui.MainScaffold
import com.alameer.map.ui.auth.AuthScreen
import com.alameer.map.ui.auth.AuthState
import com.alameer.map.ui.auth.AuthViewModel
import com.alameer.map.ui.auth.NewPasswordScreen
import com.alameer.map.ui.theme.AlAmeerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) publishLink(intent)
        setContent {
            AlAmeerTheme {
                // The whole app is Arabic: force right-to-left.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppRoot()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishLink(intent)
    }

    private fun publishLink(i: Intent?) {
        val uri = i?.data ?: return
        if (uri.scheme == "alameer") (application as AlAmeerApp).container.deepLink.value = uri
    }
}

@Composable
private fun AppRoot() {
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
        MissingConfigScreen()
        return
    }
    val app = LocalContext.current.applicationContext as AlAmeerApp
    val authVm: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel(app, app.container.authRepository, app.container.deepLink) }
        },
    )
    val state by authVm.state.collectAsStateWithLifecycle()
    val recovery by authVm.recovery.collectAsStateWithLifecycle()

    when (val s = state) {
        AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AuthState.SignedOut -> AuthScreen(authVm)
        is AuthState.SignedIn ->
            if (recovery) NewPasswordScreen(authVm)
            else MainScaffold(userId = s.userId, email = s.email, onSignOut = authVm::signOut)
    }
}

@Composable
private fun MissingConfigScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.config_missing_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.config_missing_body))
    }
}
