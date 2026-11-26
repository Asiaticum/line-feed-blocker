package dev.haman.linefeedblocker

object InAppBrowserDetector {
    const val ACTIVITY_CLASS = "com.linecorp.line.iab.browser.impl.InAppBrowserActivity"

    // An already-open browser may not send its Activity event when the service connects.
    // Match LINE's native toolbar, where the close button and minimize control share a row.
    fun hasBrowserControls(root: UiNodeSnapshot): Boolean =
        root.descendantsAndSelf().any { row ->
            row.children.any { it.isButton("閉じる") } &&
                row.children.any { child ->
                    child.isButton("ページを最小化") || child.children.any { it.isButton("ページを最小化") }
                }
        }

    private fun UiNodeSnapshot.isButton(description: String): Boolean =
        visible && clickable && className == "android.widget.Button" && contentDescription == description
}
