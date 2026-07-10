package com.nofs.desk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.GitCommitEntry
import com.nofs.desk.ui.theme.Lavender
import com.nofs.desk.ui.theme.Peach
import com.nofs.desk.ui.theme.Rose
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sand
import com.nofs.desk.ui.theme.Sky

/**
 * Граф коммитов: классическая раскладка по дорожкам (lanes).
 * Каждая дорожка «ждёт» определённый хэш; коммит садится в свою дорожку,
 * родители занимают дорожки ниже. Ветвления и мержи — диагонали к точке.
 */

/** Одна строка графа: где точка и какие линии рисовать. */
data class GraphRow(
    val entry: GitCommitEntry,
    val dotLane: Int,
    /** Дорожки, проходящие строку насквозь (вертикали). */
    val through: List<Int>,
    /** Дорожки, вливающиеся сверху в точку (мержи/продолжение). */
    val intoDot: List<Int>,
    /** Дорожки, выходящие из точки вниз (родители). */
    val outOfDot: List<Int>
)

/** Число дорожек, задействованных в раскладке (для ширины канваса). */
fun List<GraphRow>.laneCount(): Int {
    var max = 0
    for (r in this) {
        max = maxOf(max, r.dotLane, r.through.maxOrNull() ?: 0,
            r.intoDot.maxOrNull() ?: 0, r.outOfDot.maxOrNull() ?: 0)
    }
    return max + 1
}

fun buildGitGraph(log: List<GitCommitEntry>): List<GraphRow> {
    val lanes = mutableListOf<String?>()   // какой хэш «ждёт» каждая дорожка
    val rows = mutableListOf<GraphRow>()

    for (commit in log) {
        // Дорожки, ждущие этот коммит, вливаются в точку
        val intoDot = lanes.indices.filter { lanes[it] == commit.hash }

        val dotLane = intoDot.minOrNull() ?: run {
            val free = lanes.indexOfFirst { it == null }
            if (free >= 0) free else { lanes.add(null); lanes.size - 1 }
        }
        intoDot.forEach { lanes[it] = null }

        // Сквозные вертикали — дорожки, занятые ДРУГИМИ ветками
        val through = lanes.indices.filter { it != dotLane && lanes[it] != null }

        // Родители занимают дорожки вниз
        val outOfDot = mutableListOf<Int>()
        commit.parents.forEachIndexed { pi, parent ->
            val existing = lanes.indexOfFirst { it == parent }
            when {
                pi == 0 && existing < 0 -> {
                    lanes[dotLane] = parent
                    outOfDot += dotLane
                }
                existing >= 0 -> outOfDot += existing  // диагональ к уже занятой дорожке
                else -> {
                    val free = lanes.indexOfFirst { it == null }
                    val lane = if (free >= 0) free else { lanes.add(null); lanes.size - 1 }
                    lanes[lane] = parent
                    outOfDot += lane
                }
            }
        }

        rows += GraphRow(commit, dotLane, through, intoDot, outOfDot)
    }
    return rows
}

private val LaneColors: List<Color> =
    listOf(Sage.bar, Sky.bar, Peach.bar, Lavender.bar, Rose.bar, Sand.bar)

fun laneColor(lane: Int): Color = LaneColors[lane % LaneColors.size]

/** Отрисовка одной строки графа. Высота — от родительского Row (fillMaxHeight). */
@Composable
fun GitGraphCell(row: GraphRow, laneCount: Int, maxLanes: Int = 5) {
    val shown = laneCount.coerceAtMost(maxLanes)
    val laneWidth = 12.dp

    Canvas(
        modifier = Modifier
            .width(laneWidth * shown)
            .fillMaxHeight()
    ) {
        val lw = laneWidth.toPx()
        fun x(lane: Int) = (lane.coerceAtMost(maxLanes - 1) + 0.5f) * lw
        val cy = size.height / 2f
        val stroke = 2.dp.toPx()

        // Сквозные вертикали
        for (lane in row.through) {
            drawLine(
                color = laneColor(lane),
                start = androidx.compose.ui.geometry.Offset(x(lane), 0f),
                end = androidx.compose.ui.geometry.Offset(x(lane), size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        // Сверху в точку
        for (lane in row.intoDot) {
            drawLine(
                color = laneColor(lane),
                start = androidx.compose.ui.geometry.Offset(x(lane), 0f),
                end = androidx.compose.ui.geometry.Offset(x(row.dotLane), cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        // Из точки вниз к родителям
        for (lane in row.outOfDot) {
            drawLine(
                color = laneColor(lane),
                start = androidx.compose.ui.geometry.Offset(x(row.dotLane), cy),
                end = androidx.compose.ui.geometry.Offset(x(lane), size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        // Точка коммита
        drawCircle(
            color = laneColor(row.dotLane),
            radius = 3.5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(x(row.dotLane), cy)
        )
    }
}
