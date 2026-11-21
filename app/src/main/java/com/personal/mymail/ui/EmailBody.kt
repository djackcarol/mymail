package com.personal.mymail.ui

import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shows an email as a web page.
 *
 * Safety: scripts are switched off, files can't be read, and tapping a link never loads inside
 * this view. A tap is passed to [onLink] (which opens the browser or the compose screen).
 * Pictures from the internet are blocked unless [allowRemote] is true, because they can be used
 * to tell the sender that you opened the message.
 */
@Composable
fun EmailWebView(
    html: String,
    allowRemote: Boolean,
    backgroundColor: Int,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnLink by rememberUpdatedState(onLink)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                with(settings) {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    setGeolocationEnabled(false)
                    // Let the user pinch to zoom, and start zoomed out so wide emails fit.
                    setSupportZoom(true)
                    builtInZoomControls = true
                    setDisplayZoomControls(false)
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    // Many emails load pictures from plain http addresses.
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    blockNetworkLoads = true
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        // Only react to a real tap. Anything the page tries to do by itself
                        // (such as a redirect) is ignored.
                        if (request.hasGesture()) latestOnLink(request.url.toString())
                        return true
                    }
                }
            }
        },
        update = { view ->
            val key = Triple(html.hashCode(), allowRemote, backgroundColor)
            if (view.tag != key) {
                view.tag = key
                view.settings.blockNetworkLoads = !allowRemote
                view.setBackgroundColor(backgroundColor)
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}

/** Adds phone-friendly defaults around an email's own HTML (the email's own styles still win). */
internal fun wrapEmailHtml(emailHtml: String): String =
    "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>" +
        "body{margin:8px;color:#000;background:#fff;overflow-wrap:break-word;word-wrap:break-word;}" +
        "img{max-width:100%;height:auto;}" +
        "pre{white-space:pre-wrap;}" +
        "</style></head><body>" + emailHtml + "</body></html>"

private val remoteContentPattern = Regex(
    "(?i)(<img[^>]+src\\s*=\\s*[\"']?\\s*(https?:)?//|url\\(\\s*[\"']?\\s*(https?:)?//)"
)

/** True if the email would load pictures (or styles) from the internet. */
internal fun hasRemoteContent(emailHtml: String): Boolean = remoteContentPattern.containsMatchIn(emailHtml)

// ----------------------------------------------------------------------------
// Plain-text mail with tappable links
// ----------------------------------------------------------------------------

private val linkPattern = Regex(
    "(?i)((?:https?://|www\\.)[^\\s<>\"']+)|([A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})"
)

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Characters that are usually punctuation after a link, not part of it. */
private fun splitTrailingPunctuation(url: String): Pair<String, String> {
    var end = url.length
    while (end > 0) {
        val c = url[end - 1]
        val trailing = when (c) {
            '.', ',', ';', ':', '!', '?', '\'', '"', ']', '}', '>' -> true
            // Keep a closing bracket that belongs to an opening one inside the link.
            ')' -> url.substring(0, end).count { it == '(' } < url.substring(0, end).count { it == ')' }
            else -> false
        }
        if (!trailing) break
        end--
    }
    return url.substring(0, end) to url.substring(end)
}

/**
 * Turns a plain-text email into a small web page where web addresses and email addresses
 * can be tapped. Line breaks and spacing are kept. Colors follow the app theme.
 */
internal fun plainTextToHtml(text: String, textColor: String, linkColor: String, backgroundColor: String): String {
    val out = StringBuilder()
    out.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
    out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
    out.append("<style>")
    out.append("body{margin:14px;font-family:sans-serif;font-size:16px;line-height:1.45;")
    out.append("white-space:pre-wrap;overflow-wrap:anywhere;color:").append(textColor)
    out.append(";background:").append(backgroundColor).append(";}")
    out.append("a{color:").append(linkColor).append(";}")
    out.append("</style></head><body>")

    var last = 0
    for (match in linkPattern.findAll(text)) {
        out.append(escapeHtml(text.substring(last, match.range.first)))
        val (link, trailing) = splitTrailingPunctuation(match.value)
        if (link.isEmpty()) {
            out.append(escapeHtml(match.value))
        } else {
            val href = when {
                match.groups[2] != null -> "mailto:$link"
                link.startsWith("www.", ignoreCase = true) -> "http://$link"
                else -> link
            }
            out.append("<a href=\"").append(escapeHtml(href)).append("\">")
                .append(escapeHtml(link)).append("</a>")
            out.append(escapeHtml(trailing))
        }
        last = match.range.last + 1
    }
    out.append(escapeHtml(text.substring(last)))
    out.append("</body></html>")
    return out.toString()
}

internal fun colorToCss(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)
