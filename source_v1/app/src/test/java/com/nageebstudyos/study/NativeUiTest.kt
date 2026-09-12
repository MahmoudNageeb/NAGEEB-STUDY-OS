package com.nageebstudyos.study

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.nageebstudyos.study.domain.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = StudyApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitText(text: String) {
        try {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            val dir = File("build/reports/screenshots").apply { mkdirs() }
            File(dir, "failure-semantics.txt").writeText(compose.onAllNodes(isRoot())[0].printToString())
            screenshot("failure-screen")
            throw error
        }
    }

    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performTextInput(value)
    }

    private fun commit() {
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(20_000) {
            compose
                .onAllNodes(hasSetTextAction() and hasText("Title"))
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun create(kind: String, title: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("quick-add").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("quick-add").performClick()
        compose.onNode(hasText(kind) and hasClickAction()).performClick()
        input("Title", title)
        commit()
        waitText(title)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image =
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                    view.draw(Canvas(it))
                }
            }
        val dir = File("build/reports/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun nativeUiCreatesHierarchyNoteTagAndSearchesLesson() {
        waitText("Create")
        compose.onNodeWithText("Create").performClick()
        input("Title", "Mathematics")
        commit()
        waitText("Mathematics")
        compose.onNodeWithText("Mathematics").performClick()
        create("Folder", "Calculus")
        compose.onNodeWithText("Calculus").performScrollTo().performClick()
        create("Folder", "Differentiation")
        compose.onNodeWithText("Differentiation").performScrollTo().performClick()
        create("Lesson", "Lecture 01")
        compose.onNodeWithText("Lecture 01").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("quick-add").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("quick-add").performClick()
        compose.onNodeWithText("Note").performClick()
        input("Title", "Differentiation rules")
        input("Your notes", "The derivative of x squared is 2x.")
        commit()
        waitText("Lecture 01")
        compose.onNodeWithText("Assign tags").performScrollTo().performClick()
        compose.onNodeWithText("Tag").performClick()
        input("Title", "Exam")
        commit()
        waitText("Exam")
        compose.onNodeWithText("Exam").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        input("Subjects, lessons, notes, files…", "Lecture 01")
        // Robolectric's paused Android clock must advance past the search debounce.
        compose.mainClock.advanceTimeBy(500)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        val result = hasText("Lecture 01") and hasClickAction() and !hasSetTextAction()
        compose.waitUntil(20_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(result).performClick()
        waitText("Understanding")
        compose.onNodeWithText("Subject: Mathematics").assertExists()
        compose.onNodeWithText("Parent: Differentiation").assertExists()
        screenshot("lesson-light")
    }

    @Test
    fun lightDarkAndArabicSettingsAreLive() {
        waitText("Create")
        screenshot("home-light")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("study-root-dark").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("settings-dark")
        compose.onNodeWithText("العربية").performScrollTo().performClick()
        waitText("الإعدادات")
        compose.onNodeWithText("المظهر").assertExists()
        screenshot("settings-arabic-dark")
    }

    @Test
    fun populatedLibraryRendersBothThemes() {
        waitText("Create")
        val app = ApplicationProvider.getApplicationContext<StudyApplication>()
        runBlocking {
            val subjects = listOf("Mathematics", "Physics", "Chemistry")
            subjects.forEachIndexed { index, title ->
                val subject = Entry(newId(), title, Kind.SUBJECT, accent = index)
                app.container.actions.save(subject)
                if (index == 0) {
                    val folder = Entry(newId(), "Calculus", Kind.FOLDER, subjectId = subject.id)
                    app.container.actions.save(folder)
                    app.container.actions.save(
                        Entry(
                            newId(),
                            "Lecture 01",
                            Kind.LESSON,
                            subjectId = subject.id,
                            parentId = folder.id,
                        )
                    )
                }
            }
        }
        compose.onNodeWithText("Subjects").performClick()
        waitText("Mathematics")
        screenshot("library-light")
        runBlocking { app.container.repository.setSetting("theme", "dark") }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("study-root-dark").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("library-dark")
    }
}
