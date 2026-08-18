package com.dhruw.autoflow.instagram.model

/** One account from an Instagram export. [username] keeps the export's casing. */
data class InstagramAccount(
    val username: String,
    val profileUrl: String? = null
)

/** Parsed follower/following lists. [source] names the export file for display. */
data class InstagramFollowData(
    val followers: List<InstagramAccount>,
    val following: List<InstagramAccount>,
    val source: String
)

/** Outcome of comparing following against followers. */
data class InstagramAnalysisResult(
    val followersCount: Int,
    val followingCount: Int,
    val notFollowingBack: List<InstagramAccount>,
    val mutualCount: Int,
    val followersOnlyCount: Int,
    val source: String,
    val analyzedAt: Long
) {
    val notFollowingBackCount: Int
        get() = notFollowingBack.size
}
