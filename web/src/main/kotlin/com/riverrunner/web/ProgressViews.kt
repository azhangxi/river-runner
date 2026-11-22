package com.riverrunner.web

import com.riverrunner.engine.*
import com.riverrunner.i18n.Strings
import kotlin.js.Date

internal fun historyView(slot: SaveSlot, favorites: Boolean, deck: String, s: Strings): String = buildString {
    append("""<nav class="toolbar" aria-label="${escape(s.history)}">""")
    append(button(s.all, "history-all", pressed = !favorites))
    append(button(s.favorites, "history-favorites", pressed = favorites))
    append("</nav>")
    val records = if (favorites) slot.history.filter { it.favorite } else slot.history
    if (records.isEmpty()) append("<p class=\"empty\">${escape(s.noHands)}</p>")
    for (record in records) {
        val name = s.handNo(record.handNumber)
        append("""<article class="record"><div><b>${escape(name)}</b>
            <span class="muted">${escape(Stats.positionOf(record))} · ${escape(Date(record.time.toDouble()).toLocaleDateString())}</span>
            ${cards(record.hero?.hole?.toList().orEmpty(), deck)}</div>""")
        append("""<strong>${signed(record.heroNet.toDouble() / record.bigBlind)} BB</strong><div class="toolbar">""")
        append(button(s.replay, "replay:${record.id}"))
        append(button(if (record.favorite) "★ ${s.starHand}" else "☆ ${s.starHand}", "star:${record.id}", pressed = record.favorite))
        append("</div></article>")
    }
    append("<p class=\"muted\">${escape(s.keepHandsNote)}</p>")
}

internal fun statsView(slot: SaveSlot, s: Strings): String = buildString {
    val stats = Stats.compute(slot.history)
    append("""<section class="panel"><h2>${escape(s.heroStatsTitle)}</h2><dl class="metrics">""")
    append(metric(s.handsLabel, stats.hands.toString()))
    append(metric(s.vpip, percent(stats.vpip)))
    append(metric(s.pfr, percent(stats.pfr)))
    append(metric(s.threeBet, percent(stats.threeBet)))
    append(metric(s.wtsd, percent(stats.wtsd)))
    append(metric(s.wsd, percent(stats.wsd)))
    append(metric(s.bbPer100, signed(stats.bbPer100)))
    append(metric(s.netBB, signed(stats.netBB)))
    append("</dl><h3>${escape(s.byPosition)}</h3><dl class=\"metrics\">")
    for ((position, net) in stats.byPosition.entries.sortedBy { it.key }) append(metric(position, "${signed(net)} BB"))
    append("</dl></section>")
    append("""<section class="panel"><h2>${escape(s.careerTitleFor(slot.name))}</h2><dl class="metrics">""")
    val counters = listOf(
        s.handsLabel to Progress.HANDS, s.sessions to Progress.CASH_SESSIONS,
        s.tournamentsPlayed to Progress.TOURNEYS, s.tournamentWins to Progress.TOURNEY_WINS,
        s.itm to Progress.TOURNEY_ITM, s.tourneyPoints to Progress.TOURNEY_POINTS,
    )
    for ((label, key) in counters) append(metric(label, (slot.counters[key] ?: 0).toString()))
    append(metric(s.cashProfitBB, signed((slot.counters[Progress.CASH_PROFIT_CENTI_BB] ?: 0) / 100.0)))
    append("</dl></section>")
}

internal fun savesView(store: BrowserStore, s: Strings): String = buildString {
    append("<p>${escape(s.saveSlotCount(store.slots.size, 6))}</p>")
    for (slot in store.slots) {
        append("""<article class="record"><div><strong>${escape(slot.name)}</strong>
            <span class="muted">${escape(s.handsLabel)} ${slot.counters[Progress.HANDS] ?: 0} · ${escape(s.achievements)} ${slot.unlocked.size}</span></div>
            <div class="toolbar">""")
        if (slot == store.active) append("<span>${escape(s.currentSave)}</span>")
        else append(button(s.useSave, "use:${slot.id}"))
        append(button(s.rename, "rename:${slot.id}"))
        if (store.slots.size > 1) append(button(s.deleteSave, "delete:${slot.id}", "danger"))
        append("</div></article>")
    }
    if (store.slots.size < 6) append(button(s.newSave, "add-save", "primary"))
    else append("<p>${escape(s.slotsFull(6))}</p>")
}

internal fun achievementsView(slot: SaveSlot, s: Strings): String = buildString {
    append("<p>${escape(s.unlockedCount(slot.unlocked.size, Achievements.all.size))}</p>")
    for ((category, badges) in Achievements.all.groupBy { it.category }) {
        append("<section class=\"panel\"><h2>${escape(s.categoryName(category))}</h2><div class=\"achievements\">")
        for (badge in badges) {
            val at = slot.unlocked[badge.id]
            val name = if (s.isZh) badge.nameZh else badge.nameEn
            val desc = if (s.isZh) badge.descZh else badge.descEn
            val count = (slot.counters[badge.counter] ?: 0L).coerceIn(0L, badge.target)
            append("""<article class="achievement${if (at == null) "" else " earned"}">
                <span class="badge" aria-hidden="true">${escape(badge.glyph)}</span><div>
                <strong>${escape(name)}</strong><p>${escape(desc)}</p>
                <progress aria-label="${escape(name)}" max="${badge.target}" value="$count"></progress> $count / ${badge.target}""")
            if (at != null) append("<p>${escape(s.unlockedOn(Date(at.toDouble()).toLocaleDateString()))}</p>")
            append("</div></article>")
        }
        append("</div></section>")
    }
}
