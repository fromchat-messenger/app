package com.pr0gramm3r101.utils

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class NavigationItem(
    val name: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon,
    val onClick: () -> Unit
)

@Composable
fun SimpleNavigationBar(
    selectedItem: Int,
    modifier: Modifier = Modifier,
    vararg items: NavigationItem,
) {
    NavigationBar(modifier) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector =
                            if (selectedItem == index)
                                items[index].selectedIcon
                            else
                                items[index].unselectedIcon,
                        contentDescription = item.name
                    )
                },
                label = { Text(item.name) },
                selected = selectedItem == index,
                onClick = items[index].onClick
            )
        }
    }
}

@Composable
fun SimpleNavigationRail(
    selectedItem: Int,
    modifier: Modifier = Modifier,
    vararg items: NavigationItem,
) {
    NavigationRail(
        modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = {
                    Icon(
                        imageVector =
                            if (selectedItem == index)
                                items[index].selectedIcon
                            else
                                items[index].unselectedIcon,
                        contentDescription = item.name
                    )
                },
                label = { Text(item.name) },
                selected = selectedItem == index,
                onClick = items[index].onClick
            )
        }
    }
}
