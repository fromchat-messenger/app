package ru.fromchat.legal

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fromchat.ui.components.Text

private const val LINK_PRESS_SCALE = 0.92f

private fun AnnotatedString.linkAt(offset: Int): AnnotatedString.Range<LinkAnnotation>? {
    if (isEmpty()) return null

    val clamped = offset.coerceIn(0, lastIndex)
    return getLinkAnnotations(clamped, clamped + 1).firstOrNull()
        ?: getLinkAnnotations(0, length).firstOrNull { clamped in it.start until it.end }
}

private val LEGAL_STATIC_LINK_RE = Regex(
    pattern = """(?:^|/)?(?:api/)?static/(TERMS|PRIVACY)\.md$""",
    option = RegexOption.IGNORE_CASE,
)

private val SECTION_DIRECTIVE_REGEX = Regex("""<!--\s*fc:([^>]+?)\s*-->""", RegexOption.IGNORE_CASE)

private val LEGAL_MATERIAL_ICON_ALIASES = mapOf(
    "privacy" to "privacy_tip",
    "terms" to "contract",
)

fun materialIconName(iconKey: String) = LEGAL_MATERIAL_ICON_ALIASES[iconKey] ?: iconKey

fun materialIconVector(iconKey: String): ImageVector = when (materialIconName(iconKey)) {
    "privacy_tip" -> Icons.Filled.PrivacyTip
    "contract" -> Icons.AutoMirrored.Filled.Article
    "person_add" -> Icons.Filled.PersonAdd
    "chat" -> Icons.AutoMirrored.Filled.Chat
    "storage" -> Icons.Filled.Storage
    "shield" -> Icons.Filled.Shield
    "visibility_off" -> Icons.Filled.VisibilityOff
    "block" -> Icons.Filled.Block
    "call" -> Icons.Filled.Call
    "delete" -> Icons.Filled.Delete
    "description" -> Icons.Filled.Description
    "lock" -> Icons.Filled.Lock
    "notifications" -> Icons.Filled.Notifications
    "person" -> Icons.Filled.Person
    "phone" -> Icons.Filled.Phone
    else -> Icons.Filled.Info
}

data class SectionDirective(
    val shape: String,
    val icon: String,
)

data class Section(
    val directive: SectionDirective,
    val title: String,
    val bodyMarkdown: String,
)

data class ParsedDocument(
    val preamble: String,
    val sections: List<Section>,
)

private data class MarkdownTable(
    val header: List<String>,
    val rows: List<List<String>>,
)

@Composable
expect fun Markdown(
    content: String,
    modifier: Modifier = Modifier,
)

/** Subsection (`###`) size: geometric middle between body text and expressive section title. */
@Composable
fun markdownSubsectionStyle(): TextStyle {
    val body = MaterialTheme.typography.bodyMedium
    val sectionTitle = MaterialTheme.typography.titleLarge

    return body.copy(
        fontSize = ((body.fontSize.value + sectionTitle.fontSize.value) / 2).sp,
        lineHeight = ((body.lineHeight.value + sectionTitle.lineHeight.value) / 2).sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Maps static legal Markdown API paths to in-app legal document types.
 */
fun resolveLegalDocumentType(uri: String): DocumentType? = when (
    (
        LEGAL_STATIC_LINK_RE.find(
            uri
                .replace('\\', '/')
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
        ) ?: return null
    ).groupValues[1].uppercase()
) {
    "TERMS" -> DocumentType.Terms
    "PRIVACY" -> DocumentType.Privacy
    else -> null
}

fun parseSectionDirective(line: String): SectionDirective? {
    val body = (SECTION_DIRECTIVE_REGEX.find(line.trim()) ?: return null).groupValues[1]

    return SectionDirective(
        shape = Regex("""shape=([A-Za-z0-9_]+)""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null,
        icon = Regex("""icon=([A-Za-z0-9_-]+)""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
    )
}

fun parseMarkdown(markdown: String): ParsedDocument {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val preambleLines = mutableListOf<String>()
    val sections = mutableListOf<Section>()

    var i = 0
    while (i < lines.size) {
        val directive = parseSectionDirective(lines[i])

        when {
            directive != null && i + 1 < lines.size && lines[i + 1].startsWith("## ") -> {
                val title = lines[i + 1].removePrefix("## ").trim()

                i += 2

                sections += Section(
                    directive = directive,
                    title = title,
                    bodyMarkdown = buildList {
                        while (i < lines.size) {
                            if (
                                parseSectionDirective(lines[i]) != null &&
                                i + 1 < lines.size &&
                                lines[i + 1].startsWith("## ")
                            ) break

                            add(lines[i])
                            i += 1
                        }
                    }.joinToString("\n").trim(),
                )
            }

            sections.isEmpty() -> {
                preambleLines += lines[i]
                i += 1
            }

            else -> i += 1
        }
    }

    return ParsedDocument(
        preamble = preambleLines.joinToString("\n").trim(),
        sections = sections,
    )
}

@Composable
internal fun MarkdownPlain(
    content: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
) {
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )

    Column(modifier = modifier) {
        val lines = content.lineSequence().toList()
        var index = 0

        while (index < lines.size) {
            val trimmed = lines[index].trimEnd()

            when {
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(8.dp))
                    index += 1
                }

                isLegalMarkdownTableLine(trimmed) -> {
                    val tableLines = buildList {
                        while (index < lines.size && isLegalMarkdownTableLine(lines[index].trimEnd())) {
                            this += lines[index].trimEnd()
                            index += 1
                        }
                    }

                    parseMarkdownTable(tableLines).let {
                        if (it != null) {
                            LegalMarkdownTableView(
                                table = it,
                                linkStyle = linkStyle,
                                onLinkClick = onLinkClick,
                            )
                        } else {
                            tableLines.forEach { line ->
                                MarkdownBlockText(
                                    text = parseInlineMarkdown(line, linkStyle, onLinkClick),
                                    style = bodyStyle,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                }

                trimmed.startsWith("### ") -> {
                    MarkdownBlockText(
                        text = parseInlineMarkdown(trimmed.removePrefix("### "), linkStyle, onLinkClick),
                        style = markdownSubsectionStyle(),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )

                    index += 1
                }

                trimmed.startsWith("- ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                        Text(
                            text = "• ",
                            style = bodyStyle,
                        )

                        MarkdownBlockText(
                            text = parseInlineMarkdown(trimmed.removePrefix("- "), linkStyle, onLinkClick),
                            style = bodyStyle,
                        )
                    }

                    index += 1
                }

                else -> {
                    MarkdownBlockText(
                        text = parseInlineMarkdown(trimmed, linkStyle, onLinkClick),
                        style = bodyStyle,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    index += 1
                }
            }
        }
    }
}

@Composable
internal fun MarkdownBlockText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var pressedLinkRange by remember(text) { mutableStateOf<IntRange?>(null) }
    val currentLayoutResult by rememberUpdatedState(layoutResult)
    val scale by animateFloatAsState(
        targetValue = if (pressedLinkRange != null) LINK_PRESS_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "legal_link_press_scale",
    )

    // graphicsLayer on BasicText does not scale the text draw pass; wrap in Box (see MessageItem).
    Box(
        modifier = modifier
            .graphicsLayer {
                val range = pressedLinkRange
                val layout = layoutResult
                compositingStrategy = CompositingStrategy.Offscreen
                if (range != null && layout != null && layout.size.width > 0f && layout.size.height > 0f) {
                    val bounds = layout.getPathForRange(range.first, range.last + 1).getBounds()
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (bounds.left + bounds.right) / 2f / layout.size.width,
                        pivotFractionY = (bounds.top + bounds.bottom) / 2f / layout.size.height,
                    )
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    transformOrigin = TransformOrigin.Center
                }
            }
            .pointerInput(text) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        pass = PointerEventPass.Initial,
                        requireUnconsumed = false,
                    )
                    val layout = currentLayoutResult ?: return@awaitEachGesture
                    val link = text.linkAt(layout.getOffsetForPosition(down.position)) ?: return@awaitEachGesture

                    pressedLinkRange = link.start until link.end
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressedLinkRange = null
                    }
                }
            },
    ) {
        BasicText(
            text = text,
            style = style.merge(color = MaterialTheme.colorScheme.onSurface),
            onTextLayout = { layoutResult = it },
        )
    }
}

internal fun parseInlineMarkdown(
    text: String,
    linkStyle: SpanStyle,
    onLinkClick: (String) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", index + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(text[index])
                        index += 1
                    }
                }

                text[index] == '[' -> {
                    val closeBracket = text.indexOf(']', index + 1)
                    val openParen = if (closeBracket != -1) text.indexOf('(', closeBracket + 1) else -1
                    val closeParen = if (openParen != -1) text.indexOf(')', openParen + 1) else -1

                    if (closeBracket != -1 && openParen == closeBracket + 1 && closeParen != -1) {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = text.substring(openParen + 1, closeParen),
                                styles = TextLinkStyles(style = linkStyle),
                                linkInteractionListener = { link ->
                                    (link as? LinkAnnotation.Clickable)?.tag?.let(onLinkClick)
                                },
                            ),
                        ) {
                            append(text.substring(index + 1, closeBracket))
                        }

                        index = closeParen + 1
                    } else {
                        append(text[index])
                        index += 1
                    }
                }

                else -> {
                    append(text[index])
                    index += 1
                }
            }
        }
    }

private fun parseMarkdownTable(lines: List<String>): MarkdownTable? {
    return if (lines.size < 2 || !isMarkdownTableSeparator(lines[1])) null else MarkdownTable(
        header = parseMarkdownTableRow(lines.first()) ?: return null,
        rows = lines.drop(2).mapNotNull(::parseMarkdownTableRow)
    )
}

private fun isLegalMarkdownTableLine(line: String) =
    line.trim().startsWith("|") && line.trim().endsWith("|")

private fun parseMarkdownTableRow(line: String) =
    if (!isLegalMarkdownTableLine(line)) null else
        line.trim().trim('|').split('|').map { it.trim() }

private fun isMarkdownTableSeparator(line: String) =
    parseMarkdownTableRow(line)?.all { cell ->
        cell.all { it == '-' || it == ':' || it.isWhitespace() }
    } == true

@Composable
private fun LegalMarkdownTableView(
    table: MarkdownTable,
    linkStyle: SpanStyle,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            val borderColor = MaterialTheme.colorScheme.outlineVariant
            val columnCount = maxOf(
                table.header.size,
                table.rows.maxOfOrNull { it.size } ?: 0
            )

            Row(Modifier.fillMaxWidth()) {
                repeat(columnCount) { columnIndex ->
                    MarkdownTableCell(
                        text = table.header.getOrNull(columnIndex).orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        borderColor = borderColor,
                        linkStyle = linkStyle,
                        onLinkClick = onLinkClick,
                    )
                }
            }

            table.rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(columnCount) { columnIndex ->
                        MarkdownTableCell(
                            text = row.getOrNull(columnIndex).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = borderColor,
                            linkStyle = linkStyle,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableCell(
    text: String,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color,
    linkStyle: SpanStyle,
    onLinkClick: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .fillMaxHeight()
            .background(backgroundColor)
            .border(0.5.dp, borderColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        MarkdownBlockText(
            text = parseInlineMarkdown(text, linkStyle, onLinkClick),
            style = style,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSectionHeader(
    directive: SectionDirective,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(
                    when (directive.shape) {
                        "Arch" -> MaterialShapes.Arch
                        "Arrow" -> MaterialShapes.Arrow
                        "Boom" -> MaterialShapes.Boom
                        "Bun" -> MaterialShapes.Bun
                        "Burst" -> MaterialShapes.Burst
                        "ClamShell" -> MaterialShapes.ClamShell
                        "Clover4Leaf" -> MaterialShapes.Clover4Leaf
                        "Clover8Leaf" -> MaterialShapes.Clover8Leaf
                        "Cookie12Sided" -> MaterialShapes.Cookie12Sided
                        "Cookie4Sided" -> MaterialShapes.Cookie4Sided
                        "Cookie6Sided" -> MaterialShapes.Cookie6Sided
                        "Cookie7Sided" -> MaterialShapes.Cookie7Sided
                        "Cookie9Sided" -> MaterialShapes.Cookie9Sided
                        "Diamond" -> MaterialShapes.Diamond
                        "Fan" -> MaterialShapes.Fan
                        "Flower" -> MaterialShapes.Flower
                        "Gem" -> MaterialShapes.Gem
                        "Ghostish" -> MaterialShapes.Ghostish
                        "Heart" -> MaterialShapes.Heart
                        "Oval" -> MaterialShapes.Oval
                        "Pentagon" -> MaterialShapes.Pentagon
                        "Pill" -> MaterialShapes.Pill
                        "PixelCircle" -> MaterialShapes.PixelCircle
                        "PixelTriangle" -> MaterialShapes.PixelTriangle
                        "Puffy" -> MaterialShapes.Puffy
                        "PuffyDiamond" -> MaterialShapes.PuffyDiamond
                        "SemiCircle" -> MaterialShapes.SemiCircle
                        "Slanted" -> MaterialShapes.Slanted
                        "SoftBoom" -> MaterialShapes.SoftBoom
                        "SoftBurst" -> MaterialShapes.SoftBurst
                        "Square" -> MaterialShapes.Square
                        "Sunny" -> MaterialShapes.Sunny
                        "Triangle" -> MaterialShapes.Triangle
                        "VerySunny" -> MaterialShapes.VerySunny
                        else -> MaterialShapes.Circle
                    }.normalized().toShape()
                )
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = materialIconVector(directive.icon),
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
