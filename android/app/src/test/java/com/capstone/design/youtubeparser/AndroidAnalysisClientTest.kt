package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AndroidAnalysisClientTest {

    @Test
    fun parseAndroidAnalysisResponse_acceptsValidFilteredResponse() {
        val response = AndroidAnalysisClient.parseAndroidAnalysisResponse(
            """
            {
              "timestamp": 1710000000000,
              "filtered_count": 1,
              "results": [
                {
                  "original": "시발 뭐하는 거야",
                  "boundsInScreen": {"left": 0, "top": 10, "right": 300, "bottom": 60},
                  "author_id": "ocr:youtube-composite-card:0,0,300,80:시발",
                  "is_offensive": true,
                  "is_profane": true,
                  "is_toxic": true,
                  "is_hate": false,
                  "scores": {"profanity": 0.98, "toxicity": 0.91, "hate": 0.01},
                  "evidence_spans": [
                    {"text": "시발", "start": 0, "end": 2, "score": 0.98}
                  ]
                }
              ]
            }
            """.trimIndent(),
            expectedCommentCount = 2
        )

        assertEquals(1, response.filteredCount)
        assertEquals(1, response.results.size)
        assertEquals(true, response.results.first().isOffensive)
        assertEquals("ocr:youtube-composite-card:0,0,300,80:시발", response.results.first().authorId)
        assertEquals(1, AndroidAnalysisClient.countActionableOffensiveResults(response))
    }

    @Test
    fun countActionableOffensiveResults_ignoresPositiveWithoutSpan() {
        val response = AndroidAnalysisClient.parseAndroidAnalysisResponse(
            """
            {
              "timestamp": 1710000000000,
              "filtered_count": 0,
              "results": [
                {
                  "original": "abstract factory pattern 설명",
                  "boundsInScreen": {"left": 0, "top": 10, "right": 300, "bottom": 60},
                  "is_offensive": true,
                  "is_profane": true,
                  "is_toxic": true,
                  "is_hate": false,
                  "scores": {"profanity": 0.88, "toxicity": 0.78, "hate": 0.01},
                  "evidence_spans": []
                }
              ]
            }
            """.trimIndent(),
            expectedCommentCount = 1
        )

        assertEquals(0, AndroidAnalysisClient.countActionableOffensiveResults(response))
    }

    @Test
    fun matchFreshResultsToComments_doesNotApplyFilteredResponseByPosition() {
        val safeFilteredBeforeOffensive = ParsedComment(
            commentText = "청소년에게 유해한 결과는 제외되었습니다.",
            boundsInScreen = BoundsRect(left = 0, top = 10, right = 300, bottom = 60),
            authorId = "android-accessibility:title"
        )
        val offensive = ParsedComment(
            commentText = "씨발",
            boundsInScreen = BoundsRect(left = 0, top = 80, right = 120, bottom = 120),
            authorId = "android-accessibility:title"
        )
        val response = AndroidAnalysisClient.parseAndroidAnalysisResponse(
            """
            {
              "timestamp": 1710000000000,
              "filtered_count": 1,
              "results": [
                {
                  "original": "씨발",
                  "boundsInScreen": {"left": 0, "top": 80, "right": 120, "bottom": 120},
                  "author_id": "android-accessibility:title",
                  "is_offensive": true,
                  "is_profane": true,
                  "is_toxic": true,
                  "is_hate": false,
                  "scores": {"profanity": 0.98, "toxicity": 0.9, "hate": 0.01},
                  "evidence_spans": [
                    {"text": "씨발", "start": 0, "end": 2, "score": 0.98}
                  ]
                }
              ]
            }
            """.trimIndent(),
            expectedCommentCount = 2
        )

        val matched = AndroidAnalysisClient.matchFreshResultsToComments(
            comments = listOf(safeFilteredBeforeOffensive, offensive),
            results = response.results
        )

        assertEquals(null, matched[0])
        assertEquals("씨발", matched[1]?.original)
    }

    @Test
    fun matchFreshResultsToComments_reusesLookaheadAnalysisForVisibleAccessibilityTarget() {
        val visibleTitle = ParsedComment(
            commentText = "What is 'Tlqkf'?_Contemporary Korean Slang",
            boundsInScreen = BoundsRect(left = 80, top = 420, right = 680, bottom = 500),
            authorId = "android-accessibility:youtube_title"
        )
        val response = AndroidAnalysisClient.parseAndroidAnalysisResponse(
            """
            {
              "timestamp": 1710000000000,
              "filtered_count": 0,
              "results": [
                {
                  "original": "What is 'Tlqkf'?_Contemporary Korean Slang",
                  "boundsInScreen": {"left": 80, "top": 1320, "right": 680, "bottom": 1400},
                  "author_id": "android-accessibility-lookahead:android-accessibility:youtube_title",
                  "is_offensive": true,
                  "is_profane": true,
                  "is_toxic": true,
                  "is_hate": false,
                  "scores": {"profanity": 0.98, "toxicity": 0.9, "hate": 0.01},
                  "evidence_spans": [
                    {"text": "Tlqkf", "start": 9, "end": 14, "score": 0.98}
                  ]
                }
              ]
            }
            """.trimIndent(),
            expectedCommentCount = 1
        )

        val matched = AndroidAnalysisClient.matchFreshResultsToComments(
            comments = listOf(visibleTitle),
            results = response.results
        )

        assertEquals("What is 'Tlqkf'?_Contemporary Korean Slang", matched.single()?.original)
    }

    @Test
    fun parseAndroidAnalysisResponse_rejectsImpossibleCount() {
        val error = expectInvalidResponse {
            AndroidAnalysisClient.parseAndroidAnalysisResponse(
                """
                {
                  "timestamp": 1710000000000,
                  "filtered_count": 2,
                  "results": [
                    {
                      "original": "safe",
                      "boundsInScreen": {"left": 0, "top": 10, "right": 300, "bottom": 60},
                      "is_offensive": false,
                      "is_profane": false,
                      "is_toxic": false,
                      "is_hate": false,
                      "scores": {"profanity": 0.01, "toxicity": 0.01, "hate": 0.01},
                      "evidence_spans": []
                    }
                  ]
                }
                """.trimIndent(),
                expectedCommentCount = 2
            )
        }

        assertEquals("INVALID_RESPONSE_COUNT", error.message)
    }

    @Test
    fun parseAndroidAnalysisResponse_rejectsOutOfRangeSpan() {
        val error = expectInvalidResponse {
            AndroidAnalysisClient.parseAndroidAnalysisResponse(
                """
                {
                  "timestamp": 1710000000000,
                  "filtered_count": 0,
                  "results": [
                    {
                      "original": "욕",
                      "boundsInScreen": {"left": 0, "top": 10, "right": 300, "bottom": 60},
                      "is_offensive": true,
                      "is_profane": true,
                      "is_toxic": false,
                      "is_hate": false,
                      "scores": {"profanity": 0.9, "toxicity": 0.1, "hate": 0.1},
                      "evidence_spans": [
                        {"text": "욕", "start": 0, "end": 5, "score": 0.9}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
                expectedCommentCount = 1
            )
        }

        assertEquals("INVALID_RESPONSE_SPAN_RANGE_0_0", error.message)
    }

    private fun expectInvalidResponse(block: () -> Unit): IllegalArgumentException {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            return error
        }
        error("unreachable")
    }
}
