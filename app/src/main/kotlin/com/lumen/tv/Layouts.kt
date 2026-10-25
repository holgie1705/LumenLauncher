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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Alles, was die Layouts zum Zeichnen der Kacheln brauchen. */
class LayoutCtx(
    val style: TileStyle, val scale: Float, val vm: HomeViewModel,
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
    s, c.scale, mod, onFocus = { c.onHero(HeroInfo(s.id, s.label, t("Eingang", "Input"))) }, onClick = { c.vm.openInput(s) },
)

@Composable
private fun WatchCell(w: WatchItem, row: WatchRowUi, i: Int, c: LayoutCtx, mod: Modifier) = WatchTile(
    w, c.scale, mod, onFocus = { c.onHero(HeroInfo("w:${row.id}:$i", w.title, row.title)) }, onClick = { c.vm.openWatch(w) },
)

@Composable
private fun NowCell(n: NowItem, c: LayoutCtx, mod: Modifier) = NowTile(
    n, c.scale, mod,
    onFocus = { c.onHero(HeroInfo("n:${n.pkg}", n.title, n.subtitle)) },
    onClick = { c.vm.toggleNow(n) },
    onLong = { c.vm.openPkg(n.pkg) },
)

@Composable
private fun RadioCell(s: Station, c: LayoutCtx, mod: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    RadioTile(
        s, c.scale, mod,
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
    c.scale, mod,
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
        is RadioRowUi -> {
            item(key = "${row.id}:add") { RadioAddCell(c, focusMod(0, first)) }
            gridItemsIndexed(row.stations, key = { _, s -> "${row.id}:${s.uuid}" }) { _, s -> RadioCell(s, c, Modifier) }
        }
    }
}

// ---------------------------------------------------------------- Layout 1: Reihen
@Composable
fun ShelfLayout(rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(rows, key = { _, r -> r.id }) { idx, row ->
            Column {
                Text(
                    row.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(start = SIDE_PAD, top = 10.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SIDE_PAD, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) { rowItems(row, c, if (idx == 0) firstFocus else null) }
            }
        }
    }
}

// ---------------------------------------------------------------- Layout 2: Raster
@Composable
fun GridLayout(rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier) {
    val cell = maxOf(tileDims(c.style, c.scale).first, (192 * c.scale).dp) + 24.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cell),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SIDE_PAD, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        rows.forEachIndexed { ri, row ->
            item(key = "h:${row.id}", span = { GridItemSpan(maxLineSpan) }) {
                Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
            }
            gridItems(row, c, if (ri == 0) firstFocus else null)
        }
    }
}

// ---------------------------------------------------------------- Layout 3: Seitenleiste
@Composable
fun SidebarLayout(rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier) {
    var selected by remember { mutableIntStateOf(0) }
    val sel = rows.getOrNull(selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))
    val cell = maxOf(tileDims(c.style, c.scale).first, (192 * c.scale).dp) + 24.dp
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
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
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
