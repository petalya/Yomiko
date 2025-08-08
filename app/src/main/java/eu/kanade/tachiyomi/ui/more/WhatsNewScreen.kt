package eu.kanade.tachiyomi.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.components.MarkdownRender
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.updater.GITHUB_REPO
import eu.kanade.tachiyomi.util.system.openInBrowser
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen
import uy.kohesive.injekt.injectLazy

class WhatsNewScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val releaseService: ReleaseService by injectLazy()

        var release: Release? by remember { mutableStateOf(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            isLoading = true
            release = try {
                withIOContext { releaseService.latest(GITHUB_REPO) }
            } catch (e: Exception) {
                null
            }
            isLoading = false
        }

        val subtitle = release?.version ?: stringResource(MR.strings.loading)

        InfoScreen(
            icon = Icons.Outlined.NewReleases,
            headingText = stringResource(MR.strings.whats_new),
            subtitleText = subtitle,
            acceptText = stringResource(MR.strings.action_close),
            onAcceptClick = navigator::pop,
            canAccept = !isLoading,
        ) {
            if (release != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.padding.large),
                ) {
                    MarkdownRender(
                        content = extractChangesSection(release!!.info),
                        flavour = GFMFlavourDescriptor(),
                    )

                    TextButton(
                        onClick = { context.openInBrowser(release!!.releaseLink) },
                        modifier = Modifier.padding(top = MaterialTheme.padding.small),
                    ) {
                        Text(text = stringResource(MR.strings.update_check_open))
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                        Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    }
                }
            }
        }
    }
}

private fun extractChangesSection(markdown: String): String {
    val lines = markdown.lines()
    val headingRegex = Regex(
        pattern = "^#{1,6}\\s*(?:what'?s\\s+changed|what'?s\\s+new|changes|change\\s*log|changelog)\\s*:?",
        option = RegexOption.IGNORE_CASE,
    )
    val thematicBreakRegex = Regex("^\\s*([-*_])\\1{2,}\\s*$")
    val stopTitleRegex = Regex("^#{1,6}\\s*(checksums|downloads?|assets|signatures)\\s*:?$", RegexOption.IGNORE_CASE)

    var startIndex = -1
    var startLevel = 0

    for ((index, rawLine) in lines.withIndex()) {
        val line = rawLine.trim()
        if (headingRegex.containsMatchIn(line)) {
            startIndex = index
            startLevel = line.takeWhile { it == '#' }.length
            break
        }
    }

    if (startIndex == -1) return markdown.trim()

    val sectionBuilder = StringBuilder()
    for (i in startIndex until lines.size) {
        val current = lines[i]
        if (i > startIndex) {
            val trimmed = current.trim()
            if (thematicBreakRegex.matches(trimmed)) {
                break
            }
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                // Stop at same or higher level heading, or at specific stop titles regardless of level
                if (level <= startLevel || stopTitleRegex.containsMatchIn(trimmed)) break
            }
        }
        sectionBuilder.append(current).append('\n')
    }

    return sectionBuilder.toString().trim()
}
