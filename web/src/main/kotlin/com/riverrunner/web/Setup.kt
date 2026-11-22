package com.riverrunner.web

import com.riverrunner.engine.*
import com.riverrunner.i18n.Strings

internal class Setup(private val store: BrowserStore) {
    private var mode = if (store.get("mode") == "T") GameMode.TOURNAMENT else GameMode.CASH
    private var size = store.get("size")?.toIntOrNull() ?: 6
    private var stack = store.get("stack")?.toIntOrNull() ?: 100
    private var type = if (store.flag("mixed")) TableType.MIXED else TableType.GTO
    private var handsPerLevel = store.get("hpl")?.toIntOrNull() ?: 10
    private var ante = store.flag("ante")
    private var preset = store.get("preset") ?: TablePreset.RANDOM.name
    private var custom = store.get("mix")?.let(StyleMix::decode) ?: StyleMix(randomSpecial = 2)

    private val stackOptions get() = if (mode == GameMode.CASH) listOf(50, 100, 150, 200)
        else listOf(20, 30, 50, 75, 100)

    fun update(field: String, value: String) {
        when (field) {
            "mode" -> {
                mode = GameMode.fromCode(value)
                if (stack !in stackOptions) stack = if (mode == GameMode.CASH) 100 else 50
            }
            "size" -> size = value.toInt()
            "stack" -> stack = value.toInt()
            "type" -> type = TableType.valueOf(value)
            "hpl" -> handsPerLevel = value.toInt()
            "ante" -> ante = value == "yes"
            "preset" -> preset = value
            "custom-random" -> custom = custom.fit(size - 1).copy(randomSpecial = value.toInt())
            else -> if (field.startsWith("custom-")) {
                val style = BotStyle.valueOf(field.removePrefix("custom-"))
                val fitted = custom.fit(size - 1)
                custom = fitted.copy(counts = fitted.counts + (style to value.toInt()))
            }
        }
    }

    fun config(): SessionConfig {
        store.put("mode", mode.code)
        store.put("size", size.toString())
        store.put("stack", stack.toString())
        store.put("mixed", (type == TableType.MIXED).toString())
        store.put("hpl", handsPerLevel.toString())
        store.put("ante", ante.toString())
        store.put("preset", preset)
        store.put("mix", custom.encode())
        val styleMix = when {
            type == TableType.GTO -> null
            preset == "custom" -> custom.fit(size - 1)
            else -> TablePreset.valueOf(preset).mix
        }
        return SessionConfig(mode, size, stack, type, handsPerLevel, if (ante) 0.125 else 0.0, styleMix = styleMix)
    }

    fun render(s: Strings): String = buildString {
        fun number(label: String, key: String, values: List<Int>, current: Int) {
            append(select(label, key, values.map { it.toString() to it.toString() }, current.toString()))
        }
        append("""<section class="panel form-grid">""")
        append(select(s.mode, "mode", listOf("C" to s.cash, "T" to s.tournament), mode.code))
        number(s.players, "size", listOf(2, 5, 6, 7, 8), size)
        number(s.stackLabelBB, "stack", stackOptions, stack)
        append(select(s.tableType, "type", listOf("GTO" to s.gtoTable, "MIXED" to s.mixedTable), type.name))
        if (mode == GameMode.TOURNAMENT) number(s.handsPerLevel, "hpl", listOf(5, 8, 10, 15, 20), handsPerLevel)
        else append(select(s.ante, "ante", listOf("no" to s.none, "yes" to "12.5%"), if (ante) "yes" else "no"))
        if (type == TableType.MIXED) {
            append(select(s.lineup, "preset", TablePreset.entries.map { it.name to s.presetName(it) } +
                ("custom" to s.presetName(null)), preset))
            if (preset == "custom") {
                val fitted = custom.fit(size - 1)
                val room = (size - 1 - fitted.total).coerceAtLeast(0)
                for (style in StyleMix.SPECIAL) {
                    val count = fitted.count(style)
                    number(s.styleName(style), "custom-${style.name}", (0..room + count).toList(), count)
                }
                number(s.randomStyle, "custom-random", (0..room + fitted.randomSpecial).toList(), fitted.randomSpecial)
                append("<p>${escape(s.specialCount(fitted.total, size - 1))}</p>")
            }
        }
        append("</section>")
        append(button(s.start, "start", "primary fill"))
    }
}
