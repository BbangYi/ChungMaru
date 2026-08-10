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

class DebugYoutubeMaskHarnessActivity : AppCompatActivity() {

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
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(buildHeader())
        root.addView(buildCommentScroller())
        setContentView(root)
    }

    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            minimumHeight = dp(210)
            setPadding(dp(24), dp(24), dp(24), dp(18))
            setBackgroundColor(Color.rgb(250, 250, 250))

            addView(TextView(context).apply {
                text = "Comments"
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(25, 25, 25))
            })
            addView(TextView(context).apply {
                text = "Newest"
                textSize = 14f
                setTextColor(Color.rgb(90, 90, 90))
                setPadding(0, dp(8), 0, 0)
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
        list.addView(TextView(this).apply {
            text = "Share your thoughts..."
            textSize = 16f
            setTextColor(Color.rgb(100, 100, 100))
            setPadding(dp(32), dp(24), dp(32), dp(36))
            minHeight = dp(96)
        })

        return ScrollView(this).apply {
            id = View.generateViewId()
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
    }

    private fun buildCommentRow(index: Int, comment: FixtureComment): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "comment-row-$index"
            minimumHeight = dp(132)
            setPadding(dp(32), dp(14), dp(28), dp(14))
            setBackgroundColor(
                if (index % 2 == 0) Color.WHITE else Color.rgb(248, 249, 250)
            )
        }

        row.addView(TextView(this).apply {
            text = comment.author
            contentDescription = comment.author
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(75, 75, 75))
        })
        row.addView(TextView(this).apply {
            text = "${index + 1} days ago"
            textSize = 12f
            setTextColor(Color.rgb(110, 110, 110))
        })
        row.addView(TextView(this).apply {
            text = comment.body
            textSize = 17f
            setTextColor(Color.rgb(24, 24, 24))
            setPadding(0, dp(7), 0, dp(8))
            minHeight = dp(48)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("Like this comment", "Dislike this comment", "Reply").forEach { label ->
            actions.addView(TextView(this).apply {
                text = label
                contentDescription = label
                isClickable = label == "Reply"
                textSize = 12f
                setTextColor(Color.rgb(95, 95, 95))
                setPadding(0, dp(4), dp(22), dp(4))
            })
        }
        row.addView(actions)
        row.addView(View(this).apply {
            setBackgroundColor(Color.rgb(225, 225, 225))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(10)
            }
        })
        return row
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private data class FixtureComment(
        val author: String,
        val body: String
    )

    private companion object {
        val comments = listOf(
            FixtureComment(
                author = "@mask_test_01",
                body = "tlqkf this harmful fixture must stay covered on this exact comment"
            ),
            FixtureComment(
                author = "@mask_test_02",
                body = "The stabilization test should leave this normal comment readable"
            ),
            FixtureComment(
                author = "@mask_test_03",
                body = "A safe middle row verifies that masks do not cover neighboring comments"
            ),
            FixtureComment(
                author = "@mask_test_04",
                body = "The comment panel keeps a stable accessibility layout during scrolling"
            ),
            FixtureComment(
                author = "@mask_test_05",
                body = "Another ordinary row is intentionally free of abusive language"
            ),
            FixtureComment(
                author = "@mask_test_06",
                body = "This row becomes visible after the first swipe and must remain readable"
            ),
            FixtureComment(
                author = "@mask_test_07",
                body = "tlqkf second harmful fixture verifies cached re-anchoring after scroll"
            ),
            FixtureComment(
                author = "@mask_test_08",
                body = "The final safe row confirms that stale masks disappear off screen"
            )
        )
    }
}
