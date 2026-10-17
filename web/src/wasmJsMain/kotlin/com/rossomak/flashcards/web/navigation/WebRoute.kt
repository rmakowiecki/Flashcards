package com.rossomak.flashcards.web.navigation

/**
 * A page of the website, addressed by its URL path. Firebase Hosting rewrites every path to
 * index.html, so the path is read here rather than by the server; these paths are the ones the
 * Play Console listing links to, so they never change.
 */
sealed interface WebRoute {
    val path: String
    val title: String

    data object Home : WebRoute {
        override val path = "/"
        override val title = "Flashcards"
    }

    data object PrivacyPolicy : WebRoute {
        override val path = "/privacy"
        override val title = "Flashcards Privacy Policy"
    }

    data object DeleteAccount : WebRoute {
        override val path = "/delete-account"
        override val title = "Delete Flashcards Account"
    }

    companion object {
        private val all: List<WebRoute> = listOf(Home, PrivacyPolicy, DeleteAccount)

        /**
         * The page for a URL [path]. A trailing slash and the `.html` suffix of the old static pages
         * are ignored, so `/privacy/` and `/privacy.html` both open the privacy policy; any other
         * unknown path opens [Home].
         */
        fun fromPath(path: String): WebRoute {
            val normalized = path.trimEnd('/').removeSuffix(".html").ifEmpty { Home.path }
            return all.firstOrNull { route -> route.path == normalized } ?: Home
        }
    }
}

/** Opens a [WebRoute] page, as a link on another page does. */
typealias OnNavigate = (WebRoute) -> Unit
