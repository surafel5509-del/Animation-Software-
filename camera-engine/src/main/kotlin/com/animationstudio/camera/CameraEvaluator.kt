package com.animationstudio.camera

import com.animationstudio.animation.AnimationEvaluator
import com.animationstudio.core.model.AnimatableProperty
import com.animationstudio.core.model.CameraSettings
import com.animationstudio.core.model.Scene
import com.animationstudio.core.model.Transform

object CameraEvaluator {
    fun evaluate(scene: Scene, frame: Int): CameraSettings? {
        val camera = scene.cameras.firstOrNull { it.id == scene.activeCameraId } ?: scene.cameras.firstOrNull() ?: return null
        val transform = AnimationEvaluator.transform(scene, camera.id, camera.transform, frame)
        val zoom = scene.tracks.firstOrNull { it.targetId == camera.id && it.property == AnimatableProperty.CAMERA_ZOOM }
            ?.let { AnimationEvaluator.sample(it, frame, camera.zoom) } ?: camera.zoom
        return camera.copy(transform = transform, zoom = zoom.coerceAtLeast(.01f))
    }

    fun worldToScreen(point: com.animationstudio.core.model.Vec2, camera: CameraSettings): com.animationstudio.core.model.Vec2 =
        camera.transform.apply(point * camera.zoom)
}
