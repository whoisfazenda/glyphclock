package dev.glyphalarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- grouped rows, as in the Nothing settings ---------------------------------------------------

private val GROUP_OUTER = 24.dp
private val GROUP_INNER = 6.dp

/** Shape of row [index] in a group of [count]: big corners on the outside, small ones between rows. */
fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GROUP_OUTER else GROUP_INNER
    val bottom = if (index == count - 1) GROUP_OUTER else GROUP_INNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Background of one row of a group. */
@Composable
fun Modifier.nRow(shape: Shape = RoundedCornerShape(GROUP_INNER)): Modifier =
    this.fillMaxWidth().clip(shape).background(LocalN.current.surface, shape)

/** Rows stacked with hairline gaps under one rounded outline. */
@Composable
fun NGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_OUTER)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/** Caption above a group. */
@Composable
fun NSection(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        NCaps(title, Modifier.weight(1f))
        trailing()
    }
}

/** One settings-like row: optional round icon, title, optional second line, then a value or any trailing control. */
@Composable
fun NRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: Ic? = null,
    titleColor: Color = LocalN.current.display,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val n = LocalN.current
    val tap = rememberTap()
    Row(
        modifier
            .nRow()
            .then(if (onClick != null) Modifier.clickable { tap(); onClick() } else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(40.dp).background(n.bg, CircleShape), contentAlignment = Alignment.Center) { NIcon(leading, size = 20.dp) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            NText(title, style = NType.body, color = titleColor)
            if (!subtitle.isNullOrBlank()) { Spacer(Modifier.height(2.dp)); NMeta(subtitle) }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun ToggleRow(text: String, on: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    NRow(text, subtitle = subtitle, onClick = { onChange(!on) }) { NSwitch(on, onChange) }
}

// ---- floating bottom bar ----------------------------------------------------------------------

private val TABS = listOf(Ic.ALARM to "Будильник", Ic.WORLD to "Мир", Ic.TIMER to "Таймер", Ic.STOPWATCH to "Секундомер")

/** Height the bar takes above the system navigation inset; lists add it to their bottom padding. */
val NavBarSpace = 96.dp

/** Pill with the four tabs (only the active one is labelled) and, when a tab can add something, a round "+" beside it. */
@Composable
fun NavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, onPlus: (() -> Unit)? = null) {
    val n = LocalN.current
    val tap = rememberTap()
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, n.bg.copy(alpha = 0.92f), n.bg)))) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(64.dp).clip(CircleShape).background(n.surface, CircleShape).border(1.dp, n.border, CircleShape).padding(6.dp).animateContentSize(tween(180)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEachIndexed { i, (ic, label) ->
                    val on = i == selected
                    val bg by animateColorAsState(if (on) n.surfaceRaised else Color.Transparent, tween(180), label = "tab")
                    Row(
                        Modifier
                            .then(if (on) Modifier else Modifier.weight(1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(bg, CircleShape)
                            .clickable(remember { MutableInteractionSource() }, null) { if (!on) { tap(); onSelect(i) } }
                            .padding(horizontal = if (on) 16.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        NIcon(ic, tint = if (on) n.display else n.secondary, size = 22.dp)
                        if (on) {
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = n.display, style = NType.bodyMedium.copy(fontSize = 14.sp), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            if (onPlus != null) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(n.display, CircleShape).clickable { tap(); onPlus() },
                    contentAlignment = Alignment.Center,
                ) { NIcon(Ic.PLUS, tint = n.bg, size = 26.dp) }
            }
        }
    }
}

/** "Deleted · Undo" pill shown above the bottom bar for a few seconds. */
@Composable
fun UndoBar(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val n = LocalN.current
    val tap = rememberTap()
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(n.surfaceRaised, CircleShape)
            .padding(start = 22.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NText(text, Modifier.weight(1f), style = NType.body, maxLines = 1)
        Box(
            Modifier.clip(CircleShape).background(n.display, CircleShape).clickable { tap(); onAction() }.padding(horizontal = 18.dp, vertical = 10.dp),
        ) { Text(action, color = n.bg, style = NType.bodyMedium.copy(fontSize = 14.sp)) }
    }
}
