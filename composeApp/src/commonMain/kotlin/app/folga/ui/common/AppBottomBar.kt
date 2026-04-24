package app.folga.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Barra inferior de navegação — três abas: Home, Trocas, Perfil. A aba
 * Trocas exibe um badge com a contagem de trocas pendentes recebidas
 * quando [pendingSwapsCount] é maior que zero.
 *
 * As navegações acontecem pelos callbacks `onSelect*` ao invés de um
 * navigation graph — o roteamento ainda é feito no [app.folga.App] por
 * um sealed `Screen`. Manter assim evita introduzir uma dep de
 * navegação-compose-KMP só pra essa barra.
 */
enum class MainTab { HOME, SWAPS, PROFILE }

@Composable
fun AppBottomBar(
    selected: MainTab,
    pendingSwapsCount: Int,
    onSelectHome: () -> Unit,
    onSelectSwaps: () -> Unit,
    onSelectProfile: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == MainTab.HOME,
            onClick = onSelectHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = selected == MainTab.SWAPS,
            onClick = onSelectSwaps,
            icon = {
                BadgedBox(
                    badge = {
                        if (pendingSwapsCount > 0) {
                            Badge { Text(pendingSwapsCount.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Trocas")
                }
            },
            label = { Text("Trocas") },
        )
        NavigationBarItem(
            selected = selected == MainTab.PROFILE,
            onClick = onSelectProfile,
            icon = { Icon(Icons.Filled.Person, contentDescription = "Perfil") },
            label = { Text("Perfil") },
        )
    }
}
