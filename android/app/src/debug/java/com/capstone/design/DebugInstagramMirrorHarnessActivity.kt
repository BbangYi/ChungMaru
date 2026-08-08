package com.capstone.design

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.capstone.design.youtubeparser.AnalysisEndpointStore
import com.capstone.design.youtubeparser.AnalysisSensitivityStore
import com.capstone.design.youtubeparser.AndroidAnalysisClient

class DebugInstagramMirrorHarnessActivity : AppCompatActivity() {
    private val density: Float
        get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidAnalysisClient.clearCache()
        AnalysisEndpointStore.saveRawInput(this, "10.0.2.2:8000")
        AnalysisSensitivityStore.save(this, 100)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(buildHeader())
        root.addView(
            buildCommentScroller(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(buildComposer())
        setContentView(root)
    }

    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(74)
            setPadding(dp(20), 0, dp(16), 0)
            setBackgroundColor(Color.WHITE)

            addView(TextView(context).apply {
                text = "Comments"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(20, 20, 20))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(TextView(context).apply {
                text = "Close"
                contentDescription = "Close comments"
                textSize = 15f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setTextColor(Color.rgb(40, 40, 40))
                setPadding(dp(18), dp(12), dp(4), dp(12))
                setOnClickListener { finish() }
            })
        }
    }

    private fun buildCommentScroller(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        comments.forEachIndexed { index, comment ->
            list.addView(buildCommentRow(index, comment))
        }

        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            addView(
                list,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun buildCommentRow(index: Int, comment: FixtureComment): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(118)
            setPadding(dp(64), dp(14), dp(28), dp(12))
            setBackgroundColor(Color.WHITE)

            addView(TextView(context).apply {
                text = "${comment.author} ${comment.body}"
                textSize = 15f
                setTextColor(Color.rgb(24, 24, 24))
                maxLines = 4
            })
            addView(TextView(context).apply {
                text = "2\uC77C \uC804"
                textSize = 12f
                setTextColor(Color.rgb(110, 110, 110))
                setPadding(0, dp(8), 0, 0)
            })
            addView(TextView(context).apply {
                text = "Reply"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(90, 90, 90))
                setPadding(0, dp(5), 0, 0)
            })
            addView(View(context).apply {
                setBackgroundColor(Color.rgb(238, 238, 238))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                ).apply {
                    topMargin = dp(10)
                }
            })
        }
    }

    private fun buildComposer(): View {
        return TextView(this).apply {
            text = "Add a comment..."
            contentDescription = "Add a comment..."
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(105, 105, 105))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), 0, dp(24), 0)
            minimumHeight = dp(72)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private data class FixtureComment(
        val author: String,
        val body: String
    )

    private companion object {
        val comments = listOf(
            FixtureComment("safe.one", "This normal comment should be visible in the safe mirror"),
            FixtureComment("blocked.one", "tlqkf this harmful fixture must never be displayed"),
            FixtureComment("safe.two", "The parser keeps the original Instagram author and body"),
            FixtureComment("safe.three", "A normal row verifies independent mirror scrolling"),
            FixtureComment("safe.four", "The loading surface stays opaque during collection"),
            FixtureComment("safe.five", "This row appears after the first native scroll"),
            FixtureComment("blocked.two", "tlqkf another harmful row validates persistent filtering"),
            FixtureComment("safe.six", "A safe comment remains readable after prefetch"),
            FixtureComment("safe.seven", "Tablet side panel geometry remains bounded"),
            FixtureComment("safe.eight", "Closing the native panel removes the mirror"),
            FixtureComment("safe.nine", "No legacy coordinate mask is rendered above this list"),
            FixtureComment("safe.ten", "The final row confirms the collector reached the end")
        )
    }
}
