package ru.fromchat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun RowHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(30.dp),
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}