package com.animationstudio.app.ui.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.StrokeCap as ComposeCap
import androidx.compose.ui.graphics.StrokeJoin as ComposeJoin
import com.animationstudio.animation.AnimationEvaluator
import com.animationstudio.core.model.*
import com.animationstudio.core.model.RectF
import com.animationstudio.vector.VectorGeometry

fun DrawScope.drawVectorObject(
    obj: VectorObject,
    scene: Scene,
    frame: Int,
    images: Map<String, ImageBitmap>,
    tint: Color? = null,
    alphaMultiplier: Float = 1f,
) {
    val transform = AnimationEvaluator.transform(scene, obj.id, obj.transform, frame)
    withTransform({
        translate(transform.position.x, transform.position.y)
        rotate(Math.toDegrees(transform.rotation.toDouble()).toFloat(), Offset.Zero)
        scale(transform.scale.x, transform.scale.y, Offset.Zero)
    }) {
        val alpha = transform.opacity.coerceIn(0f, 1f) * alphaMultiplier
        when (obj) {
            is VectorObject.PathObject -> drawPathObject(obj, tint, alpha)
            is VectorObject.ShapeObject -> drawShapeObject(obj, tint, alpha)
            is VectorObject.TextObject -> drawTextObject(obj, scene, frame, tint, alpha)
            is VectorObject.ImageObject -> images[obj.assetId]?.let { image ->
                drawImage(image, dstOffset = androidx.compose.ui.unit.IntOffset(obj.bounds.left.toInt(), obj.bounds.top.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(obj.bounds.width.toInt(), obj.bounds.height.toInt()), alpha = alpha)
            }
        }
    }
}

private fun DrawScope.drawPathObject(obj: VectorObject.PathObject, tint: Color?, alpha: Float) {
    if (obj.points.isEmpty()) return
    val path = obj.points.toComposePath(obj.closed)
    obj.fill?.let { fill ->
        when (fill) {
            is FillStyle.Solid -> drawPath(path, tint ?: Color(fill.color), alpha = alpha)
            else -> fill.toBrush()?.let { brush -> drawPath(path, brush, alpha = alpha) }
        }
    }
    val stroke = obj.stroke ?: return
    val color = tint ?: Color(stroke.color)
    val strokeAlpha = alpha * stroke.opacity
    val variablePressure = obj.points.any { it.pressure != 1f } && obj.points.all { it.controlIn == null && it.controlOut == null }
    if (variablePressure && obj.points.size > 1) {
        obj.points.zipWithNext().forEach { (a, b) ->
            drawLine(color, a.position.toOffset(), b.position.toOffset(),
                strokeWidth = stroke.width * ((a.pressure + b.pressure) / 2f).coerceIn(.05f, 1f),
                cap = stroke.cap.toComposeCap(), alpha = strokeAlpha)
        }
    } else drawPath(path, color, alpha = strokeAlpha,
        style = Stroke(stroke.width, cap = stroke.cap.toComposeCap(), join = stroke.join.toComposeJoin(),
            pathEffect = if (stroke.dashPattern.isEmpty()) null else PathEffect.dashPathEffect(stroke.dashPattern.toFloatArray())))
}

private fun DrawScope.drawShapeObject(obj: VectorObject.ShapeObject, tint: Color?, alpha: Float) {
    val path = Path()
    when (obj.shape) {
        ShapeType.RECTANGLE -> path.addRoundRect(androidx.compose.ui.geometry.RoundRect(obj.bounds.toComposeRect(), obj.cornerRadius, obj.cornerRadius))
        ShapeType.ELLIPSE -> path.addOval(obj.bounds.toComposeRect())
        ShapeType.POLYGON, ShapeType.STAR -> {
            val points = VectorGeometry.regularPolygon(obj.bounds, obj.vertices, if (obj.shape == ShapeType.STAR) obj.innerRadiusRatio else null)
            points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            path.close()
        }
    }
    obj.fill?.let { fill ->
        when (fill) {
            is FillStyle.Solid -> drawPath(path, tint ?: Color(fill.color), alpha = alpha)
            else -> fill.toBrush()?.let { drawPath(path, it, alpha = alpha) }
        }
    }
    obj.stroke?.let { stroke ->
        drawPath(path, tint ?: Color(stroke.color), alpha = alpha * stroke.opacity,
            style = Stroke(stroke.width, cap = stroke.cap.toComposeCap(), join = stroke.join.toComposeJoin()))
    }
}

private fun DrawScope.drawTextObject(obj: VectorObject.TextObject, scene: Scene, frame: Int, tint: Color?, alpha: Float) {
    val color = scene.tracks.firstOrNull { it.targetId == obj.id && it.property == AnimatableProperty.COLOR }
        ?.let { AnimationEvaluator.sampleColor(it, frame, obj.color) } ?: obj.color
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = tint?.toArgb() ?: color
        this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        textSize = obj.size
        typeface = android.graphics.Typeface.create(obj.fontFamily, if (obj.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawText(obj.text, obj.position.x, obj.position.y, paint)
}

private fun FillStyle.toBrush(): Brush? = when (this) {
    is FillStyle.Solid -> null
    is FillStyle.LinearGradient -> if (colors.size >= 2) Brush.linearGradient(
        colors.map(::Color), start.toOffset(), end.toOffset()) else null
    is FillStyle.RadialGradient -> if (colors.size >= 2) Brush.radialGradient(
        colors.map(::Color), center.toOffset(), radius.coerceAtLeast(1f)) else null
}

fun List<PathPoint>.toComposePath(closed: Boolean = false): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(first().position.x, first().position.y)
    val count = if (closed) size else size - 1
    for (index in 0 until count) {
        val start = this[index]; val end = this[(index + 1) % size]
        if (start.controlOut != null || end.controlIn != null) {
            val c1 = start.controlOut ?: start.position; val c2 = end.controlIn ?: end.position
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.position.x, end.position.y)
        } else path.lineTo(end.position.x, end.position.y)
    }
    if (closed) path.close()
    return path
}

fun Vec2.toOffset() = Offset(x, y)
fun RectF.toComposeRect() = Rect(left, top, right, bottom)
fun StrokeCap.toComposeCap() = when (this) { StrokeCap.BUTT -> ComposeCap.Butt; StrokeCap.ROUND -> ComposeCap.Round; StrokeCap.SQUARE -> ComposeCap.Square }
fun StrokeJoin.toComposeJoin() = when (this) { StrokeJoin.MITER -> ComposeJoin.Miter; StrokeJoin.ROUND -> ComposeJoin.Round; StrokeJoin.BEVEL -> ComposeJoin.Bevel }
