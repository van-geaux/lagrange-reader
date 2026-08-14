package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationsScreenTest {

    private fun activeAnnotation(id: String = "a-1") = BookAnnotation(
        id = id,
        bookId = "book-1",
        text = "Some highlighted text",
        deletedAt = null
    )

    private fun trashedAnnotation(id: String = "a-2") = BookAnnotation(
        id = id,
        bookId = "book-1",
        text = "Some highlighted text",
        deletedAt = "2026-08-01T00:00:00Z"
    )

    @Test
    fun `active annotations offer edit, style, copy and trash`() {
        val actions = annotationMenuActions(activeAnnotation())

        assertEquals(
            listOf(
                AnnotationMenuAction.OPEN_BOOK_DETAILS,
                AnnotationMenuAction.GO_TO_ANNOTATION,
                AnnotationMenuAction.EDIT_NOTE,
                AnnotationMenuAction.CHANGE_STYLE,
                AnnotationMenuAction.COPY_TEXT,
                AnnotationMenuAction.TRASH
            ),
            actions
        )
    }

    @Test
    fun `trashed annotations offer only restore and purge`() {
        val actions = annotationMenuActions(trashedAnnotation())

        assertEquals(listOf(AnnotationMenuAction.RESTORE, AnnotationMenuAction.PURGE), actions)
    }

    @Test
    fun `blank deletedAt is treated as active`() {
        val annotation = activeAnnotation().copy(deletedAt = "")
        assertEquals(annotationMenuActions(activeAnnotation()), annotationMenuActions(annotation))
    }

    @Test
    fun `every menu action has a non-blank label`() {
        AnnotationMenuAction.entries.forEach { action ->
            assertTrue(annotationMenuActionLabel(action).isNotBlank())
        }
    }

    @Test
    fun `overflow tap and long-press open the identical menu state for the identical annotation`() {
        val opener = AnnotationMenuOpener()
        val annotation = activeAnnotation()

        assertNull(opener.expandedAnnotationId)
        assertFalse(opener.isExpandedFor(annotation.id))

        opener.openFromOverflow(annotation.id)
        val stateAfterOverflow = opener.expandedAnnotationId
        assertTrue(opener.isExpandedFor(annotation.id))

        opener.dismiss()
        assertNull(opener.expandedAnnotationId)

        opener.openFromLongPress(annotation.id)
        val stateAfterLongPress = opener.expandedAnnotationId

        // Both entry points must produce the same resulting state (same annotation expanded),
        // and therefore the same action set from annotationMenuActions for that annotation.
        assertEquals(stateAfterOverflow, stateAfterLongPress)
        assertTrue(opener.isExpandedFor(annotation.id))
    }

    @Test
    fun `opening for one annotation closes the menu for another`() {
        val opener = AnnotationMenuOpener()
        opener.openFromOverflow("a-1")
        opener.openFromLongPress("a-2")

        assertFalse(opener.isExpandedFor("a-1"))
        assertTrue(opener.isExpandedFor("a-2"))
    }
}
