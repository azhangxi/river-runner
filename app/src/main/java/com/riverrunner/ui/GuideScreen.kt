package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.reference.Guide
import kotlin.math.cos
import kotlin.math.sin

enum class GuideTab { BASICS, RANKINGS, POSITIONS, GLOSSARY, TIPS, APP }

@Composable
fun GuideScreen(vm: AppViewModel) {
    BackHandler { vm.screen = Screen.Home }
    GuideContent(onClose = { vm.screen = Screen.Home })
}

@Composable
fun GuideContent(onClose: () -> Unit, startTab: GuideTab = GuideTab.BASICS) {
    val st = LocalStrings.current
    val zh = st.isZh
    var tab by remember { mutableStateOf(startTab) }
    val scroll = rememberScrollState()
    LaunchedEffect(tab) { scroll.scrollTo(0) }
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallButton("←", onClose)
                Spacer(Modifier.width(8.dp))
                Text(st.guide, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            for (t in GuideTab.entries) {
                val sel = t == tab
                Box(
                    Modifier.fillMaxWidth().pressable { tab = t }.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Palette.AccentDim else Palette.Panel)
                        .then(if (sel) Modifier.border(1.dp, Palette.Accent, RoundedCornerShape(8.dp)) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) { Text(tabTitle(t, zh), color = Palette.Text, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
                .padding(14.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                GuideTab.BASICS -> Basics(zh)
                GuideTab.RANKINGS -> Rankings(zh)
                GuideTab.POSITIONS -> Positions(zh)
                GuideTab.GLOSSARY -> Glossary(zh)
                GuideTab.TIPS -> Paragraphs(Guide.tips(zh))
                GuideTab.APP -> Paragraphs(Guide.app(zh))
            }
        }
    }
}

private fun tabTitle(t: GuideTab, zh: Boolean) = when (t) {
    GuideTab.BASICS -> if (zh) "① 基本玩法" else "① How to play"
    GuideTab.RANKINGS -> if (zh) "② 牌型大小" else "② Hand rankings"
    GuideTab.POSITIONS -> if (zh) "③ 位置" else "③ Positions"
    GuideTab.GLOSSARY -> if (zh) "④ 术语表" else "④ Glossary"
    GuideTab.TIPS -> if (zh) "⑤ 新手建议" else "⑤ Beginner tips"
    GuideTab.APP -> if (zh) "⑥ 软件说明" else "⑥ Using the app"
}

@Composable
private fun H(text: String) = Text(text, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)

@Composable
private fun P(text: String) = Text(text, color = Palette.Text, fontSize = 13.sp, lineHeight = 20.sp)

@Composable
private fun Paragraphs(items: List<Pair<String, String>>) {
    for ((title, body) in items) {
        H(title)
        P(body)
        Spacer(Modifier.height(2.dp))
    }
}

// ----------------------------------------------------------------- basics

@Composable
private fun Basics(zh: Boolean) {
    val content = Guide.basics(zh)
    H(content.goalTitle)
    P(content.goal)
    H(content.flowTitle)
    Steps(content.flow)
    H(content.actionsTitle)
    Steps(content.actions)
    H(content.buttonTitle)
    P(content.button)
}

@Composable
private fun Steps(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((i, it) in items.withIndex()) {
            Row {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.AccentDim), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(it.first, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(it.second, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------- rankings

@Composable
private fun Rankings(zh: Boolean) {
    H(if (zh) "从大到小" else "Strongest to weakest")
    for ((i, r) in Guide.ranks.withIndex()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", color = Palette.Accent, fontSize = 18.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Column(Modifier.width(118.dp)) {
                Text(if (zh) r.zh else r.en, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(if (zh) r.zhDesc else r.enDesc, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in Cards.parseMany(r.cards)) PlayingCard(c, 26.dp)
            }
        }
    }
    H(if (zh) "比大小的细节" else "Tie-breaks")
    P(Guide.tieBreaks(zh))
}

// ----------------------------------------------------------------- positions

@Composable
private fun Positions(zh: Boolean) {
    H(if (zh) "8人桌的位置" else "Positions at an 8-handed table")
    BoxWithConstraints(Modifier.fillMaxWidth().height(190.dp)) {
        val w = maxWidth.value
        val h = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val rx = w * 0.30f * d
            val ry = h * 0.32f * d
            val c = Offset(w / 2 * d, h / 2 * d)
            drawOval(Palette.Rail, Offset(c.x - rx - 8 * d, c.y - ry - 8 * d), Size((rx + 8 * d) * 2, (ry + 8 * d) * 2))
            drawOval(Brush.radialGradient(listOf(Palette.FeltCenter, Palette.FeltEdge), c, rx), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
        }
        Text(
            if (zh) "顺时针行动 ↻" else "Action moves clockwise ↻", color = Palette.Text.copy(alpha = 0.8f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        for ((i, l) in Guide.seats.withIndex()) {
            val ang = Math.toRadians(90.0 + i * 45.0)
            val x = w / 2 + w * 0.40f * cos(ang).toFloat()
            val y = h / 2 + h * 0.42f * sin(ang).toFloat()
            val color = when (l) {
                "BTN", "CO" -> Palette.Green
                "SB", "BB" -> Palette.Blue
                "HJ" -> Palette.Gold
                else -> Palette.Red
            }
            Box(
                Modifier.offset((x - 30).dp, (y - 11).dp).width(60.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
    for ((t, d) in Guide.positions(zh)) {
        Text(t, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(d, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 18.sp)
    }
    H(if (zh) "为什么位置重要" else "Why position matters")
    P(Guide.whyPositionMatters(zh))
}

// ----------------------------------------------------------------- glossary

)

@Composable
private fun Glossary(zh: Boolean) {
    H(if (zh) "常用术语" else "Common terms")
    for (t in Guide.terms) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (zh) t.zh else t.en, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (t.full.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(t.full, color = Palette.Accent, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
            Text(if (zh) t.zhDesc else t.enDesc, color = Palette.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
