package com.animationstudio.core.model

import org.junit.Assert.*
import org.junit.Test

class ProjectTest {
    @Test fun factoryCreatesEditableSceneLayerCameraAndWorkArea() {
        val project = ProjectFactory.create("Test", 1280, 720, 30, 90, now = 42L)
        assertEquals("Test", project.name)
        assertEquals(1280, project.canvas.width)
        assertEquals(1, project.activeScene.layers.size)
        assertEquals(project.activeScene.activeCameraId, project.activeScene.cameras.single().id)
        assertEquals(90, project.activeScene.workArea.end)
        assertEquals(42L, project.createdAt)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidFps() {
        ProjectFactory.create("Invalid", fps = 0)
    }
}
