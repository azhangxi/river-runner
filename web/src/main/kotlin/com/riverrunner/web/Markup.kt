package com.riverrunner.web

import com.riverrunner.engine.*
import com.riverrunner.i18n.Strings
import kotlin.math.roundToInt

internal fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

internal fun button(label: String, action: String, css: String = "", pressed: Boolean? = null): String {
    val state = if (pressed == null) "" else " aria-pressed=\"$pressed\""
    return """<button type="button" class="$css" data-action="$action"$state>${escape(label)}</button>"""
}

internal fun select(label: String, name: String, options: List<Pair<String, String>>, selected: String): String =
    """<label class="field"><span>${escape(label)}</span><select data-field="$name">""" +
        options.joinToString("") { (value, title) ->
            """<option value="${escape(value)}"${if (value == selected) " selected" else ""}>${escape(title)}</option>"""
        } + "</select></label>"

internal fun metric(label: String, value: String) =
    "<div><dt>${escape(label)}</dt><dd>${escape(value)}</dd></div>"

internal fun decimal(value: Double): String = value.asDynamic().toFixed(1) as String
internal fun percent(value: Double): String = "${(value * 100).roundToInt()}%"
internal fun signed(value: Double): String = if (value > 0) "+${decimal(value)}" else decimal(value)

internal fun Strings.street(street: Street) = when (street) {
    Street.PREFLOP -> preflop
    Street.FLOP -> flop
    Street.TURN -> turn
    Street.RIVER -> river
}

internal fun Strings.action(action: Action, amount: (Int) -> String) = when (action.type) {
    ActionType.FOLD -> fold
    ActionType.CHECK -> check
    ActionType.CALL -> "${if (action.allIn) allIn else call} ${amount(action.amount)}"
    ActionType.BET -> "${if (action.allIn) allIn else bet} ${amount(action.streetTotal)}"
    ActionType.RAISE -> "${if (action.allIn) allIn else raiseTo} ${amount(action.streetTotal)}"
    ActionType.ANTE -> "$ante ${amount(action.amount)}"
    ActionType.SMALL_BLIND -> "$smallBlindShort ${amount(action.amount)}"
    ActionType.BIG_BLIND -> "$bigBlindShort ${amount(action.amount)}"
}

internal fun Strings.move(type: MoveType) = when (type) {
    MoveType.FOLD -> fold
    MoveType.CHECK -> check
    MoveType.CALL -> call
    MoveType.RAISE -> raise
}

internal fun cards(values: List<Int>, deck: String, concealed: Int = 0): String {
    val suits = listOf("♣", "♦", "♥", "♠")
    return """<span class="cards deck-$deck">""" + values.joinToString("") { card ->
        val rank = Cards.toString(card).first()
        val suit = Cards.suit(card)
        """<span class="card suit-$suit" aria-label="$rank${suits[suit]}"><b>$rank</b><span>${suits[suit]}</span></span>"""
    } + """<span class="card back" aria-hidden="true"></span>""".repeat(concealed) + "</span>"
}
