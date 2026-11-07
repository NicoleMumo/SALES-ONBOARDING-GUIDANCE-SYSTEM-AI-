package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IdPhotoFilesTest {
    @get:Rule
    val cache = TemporaryFolder()

    private fun photosIn(cacheDir: File): List<File> =
        File(cacheDir, "id_photos").listFiles()?.toList().orEmpty()

    @Test
    fun newPhotoFileIsInsideThePrivateCacheFolder() {
        val photo = newIdPhotoFile(cache.root)

        assertEquals(File(cache.root, "id_photos"), photo.parentFile)
        assertTrue(photo.name.endsWith(".jpg"))
    }

    @Test
    fun newPhotoFileDeletesAnyEarlierPhoto() {
        newIdPhotoFile(cache.root).writeText("earlier photo")

        newIdPhotoFile(cache.root)

        assertTrue(photosIn(cache.root).isEmpty())
    }

    @Test
    fun clearingDeletesEveryPhotoAndKeepsOtherCacheFiles() {
        newIdPhotoFile(cache.root).writeText("photo")
        val other = File(cache.root, "other.txt").apply { writeText("keep") }

        clearIdPhotos(cache.root)

        assertTrue(photosIn(cache.root).isEmpty())
        assertTrue(other.exists())
    }

    @Test
    fun clearingWithNoPhotoFolderDoesNothing() {
        clearIdPhotos(cache.root)

        assertFalse(File(cache.root, "id_photos").exists())
    }
}
