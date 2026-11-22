package com.riverrunner.web

import com.riverrunner.engine.*
import com.riverrunner.i18n.Strings

internal fun feedbackView(
    feedback: PracticeFeedback,
    s: Strings,
    amount: (Int) -> String,
    showEquity: Boolean = false,
): String = buildString {
    val verdict = when (feedback.verdict) {
        Deviation.Verdict.OK -> s.inLine
        Deviation.Verdict.ACTION -> s.deviation
        Deviation.Verdict.SIZE -> s.sizeOff
    }
    append("""<section class="panel feedback"><h2>${escape(s.gtoSuggest)} · ${escape(verdict)}</h2>
        <p>${escape(s.yourAction)} · ${escape(s.action(feedback.action, amount))}</p><dl class="metrics">""")
    for ((move, frequency) in Deviation.actionMix(feedback.policy)) {
        if (frequency > 0) append(metric(s.move(move), percent(frequency)))
    }
    for ((size, frequency) in Deviation.sizeMix(feedback.policy)) {
        val label = when (size) {
            SizeClass.SMALL -> s.sizeSmall
            SizeClass.MEDIUM -> s.sizeMedium
            SizeClass.LARGE -> s.sizeLarge
            SizeClass.POT -> s.sizePot
            SizeClass.OVER -> s.sizeOver
        }
        if (frequency > 0) append(metric(label, percent(frequency)))
    }
    if (showEquity) {
        feedback.policy.equity?.let { append(metric(s.estEquity, percent(it))) }
        if (feedback.policy.potOdds > 0) append(metric(s.needEquity, percent(feedback.policy.potOdds)))
    }
    append("</dl></section>")
}

internal fun tableSeats(
    players: List<PlayerState>,
    board: List<Int>,
    buttonSeat: Int,
    acting: Int,
    pot: String,
    s: Strings,
    deck: String,
    amount: (Int) -> String,
    show: (Int) -> Boolean,
    seatDetail: (PlayerState) -> String,
): String = buildString {
    fun seat(player: PlayerState) {
        val hero = player.seat == GameSession.HERO_SEAT
        val state = when { player.folded -> s.fold; player.allIn -> s.allIn; else -> "" }
        append("""<article class="seat${if (player.seat == acting) " acting" else ""}${if (player.folded) " folded" else ""}">
            <div class="seat-copy"><strong>${escape(if (hero) s.you else player.name)}</strong>
            ${if (player.seat == buttonSeat) "<span class=\"dealer\" aria-label=\"Dealer\">D</span>" else ""}
            <span>${escape(amount(player.stack))}</span></div>""")
        append(if (show(player.seat)) cards(player.hole.toList(), deck) else cards(emptyList(), deck, if (player.folded) 0 else 2))
        if (state.isNotEmpty()) append("<span class=\"muted\">${escape(state)}</span>")
        append(seatDetail(player))
        append("</article>")
    }
    append("""<section class="felt"><div class="opponents">""")
    players.filter { it.seat != GameSession.HERO_SEAT }.sortedBy { it.seat }.forEach { seat(it) }
    append("""</div><div class="board"><span>${escape(s.pot)} <strong>${escape(pot)}</strong></span>""")
    append(cards(board, deck, 5 - board.size))
    append("</div>")
    players.firstOrNull { it.seat == GameSession.HERO_SEAT }?.let { seat(it) }
    append("</section>")
}

internal fun actionLog(actions: List<Action>, players: List<PlayerState>, s: Strings, amount: (Int) -> String, expanded: Boolean) =
    buildString {
        append("""<details class="panel log" data-log${if (expanded) " open" else ""}><summary>${escape(s.actionLog)}</summary><ol>""")
        for ((street, moves) in actions.groupBy { it.street }) {
            append("<li><strong>${escape(s.street(street))}</strong><ul>")
            for (action in moves) {
                val name = if (action.seat == GameSession.HERO_SEAT) s.you else players.firstOrNull { it.seat == action.seat }?.name.orEmpty()
                append("<li>${escape(name)} · ${escape(s.action(action, amount))}</li>")
            }
            append("</ul></li>")
        }
        append("</ol></details>")
    }

internal fun reportView(game: Practice, s: Strings): String = buildString {
    val summary = game.summary ?: return@buildString
    append("""<section class="panel"><h2>${escape(
        if (summary.mode == GameMode.CASH) s.champion else if (summary.heroPlace == 1) s.championTitle
        else s.placeN(summary.heroPlace)
    )}</h2><dl class="metrics">""")
    if (summary.endedEarly) append("<p>${escape(s.endedEarly)}</p>")
    append(metric(s.handsPlayed, summary.handsPlayed.toString()))
    append(metric(s.profit, signed(summary.heroProfit) + " " + if (summary.mode == GameMode.CASH) "BB" else s.tourneyPoints))
    append("</dl><ol>")
    for (standing in summary.standings) {
        append("<li>${escape(if (standing.isHuman) s.you else standing.name)} · ${standing.stack} ${escape(s.chips)}</li>")
    }
    append("</ol></section><section class=\"panel\"><h2>${escape(s.sessionReport)}</h2>")
    val tally = game.tally
    if (tally.decisions == 0) append("<p>${escape(s.noDecisions)}</p>")
    else {
        append("<p>${escape(s.decisionsLine(tally.decisions, tally.deviations))}</p>")
        append("<h3>${escape(s.byStreet)}</h3><dl class=\"metrics\">")
        for ((street, counts) in tally.byStreet) append(metric(s.street(street), "${counts.second} / ${counts.first}"))
        append("</dl><h3>${escape(s.devByPosition)}</h3><dl class=\"metrics\">")
        for ((position, counts) in tally.byPosition) append(metric(position, "${counts.second} / ${counts.first}"))
        append("</dl>")
    }
    append("<h3>${escape(s.whoTheyWere)}</h3><ul>")
    for (bot in game.session.players.filterNot { it.isHuman }) {
        val hud = game.hud[bot.seat]
        val detail = hud?.let { " · VPIP ${percent(it.vpip)} · PFR ${percent(it.pfr)} · AF ${it.af?.let(::decimal) ?: "–"}" }.orEmpty()
        append("<li>${escape(bot.name)} · ${escape(s.styleName(bot.style))}${escape(detail)}</li>")
    }
    append("</ul></section>")
    append("<div class=\"toolbar\">${button(s.playAgain, "again", "primary")}${button(s.history, "history")}${button(s.home, "home")}</div>")
}

internal fun replayLog(frames: List<ReplayFrame>, selected: Int, s: Strings, amount: (Int) -> String) = buildString {
    append("""<details class="panel log"><summary>${escape(s.actionLog)}</summary><ol>""")
    for (frame in frames) {
        val label = frame.action?.let { action ->
            val player = frame.players.firstOrNull { it.seat == action.seat }
            val name = if (action.seat == GameSession.HERO_SEAT) s.you else player?.name.orEmpty()
            "$name · ${s.action(action, amount)}"
        } ?: s.street(frame.street)
        append("<li>${button(label, "replay-jump:${frame.index}", pressed = frame.index == selected)}</li>")
    }
    append("</ol></details>")
}
