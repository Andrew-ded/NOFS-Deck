package com.nofs.desk.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Строковый ключ (из протокола/конфига агента) -> ImageVector.
 * Неизвестный ключ -> Bolt.
 */
fun macroIcon(key: String): ImageVector = when (key.lowercase()) {
    "screenshot", "camera" -> Icons.Rounded.PhotoCamera
    "build", "hammer" -> Icons.Rounded.Construction
    "terminal", "console" -> Icons.Rounded.Terminal
    "lock" -> Icons.Rounded.Lock
    "night", "darkmode" -> Icons.Rounded.DarkMode
    "mute", "silence" -> Icons.Rounded.VolumeOff
    "files", "explorer", "folder" -> Icons.Rounded.Folder
    "sleep" -> Icons.Rounded.Bedtime
    "power", "shutdown" -> Icons.Rounded.PowerSettingsNew
    "code", "ide" -> Icons.Rounded.Code
    "android" -> Icons.Rounded.Android
    "doc", "word" -> Icons.Rounded.Description
    "slides", "powerpoint" -> Icons.Rounded.Slideshow
    "table", "excel" -> Icons.Rounded.TableChart
    "browser", "web" -> Icons.Rounded.Language
    "music", "player" -> Icons.Rounded.MusicNote
    "chat", "messenger" -> Icons.Rounded.ChatBubbleOutline
    "game" -> Icons.Rounded.SportsEsports
    "gamepad" -> Icons.Rounded.VideogameAsset
    "app" -> Icons.Rounded.Apps
    "save" -> Icons.Rounded.Save
    "print" -> Icons.Rounded.Print
    "play", "run" -> Icons.Rounded.PlayArrow
    "debug" -> Icons.Rounded.BugReport
    "tests", "test", "check" -> Icons.Rounded.Checklist
    "bold" -> Icons.Rounded.FormatBold
    "italic" -> Icons.Rounded.FormatItalic
    "underline" -> Icons.Rounded.FormatUnderlined
    "search", "find" -> Icons.Rounded.Search
    "add", "new" -> Icons.Rounded.Add
    "filter" -> Icons.Rounded.FilterList
    "sync" -> Icons.Rounded.Sync
    "tab" -> Icons.Rounded.Tab
    "incognito" -> Icons.Rounded.VisibilityOff
    else -> Icons.Rounded.Bolt
}
