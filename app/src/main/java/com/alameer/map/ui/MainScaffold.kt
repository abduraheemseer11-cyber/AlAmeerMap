package com.alameer.map.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alameer.map.AlAmeerApp
import com.alameer.map.R
import com.alameer.map.ui.map.MapScreen
import com.alameer.map.ui.map.MapViewModel
import com.alameer.map.ui.map.MarkerDetailsSheet
import com.alameer.map.ui.map.MarkerForm
import com.alameer.map.ui.map.MarkerFormDialog
import com.alameer.map.ui.profile.ProfileScreen
import com.alameer.map.ui.search.SearchScreen

private enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    MapTab(R.string.nav_map, Icons.Default.Place),
    SearchTab(R.string.nav_search, Icons.Default.Search),
    ProfileTab(R.string.nav_profile, Icons.Default.Person),
}

@Composable
fun MainScaffold(userId: String, email: String, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as AlAmeerApp
    val vm: MapViewModel = viewModel(
        key = userId,
        factory = viewModelFactory {
            initializer { MapViewModel(app as Context, userId, app.container.markerRepository) }
        },
    )

    var tab by rememberSaveable { mutableStateOf(MainTab.MapTab) }
    var form by remember { mutableStateOf<MarkerForm?>(null) }
    val snack = remember { SnackbarHostState() }

    val markers by vm.markers.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()

    LaunchedEffect(vm) { vm.events.collect { snack.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                MainTab.MapTab -> MapScreen(vm, onAddAt = { lat, lon -> form = MarkerForm.blank(lat, lon) })
                MainTab.SearchTab -> SearchScreen(vm, onPick = { m -> vm.focusOn(m); tab = MainTab.MapTab })
                MainTab.ProfileTab -> ProfileScreen(
                    email = email,
                    count = markers.size,
                    syncing = syncing,
                    onSync = { vm.sync(manual = true) },
                    onExport = { uri -> vm.export(uri, ctx.contentResolver) },
                    onImport = { uri -> vm.import(uri, ctx.contentResolver) },
                    onSignOut = onSignOut,
                )
            }
        }
    }

    if (tab == MainTab.MapTab) {
        selected?.let { m ->
            MarkerDetailsSheet(
                marker = m,
                password = remember(m.passwordEnc) { vm.password(m) },
                onDismiss = { vm.select(null) },
                onEdit = { form = vm.formFor(m); vm.select(null) },
                onDelete = { vm.delete(m) },
            )
        }
    }

    form?.let { initial ->
        MarkerFormDialog(
            initial = initial,
            onDismiss = { form = null },
            onSave = { f -> vm.save(f); form = null },
        )
    }
}
