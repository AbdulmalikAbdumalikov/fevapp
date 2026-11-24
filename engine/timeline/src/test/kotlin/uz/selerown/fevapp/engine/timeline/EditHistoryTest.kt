package uz.selerown.fevapp.engine.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditHistoryTest {

    @Test
    fun `undo and redo walk through every edit`() {
        val history = EditHistory(sampleProject())
        assertTrue(history.execute(TrimClipCommand("a", 0, 2 * SEC)))
        assertTrue(history.execute(SplitClipCommand(3 * SEC, "b2")))
        assertTrue(history.execute(DeleteClipCommand("c")))
        assertTrue(history.execute(MoveClipCommand("b2", 0)))
        assertEquals(listOf("b2", "a", "b"), history.current.ids())

        assertTrue(history.undo()) // move
        assertEquals(listOf("a", "b", "b2"), history.current.ids())
        assertTrue(history.undo()) // delete
        assertEquals(listOf("a", "b", "b2", "c"), history.current.ids())
        assertTrue(history.undo()) // split
        assertEquals(listOf("a", "b", "c"), history.current.ids())
        assertTrue(history.undo()) // trim
        assertEquals(sampleProject(), history.current)
        assertFalse(history.undo())

        assertTrue(history.redo())
        assertTrue(history.redo())
        assertEquals(listOf("a", "b", "b2", "c"), history.current.ids())
    }

    @Test
    fun `no-op commands are not recorded`() {
        val history = EditHistory(sampleProject())
        assertFalse(history.execute(DeleteClipCommand("missing")))
        assertFalse(history.canUndo)
    }

    @Test
    fun `new edit clears redo stack`() {
        val history = EditHistory(sampleProject())
        history.execute(DeleteClipCommand("a"))
        history.undo()
        assertTrue(history.canRedo)
        history.execute(DeleteClipCommand("b"))
        assertFalse(history.canRedo)
    }

    @Test
    fun `history depth is limited`() {
        val history = EditHistory(sampleProject(), maxSteps = 2)
        history.execute(DuplicateClipCommand("a", "a1"))
        history.execute(DuplicateClipCommand("a", "a2"))
        history.execute(DuplicateClipCommand("a", "a3"))
        assertTrue(history.undo())
        assertTrue(history.undo())
        assertFalse(history.undo())
    }
}
