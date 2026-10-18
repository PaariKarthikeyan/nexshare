package com.example.nexshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.nexshare.AppContainer
import com.example.nexshare.transfer.ConnectionMethod
import com.example.nexshare.transfer.TransferControl
import com.example.nexshare.ui.theme.DarkPanel
import com.example.nexshare.ui.theme.MainPanel
import com.example.nexshare.ui.theme.PrimaryAccent
import com.example.nexshare.ui.theme.PrimaryText
import com.example.nexshare.ui.theme.SecondaryText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class Route(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Route("dashboard", "Home", Icons.Default.Home)
    data object Receive : Route("receive", "Receive", Icons.Default.Download)
    data object Send : Route("send", "Send", Icons.Default.Send)
    data object Peers : Route("peers", "Peers", Icons.Default.Group)
    data object Hotspot : Route("hotspot", "Hotspot", Icons.Default.Wifi)
    data object Bluetooth : Route("bluetooth", "Bluetooth", Icons.Default.Bluetooth)
    data object Transfers : Route("transfers", "Transfers", Icons.Default.SwapVert)
    data object History : Route("history", "History", Icons.Default.History)
    data object Settings : Route("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    container: AppContainer,
    peersViewModel: PeersViewModel,
    hotspotViewModel: HotspotViewModel,
    bluetoothViewModel: BluetoothViewModel,
    sendViewModel: SendViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val queued by sendViewModel.files.collectAsState()
    val transfersViewModel: TransfersViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(container.transferRepository) as T
        }
    )

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NavRail(
            currentRoute = backStackEntry?.destination?.route,
            onSelect = { route ->
                navController.navigate(route) {
                    popUpTo(Route.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )

        NavHost(
            navController = navController,
            startDestination = Route.Dashboard.route,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            composable(Route.Dashboard.route) {
                DashboardScreen(
                    peersViewModel = peersViewModel,
                    hotspotViewModel = hotspotViewModel,
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Route.Receive.route) {
                ReceiveScreen(container = container, peersViewModel = peersViewModel)
            }
            composable(Route.Send.route) {
                SendScreen(
                    viewModel = sendViewModel,
                    container = container,
                    onDone = { navController.navigate(Route.Peers.route) }
                )
            }
            composable(Route.Peers.route) {
                PeersScreen(
                    viewModel = peersViewModel,
                    hasQueuedFiles = queued.isNotEmpty(),
                    onSendTo = { peer ->
                        val totalSize = queued.sumOf { it.file.sizeBytes }
                        val chosenMethod = if (peer.method == ConnectionMethod.BLUETOOTH) {
                            ConnectionMethod.BLUETOOTH
                        } else if (totalSize < 5L * 1024 * 1024) {
                            ConnectionMethod.BLUETOOTH
                        } else {
                            ConnectionMethod.HOTSPOT
                        }
                        val adjustedPeer = peer.copy(method = chosenMethod)

                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                queued.forEach { hashedFile ->
                                    hashedFile.sha256?.let { hash ->
                                        container.transferService.sendFile(
                                            peer = adjustedPeer,
                                            file = hashedFile.file,
                                            sha256 = hash,
                                            control = TransferControl()
                                        )
                                    }
                                }
                            }
                            sendViewModel.clear()
                        }
                        navController.navigate(Route.Transfers.route)
                    }
                )
            }
            composable(Route.Hotspot.route) {
                HotspotScreen(viewModel = hotspotViewModel)
            }
            composable(Route.Bluetooth.route) {
                BluetoothScreen(viewModel = bluetoothViewModel)
            }
            composable(Route.Transfers.route) {
                TransfersScreen(
                    viewModel = transfersViewModel,
                    onRetry = {}
                )
            }
            composable(Route.History.route) {
                HistoryScreen(viewModel = historyViewModel)
            }
            composable(Route.Settings.route) {
                SettingsScreen(
                    security = container.security,
                    onClearHistory = { historyViewModel.clear() }
                )
            }
        }
    }
}

@Composable
private fun NavRail(currentRoute: String?, onSelect: (String) -> Unit) {
    val items = listOf(
        Route.Dashboard, Route.Receive, Route.Send, Route.Peers, Route.Hotspot,
        Route.Bluetooth, Route.Transfers, Route.History, Route.Settings
    )
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DarkPanel)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            IconButton(
                onClick = { onSelect(item.route) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) PrimaryAccent else Color.Transparent)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) PrimaryText else SecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
