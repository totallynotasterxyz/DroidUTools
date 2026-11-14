// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.updater

/**
 * Global configuration constants for DroidUtools in-app update checks.
 *
 * NOTE FOR MAINTAINERS:
 * Ensure GITHUB_OWNER and REPO_NAME match the published GitHub repository coordinates.
 */
object UpdateConfig {
    /** GitHub organization or user account hosting the repository */
    const val GITHUB_OWNER = "[GITHUB_OWNER]"

    /** Repository name hosting releases and source code */
    const val REPO_NAME = "[REPO_NAME]"

    /** Minimum interval between automatic background update checks (12 hours) */
    const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    /** HTTP User-Agent sent in API requests */
    const val USER_AGENT = "DroidUtools-Updater/1.1 (Android; Linux; arm64-v8a)"

    /** Base GitHub API endpoint */
    val RELEASES_API_URL: String
        get() = "https://api.github.com/repos/$GITHUB_OWNER/$REPO_NAME/releases"

    /** Base GitHub Repository URL */
    val REPO_WEB_URL: String
        get() = "https://github.com/$GITHUB_OWNER/$REPO_NAME"
}
