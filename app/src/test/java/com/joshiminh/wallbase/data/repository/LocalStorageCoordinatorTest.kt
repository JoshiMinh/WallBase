package com.joshiminh.wallbase.data.repository

import android.content.ContentResolver
import android.content.ContextWrapper
import android.test.mock.MockContentResolver
import android.test.mock.MockContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalStorageCoordinatorTest {

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var coordinator: LocalStorageCoordinator

    private class TestContext(
        filesDir: File,
        private val cacheDir: File,
        private val resolver: ContentResolver
    ) : ContextWrapper(MockContext()) {
        private val filesDirectory = filesDir

        override fun getFilesDir(): File = filesDirectory

        override fun getCacheDir(): File = cacheDir

        override fun getContentResolver(): ContentResolver = resolver
    }

    @Before
    fun setUp() {
        filesDir = createTempDir(prefix = "localStorage-files").apply { deleteOnExit() }
        cacheDir = createTempDir(prefix = "localStorage-cache").apply { deleteOnExit() }
        val resolver = MockContentResolver()
        val context = TestContext(filesDir, cacheDir, resolver)
        coordinator = LocalStorageCoordinator(context)
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    @Test
    fun writeStream_writesInputToDestination() = runBlocking {
        val data = ByteArray(8_192) { index -> (index % 256).toByte() }

        val result = coordinator.writeStream(
            input = data.inputStream(),
            sourceFolder = "Local",
            subFolder = "Wallpapers",
            displayName = "image",
            mimeTypeHint = "image/png"
        )

        val storedFile = File(result.uri.path!!)
        assertTrue("Expected destination file to exist", storedFile.exists())
        assertEquals("Expected reported size to match file contents", storedFile.length(), result.sizeBytes)
        assertArrayEquals("Stored file contents should match source stream", data, storedFile.readBytes())
    }
}
