package com.riverrunner.web

import com.riverrunner.engine.Action
import com.riverrunner.engine.Bot
import com.riverrunner.engine.BotStyle
import com.riverrunner.engine.Cards
import com.riverrunner.engine.Deviation
import com.riverrunner.engine.DeviationTally
import com.riverrunner.engine.Equity
import com.riverrunner.engine.Facts
import com.riverrunner.engine.GameSession
import com.riverrunner.engine.HandClasses
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandEvaluator
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.engine.Phase
import com.riverrunner.engine.Policy
import com.riverrunner.engine.Progress
import com.riverrunner.engine.ReplayFrame
import com.riverrunner.engine.Replayer
import com.riverrunner.engine.SeatStats
import com.riverrunner.engine.SeatStatsCalc
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.SessionSummary
import com.riverrunner.engine.Stats
import com.riverrunner.engine.Street
import com.riverrunner.i18n.EN
import com.riverrunner.i18n.ZH
import kotlin.random.Random

data class TrainingMetrics(
    val toCall: Int,
    val pot: Int,
    val requiredEquity: Double,
    val spr: Double,
)

data class PracticeFeedback(
    val policy: Policy,
    val action: Action,
    val verdict: Deviation.Verdict,
)

/** Advances by one transition at a time so the browser can paint between decisions and streets. */
class Practice(
    val config: SessionConfig,
    private val onHand: (HandRecord) -> Unit,
    private val onProgress: (Map<String, Long>) -> Unit,
) {
    val session = GameSession(config)
    var hand: HandEngine = session.startHand()
        private set
    var fastForward = false
    var starred = false
    var lastHand: HandRecord? = null
        private set
    var hud: Map<Int, SeatStats> = emptyMap()
        private set
    var feedback: PracticeFeedback? = null
        private set
    var tally = DeviationTally()
        private set
    var summary: SessionSummary? = null
        private set
    private val records = ArrayList<HandRecord>()
    private val verdicts = ArrayList<Pair<Street, Deviation.Verdict>>()
    private var recorded = false

    val handNumber: Int get() = session.handNumber + if (recorded) 0 else 1
    val legal: LegalActions?
        get() = if (hand.phase == Phase.BETTING && hand.toAct == GameSession.HERO_SEAT && summary == null) {
            hand.legalActions()
        } else null
    val training: TrainingMetrics?
        get() = legal?.let { actions ->
            TrainingMetrics(
                actions.toCall,
                actions.pot,
                if (actions.toCall > 0) actions.toCall.toDouble() / (actions.pot + actions.toCall) else 0.0,
                hand.player(actions.seat).stack.toDouble() / maxOf(1, actions.pot),
            )
        }

    fun act(move: Move): Action? {
        val actions = legal ?: return null
        val seed = config.seed xor (handNumber.toLong() shl 32) xor hand.actions.size.toLong()
        val policy = Bot.decide(hand, actions.seat, BotStyle.GTO, Random(seed), session.tournamentContext())
        val action = hand.apply(move)
        val verdict = Deviation.judge(policy, action)
        feedback = PracticeFeedback(policy, action, verdict)
        verdicts += action.street to verdict
        return action
    }

    /** False when the hero needs to act or the table is waiting for the next hand. */
    fun tick(): Boolean {
        if (recorded || summary != null) return false
        when (hand.phase) {
            Phase.BETTING -> {
                val seat = hand.toAct
                if (seat == GameSession.HERO_SEAT) {
                    fastForward = false
                    return false
                }
                val policy = Bot.decide(hand, seat, session.players[seat].style, session.random, session.tournamentContext())
                hand.apply(policy.sample(session.random))
            }
            Phase.STREET_DONE -> hand.dealNextStreet()
            Phase.HAND_DONE -> completeHand()
        }
        return true
    }

    fun next() {
        if (!recorded || summary != null) return
        if (session.isOver) {
            finish(early = false)
            return
        }
        hand = session.startHand()
        recorded = false
        starred = false
        fastForward = false
        feedback = null
    }

    fun quit() {
        if (summary != null) return
        while (!recorded) {
            if (legal != null) hand.apply(Move.FOLD) else tick()
        }
        finish(early = !session.isOver)
    }

    private fun completeHand() {
        val record = session.completeHand(hand).copy(favorite = starred)
        recorded = true
        fastForward = false
        lastHand = record
        records += record
        hud = session.players.filter { !it.isHuman }.associate { it.seat to SeatStatsCalc.forSeat(records, it.seat) }
        val position = Stats.positionOf(record)
        for ((street, verdict) in verdicts) tally = tally.add(street, position, verdict)
        verdicts.clear()
        onHand(record)
        onProgress(Facts.forHand(record))
        if (record.favorite) onProgress(mapOf(Progress.STARS to 1L))
    }

    private fun finish(early: Boolean) {
        val result = session.finish(early)
        summary = result
        onProgress(Facts.forSession(result, config))
    }
}

fun replayAdvice(replayer: Replayer, frame: ReplayFrame): PracticeFeedback? {
    val hero = replayer.record.heroSeat
    val actionsSoFar = replayer.frames.take(frame.index + 1).count { it.action != null }
    val index = when {
        frame.action?.seat == hero -> actionsSoFar - 1
        frame.toAct == hero && !frame.isEnd -> actionsSoFar
        else -> return null
    }
    val action = replayer.record.actions.filter { !it.type.isPost }.getOrNull(index) ?: return null
    val engine = replayer.engineBeforeAction(index)
    val policy = Bot.decide(engine, hero, BotStyle.GTO, Random(replayer.record.id + index))
    return PracticeFeedback(policy, action, Deviation.judge(policy, action))
}

fun replayEquities(frame: ReplayFrame, seed: Long = 0): Map<Int, Double> {
    val live = frame.players.filter { !it.folded }
    if (frame.isEnd || live.size < 2) return emptyMap()
    val equities = Equity.showdown(live.map { it.hole }, frame.board, Random(seed))
    return live.mapIndexed { index, player -> player.seat to equities[index] }.toMap()
}

fun practiceHandText(hole: IntArray, board: List<Int>, zh: Boolean): String {
    val strings = if (zh) ZH else EN
    if (board.size < 3) {
        val name = HandClasses.className(hole[0], hole[1])
        val top = (HandClasses.percentile(hole[0], hole[1]) * 100).toInt().coerceAtLeast(1)
        return "$name · ${strings.topPercent(top)}"
    }
    val score = HandEvaluator.evaluate((board + hole.toList()).toIntArray())
    val category = HandEvaluator.category(score)
    val name = HandEvaluator.categoryName(score, zh)
    fun rankText(rank: Int) = Cards.RANKS[rank].let { if (it == 'T') "10" else it.toString() }
    val highBoard = board.maxOf { Cards.rank(it) }
    val first = Cards.rank(hole[0])
    val second = Cards.rank(hole[1])
    val boardCategory = HandEvaluator.category(HandEvaluator.evaluate(board.toIntArray()))
    val primary = (score ushr 16) and 0xF
    return when {
        category == HandEvaluator.PAIR && boardCategory == HandEvaluator.PAIR -> strings.boardPair
        category == HandEvaluator.PAIR && first == second && first > highBoard ->
            "${strings.overPair} ${rankText(first)}"
        category == HandEvaluator.PAIR && primary == highBoard && (first == primary || second == primary) ->
            "${strings.topPair} ${rankText(primary)}"
        category == HandEvaluator.TWO_PAIR -> "$name ${rankText(primary)} & ${rankText((score ushr 12) and 0xF)}"
        category == HandEvaluator.TRIPS && first == second -> "${strings.set} ${rankText(primary)}"
        category == HandEvaluator.PAIR || category == HandEvaluator.TRIPS || category == HandEvaluator.HIGH_CARD ->
            "$name ${rankText(primary)}"
        else -> name
    }
}
