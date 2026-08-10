package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeCommentOcrFallbackTest {

    @Test
    fun planRows_limitsCaptureToBandBetweenAuthorAndReply() {
        val rows = YoutubeCommentOcrFallback.planRows(
            anchors = listOf(
                YoutubeCommentOcrAnchor(
                    authorLabel = "@safe-user",
                    authorBounds = BoundsRect(24, 967, 72, 1015),
                    rowBounds = BoundsRect(24, 943, 1200, 1143),
                    replyBounds = BoundsRect(276, 1047, 374, 1143)
                )
            ),
            panelBounds = BoundsRect(0, 843, 1200, 1824),
            screenWidth = 1200,
            screenHeight = 1920
        )

        val row = rows.single()
        assertEquals(BoundsRect(96, 1017, 1128, 1039), row.bodyBounds)
        assertTrue(row.captureBounds.top <= row.bodyBounds.top)
        assertTrue(row.captureBounds.bottom >= row.bodyBounds.bottom)
        assertTrue(row.captureBounds.bottom - row.captureBounds.top >= 44)
    }

    @Test
    fun assembleComments_joinsBodyLinesAndKeepsAccessibilityAuthorIdentity() {
        val row = YoutubeCommentOcrRowPlan(
            authorLabel = "@safe-user",
            bodyBounds = BoundsRect(96, 1020, 1128, 1100),
            captureBounds = BoundsRect(96, 1012, 1128, 1108)
        )
        val metadata = VisualTextOcrMetadataCodec.encode(
            source = YoutubeCommentOcrFallback.ROI_SOURCE,
            roiBoundsInScreen = row.captureBounds,
            visualText = "line"
        )

        val comments = YoutubeCommentOcrFallback.assembleComments(
            rows = listOf(row),
            ocrCandidates = listOf(
                ParsedComment("first line", BoundsRect(110, 1024, 400, 1048), metadata),
                ParsedComment("second line", BoundsRect(110, 1052, 460, 1078), metadata)
            )
        )

        val comment = comments.single()
        assertEquals("first line second line", comment.commentText)
        assertEquals("android-accessibility-comment:youtube:safe-user", comment.authorId)
        assertEquals(BoundsRect(110, 1024, 460, 1078), comment.boundsInScreen)
    }

    @Test
    fun assembleComments_rejectsControlsAndTextOutsideBodyBand() {
        val row = YoutubeCommentOcrRowPlan(
            authorLabel = "@safe-user",
            bodyBounds = BoundsRect(96, 1020, 1128, 1080),
            captureBounds = BoundsRect(96, 1008, 1128, 1092)
        )
        val metadata = VisualTextOcrMetadataCodec.encode(
            source = YoutubeCommentOcrFallback.ROI_SOURCE,
            roiBoundsInScreen = row.captureBounds,
            visualText = "line"
        )

        val comments = YoutubeCommentOcrFallback.assembleComments(
            rows = listOf(row),
            ocrCandidates = listOf(
                ParsedComment("답글", BoundsRect(110, 1030, 180, 1050), metadata),
                ParsedComment("safe body", BoundsRect(110, 1090, 360, 1112), metadata)
            )
        )

        assertTrue(comments.isEmpty())
    }
}
