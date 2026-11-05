package com.animationstudio.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.animationstudio.core.model.ToolType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeftToolbox(selected: ToolType, onSelect: (ToolType) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(54.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        ToolType.entries.forEach { tool ->
            TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(tool.title()) } }, state = rememberTooltipState()) {
                IconButton(onClick = { onSelect(tool) }, modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (selected == tool) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected == tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(tool.icon(), tool.title(), modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

fun ToolType.title(): String = when (this) {
    ToolType.SELECTION -> "Selection"; ToolType.DIRECT_SELECTION -> "Direct selection / nodes"; ToolType.MOVE -> "Move"
    ToolType.TRANSFORM -> "Transform"; ToolType.ROTATE -> "Rotate"; ToolType.SCALE -> "Scale"
    ToolType.BRUSH -> "Brush"; ToolType.PENCIL -> "Pencil"; ToolType.PEN -> "Pen path"; ToolType.BEZIER -> "Bézier pen"
    ToolType.ERASER -> "Eraser"; ToolType.FILL -> "Fill"; ToolType.GRADIENT -> "Gradient"; ToolType.EYEDROPPER -> "Eyedropper"
    ToolType.RECTANGLE -> "Rectangle"; ToolType.ELLIPSE -> "Ellipse"; ToolType.POLYGON -> "Polygon"; ToolType.STAR -> "Star"
    ToolType.TEXT -> "Text"; ToolType.SHAPE_EDITOR -> "Shape editor"; ToolType.BONE -> "Bone"; ToolType.CAMERA -> "Camera"
    ToolType.ZOOM -> "Zoom"; ToolType.HAND -> "Pan"
}

private fun ToolType.icon(): ImageVector = when (this) {
    ToolType.SELECTION -> Icons.Default.NearMe; ToolType.DIRECT_SELECTION -> Icons.Default.Adjust; ToolType.MOVE -> Icons.Default.OpenWith
    ToolType.TRANSFORM -> Icons.Default.Transform; ToolType.ROTATE -> Icons.Default.RotateRight; ToolType.SCALE -> Icons.Default.AspectRatio
    ToolType.BRUSH -> Icons.Default.Brush; ToolType.PENCIL -> Icons.Default.Edit; ToolType.PEN -> Icons.Default.Create; ToolType.BEZIER -> Icons.Default.Gesture
    ToolType.ERASER -> Icons.Default.AutoFixHigh; ToolType.FILL -> Icons.Default.FormatColorFill; ToolType.GRADIENT -> Icons.Default.Gradient
    ToolType.EYEDROPPER -> Icons.Default.Colorize; ToolType.RECTANGLE -> Icons.Default.CropSquare; ToolType.ELLIPSE -> Icons.Default.Circle
    ToolType.POLYGON -> Icons.Default.Hexagon; ToolType.STAR -> Icons.Default.Star; ToolType.TEXT -> Icons.Default.TextFields
    ToolType.SHAPE_EDITOR -> Icons.Default.Architecture; ToolType.BONE -> Icons.Default.AccountTree; ToolType.CAMERA -> Icons.Default.CameraAlt
    ToolType.ZOOM -> Icons.Default.ZoomIn; ToolType.HAND -> Icons.Default.PanTool
}
