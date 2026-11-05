package com.animationstudio.rigging

import com.animationstudio.core.model.*
import com.animationstudio.graphics.Matrix3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun sqrtOf(value: Float) = sqrt(value)

data class BonePose(val bone: Bone, val matrix: Matrix3, val start: Vec2, val end: Vec2)

object SkeletonSolver {
    fun forwardKinematics(rig: CharacterRig): Map<String, BonePose> {
        val result = linkedMapOf<String, BonePose>()
        val byParent = rig.bones.groupBy(Bone::parentId)
        fun visit(bone: Bone) {
            val parent = bone.parentId?.let(result::get)
            val localTranslation = if (parent == null) Matrix3.translation(bone.start) else Matrix3.translation(Vec2(parent.bone.length, 0f))
            val rotation = bone.rotation.coerceIn(bone.minRotation, bone.maxRotation)
            var matrix = (parent?.matrix ?: Matrix3()) * localTranslation * Matrix3.rotation(rotation) * Matrix3.scale(Vec2(bone.scale, bone.scale))
            if (parent != null && (!bone.inheritRotation || !bone.inheritScale)) {
                val inheritedAngle = if (bone.inheritRotation) atan2(parent.matrix.d, parent.matrix.a) else 0f
                val inheritedScale = if (bone.inheritScale) sqrtOf(parent.matrix.a * parent.matrix.a + parent.matrix.d * parent.matrix.d) else 1f
                matrix = Matrix3.translation(parent.end) * Matrix3.rotation(inheritedAngle + rotation) *
                    Matrix3.scale(Vec2(inheritedScale * bone.scale, inheritedScale * bone.scale))
            }
            val start = matrix * Vec2.ZERO
            val end = matrix * Vec2(bone.length, 0f)
            result[bone.id] = BonePose(bone.copy(rotation = rotation), matrix, start, end)
            byParent[bone.id].orEmpty().forEach(::visit)
        }
        byParent[null].orEmpty().forEach(::visit)
        // Disconnected/cyclic bones are still represented without recursing forever.
        rig.bones.filterNot { result.containsKey(it.id) }.forEach { bone ->
            val start = bone.start
            result[bone.id] = BonePose(bone, Matrix3.translation(start) * Matrix3.rotation(bone.rotation), start,
                start + Vec2(cos(bone.rotation) * bone.length, sin(bone.rotation) * bone.length))
        }
        return result
    }

    /** Cyclic Coordinate Descent IK. Returns a new immutable rig and never mutates the source. */
    fun solveIk(rig: CharacterRig, endBoneId: String, target: Vec2, iterations: Int = 20, tolerance: Float = .5f): CharacterRig {
        val bones = rig.bones.toMutableList()
        val index = bones.indexOfFirst { it.id == endBoneId }
        if (index < 0) return rig
        val chain = mutableListOf<Bone>()
        var current: Bone? = bones[index]
        while (current != null) {
            chain += current
            current = bones.firstOrNull { it.id == current?.parentId }
        }
        if (chain.isEmpty()) return rig
        repeat(iterations) {
            if (distance(forwardKinematics(rig.copy(bones = bones))[endBoneId]?.end ?: return rig, target) <= tolerance) return@repeat
            for (joint in chain) {
                if (joint.pinned) continue
                val poses = forwardKinematics(rig.copy(bones = bones))
                val jointPose = poses[joint.id] ?: continue
                val end = poses[endBoneId]?.end ?: continue
                val toEnd = end - jointPose.start
                val toTarget = target - jointPose.start
                val delta = atan2(toTarget.y, toTarget.x) - atan2(toEnd.y, toEnd.x)
                val normalized = atan2(sin(delta), cos(delta))
                val jointIndex = bones.indexOfFirst { it.id == joint.id }
                val old = bones[jointIndex]
                bones[jointIndex] = old.copy(rotation = (old.rotation + normalized).coerceIn(old.minRotation, old.maxRotation))
            }
        }
        return rig.copy(bones = bones)
    }

    fun deform(points: List<Vec2>, weights: List<SkinWeight>, original: CharacterRig, posed: CharacterRig): List<Vec2> {
        val rest = forwardKinematics(original)
        val current = forwardKinematics(posed)
        return points.indices.map { index ->
            val influences = weights.filter { it.vertexIndex == index && it.weight > 0f }
            val total = influences.sumOf { it.weight.toDouble() }.toFloat()
            if (total <= 0f) points[index] else influences.fold(Vec2.ZERO) { acc, weight ->
                val restPose = rest[weight.boneId]
                val currentPose = current[weight.boneId]
                if (restPose == null || currentPose == null) acc else {
                    val local = restPose.matrix.inverse() * points[index]
                    acc + (currentPose.matrix * local) * (weight.weight / total)
                }
            }
        })
    }
}

// Kept private to avoid a dependency from the rigging module to the vector module.
private fun distance(a: Vec2, b: Vec2) = kotlin.math.sqrt((a - b).lengthSquared())
