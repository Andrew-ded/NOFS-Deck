package com.nofs.desk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.GitState
import com.nofs.desk.ui.theme.DeskBg
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskHandle
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.Lavender
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sky
import kotlinx.coroutines.launch

/**
 * Панель Git: свопается между «Локально» и «GitHub»
 * (таб-свитчер сверху + свайп страниц).
 */
@Composable
fun GitPanel(
    git: GitState,
    onRefresh: () -> Unit,
    onPull: () -> Unit,
    onCommit: (String) -> Unit,
    onPush: () -> Unit,
    onCheckout: (String) -> Unit,
    onGitHubRefresh: () -> Unit,
    builds: List<com.nofs.desk.data.BuildOption> = emptyList(),
    onRunBuild: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var githubRequested by rememberSaveable { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1 && !githubRequested) {
            githubRequested = true
            onGitHubRefresh()
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(DeskCard)
            .padding(16.dp)
    ) {
        // Таб-свитчер Локально/GitHub
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DeskBg)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GitTab("Локально", pagerState.currentPage == 0, Modifier.weight(1f)) {
                scope.launch { pagerState.animateScrollToPage(0) }
            }
            GitTab("GitHub", pagerState.currentPage == 1, Modifier.weight(1f)) {
                scope.launch { pagerState.animateScrollToPage(1) }
            }
        }

        Spacer(Modifier.height(12.dp))

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

        // Кнопки запуска сборки (сцена «Тень билда»)
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> LocalPage(git, onRefresh, onPull, onPush, onCheckout) { showCommitDialog = true }
                1 -> GitHubPage(git, onGitHubRefresh)
            }
        }
    }

    if (showCommitDialog) {
        CommitDialog(
            dirtyFiles = git.dirtyFiles,
            changes = git.changes,
            onDismiss = { showCommitDialog = false },
            onConfirm = { msg ->
                showCommitDialog = false
                onCommit(msg)
            }
        )
    }
}

@Composable
private fun GitTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DeskCard else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) DeskText else DeskMuted
        )
    }
}

// ---------- страница «Локально» ----------

@Composable
private fun LocalPage(
    git: GitState,
    onRefresh: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onCheckout: (String) -> Unit,
    onCommitClick: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Ветка (дропдаун для переключения) + обновить
        Row(verticalAlignment = Alignment.CenterVertically) {
            BranchSelector(
                current = git.branch,
                branches = git.branches,
                enabled = !git.busy,
                onCheckout = onCheckout,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Обновить",
                    tint = DeskMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (git.dirtyFiles > 0) StatusChip("± ${git.dirtyFiles}", Lavender.bg)
            if (git.ahead > 0) StatusChip("↑ ${git.ahead}", Sage.bg)
            if (git.behind > 0) StatusChip("↓ ${git.behind}", Sky.bg)
            if (git.dirtyFiles == 0 && git.ahead == 0 && git.behind == 0) {
                StatusChip("чисто", Sage.bg)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = listOf(git.repoName, git.lastSync)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = DeskMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        // История с графом: строки вплотную, чтобы линии дорожек сходились
        val graph = remember(git.log) { buildGitGraph(git.log) }
        val lanes = remember(graph) { graph.laneCount() }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(graph, key = { it.entry.hash }) { row ->
                CommitRow(row, lanes)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Pull / Commit / Push
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GitAction("Pull", Icons.Rounded.CloudDownload, Sky.bg, Modifier.weight(1f), onPull)
            GitAction("Commit", Icons.Rounded.Commit, Lavender.bg, Modifier.weight(1f), onCommitClick)
            GitAction("Push", Icons.Rounded.CloudUpload, Sage.bg, Modifier.weight(1f), onPush)
        }
    }
}

@Composable
private fun BranchSelector(
    current: String,
    branches: List<String>,
    enabled: Boolean,
    onCheckout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled && branches.isNotEmpty()) { expanded = true }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = null,
                tint = DeskMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = current,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetMono, fontWeight = FontWeight.Medium
                ),
                color = DeskText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (branches.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = "Сменить ветку",
                    tint = DeskMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DeskCard
        ) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = branch,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetMono),
                            color = if (branch == current) DeskMuted else DeskText
                        )
                    },
                    onClick = {
                        expanded = false
                        if (branch != current) onCheckout(branch)
                    }
                )
            }
        }
    }
}

@Composable
private fun CommitRow(row: GraphRow, lanes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        GitGraphCell(row, lanes)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.entry.hash,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                    color = DeskMuted
                )
                val branchTag = row.entry.refs
                    .split(',')
                    .map { it.trim().removePrefix("HEAD -> ") }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("tag:") }
                if (branchTag != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = branchTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = DeskText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(laneColor(row.dotLane).copy(alpha = 0.25f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = row.entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = DeskText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.entry.timeAgo,
            style = MaterialTheme.typography.labelSmall,
            color = DeskMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusChip(text: String, bg: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = DeskText,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun GitAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = DeskText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DeskText
        )
    }
}

// ---------- страница «GitHub» ----------

@Composable
private fun GitHubPage(git: GitState, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = git.repoUrl.ifBlank { "репозиторий не настроен" },
                style = MaterialTheme.typography.labelSmall,
                color = DeskMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Обновить",
                    tint = DeskMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionLabel("Открытые PR · ${git.pullRequests.size}")
            }
            items(git.pullRequests, key = { "pr${it.number}" }) { pr ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.CallMerge,
                        contentDescription = null,
                        tint = Sage.bar,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "#${pr.number} ${pr.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeskText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(pr.author, pr.updated)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = DeskMuted
                        )
                    }
                }
            }
            if (git.pullRequests.isEmpty()) {
                item { EmptyLine("нет открытых PR") }
            }

            item {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Открытые Issues · ${git.issues.size}")
            }
            items(git.issues, key = { "is${it.number}" }) { issue ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.Adjust,
                        contentDescription = null,
                        tint = Lavender.bar,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "#${issue.number} ${issue.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeskText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = (issue.labels + issue.updated)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = DeskMuted
                            )
                        }
                    }
                }
            }
            if (git.issues.isEmpty()) {
                item { EmptyLine("нет открытых issues") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = DeskMuted,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = DeskHandle,
        modifier = Modifier.padding(start = 24.dp)
    )
}

// ---------- диалог коммита ----------

@Composable
private fun CommitDialog(
    dirtyFiles: Int,
    changes: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeskCard,
        title = {
            Text(
                text = "Коммит · $dirtyFiles файл(ов)",
                style = MaterialTheme.typography.titleMedium,
                color = DeskText
            )
        },
        text = {
            Column {
                // Что войдёт в коммит
                if (changes.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeskBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        items(changes) { line ->
                            val status = line.substringBefore(' ')
                            val path = line.substringAfter(' ').trim()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = status.take(2),
                                    style = MaterialTheme.typography.labelSmall
                                        .copy(fontFamily = JetMono),
                                    color = when (status.firstOrNull()) {
                                        'M' -> Sky.bar
                                        'A' -> Sage.bar
                                        'D' -> Lavender.bar
                                        '?' -> DeskMuted
                                        else -> DeskMuted
                                    },
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    text = path.substringAfterLast('/'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeskText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("Сообщение коммита") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(message.ifBlank { "wip: с планшета" }) }
            ) { Text("Коммит", color = DeskText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = DeskMuted) }
        }
    )
}
