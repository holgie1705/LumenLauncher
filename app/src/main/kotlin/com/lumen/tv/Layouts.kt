package com.lumen.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Alles, was die Layouts zum Zeichnen der Kacheln brauchen. */
class LayoutCtx(
    val style: TileStyle, val scale: Float, val gap: Dp, val vm: HomeViewModel,
    val onHero: (HeroInfo) -> Unit, val onMenu: (TvApp, AppRowUi) -> Unit,
    val onRadioSearch: () -> Unit = {}, val onStationMenu: (Station) -> Unit = {},
    val onWebOpen: (WebWidget) -> Unit = {}, val onWebMenu: (WebWidget) -> Unit = {},
)

private fun focusMod(i: Int, r: FocusRequester?) = if (i == 0 && r != null) Modifier.focusRequester(r) else Modifier

@Composable
private fun AppCell(a: TvApp, row: AppRowUi, c: LayoutCtx, mod: Modifier) = AppTile(
    a, c.style, c.scale, c.vm, mod,
    onFocus = { c.onHero(HeroInfo(a.pkg, a.label, app = a)) },
    onClick = { c.vm.open(a) },
    onLong = { c.onMenu(a, row) },
)

@Composable
private fun InputCell(s: InputSource, c: LayoutCtx, mod: Modifier) = InputTile(
    s, c.style, c.scale, mod, onFocus = { c.onHero(HeroInfo(s.id, s.label, t("Eingang", "Input"))) }, onClick = { c.vm.openInput(s) },
)

@Composable
private fun WatchCell(w: WatchItem, row: WatchRowUi, i: Int, c: LayoutCtx, mod: Modifier) = WatchTile(
    w, c.style, c.scale, mod, onFocus = { c.onHero(HeroInfo("w:${row.id}:$i", w.title, row.title)) }, onClick = { c.vm.openWatch(w) },
)

@Composable
private fun NowCell(n: NowItem, c: LayoutCtx, mod: Modifier) = NowTile(
    n, c.style, c.scale, mod,
    onFocus = { c.onHero(HeroInfo("n:${n.pkg}", n.title, n.subtitle)) },
    onClick = { c.vm.toggleNow(n) },
    onLong = { c.vm.openPkg(n.pkg) },
)

@Composable
private fun RadioCell(s: Station, c: LayoutCtx, mod: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    RadioTile(
        s, c.style, c.scale, mod,
        onFocus = { c.onHero(HeroInfo("r:${s.uuid}", s.name, t("Radio · OK zum Abspielen, lang drücken für Optionen", "Radio · OK to play, long-press for options"))) },
        onClick = { Radio.toggle(ctx, s) },
        onLong = { c.onStationMenu(s) },
    )
}

@Composable
private fun WebCell(w: WebWidget, c: LayoutCtx, mod: Modifier) = WebTile(
    w, tileDims(c.style, c.scale).first * listOf(1f, 1.6f, 2.4f)[w.size.coerceIn(0, 2)], tileDims(c.style, c.scale).second * listOf(1f, 1.6f, 2.4f)[w.size.coerceIn(0, 2)], mod,
    onFocus = { c.onHero(HeroInfo("web:${w.id}", w.name, t("Widget · OK öffnet groß, lang drücken für Optionen", "Widget · OK opens it large, long-press for options"))) },
    onClick = { c.onWebOpen(w) },
    onLong = { c.onWebMenu(w) },
)

@Composable
private fun RadioAddCell(c: LayoutCtx, mod: Modifier) = RadioAddTile(
    c.style, c.scale, mod,
    onFocus = { c.onHero(HeroInfo("r:add", t("Radiosender suchen", "Find radio stations"), t("Tausende Sender, z. B. SWR3, SR1 – OK zum Suchen", "Thousands of stations – OK to search"))) },
    onClick = { c.onRadioSearch() },
)

private fun LazyListScope.rowItems(row: RowUi, c: LayoutCtx, first: FocusRequester?) {
    when (row) {
        is AppRowUi -> itemsIndexed(row.apps, key = { _, a -> a.pkg }) { i, a -> AppCell(a, row, c, focusMod(i, first)) }
        is InputRowUi -> itemsIndexed(row.inputs, key = { _, s -> s.id }) { i, s -> InputCell(s, c, focusMod(i, first)) }
        is WatchRowUi -> itemsIndexed(row.items) { i, w -> WatchCell(w, row, i, c, focusMod(i, first)) }
        is NowRowUi -> itemsIndexed(row.items) { i, n -> NowCell(n, c, focusMod(i, first)) }
        is WebRowUi -> itemsIndexed(row.widgets, key = { _, w -> w.id }) { i, w -> WebCell(w, c, focusMod(i, first)) }
        is InfoRowUi -> itemsIndexed(row.kinds, key = { _, k -> "info_$k" }) { i, k -> InfoWidgetCell(k, c.vm, focusMod(i, first), c.onHero, wideDims(c.style, c.scale)) }
        is RadioRowUi -> {
            item(key = "radio_add") { RadioAddCell(c, focusMod(0, first)) }
            itemsIndexed(row.stations, key = { _, s -> s.uuid }) { _, s -> RadioCell(s, c, Modifier) }
        }
    }
}

private fun LazyGridScope.gridItems(row: RowUi, c: LayoutCtx, first: FocusRequester?) {
    when (row) {
        is AppRowUi -> gridItemsIndexed(row.apps, key = { _, a -> "${row.id}:${a.pkg}" }) { i, a -> AppCell(a, row, c, focusMod(i, first)) }
        is InputRowUi -> gridItemsIndexed(row.inputs, key = { _, s -> "${row.id}:${s.id}" }) { i, s -> InputCell(s, c, focusMod(i, first)) }
        is WatchRowUi -> gridItemsIndexed(row.items) { i, w -> WatchCell(w, row, i, c, focusMod(i, first)) }
        is NowRowUi -> gridItemsIndexed(row.items) { i, n -> NowCell(n, c, focusMod(i, first)) }
        is WebRowUi -> gridItemsIndexed(row.widgets, key = { _, w -> "${row.id}:${w.id}" }) { i, w -> WebCell(w, c, focusMod(i, first)) }
        is InfoRowUi -> gridItemsIndexed(row.kinds, key = { _, k -> "${row.id}:$k" }) { i, k -> InfoWidgetCell(k, c.vm, focusMod(i, first), c.onHero, wideDims(c.style, c.scale)) }
        is RadioRowUi -> {
            item(key = "${row.id}:add") { RadioAddCell(c, focusMod(0, first)) }
            gridItemsIndexed(row.stations, key = { _, s -> "${row.id}:${s.uuid}" }) { _, s -> RadioCell(s, c, Modifier) }
        }
    }
}

// ---------------------------------------------------------------- Layout 1: Reihen
@Composable
fun ShelfLayout(
    rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier,
    bottomAnchor: Boolean = false, heroMode: Int = 0, hero: @Composable (Modifier) -> Unit = {},
) {
    var cur by remember { mutableIntStateOf(0) }
    if (bottomAnchor) {
        // Fester Aufbau wie bei Google TV: Der Text zur App steht an einer festen Stelle, die fokussierte Reihe
        // sitzt immer direkt darunter. Reihen darüber verschwinden oben aus dem Bild (abgeschnitten), sodass nichts überlappt.
        androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
            val th = tileDims(c.style, c.scale).second
            val labelExtra = if (c.style == TileStyle.ICON || c.style == TileStyle.ROUND) 28.dp else 0.dp
            fun rowH(r: RowUi?): Dp = when (r) { is WebRowUi -> th * 2.4f + labelExtra + 70.dp; is RadioRowUi -> th + 22.dp + 70.dp; else -> th + labelExtra + 70.dp }
            val listH = rowH(rows.firstOrNull()).coerceAtMost(maxHeight)
            Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                hero(Modifier.padding(start = 14.dp, end = 14.dp))
                ShelfList(
                    rows, c, firstFocus, Modifier.fillMaxWidth().height(listH),
                    PaddingValues(bottom = listH),
                    cur = cur, onCur = { cur = it },
                    pinTop = true,
                )
            }
        }
    } else ShelfList(rows, c, firstFocus, modifier, PaddingValues(bottom = 24.dp), cur = cur, onCur = { cur = it })
}

@Composable
private fun ShelfList(
    rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier, padding: PaddingValues,
    cur: Int, onCur: (Int) -> Unit,
    pinTop: Boolean = false,
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    if (pinTop) {
        androidx.compose.runtime.LaunchedEffect(cur, rows.size) {
            if (rows.getOrNull(cur) == null) return@LaunchedEffect
            // Fokussierte Reihe oben im Reihenbereich festhalten; ist sie höher als der Bereich (Web-Widgets), ihre Unterkante unten.
            if (state.layoutInfo.visibleItemsInfo.none { it.index == cur }) state.scrollToItem(cur)
            val info = state.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == cur } ?: return@LaunchedEffect
            val view = info.viewportSize.height
            val yNow = (item.offset - info.viewportStartOffset).toFloat()
            val yTarget = if (item.size <= view) 0f else (view - item.size).toFloat()
            val delta = yNow - yTarget
            if (kotlin.math.abs(delta) > 1f) state.animateScrollBy(delta)
        }
    }
    LazyColumn(modifier, state = state, contentPadding = padding) {
        itemsIndexed(rows, key = { _, r -> r.id }) { idx, row ->
            Column(Modifier.onFocusChanged { if (it.hasFocus) onCur(idx) }) {
                Text(
                    row.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(start = SIDE_PAD, top = 10.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SIDE_PAD, vertical = c.gap),
                    horizontalArrangement = Arrangement.spacedBy(c.gap),
                ) { rowItems(row, c, if (idx == 0) firstFocus else null) }
            }
        }
    }
}

// ---------------------------------------------------------------- Layout 2: Raster
@Composable
fun GridLayout(rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier) {
    val cell = maxOf(tileDims(c.style, c.scale).first, (192 * c.scale).dp) + c.gap + 6.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cell),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SIDE_PAD, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(c.gap + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(c.gap),
    ) {
        rows.forEachIndexed { ri, row ->
            item(key = "h:${row.id}", span = { GridItemSpan(maxLineSpan) }) {
                Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
            }
            gridItems(row, c, if (ri == 0) firstFocus else null)
        }
    }
}

// ---------------------------------------------------------------- Layout 4: Frei
/** Merkt sich Standardpositionen und Grenzen der freien Fläche, damit das Verschieben (in HomeScreen) sie kennt. */
class FreeCtx {
    val defaults = HashMap<String, Pair<Int, Int>>()
    var maxX = 0
    var maxY = 0
}

@Composable
fun FreeLayout(
    rows: List<RowUi>, c: LayoutCtx, pos: Map<String, Pair<Int, Int>>, fc: FreeCtx,
    firstFocus: FocusRequester?, moving: String?, modifier: Modifier,
) {
    val appRows = rows.filterIsInstance<AppRowUi>()
    val row = appRows.firstOrNull { it.id == "favs" && it.apps.isNotEmpty() } ?: appRows.firstOrNull { it.apps.isNotEmpty() } ?: return
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val (tw, th) = tileDims(c.style, c.scale)
        val gap = 12
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        val cols = ((w - 2 * 20) / (tw.value.toInt() + gap)).coerceIn(1, 5)
        val n = row.apps.size
        val rowsN = (n + cols - 1) / cols
        val y0 = ((h - rowsN * (th.value.toInt() + gap)) / 2).coerceAtLeast(0)
        val defaults = row.apps.mapIndexed { i, a ->
            val r = i / cols
            val k = i % cols
            val inRow = minOf(cols, n - r * cols)
            val x = (w - inRow * (tw.value.toInt() + gap) + gap) / 2 + k * (tw.value.toInt() + gap)
            a.pkg to (x to (y0 + r * (th.value.toInt() + gap)))
        }.toMap()
        androidx.compose.runtime.SideEffect {
            fc.defaults.clear(); fc.defaults.putAll(defaults)
            fc.maxX = (w - tw.value.toInt()).coerceAtLeast(0); fc.maxY = (h - th.value.toInt()).coerceAtLeast(0)
        }
        row.apps.forEachIndexed { i, a ->
            val p = pos[a.pkg] ?: defaults[a.pkg] ?: (0 to 0)
            Box(
                Modifier.offset(p.first.dp, p.second.dp).then(
                    if (moving == a.pkg) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier
                )
            ) { AppCell(a, row, c, focusMod(i, firstFocus)) }
        }
    }
}

// ---------------------------------------------------------------- Layout 3: Seitenleiste
@Composable
fun SidebarLayout(rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier) {
    var selected by remember { mutableIntStateOf(0) }
    val sel = rows.getOrNull(selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))
    val cell = maxOf(tileDims(c.style, c.scale).first, (192 * c.scale).dp) + c.gap + 6.dp
    Row(modifier) {
        LazyColumn(
            Modifier.width(250.dp).fillMaxHeight().padding(start = 40.dp, end = 8.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                SideItem(r.title, i == selected, if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier) {
                    selected = i
                }
            }
        }
        if (sel != null) {
            key(sel.id) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cell),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 16.dp, end = SIDE_PAD, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(c.gap + 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(c.gap),
                ) { gridItems(sel, c, null) }
            }
        }
    }
}

@Composable
private fun SideItem(title: String, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    val fm = LocalFocusManager.current
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { fm.moveFocus(FocusDirection.Right) },
        modifier = modifier.fillMaxWidth().height(48.dp).onFocusChanged { if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(title, fontSize = 17.sp, color = Color.White, maxLines = 1)
        }
    }
}
