package com.hearopilot.app.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized icon definitions using Material Icons Outlined.
 * Modern outlined style for a fresh, clean UI look.
 */
object AppIcons {
    // Navigation & Actions
    val Add: ImageVector get() = Icons.Outlined.Add
    val Back: ImageVector get() = Icons.Outlined.ArrowBack
    val Search: ImageVector get() = Icons.Outlined.Search
    val Close: ImageVector get() = Icons.Outlined.Close
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val Delete: ImageVector get() = Icons.Outlined.Delete
    val Share: ImageVector get() = Icons.Outlined.Share
    val Download: ImageVector get() = Icons.Outlined.Download
    val Upload: ImageVector get() = Icons.Outlined.Upload

    // Recording & Audio
    val Mic: ImageVector get() = Icons.Outlined.Mic
    val MicOff: ImageVector get() = Icons.Outlined.MicOff
    val Record: ImageVector get() = Icons.Filled.FiberManualRecord  // Keep filled for visibility
    val Stop: ImageVector get() = Icons.Outlined.Stop
    val Pause: ImageVector get() = Icons.Outlined.Pause
    val Play: ImageVector get() = Icons.Outlined.PlayArrow

    // AI & Features
    val AutoAwesome: ImageVector get() = Icons.Outlined.AutoAwesome
    val Lightbulb: ImageVector get() = Icons.Outlined.Lightbulb
    val Psychology: ImageVector get() = Icons.Outlined.Psychology
    val Lock: ImageVector get() = Icons.Outlined.Lock
    val LockOpen: ImageVector get() = Icons.Outlined.LockOpen

    // Content
    val Description: ImageVector get() = Icons.Outlined.Description
    val CheckCircle: ImageVector get() = Icons.Outlined.CheckCircle
    val Error: ImageVector get() = Icons.Outlined.Error
    val Info: ImageVector get() = Icons.Outlined.Info
    val Warning: ImageVector get() = Icons.Outlined.Warning

    // Expand/Collapse
    val ExpandMore: ImageVector get() = Icons.Outlined.ExpandMore
    val ExpandLess: ImageVector get() = Icons.Outlined.ExpandLess
    val ChevronRight: ImageVector get() = Icons.Outlined.ChevronRight
    val ChevronLeft: ImageVector get() = Icons.Outlined.ChevronLeft

    // Download & Cloud
    val CloudDownload: ImageVector get() = Icons.Outlined.CloudDownload
    val CloudUpload: ImageVector get() = Icons.Outlined.CloudUpload
    val Refresh: ImageVector get() = Icons.Outlined.Refresh

    // Navigation tabs
    val Home: ImageVector get() = Icons.Outlined.Home
    val List: ImageVector get() = Icons.Outlined.List

    // Speed & Performance
    val Speed: ImageVector get() = Icons.Outlined.Speed
    val OfflineBolt: ImageVector get() = Icons.Outlined.OfflineBolt

    // Language / Translation
    val Translate: ImageVector get() = Icons.Outlined.Translate

    // Time / Interval
    val Schedule: ImageVector get() = Icons.Outlined.Schedule

    // Edit / Rename
    val Edit: ImageVector get() = Icons.Outlined.Edit

    // External links
    val OpenInNew: ImageVector get() = Icons.Outlined.OpenInNew

    // Recording mode identities
    val ModeSimpleListening: ImageVector get() = Icons.Outlined.Mic
    val ModeShortMeeting: ImageVector get() = Icons.Outlined.Description
    val ModeLongMeeting: ImageVector get() = Icons.Outlined.Article
    val ModeTranslation: ImageVector get() = Icons.Outlined.Translate

    // Insight strategy
    val Summarize: ImageVector get() = Icons.Outlined.Summarize
}
