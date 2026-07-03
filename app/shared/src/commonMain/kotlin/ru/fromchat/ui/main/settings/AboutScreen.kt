package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import ru.fromchat.ui.components.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.ui.Website
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import ru.fromchat.Res
import ru.fromchat.about
import ru.fromchat.about_link_max
import ru.fromchat.about_link_privacy
import ru.fromchat.about_link_telegram
import ru.fromchat.about_link_terms
import ru.fromchat.about_link_website
import ru.fromchat.about_version
import ru.fromchat.app_desc
import ru.fromchat.back
import ru.fromchat.legal.DocumentType
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.BrandTitle

private const val URL_TELEGRAM = "https://t.me/fromchat_ch"
private const val URL_MAX = "https://maxgate.io/fromchat_ch"
private const val URL_WEBSITE = "https://fromchat.ru"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navController = LocalNavController.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.about),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.padding(bottom = 12.dp)) {
                    AsyncImage(
                        model = Res.getUri("drawable/logo_square.svg"),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(28.dp))
                    )
                }

                BrandTitle(Modifier.padding(bottom = 4.dp))

                Text(
                    text = stringResource(Res.string.about_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = stringResource(Res.string.app_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Category(Modifier.padding(top = 16.dp)) {
                ListItem(
                    headline = stringResource(Res.string.about_link_telegram),
                    supportingText = URL_TELEGRAM,
                    onClick = { uriHandler.openUri(URL_TELEGRAM) },
                    divider = true,
                    leadingContent = {
                        Icon(vectorResource(Res.drawable.about_link_telegram), null)
                    }
                )

                ListItem(
                    headline = stringResource(Res.string.about_link_max),
                    supportingText = URL_MAX,
                    onClick = { uriHandler.openUri(URL_MAX) },
                    divider = true,
                    leadingContent = {
                        Icon(vectorResource(Res.drawable.about_link_max), null)
                    }
                )

                ListItem(
                    headline = stringResource(Res.string.about_link_website),
                    supportingText = URL_WEBSITE,
                    onClick = { uriHandler.openUri(URL_WEBSITE) },
                    divider = true,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Website,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                )

                ListItem(
                    headline = stringResource(Res.string.about_link_privacy),
                    onClick = { navController.navigate(DocumentType.route(DocumentType.Privacy)) },
                    divider = true,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                )

                ListItem(
                    headline = stringResource(Res.string.about_link_terms),
                    onClick = { navController.navigate(DocumentType.route(DocumentType.Terms)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                )
            }
        }
    }
}
