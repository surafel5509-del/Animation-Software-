package com.animationstudio.rendering

import android.content.Context
import android.graphics.*
import android.util.LruCache
import com.animationstudio.animation.AnimationEvaluator
import com.animationstudio.camera.CameraEvaluator
import com.animationstudio.core.model.*
import com.animationstudio.effects.BitmapEffects
import com.animationstudio.projectmanager.AssetStorage
import com.animationstudio.vector.VectorGeometry

/** Deterministic Canvas renderer shared by stills and all video/GIF exporters. */
class SoftwareFrameRenderer(context: Context, private val project: StudioProject) {
    private val assets = AssetStorage(context)
    private val imageCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt().coerceAtLeast(4096)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    @Synchronized
    fun render(
        scene: Scene = project.activeScene,
        frame: Int,
        width: Int = project.canvas.width,
        height: Int = project.canvas.height,
        applyEffects: Boolean = true,
    ): Bitmap {
        require(width > 0 && height > 0)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        if (!project.canvas.transparent) canvas.drawColor(project.canvas.backgroundColor)
        val scaleX = width.toFloat() / project.canvas.width
        val scaleY = height.toFloat() / project.canvas.height
        canvas.scale(scaleX, scaleY)
        CameraEvaluator.evaluate(scene, frame)?.let { camera ->
            canvas.translate(project.canvas.width / 2f, project.canvas.height / 2f)
            canvas.scale(camera.zoom, camera.zoom)
            canvas.rotate(-Math.toDegrees(camera.transform.rotation.toDouble()).toFloat())
            canvas.translate(-camera.transform.position.x, -camera.transform.position.y)
            canvas.translate(-project.canvas.width / 2f, -project.canvas.height / 2f)
        }
        val hiddenGroups = scene.layers.filter { !it.visible }.flatMap { layer ->
            scene.layers.filter { it.parentId == layer.id }.map(Layer::id)
        }.toSet()
        scene.layers.filter { it.visible && it.id !in hiddenGroups }.forEach { layer ->
            val cel = layer.celAt(frame) ?: return@forEach
            val layerPaint = Paint().apply {
                alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                xfermode = layer.blendMode.toXfermode()
            }
            if (applyEffects && layer.effects.any(EffectSettings::enabled)) {
                val layerBitmap = Bitmap.createBitmap(project.canvas.width, project.canvas.height, Bitmap.Config.ARGB_8888)
                val layerCanvas = Canvas(layerBitmap)
                cel.objects.filter(VectorObject::visible).forEach { obj -> drawObject(layerCanvas, scene, obj, frame) }
                val processed = BitmapEffects.apply(layerBitmap, layer.effects)
                canvas.drawBitmap(processed, 0f, 0f, layerPaint)
            } else {
                val save = canvas.saveLayer(0f, 0f, project.canvas.width.toFloat(), project.canvas.height.toFloat(), layerPaint)
                cel.objects.filter(VectorObject::visible).forEach { obj -> drawObject(canvas, scene, obj, frame) }
                canvas.restoreToCount(save)
            }
        }
        return output
    }

    private fun drawObject(canvas: Canvas, scene: Scene, obj: VectorObject, frame: Int) {
        val evaluated = AnimationEvaluator.transform(scene, obj.id, obj.transform, frame)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (evaluated.opacity * 255).toInt().coerceIn(0, 255) }
        val baseColor = VectorGeometry.colorOf(obj)
        val objectColor = scene.tracks.firstOrNull { it.targetId == obj.id && it.property == AnimatableProperty.COLOR }
            ?.let { track -> baseColor?.let { color -> AnimationEvaluator.sampleColor(track, frame, color) } }
        val save = canvas.save()
        canvas.translate(evaluated.position.x, evaluated.position.y)
        canvas.rotate(Math.toDegrees(evaluated.rotation.toDouble()).toFloat())
        canvas.scale(evaluated.scale.x, evaluated.scale.y)
        when (obj) {
            is VectorObject.PathObject -> drawPath(canvas, obj, paint, objectColor)
            is VectorObject.ShapeObject -> drawShape(canvas, obj, paint, objectColor)
            is VectorObject.TextObject -> {
                paint.color = animatedColor(scene, obj, frame)
                paint.textSize = obj.size
                paint.typeface = Typeface.create(obj.fontFamily, if (obj.bold) Typeface.BOLD else Typeface.NORMAL)
                canvas.drawText(obj.text, obj.position.x, obj.position.y, paint)
            }
            is VectorObject.ImageObject -> {
                val asset = project.assets.firstOrNull { it.id == obj.assetId }
                val bitmap = asset?.let(::loadBitmap)
                if (bitmap != null) canvas.drawBitmap(bitmap, null, obj.bounds.toAndroid(), paint)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun animatedColor(scene: Scene, obj: VectorObject.TextObject, frame: Int): Int = scene.tracks
        .firstOrNull { it.targetId == obj.id && it.property == AnimatableProperty.COLOR }
        ?.let { AnimationEvaluator.sampleColor(it, frame, obj.color) } ?: obj.color

    private fun drawPath(canvas: Canvas, obj: VectorObject.PathObject, basePaint: Paint, objectColor: Int?) {
        if (obj.points.isEmpty()) return
        val path = obj.points.toAndroidPath(obj.closed)
        obj.fill?.let { fill ->
            val paint = Paint(basePaint).apply { style = Paint.Style.FILL; shader = fill.toShader(obj) }
            if (fill is FillStyle.Solid) paint.color = objectColor ?: fill.color
            canvas.drawPath(path, paint)
        }
        val stroke = obj.stroke ?: return
        val paint = Paint(basePaint).apply {
            style = Paint.Style.STROKE; color = objectColor ?: stroke.color; strokeWidth = stroke.width
            strokeCap = when (stroke.cap) { StrokeCap.BUTT -> Paint.Cap.BUTT; StrokeCap.ROUND -> Paint.Cap.ROUND; StrokeCap.SQUARE -> Paint.Cap.SQUARE }
            strokeJoin = when (stroke.join) { StrokeJoin.MITER -> Paint.Join.MITER; StrokeJoin.ROUND -> Paint.Join.ROUND; StrokeJoin.BEVEL -> Paint.Join.BEVEL }
            if (stroke.dashPattern.isNotEmpty()) pathEffect = DashPathEffect(stroke.dashPattern.toFloatArray(), 0f)
            alpha = (alpha * stroke.opacity).toInt().coerceIn(0, 255)
            if (obj.brush == BrushKind.SOFT || obj.brush == BrushKind.AIRBRUSH)
                maskFilter = BlurMaskFilter((stroke.width / 3f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        val variablePressure = obj.points.any { it.pressure != 1f } && obj.points.all { it.controlIn == null && it.controlOut == null }
        if (variablePressure && obj.points.size > 1) {
            obj.points.zipWithNext().forEach { (a, b) ->
                paint.strokeWidth = stroke.width * ((a.pressure + b.pressure) / 2f).coerceIn(.05f, 1f)
                canvas.drawLine(a.position.x, a.position.y, b.position.x, b.position.y, paint)
            }
        } else canvas.drawPath(path, paint)
    }

    private fun drawShape(canvas: Canvas, obj: VectorObject.ShapeObject, basePaint: Paint, objectColor: Int?) {
        val path = Path()
        when (obj.shape) {
            ShapeType.RECTANGLE -> path.addRoundRect(obj.bounds.toAndroid(), obj.cornerRadius, obj.cornerRadius, Path.Direction.CW)
            ShapeType.ELLIPSE -> path.addOval(obj.bounds.toAndroid(), Path.Direction.CW)
            ShapeType.POLYGON, ShapeType.STAR -> {
                val points = VectorGeometry.regularPolygon(obj.bounds, obj.vertices, if (obj.shape == ShapeType.STAR) obj.innerRadiusRatio else null)
                points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
                path.close()
            }
        }
        obj.fill?.let { fill ->
            val paint = Paint(basePaint).apply { style = Paint.Style.FILL; shader = fill.toShader(obj) }
            if (fill is FillStyle.Solid) paint.color = objectColor ?: fill.color
            canvas.drawPath(path, paint)
        }
        obj.stroke?.let { stroke ->
            val paint = Paint(basePaint).apply {
                style = Paint.Style.STROKE; color = objectColor ?: stroke.color; strokeWidth = stroke.width
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                if (stroke.dashPattern.isNotEmpty()) pathEffect = DashPathEffect(stroke.dashPattern.toFloatArray(), 0f)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun FillStyle.toShader(obj: VectorObject): Shader? = when (this) {
        is FillStyle.Solid -> null
        is FillStyle.LinearGradient -> if (colors.size >= 2) LinearGradient(start.x, start.y, end.x, end.y,
            colors.toIntArray(), stops.take(colors.size).toFloatArray(), Shader.TileMode.CLAMP) else null
        is FillStyle.RadialGradient -> if (colors.size >= 2) RadialGradient(center.x, center.y, radius.coerceAtLeast(1f),
            colors.toIntArray(), stops.take(colors.size).toFloatArray(), Shader.TileMode.CLAMP) else null
    }

    private fun loadBitmap(asset: AssetMetadata): Bitmap? {
        imageCache.get(asset.id)?.let { return it }
        val file = assets.file(project.id, asset.entryName)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        imageCache.put(asset.id, bitmap)
        return bitmap
    }

    private fun BlendMode.toXfermode(): PorterDuffXfermode? = when (this) {
        BlendMode.NORMAL -> null
        BlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        BlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        BlendMode.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        BlendMode.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        BlendMode.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        BlendMode.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    companion object {
        fun List<PathPoint>.toAndroidPath(closed: Boolean = false): Path {
            val path = Path()
            if (isEmpty()) return path
            path.moveTo(first().position.x, first().position.y)
            val count = if (closed) size else size - 1
            for (index in 0 until count) {
                val start = this[index]
                val end = this[(index + 1) % size]
                if (start.controlOut != null || end.controlIn != null) {
                    val c1 = start.controlOut ?: start.position
                    val c2 = end.controlIn ?: end.position
                    path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.position.x, end.position.y)
                } else path.lineTo(end.position.x, end.position.y)
            }
            if (closed) path.close()
            return path
        }

        fun RectF.toAndroid() = android.graphics.RectF(left, top, right, bottom)
    }
}
