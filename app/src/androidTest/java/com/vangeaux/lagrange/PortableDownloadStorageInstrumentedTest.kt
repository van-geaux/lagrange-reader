package com.vangeaux.lagrange

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableDownloadStorageInstrumentedTest {
    private val authority = "com.android.externalstorage.documents"

    @Test
    fun managedDocumentUnderPersistedTreeContainsOnlyItsDescendants() {
        val selectedTree = Uri.parse(
            "content://$authority/tree/1234-5678%3ABooks"
        )
        val managedDocument = Uri.parse(
            "content://$authority/tree/1234-5678%3ABooks/document/" +
                "1234-5678%3ABooks%2FLagrange%20Reader"
        )
        val child = Uri.parse(
            "content://$authority/tree/1234-5678%3ABooks/document/" +
                "1234-5678%3ABooks%2FLagrange%20Reader%2Faccount-abc%2Fbook.epub"
        )
        val siblingPrefix = Uri.parse(
            "content://$authority/tree/1234-5678%3ABooks/document/" +
                "1234-5678%3ABooks%2FLagrange%20Reader-old%2Fbook.epub"
        )

        assertEquals("1234-5678", documentVolumeId(selectedTree))
        assertEquals(selectedTree, persistedTreeUriForDocument(managedDocument))
        assertTrue(documentBelongsToRoot(managedDocument, child))
        assertFalse(documentBelongsToRoot(managedDocument, siblingPrefix))
    }

    @Test
    fun authorityMismatchNeverPassesContainment() {
        val root = Uri.parse("content://$authority/tree/1234-5678%3ABooks")
        val foreign = Uri.parse(
            "content://other.documents/document/1234-5678%3ABooks%2Fbook.epub"
        )

        assertFalse(documentBelongsToRoot(root, foreign))
    }
}
