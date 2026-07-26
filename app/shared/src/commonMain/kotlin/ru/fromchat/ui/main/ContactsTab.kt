package ru.fromchat.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import ru.fromchat.ui.components.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.contacts
import ru.fromchat.contacts_empty_body
import ru.fromchat.contacts_empty_title
import ru.fromchat.ui.extraStatusBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsTab() {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = if (LocalMainChromeInsets.current.top > 0.dp) {
                    WindowInsets.extraStatusBars
                } else {
                    WindowInsets(0, 0, 0, 0)
                },
                title = {
                    Text(
                        text = stringResource(Res.string.contacts),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .mainPagerBottomInset()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val tonal = ButtonDefaults.filledTonalButtonColors()
                val pillWidth = 148.dp
                val pillHeight = 66.dp
                val pillShape = RoundedCornerShape(pillHeight / 2)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.width(pillWidth)
                ) {
                    Column(
                        modifier = Modifier.width(pillWidth),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(pillWidth)
                                .height(pillHeight),
                            shape = pillShape,
                            color = tonal.containerColor
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                        Surface(
                            modifier = Modifier
                                .width(pillWidth)
                                .height(pillHeight),
                            shape = pillShape,
                            color = tonal.containerColor
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(88.dp),
                        tint = tonal.contentColor
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = stringResource(Res.string.contacts_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.contacts_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
