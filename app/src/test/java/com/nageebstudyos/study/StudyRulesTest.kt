package com.nageebstudyos.study

import com.nageebstudyos.study.domain.*
import org.junit.Assert.*
import org.junit.Test

class StudyRulesTest {
    private val tree =
        listOf(
            FolderNode("a", "s", null, "Calculus", 0),
            FolderNode("b", "s", "a", "Differentiation", 0),
            FolderNode("c", "s", "b", "Rules", 0),
            FolderNode("x", "other", null, "Other", 0),
        )

    @Test
    fun rejectsSelfMove() {
        assertFalse(StudyRules.canMove("a", "s", "a", tree))
    }

    @Test
    fun rejectsDescendantMove() {
        assertFalse(StudyRules.canMove("a", "s", "c", tree))
    }

    @Test
    fun rejectsCrossSubjectMove() {
        assertFalse(StudyRules.canMove("a", "s", "x", tree))
    }

    @Test
    fun acceptsRootMove() {
        assertTrue(StudyRules.canMove("c", "s", null, tree))
    }

    @Test
    fun acceptsValidSiblingMove() {
        assertTrue(StudyRules.canMove("c", "s", "a", tree))
    }

    @Test
    fun rejectsMissingDestination() {
        assertFalse(StudyRules.canMove("a", "s", "missing", tree))
    }

    @Test
    fun corruptedCycleTerminates() {
        val bad = listOf(FolderNode("a", "s", "b", "A", 0), FolderNode("b", "s", "a", "B", 0))
        assertFalse(StudyRules.canMove("c", "s", "a", bad))
        assertEquals(2, StudyRules.ancestors("a", bad).size)
    }

    @Test
    fun deepTreeDoesNotRecurse() {
        val nodes =
            (0..4999).map { FolderNode("$it", "s", if (it == 0) null else "${it-1}", "$it", it) }
        assertEquals(5000, StudyRules.ancestors("4999", nodes).size)
        assertFalse(StudyRules.canMove("0", "s", "4999", nodes))
    }

    @Test
    fun trimsTitle() {
        assertEquals("Mathematics", StudyRules.title("  Mathematics  "))
    }

    @Test(expected = StudyException::class)
    fun rejectsEmptyTitle() {
        StudyRules.title(" \n ")
    }

    @Test
    fun acceptsHttpsUrl() {
        assertEquals(
            "https://example.org/lesson?q=1",
            StudyRules.url("https://example.org/lesson?q=1"),
        )
    }

    @Test(expected = StudyException::class)
    fun rejectsJavascriptUrl() {
        StudyRules.url("javascript:alert(1)")
    }

    @Test(expected = StudyException::class)
    fun rejectsMalformedUrl() {
        StudyRules.url("https://")
    }

    @Test(expected = StudyException::class)
    fun rejectsEmbeddedCredentials() {
        StudyRules.url("https://user:password@example.org")
    }

    @Test
    fun progressBoundaries() {
        StudyRules.progress(0, 100, 50)
    }

    @Test(expected = StudyException::class)
    fun rejectsNegativeProgress() {
        StudyRules.progress(-1)
    }

    @Test(expected = StudyException::class)
    fun rejectsExcessProgress() {
        StudyRules.progress(101)
    }

    @Test
    fun searchEscapesWildcards() {
        assertEquals("50\\%\\_\\\\", StudyRules.escapeLike("50%_\\"))
    }

    @Test
    fun idsDoNotDependOnNames() {
        assertNotEquals(newId(), newId())
    }
}
