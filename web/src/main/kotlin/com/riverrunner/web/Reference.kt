package com.riverrunner.web

import com.riverrunner.engine.Cards
import com.riverrunner.engine.Changelog
import com.riverrunner.reference.Guide
import kotlinx.browser.window

internal fun loadChangelog(onLoaded: (List<Changelog.Release>) -> Unit) {
    window.fetch("./CHANGELOG.md").then(
        onFulfilled = { response ->
            if (response.ok) {
                response.text().then(
                    onFulfilled = { onLoaded(Changelog.parse(it)) },
                    onRejected = { onLoaded(emptyList()) },
                )
            } else {
                onLoaded(emptyList())
            }
        },
        onRejected = { onLoaded(emptyList()) },
    )
}

internal fun referenceScreen(
    section: String,
    zh: Boolean,
    changelog: List<Changelog.Release>? = null,
    latestOnly: Boolean = false,
    startAtGlossary: Boolean = false,
): String = buildString {
    append("<article class=\"reference-screen\">")
    when (section) {
        "guide" -> guide(zh, startAtGlossary)
        "changelog" -> changelog(zh, changelog, latestOnly)
    }
    append("</article>")
}

private fun StringBuilder.guide(zh: Boolean, startAtGlossary: Boolean) {
    append("<h2>${if (zh) "新手指南" else "Beginner guide"}</h2>")
    chapter(if (zh) "基本玩法" else "How to play", open = !startAtGlossary) {
        val basics = Guide.basics(zh)
        paragraphs(listOf(basics.goalTitle to basics.goal))
        heading(basics.flowTitle)
        entries(basics.flow, listTag = "ol")
        heading(basics.actionsTitle)
        entries(basics.actions, listTag = "ul")
        paragraphs(listOf(basics.buttonTitle to basics.button))
    }
    chapter(if (zh) "牌型大小" else "Hand rankings") {
        heading(if (zh) "从大到小" else "Strongest to weakest")
        append("<ol class=\"reference-ranks\">")
        for (rank in Guide.ranks) {
            append("<li><strong>${(if (zh) rank.zh else rank.en).referenceEscape()}</strong>")
            append("<p>${(if (zh) rank.zhDesc else rank.enDesc).referenceEscape()}</p>")
            append("<div class=\"reference-hand\">")
            for (card in Cards.parseMany(rank.cards)) {
                val rankLabel = Cards.RANKS[Cards.rank(card)].toString().replace("T", "10")
                val suit = Cards.suit(card)
                val red = if (suit == 1 || suit == 2) " reference-card-red" else ""
                append("<span class=\"reference-card$red\">$rankLabel${"♣♦♥♠"[suit]}</span>")
            }
            append("</div></li>")
        }
        append("</ol>")
        heading(if (zh) "比大小的细节" else "Tie-breaks")
        append("<ul>")
        for (rule in Guide.tieBreaks(zh).lines()) {
            append("<li>${rule.removePrefix("• ").referenceEscape()}</li>")
        }
        append("</ul>")
    }
    chapter(if (zh) "位置" else "Positions") {
        heading(if (zh) "8人桌的位置" else "Positions at an 8-handed table")
        append("<div class=\"reference-positions\">")
        append("<p>${if (zh) "顺时针行动 ↻" else "Action moves clockwise ↻"}</p><ol>")
        for ((index, seat) in Guide.seats.withIndex()) {
            append("<li class=\"reference-seat-$index\">${seat.referenceEscape()}</li>")
        }
        append("</ol></div>")
        entries(Guide.positions(zh), listTag = "ul")
        paragraphs(listOf((if (zh) "为什么位置重要" else "Why position matters") to Guide.whyPositionMatters(zh)))
    }
    chapter(if (zh) "术语表" else "Glossary", open = startAtGlossary) {
        heading(if (zh) "常用术语" else "Common terms")
        append("<dl class=\"reference-glossary\">")
        for (term in Guide.terms) {
            append("<dt>${(if (zh) term.zh else term.en).referenceEscape()}")
            if (term.full.isNotEmpty()) append(" <i>${term.full.referenceEscape()}</i>")
            append("</dt><dd>${(if (zh) term.zhDesc else term.enDesc).referenceEscape()}</dd>")
        }
        append("</dl>")
    }
    chapter(if (zh) "新手建议" else "Beginner tips") {
        paragraphs(Guide.tips(zh))
    }
    chapter(if (zh) "软件说明" else "Using the app") {
        paragraphs(Guide.app(zh))
    }
}

private fun StringBuilder.changelog(
    zh: Boolean,
    releases: List<Changelog.Release>?,
    latestOnly: Boolean,
) {
    val title = when {
        latestOnly -> if (zh) "更新内容" else "What's new"
        else -> if (zh) "全部更新记录" else "All updates"
    }
    append("<h2>${title.referenceEscape()}</h2>")
    if (releases == null) {
        append("<p role=\"status\">${if (zh) "正在加载更新记录…" else "Loading updates…"}</p>")
        return
    }
    if (releases.isEmpty()) {
        append("<p role=\"status\">${if (zh) "暂时无法读取更新记录。" else "Updates are unavailable."}</p>")
        return
    }
    for ((index, release) in (if (latestOnly) releases.take(1) else releases).withIndex()) {
        val heading = listOf(release.version, release.date).filter(String::isNotBlank).joinToString(" · ")
        chapter(heading, open = index == 0) {
            for (section in release.sections) {
                val sectionTitle = section.title.text(zh)
                if (sectionTitle.isNotEmpty()) heading(sectionTitle)
                append("<ul>")
                for (item in section.items) append("<li>${item.text(zh).referenceEscape()}</li>")
                append("</ul>")
            }
        }
    }
}

private inline fun StringBuilder.chapter(title: String, open: Boolean = false, content: StringBuilder.() -> Unit) {
    append("<details${if (open) " open" else ""}><summary>${title.referenceEscape()}</summary>")
    append("<div class=\"reference-chapter\">")
    content()
    append("</div></details>")
}

private fun StringBuilder.heading(text: String) {
    append("<h3>${text.referenceEscape()}</h3>")
}

private fun StringBuilder.paragraphs(items: List<Pair<String, String>>) {
    for ((title, body) in items) {
        heading(title)
        append("<p>${body.referenceEscape()}</p>")
    }
}

private fun StringBuilder.entries(items: List<Pair<String, String>>, listTag: String) {
    append("<$listTag class=\"reference-entries\">")
    for ((title, body) in items) {
        append("<li><strong>${title.referenceEscape()}</strong><p>${body.referenceEscape()}</p></li>")
    }
    append("</$listTag>")
}

private fun String.referenceEscape(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
