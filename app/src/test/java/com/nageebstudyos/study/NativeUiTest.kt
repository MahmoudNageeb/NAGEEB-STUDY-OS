package com.nageebstudyos.study

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
            compose.waitUntilAtLeastOneExists(hasText(text), 20_000)
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
        compose.onNodeWithText("حفظ").performClick()
        compose.waitUntil(20_000) {
            compose
                .onAllNodes(hasSetTextAction() and hasText("العنوان"))
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun create(kind: String, title: String) {
        compose.waitUntilAtLeastOneExists(hasTestTag("quick-add"), 20_000)
        compose.onNodeWithTag("quick-add").performClick()
        compose.onNode(hasText(kind) and hasClickAction()).performClick()
        input("العنوان", title)
        commit()
        waitText(title)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val dir = File("build/reports/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun createsHierarchyOpensFocusPanelAndSearchesLesson() {
        // Create the first subject from the dashboard quick action.
        compose.waitUntilAtLeastOneExists(hasTestTag("quick-add"), 20_000)
        compose.onNodeWithTag("quick-add").performClick()
        compose.onNode(hasText("مادة") and hasClickAction()).performClick()
        input("العنوان", "Mathematics")
        commit()
        compose.onNodeWithText("المواد").performClick()
        waitText("Mathematics")
        compose.onNodeWithText("Mathematics").performClick()
        create("مجلد", "Calculus")
        compose.onNodeWithText("Calculus").performScrollTo().performClick()
        create("مجلد", "Differentiation")
        compose.onNodeWithText("Differentiation").performScrollTo().performClick()
        create("درس", "Lecture 01")
        compose.onNodeWithText("Lecture 01").performScrollTo().performClick()

        // The V2 lesson panel is present with the one-tap focus entry point.
        compose.waitUntilAtLeastOneExists(hasText("ابدأ التركيز على هذا الدرس"), 20_000)
        compose.onNodeWithText("ابدأ التركيز على هذا الدرس").assertExists()

        // Add a note (same V1 flow).
        compose.onNodeWithTag("quick-add").performClick()
        compose.onNodeWithText("ملاحظة").performClick()
        input("العنوان", "Differentiation rules")
        input("ملاحظاتك", "The derivative of x squared is 2x.")
        commit()
        waitText("Lecture 01")

        // Tags.
        compose.onNodeWithText("تعيين الوسوم").performScrollTo().performClick()
        compose.onNodeWithText("وسم").performClick()
        input("العنوان", "Exam")
        commit()
        waitText("Exam")
        compose.onNodeWithText("Exam").performClick()
        compose.onNodeWithText("تم").performClick()

        // Local search still works.
        compose.onNodeWithContentDescription("البحث").performClick()
        input("مواد، دروس، ملاحظات، ملفات…", "Lecture 01")
        compose.mainClock.advanceTimeBy(500)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500))
        val result = hasText("Lecture 01") and hasClickAction() and !hasSetTextAction()
        compose.waitUntilAtLeastOneExists(result, 20_000)
        compose.onNode(result).performClick()
        waitText("الفهم")
        compose.onNodeWithText("المادة: Mathematics").assertExists()
        compose.onNodeWithText("الأصل: Differentiation").assertExists()
        screenshot("lesson-arabic-dark")
    }

    @Test
    fun dashboardOpensV2DestinationsInArabicDark() {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasTestTag("study-root-dark")).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("dashboard-dark")
        compose.onNodeWithContentDescription("الإعدادات").performClick()
        waitText("الهدف اليومي للتركيز")
        screenshot("settings-arabic-dark")
    }

    @Test
    fun populatedLibraryRendersDark() {
        compose.waitUntilAtLeastOneExists(hasTestTag("quick-add"), 20_000)
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<StudyApplication>()
        runBlocking {
            listOf("Mathematics", "Physics", "Chemistry").forEach { title ->
                val subject = Entry(newId(), title, Kind.SUBJECT)
                app.container.actions.save(subject)
                val folder = Entry(newId(), "$title folder", Kind.FOLDER, subjectId = subject.id)
                app.container.actions.save(folder)
                repeat(2) { index ->
                    app.container.actions.save(
                        Entry(
                            newId(),
                            "Lecture 0${index + 1}",
                            Kind.LESSON,
                            subjectId = subject.id,
                            parentId = folder.id,
                        )
                    )
                }
            }
        }
        compose.onNodeWithText("المواد").performClick()
        waitText("Mathematics")
        screenshot("library-dark")
    }
}
