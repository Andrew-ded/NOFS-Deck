package com.nofs.desk.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.BuildOption
import com.nofs.desk.data.GitState
import com.nofs.desk.ui.theme.DeskBg
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.Lavender
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Sage

/**
 * Урезанная Git-панель для телефона: без Pull/Commit/Push/переключения
 * веток — только кнопки сборки (место фиксировано, см. android/CLAUDE.md
 * про «Тень билда») и граф коммитов на первой странице; PR/Issues с
 * GitHub — свайпом на второй, тап по строке открывает ссылку в браузере.
 * GitHub подтягивается автоматически, но НЕ здесь — см. PhoneDeskScreen
 * (запрос при коннекте, один раз за экран). Эта панель монтируется и
 * размонтируется при каждом открытии/закрытии плеера (условная композиция
 * в PhoneGitAndPlayerSlot) — если бы запрос жил тут, он бы дублировался
 * на каждый тап по плееру и рвал анимацию слайда лишними перерисовками.
 */
@Composable
fun PhoneGitPanel(
    git: GitState,
    builds: List<BuildOption>,
    onRunBuild: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDeskPalette.current
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(palette.card)
            .padding(16.dp)
    ) {
        if (builds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                builds.forEach { b ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Sage.bg)
                            .clickable { onRunBuild(b.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = DeskText,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = b.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = DeskText,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (git.busy || git.githubLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Sage.bar,
                trackColor = DeskBg
            )
            Spacer(Modifier.height(8.dp))
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> PhoneCommitGraph(git)
                1 -> PhoneGitHubList(git)
            }
        }
        Spacer(Modifier.height(6.dp))
        PagerDots(count = 2, current = pagerState.currentPage)
    }
}

@Composable
private fun PhoneCommitGraph(git: GitState) {
    val graph = remember(git.log) { buildGitGraph(git.log) }
    val lanes = remember(graph) { graph.laneCount() }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = listOf(git.repoName, git.branch).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = DeskMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(graph, key = { it.entry.hash }) { row ->
                PhoneCommitRow(row, lanes)
            }
        }
    }
}

@Composable
private fun PhoneCommitRow(row: GraphRow, lanes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        GitGraphCell(row, lanes)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = DeskText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${row.entry.hash} · ${row.entry.timeAgo}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                color = DeskMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PhoneGitHubList(git: GitState) {
    val context = LocalContext.current
    fun open(path: String) {
        if (git.repoUrl.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://${git.repoUrl}/$path")))
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PhoneSectionLabel("PR · ${git.pullRequests.size}") }
        items(git.pullRequests, key = { "pr${it.number}" }) { pr ->
            PhoneLinkRow(
                icon = Icons.Rounded.CallMerge,
                iconTint = Sage.bar,
                title = "#${pr.number} ${pr.title}",
                subtitle = listOf(pr.author, pr.updated).filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { open("pull/${pr.number}") }
            )
        }
        if (git.pullRequests.isEmpty()) item { PhoneEmptyLine("нет открытых PR") }

        item {
            Spacer(Modifier.height(6.dp))
            PhoneSectionLabel("Issues · ${git.issues.size}")
        }
        items(git.issues, key = { "is${it.number}" }) { issue ->
            PhoneLinkRow(
                icon = Icons.Rounded.Adjust,
                iconTint = Lavender.bar,
                title = "#${issue.number} ${issue.title}",
                subtitle = (issue.labels + issue.updated).filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { open("issues/${issue.number}") }
            )
        }
        if (git.issues.isEmpty()) item { PhoneEmptyLine("нет открытых issues") }
    }
}

@Composable
private fun PhoneLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = DeskText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = DeskMuted
                )
            }
        }
    }
}

@Composable
private fun PhoneSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = DeskMuted,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun PhoneEmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = DeskMuted,
        modifier = Modifier.padding(start = 24.dp)
    )
}
