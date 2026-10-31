package ru.fromchat.ui.main.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import ru.fromchat.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.ui.Website
import ru.fromchat.utils.conditional
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import ru.fromchat.AppBuildInfo
import ru.fromchat.appRepoBlobUrl
import ru.fromchat.Res
import ru.fromchat.about
import ru.fromchat.about_link_max
import ru.fromchat.about_link_license
import ru.fromchat.about_link_license_desc
import ru.fromchat.about_link_privacy
import ru.fromchat.about_link_telegram
import ru.fromchat.about_link_terms
import ru.fromchat.about_link_website
import ru.fromchat.about_version_commit_prefix
import ru.fromchat.about_what_changed
import ru.fromchat.app_desc
import ru.fromchat.logo_square
import ru.fromchat.ui.main.settings.about.legal.DocumentType
import ru.fromchat.ui.main.settings.about.legal.appDisplayVersion
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.BrandTitle
import ru.fromchat.ui.main.settings.SettingsDetailTopBar
import ru.fromchat.ui.main.settings.rememberSettingsCollapsingScrollBehavior
import ru.fromchat.ui.main.settings.rememberSettingsDetailHazeState
import ru.fromchat.ui.main.settings.settingsDetailHazeSource
import ru.fromchat.ui.main.settings.settingsDetailUseCollapsingTopBar
import ru.fromchat.ui.release.ReleaseNotesDialog

private const val URL_TELEGRAM = "https://t.me/fromchat_ch"
private const val URL_MAX = "https://maxgate.io/fromchat_ch"
private const val URL_WEBSITE = "https://fromchat.ru"

@Composable
fun AboutScreen() {
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()
    val navController = LocalNavController.current
    val uriHandler = LocalUriHandler.current
    var showReleaseNotes by remember { mutableStateOf(false) }

    if (showReleaseNotes) {
        ReleaseNotesDialog(onDismiss = { showReleaseNotes = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsDetailTopBar(
                title = stringResource(Res.string.about),
                onBack = { navController.navigateUp() },
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .settingsDetailHazeSource(hazeState)
                .conditional(useCollapsing) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                }
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
                    Image(
                        painter = painterResource(Res.drawable.logo_square),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(28.dp)),
                    )
                }

                BrandTitle(Modifier.padding(bottom = 4.dp))

                AboutVersionLine(
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Text(
                    text = stringResource(Res.string.app_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (AppBuildInfo.releaseNotesMarkdown.isNotBlank()) {
                Category(
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    ListItem(
                        headline = stringResource(Res.string.about_what_changed),
                        onClick = { showReleaseNotes = true },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                    )
                }
            }

            Category(
                modifier = Modifier.padding(top = if (AppBuildInfo.releaseNotesMarkdown.isNotBlank()) 8.dp else 16.dp),
            ) {
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
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Website,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                )
            }

            Category(
                modifier = Modifier.padding(top = 8.dp),
            ) {
                ListItem(
                    headline = stringResource(Res.string.about_link_terms),
                    onClick = { navController.navigate(DocumentType.route(DocumentType.Terms)) },
                    divider = true,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                )

                ListItem(
                    headline = stringResource(Res.string.about_link_privacy),
                    onClick = { navController.navigate(DocumentType.route(DocumentType.Privacy)) },
                    divider = true,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                )

                ListItem(
                    headline = stringResource(Res.string.about_link_license),
                    supportingText = stringResource(Res.string.about_link_license_desc),
                    onClick = {
                        AppBuildInfo.appRepoBlobUrl("LICENSE")?.let(uriHandler::openUri)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Balance,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AboutVersionLine(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val versionDisplay = appDisplayVersion(AppBuildInfo.version, AppBuildInfo.isDebug)
    val prefix = stringResource(Res.string.about_version_commit_prefix, versionDisplay)
    val commitShort = AppBuildInfo.gitCommitShort
    val commitUrl = AppBuildInfo.gitCommitUrl
    val textStyle = MaterialTheme.typography.bodyMedium
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkStyle = textStyle.toSpanStyle().copy(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val annotated = buildAnnotatedString {
        append(prefix)
        if (commitUrl.isNotBlank()) {
            withLink(
                LinkAnnotation.Clickable(
                    tag = commitUrl,
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = { link ->
                        (link as? LinkAnnotation.Clickable)?.tag?.let(uriHandler::openUri)
                    },
                ),
            ) {
                append(commitShort)
            }
        } else {
            append(commitShort)
        }
    }
    Text(
        text = annotated,
        style = textStyle,
        color = bodyColor,
        modifier = modifier,
    )
}
