package com.animationstudio.rigging

import com.animationstudio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SkeletonSolverTest {
    @Test fun forwardKinematicsRespectsParentTransforms() {
        val rig = CharacterRig("r", bones = listOf(
            Bone("root", "Root", start = Vec2(10f, 20f), length = 100f, scale = 2f),
            Bone("child", "Child", parentId = "root", start = Vec2.ZERO, length = 50f),
        ))
        val poses = SkeletonSolver.forwardKinematics(rig)
        assertEquals(Vec2(210f, 20f), poses["root"]!!.end)
        assertEquals(Vec2(210f, 20f), poses["child"]!!.start)
        assertEquals(Vec2(260f, 20f), poses["child"]!!.end)
    }

    @Test fun ikMovesEndEffectorAndHonorsRotationConstraints() {
        val rig = CharacterRig("r", bones = listOf(
            Bone("upper", "Upper", start = Vec2.ZERO, length = 50f),
            Bone("lower", "Lower", parentId = "upper", start = Vec2.ZERO, length = 50f),
        ))
        val solved = SkeletonSolver.solveIk(rig, "lower", Vec2(20f, 50f), iterations = 60)
        val end = SkeletonSolver.forwardKinematics(solved)["lower"]!!.end
        assertEquals(20f, end.x, 2f)
        assertEquals(50f, end.y, 2f)
        solved.bones.forEach { assertTrue(it.rotation in it.minRotation..it.maxRotation) }
    }

    @Test fun weightedSkinningNormalizesInfluences() {
        val rest = CharacterRig("r", bones = listOf(Bone("b", "Bone", start = Vec2.ZERO, length = 100f)))
        val posed = rest.copy(bones = listOf(rest.bones[0].copy(rotation = Math.PI.toFloat() / 2f)))
        val output = SkeletonSolver.deform(listOf(Vec2(100f, 0f)),
            listOf(SkinWeight(0, "b", 2f)), rest, posed)
        assertEquals(0f, output[0].x, .001f)
        assertEquals(100f, output[0].y, .001f)
    }
}
