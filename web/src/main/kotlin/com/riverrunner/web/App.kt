package com.riverrunner.web

import com.riverrunner.engine.*
import com.riverrunner.i18n.EN
import com.riverrunner.i18n.ZH
import com.riverrunner.presentation.betPresets
import com.riverrunner.presentation.defaultBetTarget
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import kotlin.math.roundToInt

fun main() {
    val root = document.getElementById("app") as HTMLElement
    RiverRunnerApp(root).mount()
}

private enum class Screen { HOME, SETUP, TABLE, REPLAY, RESULT, HISTORY, STATS, SAVES, ACHIEVEMENTS, SETTINGS, GUIDE, CHANGELOG }

private class RiverRunnerApp(private val root: HTMLElement) {
    private val store = BrowserStore(if (window.navigator.language.startsWith("zh")) ZH.saveDefaultName else EN.saveDefaultName)
    private val setup = Setup(store)
    private val s get() = if (store.flag("zh", window.navigator.language.startsWith("zh"))) ZH else EN
    private var screen = Screen.HOME
    private var referenceBack = Screen.HOME
    private var replayBack = Screen.HISTORY
    private var game: Practice? = null
    private var replay: Replayer? = null
    private var replayIndex = 0
    private var autoReview = false
    private var favorites = false
    private var timer: Int? = null
    private var playPractice = true
    private var logExpanded = false
    private var notice = ""
    private var changelog: List<Changelog.Release>? = null
    private var latestOnly = false
    private val equityCache = mutableMapOf<Pair<List<Int>, List<Int>>, Map<Int, Double>>()
    private val adviceCache = mutableMapOf<Int, PracticeFeedback?>()
    private val deck get() = store.get("deck") ?: "four"
    private val audio = TableAudio()
    private var showedLatestOnLaunch = false

    fun mount() {
        root.addEventListener("click", { event ->
            val target = (event.target as? Element)?.closest("button[data-action]") ?: return@addEventListener
            val action = target.getAttribute("data-action") ?: return@addEventListener
            audio.unlock()
            if (changeRaiseSelection(action)) return@addEventListener
            when (action.substringBefore(':')) {
                "game-fold", "game-check", "game-call", "game-raise", "game-allin" -> Unit
                "start", "quick", "again", "next-hand" -> sound("deal")
                else -> sound("click")
            }
            logExpanded = root.querySelector("[data-log]")?.hasAttribute("open") ?: logExpanded
            dispatch(action)
            render()
            schedule()
        })
        root.addEventListener("change", { event ->
            val input = event.target as? HTMLSelectElement ?: return@addEventListener
            val field = input.getAttribute("data-field") ?: return@addEventListener
            if (screen == Screen.SETUP) setup.update(field, input.value) else store.put(field, input.value)
            render()
        })
        root.addEventListener("input", { event ->
            val input = event.target as? HTMLInputElement ?: return@addEventListener
            if (input.id == "raise-size") {
                val bigBlind = game?.hand?.bigBlind ?: return@addEventListener
                root.querySelector("#raise-value")?.textContent = amount(input.value.toInt(), bigBlind)
            }
        })
        render()
        loadChangelog { releases ->
            changelog = releases
            val version = releases.firstOrNull()?.version
            if (version != null && store.get("seen-changelog") != version && screen == Screen.HOME) {
                latestOnly = true
                showedLatestOnLaunch = true
                navigate(Screen.CHANGELOG)
            }
            if (screen == Screen.CHANGELOG) render()
        }
    }

    private fun changeRaiseSelection(action: String): Boolean {
        val value = action.takeIf { it.startsWith("size:") }?.substringAfter(':')?.toIntOrNull() ?: return false
        val input = root.querySelector("#raise-size") as? HTMLInputElement ?: return false
        val bb = game?.hand?.bigBlind ?: return false
        input.value = value.toString()
        root.querySelector("#raise-value")?.textContent = amount(value, bb)
        val items = root.querySelectorAll(".sizing button")
        for (index in 0 until items.length) {
            val button = items.item(index) as? Element ?: continue
            button.setAttribute("aria-pressed", (button.getAttribute("data-action") == action).toString())
        }
        return true
    }

    private fun navigate(next: Screen) {
        if (screen != next) {
            screen = next
            window.scrollTo(0.0, 0.0)
        }
    }

    private fun sound(name: String) {
        if (!store.flag("sound", true)) return
        audio.play(name)
    }

    private fun progress(delta: Map<String, Long>) {
        val badges = store.recordProgress(delta)
        if (badges.isNotEmpty()) {
            notice = s.achievementUnlocked + " · " + badges.joinToString(", ") { if (s.isZh) it.nameZh else it.nameEn }
            sound("unlock")
        }
    }

    private fun start(config: SessionConfig) {
        notice = ""
        game = Practice(config, store::record, ::progress)
        playPractice = store.flag("practice", true)
        logExpanded = false
        navigate(Screen.TABLE)
    }

    private fun openReplay(record: HandRecord, back: Screen) {
        replay = Replayer(record)
        replayIndex = 0
        replayBack = back
        equityCache.clear()
        adviceCache.clear()
        progress(mapOf(Progress.REPLAYS to 1))
        navigate(Screen.REPLAY)
    }

    private fun nextHand() {
        val running = game ?: return
        running.next()
        autoReview = false
        notice = ""
        if (running.summary != null) navigate(Screen.RESULT) else navigate(Screen.TABLE)
    }

    private fun schedule() {
        if (timer != null || screen != Screen.TABLE) return
        val running = game ?: return
        if (running.legal != null || running.lastHand?.handNumber == running.handNumber) return
        val delay = if (running.fastForward) 0 else when (store.get("speed")) {
            "0" -> 660
            "2" -> 100
            else -> 300
        }
        timer = window.setTimeout({
            timer = null
            if (game === running && screen == Screen.TABLE) {
                val oldStreet = running.hand.street
                val oldActions = running.hand.actions.size
                if (running.tick()) {
                    if (running.hand.street != oldStreet) sound("flip")
                    else if (running.hand.actions.size > oldActions) playAction(running.hand.actions.last())
                    if (running.legal != null) {
                        running.fastForward = false
                        sound("turn")
                        val navigator = window.navigator.asDynamic()
                        if (jsTypeOf(navigator.vibrate) == "function") navigator.vibrate(25)
                    }
                    val last = running.lastHand
                    if (last != null && last.handNumber == running.handNumber) {
                        sound(if (last.heroNet > 0) "win" else "collect")
                        if (store.flag("review")) {
                            autoReview = true
                            openReplay(last, Screen.TABLE)
                        }
                    }
                    render()
                    schedule()
                }
            } else schedule()
        }, delay)
    }

    private fun playAction(action: Action) = sound(when {
        action.allIn -> "allin"
        action.type == ActionType.FOLD -> "fold"
        action.type == ActionType.CHECK -> "check"
        else -> "chip"
    })

    private fun act(move: Move) {
        game?.act(move)?.let(::playAction)
    }

    private fun dispatch(action: String) {
        val key = action.substringBefore(':')
        val arg = action.substringAfter(':', "")
        val running = game
        when (key) {
            "home" -> { notice = ""; navigate(Screen.HOME) }
            "setup" -> navigate(Screen.SETUP)
            "start", "quick" -> start(setup.config())
            "history" -> navigate(Screen.HISTORY)
            "history-all", "history-favorites" -> favorites = key == "history-favorites"
            "settings" -> navigate(Screen.SETTINGS)
            "stats" -> navigate(Screen.STATS)
            "saves" -> navigate(Screen.SAVES)
            "achievements" -> navigate(Screen.ACHIEVEMENTS)
            "guide" -> {
                referenceBack = screen
                progress(mapOf(Progress.GUIDE_OPENED to 1))
                navigate(Screen.GUIDE)
            }
            "guide-back" -> navigate(referenceBack)
            "changelog" -> { latestOnly = false; showedLatestOnLaunch = false; navigate(Screen.CHANGELOG) }
            "show-updates" -> latestOnly = false
            "close-updates" -> {
                changelog?.firstOrNull()?.let { store.put("seen-changelog", it.version) }
                navigate(if (showedLatestOnLaunch) Screen.HOME else Screen.SETTINGS)
            }
            "game-fold" -> act(Move.FOLD)
            "game-check" -> act(Move.CHECK)
            "game-call" -> act(Move.CALL)
            "game-raise" -> {
                val input = root.querySelector("#raise-size") as? HTMLInputElement
                input?.value?.toIntOrNull()?.let { act(Move.raiseTo(it)) }
            }
            "game-allin" -> running?.legal?.let { act(Move.raiseTo(it.maxRaiseTo)) }
            "fast-forward" -> { if (running != null) running.fastForward = true }
            "practice" -> playPractice = !playPractice
            "toggle" -> store.put(arg, (!store.flag(arg, arg in setOf("sound", "practice", "bb"))).toString())
            "next-hand" -> nextHand()
            "quit" -> if (running != null && window.confirm(s.exitConfirmText)) {
                running.quit()
                navigate(Screen.RESULT)
            }
            "again" -> running?.config?.let { start(it.copy(seed = kotlin.random.Random.nextLong())) }
            "replay-last" -> running?.lastHand?.let {
                autoReview = false
                openReplay(store.active.history.firstOrNull { record -> record.id == it.id } ?: it, Screen.TABLE)
            }
            "replay" -> store.active.history.firstOrNull { it.id.toString() == arg }?.let { openReplay(it, Screen.HISTORY) }
            "replay-back" -> {
                if (autoReview && replayBack == Screen.TABLE) nextHand() else navigate(replayBack)
            }
            "replay-jump" -> replay?.let { replayIndex = arg.toInt().coerceIn(it.frames.indices) }
            "replay-step" -> replay?.let { replayIndex = (replayIndex + arg.toInt()).coerceIn(it.frames.indices) }
            "replay-street" -> replay?.frames?.indexOfFirst { it.street.name == arg }?.takeIf { it >= 0 }?.let { replayIndex = it }
            "star-current" -> if (running != null) {
                val last = running.lastHand
                if (last != null && last.handNumber == running.handNumber) {
                    running.starred = store.toggleFavorite(last).favorite
                } else running.starred = !running.starred
            }
            "star" -> store.active.history.firstOrNull { it.id.toString() == arg }?.let {
                val updated = store.toggleFavorite(it)
                if (replay?.record?.id == updated.id) replay = Replayer(updated)
                if (running != null && running.lastHand?.id == updated.id && running.lastHand?.handNumber == running.handNumber) {
                    running.starred = updated.favorite
                }
            }
            "add-save" -> window.prompt(s.saveName, s.saveDefaultName(store.slots.size + 1))
                ?.takeIf { it.isNotBlank() }?.let(store::add)
            "use", "rename", "delete" -> store.slots.firstOrNull { it.id == arg }?.let { slot ->
                when (key) {
                    "use" -> store.select(slot)
                    "rename" -> window.prompt(s.saveName, slot.name)?.takeIf { it.isNotBlank() }?.let { store.rename(slot, it) }
                    "delete" -> if (window.confirm(s.deleteSaveTitle(slot.name) + "\n" + s.deleteSaveText)) store.remove(slot)
                }
            }
            "clear-history" -> if (window.confirm(s.clearHistoryConfirm)) store.clearHistory()
        }
    }

    private fun amount(chips: Int, bb: Int): String =
        if (store.flag("bb", true)) "${decimal(chips.toDouble() / bb)} BB" else chips.toString()

    private fun render() {
        document.documentElement?.setAttribute("lang", if (s.isZh) "zh-CN" else "en")
        val content = when (screen) {
            Screen.HOME -> home()
            Screen.SETUP -> page(s.newGame, setup.render(s))
            Screen.TABLE -> game?.let(::table).orEmpty()
            Screen.REPLAY -> replay?.let(::replayView).orEmpty()
            Screen.RESULT -> page(s.result, game?.let { reportView(it, s) }.orEmpty())
            Screen.HISTORY -> page(s.history, historyView(store.active, favorites, deck, s))
            Screen.STATS -> page(s.stats, statsView(store.active, s))
            Screen.SAVES -> page(s.saves, savesView(store, s))
            Screen.ACHIEVEMENTS -> page(s.achievements, achievementsView(store.active, s))
            Screen.SETTINGS -> page(s.settings, settings())
            Screen.GUIDE -> page(s.guide, referenceScreen("guide", s.isZh, startAtGlossary = referenceBack == Screen.TABLE), "guide-back")
            Screen.CHANGELOG -> page(s.whatsNew, referenceScreen("changelog", s.isZh, changelog, latestOnly) +
                if (latestOnly) button(s.allUpdates, "show-updates") else "", "close-updates")
        }
        val banner = when {
            store.writeFailed -> if (s.isZh) "设备存储已满或不可用，本次部分改动可能无法保存。" else "Device storage is full or unavailable. Some changes may be lost."
            else -> notice
        }
        root.innerHTML = (if (banner.isBlank()) "" else """<div role="status" class="notice">${escape(banner)}</div>""") + content
    }

    private fun page(title: String, content: String, back: String = "home") =
        """<header class="page-heading">${button(s.back, back)}<h1>${escape(title)}</h1></header>$content"""

    private fun home(): String = buildString {
        append("""<header class="lobby-header"><h1>River Runner <span aria-hidden="true">♠</span></h1>
            ${button(s.settings, "settings")}</header>
            <section class="lobby panel"><div><h2>${escape(store.active.name)}</h2>
            <p>${escape(s.handsLabel)} ${store.active.counters[Progress.HANDS] ?: 0} ·
            ${escape(s.achievements)} ${store.active.unlocked.size} / ${Achievements.all.size}</p></div>""")
        append("""<div class="toolbar">${button(s.quickStart, "quick", "primary")}${button(s.newGame, "setup")}</div></section>""")
        append("""<nav class="lobby-nav">""")
        for ((title, action) in listOf(s.history to "history", s.stats to "stats", s.saves to "saves",
            s.achievements to "achievements", s.guide to "guide")) append(button(title, action))
        append("</nav>")
    }

    private fun settings(): String = buildString {
        append("""<section class="panel form-grid">""")
        append(select(s.language, "zh", listOf("true" to "中文", "false" to "English"), s.isZh.toString()))
        append(select(s.deckStyle, "deck", listOf("four" to s.deckFour, "filled" to s.deckFilled, "classic" to s.deckClassic), deck))
        append(select(s.speed, "speed", listOf("0" to s.slow, "1" to s.normal, "2" to s.fast), store.get("speed") ?: "1"))
        append(cards(listOf("As", "Kh", "Qd", "Jc").map(Cards::parse), deck))
        append("""<div class="toolbar">${button(s.practiceDefault, "toggle:practice", pressed = store.flag("practice", true))}
            ${button(s.showInBB, "toggle:bb", pressed = store.flag("bb", true))}
            ${button(s.sound, "toggle:sound", pressed = store.flag("sound", true))}</div></section>""")
        append("""<section class="panel"><h2>${escape(s.about)}</h2><p>${escape(s.aboutText)}</p>
            <p><a href="https://github.com/shunzhe1121/river-runner">GitHub</a></p>
            ${button(s.whatsNew, "changelog")}</section>""")
        append(button(s.clearHistory, "clear-history", "danger"))
    }

    private fun table(running: Practice): String = buildString {
        val hand = running.hand
        val bb = hand.bigBlind
        val legal = running.legal
        val completed = running.lastHand?.handNumber == running.handNumber
        val result = hand.result
        val levelIndex = (running.handNumber - 1) / running.config.handsPerLevel
        val level = if (running.config.mode == GameMode.TOURNAMENT) GameSession.levelFor(levelIndex).level else 1
        val acting = if (hand.phase == Phase.BETTING) hand.toAct else -1
        val showHud = store.flag("hud")
        fun fmt(chips: Int) = amount(chips, bb)
        append("""<header class="table-header"><div><h1>${escape(s.handNo(running.handNumber))}</h1>
            <p>${escape(s.blindsText(hand.setup.smallBlind, bb, hand.setup.ante))} · ${escape(s.street(hand.street))}</p></div>
            ${button(s.leaveTable, "quit")}</header>""")
        if (running.config.mode == GameMode.TOURNAMENT) {
            val until = running.config.handsPerLevel - (running.handNumber - 1) % running.config.handsPerLevel
            append("<p class=\"muted\">${escape(s.levelText(level, until))}</p>")
        }
        val lastActions = hand.actions.filter { it.street == hand.street && it.type != ActionType.ANTE }.associateBy { it.seat }
        append(tableSeats(hand.players, hand.board, hand.buttonSeat, acting, fmt(hand.pot), s, deck, ::fmt,
            { seat -> seat == GameSession.HERO_SEAT || seat in result?.showdown.orEmpty() }
        ) { seat ->
            buildString {
                lastActions[seat.seat]?.let { append("<span>${escape(s.action(it, ::fmt))}</span>") }
                if (showHud) running.hud[seat.seat]?.let {
                    append("<span class=\"muted\">${escape(s.hudShort((it.vpip * 100).roundToInt(), (it.pfr * 100).roundToInt()))}</span>")
                }
                result?.scores?.get(seat.seat)?.let { append("<span>${escape(HandEvaluator.categoryName(it, s.isZh))}</span>") }
                result?.won?.get(seat.seat)?.takeIf { it > 0 }?.let { append("<strong class=\"win\">+${escape(fmt(it))}</strong>") }
            }
        })
        if (completed && result != null) {
            val net = result.net[GameSession.HERO_SEAT] ?: 0
            val winners = hand.players.filter { (result.won[it.seat] ?: 0) > it.committed }
                .joinToString(", ") { if (it.seat == GameSession.HERO_SEAT) s.you else it.name }
            append("""<section class="panel result-line"><div><strong>${escape(s.handResult)}</strong>
                <span>${escape(if (winners.isBlank()) s.split else s.winsFor(winners))} · ${if (net > 0) "+" else ""}${escape(fmt(net))}</span></div>
                ${button(if (running.session.isOver) s.viewResults else s.nextHand, "next-hand", "primary")}</section>""")
        } else if (legal != null) {
            if (playPractice) running.training?.let { info ->
                val hero = hand.player(GameSession.HERO_SEAT)
                append("""<section class="panel practice"><strong>${escape(practiceHandText(hero.hole, hand.board, s.isZh))}</strong><dl class="metrics">""")
                append(metric(s.toCall, fmt(info.toCall)))
                append(metric(s.potOdds, if (info.toCall > 0) "1 : ${decimal(info.pot.toDouble() / info.toCall)}" else "–"))
                append(metric(s.needEquity, percent(info.requiredEquity)))
                append(metric(s.spr, decimal(info.spr)))
                append("</dl></section>")
            }
            append("""<section class="panel controls" aria-label="${escape(s.yourTurn)}"><div class="toolbar">""")
            if (!legal.canCheck) append(button(s.fold, "game-fold", "danger"))
            if (legal.canCheck) append(button(s.check, "game-check", "primary"))
            if (legal.canCall) {
                val label = if (legal.toCall >= legal.maxRaiseTo - legal.myStreetBet) s.allIn else s.call
                append(button("$label ${fmt(legal.toCall)}", "game-call", "primary"))
            }
            if (legal.canRaise) append(button(s.allIn, "game-allin"))
            append("</div>")
            if (legal.canRaise) {
                val label = if (legal.isOpeningBet) s.bet else s.raiseTo
                val target = defaultBetTarget(legal, bb)
                append("""<div class="toolbar sizing">""")
                for ((name, size) in betPresets(s.potShort, s.allIn, legal, bb)) {
                    append(button(name, "size:$size", pressed = target == size))
                }
                append("</div>")
                append("""<div class="raise"><label for="raise-size">${escape(label)} <output id="raise-value">
                    ${escape(fmt(target))}</output></label>
                    <input id="raise-size" type="range" min="${legal.minRaiseTo}" max="${legal.maxRaiseTo}" value="$target">
                    ${button(label, "game-raise", "primary")}</div>""")
            }
            append("</section>")
        } else {
            val actor = hand.players.firstOrNull { it.seat == acting }
            val label = if (actor == null) s.dealing else s.thinking(actor.name)
            append("""<div class="toolbar table-status"><span>${escape(label)}</span>
                ${button(if (hand.player(GameSession.HERO_SEAT).folded) s.skipHand else s.fastForward, "fast-forward", pressed = running.fastForward)}</div>""")
        }
        append("""<nav class="toolbar table-options">""")
        append(button(s.chipPractice, "practice", pressed = playPractice))
        append(button(s.chipReview, "toggle:review", pressed = store.flag("review")))
        append(button(s.chipHud, "toggle:hud", pressed = showHud))
        append(button(s.chipDeviation, "toggle:dev", pressed = store.flag("dev")))
        append(button(s.sound, "toggle:sound", pressed = store.flag("sound", true)))
        append(button((if (running.starred) "★ " else "☆ ") + s.starHand, "star-current", pressed = running.starred))
        if (running.lastHand != null) append(button(s.replayLast, "replay-last"))
        append(button(s.guide, "guide"))
        append("</nav>")
        if (store.flag("dev")) running.feedback?.let { append(feedbackView(it, s, ::fmt)) }
        append(actionLog(hand.actions, hand.players, s, ::fmt, logExpanded))
    }

    private fun replayView(player: Replayer): String = buildString {
        val hand = player.record
        val frame = player.frames[replayIndex]
        val street = s.street(frame.street)
        val bb = hand.bigBlind
        fun fmt(chips: Int) = amount(chips, bb)
        val title = "${s.replay} · ${s.handNo(hand.handNumber)}"
        append("""<header class="page-heading">${button(if (autoReview) s.reviewNext else s.back, "replay-back")}
            <h1>${escape(title)}</h1></header>""")
        append("""<nav class="toolbar replay-streets">""")
        for (stage in player.frames.map { it.street }.distinct()) {
            append(button(s.street(stage), "replay-street:${stage.name}", pressed = stage == frame.street))
        }
        append("</nav>")
        val equity = if (frame.isEnd) emptyMap() else equityCache.getOrPut(
            frame.board to frame.players.filterNot { it.folded }.map { it.seat },
        ) { replayEquities(frame, hand.id) }
        append(tableSeats(frame.players, frame.board, hand.buttonSeat, frame.toAct, fmt(frame.pot), s, deck, ::fmt, { true }) { seat ->
            buildString {
                equity[seat.seat]?.let { append("<span>${escape(s.equity)} ${percent(it)}</span>") }
                if (seat.seat != hand.heroSeat) hand.players.find { it.seat == seat.seat }?.let {
                    append("<span class=\"muted\">${escape(s.styleName(it.style))}</span>")
                }
                if (frame.isEnd) hand.won[seat.seat]?.let { append("<strong class=\"win\">+${escape(fmt(it))}</strong>") }
            }
        })
        append("""<nav class="toolbar replay-controls">
            ${if (replayIndex > 0) button(s.prev, "replay-step:-1") else "<button disabled>${escape(s.prev)}</button>"}
            <output>${escape(street)} · ${replayIndex + 1} / ${player.frames.size}</output>
            ${if (replayIndex < player.frames.lastIndex) button(s.next, "replay-step:1") else "<button disabled>${escape(s.next)}</button>"}
            ${button((if (hand.favorite) "★ " else "☆ ") + s.starHand, "star:${hand.id}", pressed = hand.favorite)}
            </nav>""")
        frame.action?.let {
            val name = if (it.seat == hand.heroSeat) s.you else hand.players.find { seat -> seat.seat == it.seat }?.name.orEmpty()
            append("<p>${escape(name)} · ${escape(s.action(it, ::fmt))}</p>")
        }
        val feedback = if (adviceCache.containsKey(frame.index)) adviceCache[frame.index] else {
            replayAdvice(player, frame).also { adviceCache[frame.index] = it }
        }
        if (feedback != null) append(feedbackView(feedback, s, ::fmt, showEquity = true))
        else append("<p class=\"muted\">${escape(s.replayNoSuggestion)}</p>")
        append(replayLog(player.frames, frame.index, s, ::fmt))
    }
}
