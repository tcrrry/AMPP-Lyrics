package dev.amenhancer.module.hook

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.layout.onGloballyPositioned
import dev.amenhancer.glass.GlassNavigation
import dev.amenhancer.glass.GlassNavigationStyle
import dev.amenhancer.glass.GlassSidebarIcon
import dev.amenhancer.glass.GlassTab

/** One measured lens renderer for Fragment phone and tablet; the native drawer has no cell. */
@Composable
internal fun FragmentGlassNavigation(
    snapshot: NavigationSnapshot,
    backdrop: Backdrop,
    foreground: Color,
    accent: Color,
    blurDp: Int,
    select: (Int) -> Int?,
    onGeometry: (Boolean) -> Unit,
    onDrawn: (Long) -> Unit,
    onDrawer: () -> Unit = {},
    panelHeightPx: Int = 0,
) {
    if (snapshot.placement == NavigationPlacement.TOP) {
        val density = LocalDensity.current
        var measuredWidth by remember { mutableIntStateOf(0) }
        var measuredHeight by remember { mutableIntStateOf(0) }
        val tabs = remember(snapshot.tabs, foreground) { snapshot.tabs.map { tab ->
            GlassTab(tab.id, tab.label, tab.icon?.let(::copyNavigationIcon)?.apply { setTint(foreground.toArgb()) }, tab.enabled)
        } }
        val drawer = remember(foreground) { GlassSidebarIcon(foreground.toArgb()) }
        val drawn = rememberUpdatedState(onDrawn)
        LaunchedEffect(snapshot.renderable, snapshot.revision, measuredWidth, measuredHeight) {
            onGeometry(snapshot.renderable && measuredWidth > 1 && measuredHeight > 1)
        }
        if (!snapshot.renderable) return
        Box(Modifier.fillMaxSize().onGloballyPositioned { measuredWidth = it.size.width; measuredHeight = it.size.height }
            .drawWithContent { drawContent(); drawn.value(snapshot.revision) }) {
            GlassNavigation(tabs, checkNotNull(snapshot.selectedId), accent, foreground, backdrop,
                onSelect = { id -> select(id) ?: snapshot.selectedId ?: id },
                panelHeight = if (panelHeightPx > 1) with(density) { panelHeightPx.toDp() } else 56.dp,
                panelBlur = blurDp.dp, style = GlassNavigationStyle.TabletLabels,
                drawerIcon = drawer, drawerDescription = "打开侧边导航", onDrawer = onDrawer)
        }
        return
    }
    val tabs = snapshot.tabs
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val labels = remember(tabs, density, measurer) {
        tabs.map { measurer.measure(it.label, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1) }
    }
    val icons = remember(tabs) { tabs.map { it.icon?.let(::copyNavigationIcon) } }
    val scope = rememberCoroutineScope()
    val selection = rememberUpdatedState(select)
    val confirmed = rememberUpdatedState(snapshot.selectedId)
    val drawn = rememberUpdatedState(onDrawn)
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    val center = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = with(density) { maxWidth.toPx() }
        val panelHeight = with(density) { maxHeight.toPx() }
        val cells = NavigationGeometry.cells(tabs.map { it.id }, labels.map { it.size.width + with(density) { 28.dp.toPx() } },
            tabs.map { it.enabled }, panelWidth, with(density) { 48.dp.toPx() }, with(density) { 4.dp.toPx() }, rtl)
        val geometryReady = cells.isNotEmpty() && snapshot.renderable
        LaunchedEffect(geometryReady, snapshot.revision) { onGeometry(geometryReady) }
        val selectedCell = cells.firstOrNull { it.id == snapshot.selectedId }
        LaunchedEffect(selectedCell, dragging) {
            if (!dragging && selectedCell != null) {
                launch { center.animateTo(selectedCell.center, spring(0.8f, 500f)) }
                launch { width.animateTo(selectedCell.width, spring(0.8f, 500f)) }
            }
        }
        if (!geometryReady || selectedCell == null) return@BoxWithConstraints
        val material = if (foreground == Color.White) Color(0xff121212).copy(alpha = .4f)
            else Color(0xfffafafa).copy(alpha = .4f)
        Box(Modifier.fillMaxSize()
            .pointerInput(cells, snapshot.revision) {
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hit = NavigationGeometry.hit(cells, down.position.x) ?: return@awaitEachGesture
                        down.consume()
                        var active = down.id
                        val origin = down.position
                        var moved = false
                        var released = false
                        pointerX = down.position.x
                        dragging = true
                        highlight.pressAt(down.position)
                        scope.launch { press.animateTo(1f, spring(.8f, 700f)) }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                var change = event.changes.firstOrNull { it.id == active }
                                if (change == null || !change.pressed) {
                                    val successor = event.changes.firstOrNull { it.pressed }
                                    if (successor == null) { released = change != null; break }
                                    active = successor.id
                                    change = successor
                                }
                                if (change.isConsumed) break
                                if (abs(change.position.y - origin.y) > viewConfiguration.touchSlop * 2 &&
                                    abs(change.position.y - origin.y) > abs(change.position.x - origin.x)) break
                                moved = moved || (change.position - origin).getDistance() > viewConfiguration.touchSlop
                                pointerX = change.position.x.coerceIn(cells.minOf { it.start }, cells.maxOf { it.end })
                                highlight.moveTo(change.position)
                                val target = NavigationGeometry.nearestEnabled(cells, pointerX)
                                if (target != null) {
                                    scope.launch { center.snapTo(pointerX) }
                                    scope.launch { width.snapTo(target.width) }
                                }
                                change.consume()
                            }
                            if (released) {
                                val target = if (moved) NavigationGeometry.nearestEnabled(cells, pointerX) else hit
                                target?.let { selection.value(it.id) }
                            }
                        } finally {
                            dragging = false
                            highlight.releasePress()
                            scope.launch { press.animateTo(0f, spring(.85f, 500f)) }
                            // Rejected native actions settle back to the confirmed native destination.
                            cells.firstOrNull { it.id == confirmed.value }?.let { cell ->
                                scope.launch { center.animateTo(cell.center, spring(.8f, 500f)) }
                                scope.launch { width.animateTo(cell.width, spring(.8f, 500f)) }
                            }
                        }
                    }
                } finally {
                    dragging = false
                    highlight.releasePress()
                    scope.launch { press.snapTo(0f) }
                }
            }
            .drawBackdrop(backdrop, shape = { Capsule() }, effects = {
                vibrancy(); blur(blurDp.dp.toPx())
                val edge = minOf(24.dp.toPx(), size.minDimension * .375f)
                lens(edge, edge)
            }, onDrawSurface = { drawRect(material) }, layerBlock = {
                val scale = 1f + 4.dp.toPx() / size.height * press.value
                scaleX = scale; scaleY = scale
            })
            .then(highlight.modifier))
        Box(Modifier.absoluteOffset(x = with(density) { (center.value - width.value / 2).toDp() }, y = 4.dp)
            .width(with(density) { width.value.coerceAtLeast(1f).toDp() })
            .height(with(density) { (panelHeight - 8.dp.toPx()).coerceAtLeast(1f).toDp() })
            .graphicsLayer { scaleX = 1f + .08f * press.value; scaleY = 1f + .08f * press.value }
            .drawBackdrop(backdrop, shape = { Capsule() }, effects = {
                vibrancy(); blur(blurDp.dp.toPx())
                val edge = minOf(12.dp.toPx(), size.minDimension * .35f)
                lens(edge, edge)
            }, onDrawSurface = { drawRect(accent.copy(alpha = .14f)) }))
        Canvas(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            if (geometryReady) drawn.value(snapshot.revision)
        }) {
            cells.forEachIndexed { index, cell ->
                val tab = tabs[index]
                val selected = tab.id == snapshot.selectedId
                val tint = if (selected) accent else foreground
                val label = labels[index]
                val iconSize = minOf(22.dp.toPx(), panelHeight * .4f)
                icons[index]?.let { icon ->
                    val canvas = drawContext.canvas.nativeCanvas
                    val save = canvas.save()
                    try {
                        canvas.translate(cell.center - iconSize / 2, maxOf(4.dp.toPx(), (panelHeight - iconSize - label.size.height - 4.dp.toPx()) / 2))
                        icon.setTint(tint.toArgb())
                        icon.alpha = if (tab.enabled) 255 else 90
                        icon.setBounds(0, 0, iconSize.toInt(), iconSize.toInt())
                        icon.draw(canvas)
                    } finally { canvas.restoreToCount(save) }
                }
                drawText(label, color = if (tab.enabled) tint else tint.copy(alpha = .4f),
                    topLeft = Offset(cell.center - label.size.width / 2, panelHeight - label.size.height - 8.dp.toPx()))
            }
        }
        cells.forEachIndexed { index, cell ->
            val tab = tabs[index]
            Box(Modifier.absoluteOffset(x = with(density) { cell.start.toDp() })
                .width(with(density) { cell.width.toDp() }).fillMaxHeight()
                .semantics {
                    contentDescription = tab.label; selected = tab.id == snapshot.selectedId; role = Role.Tab
                    if (!tab.enabled) disabled()
                    onClick { if (tab.enabled) selection.value(tab.id); tab.enabled }
                })
        }
    }
}

/** Copy once per menu revision; never tint or resize the host-owned drawable during animation. */
private fun copyNavigationIcon(original: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable {
    original.constantState?.newDrawable()?.mutate()?.let { return it }
    val bitmap = android.graphics.Bitmap.createBitmap(original.intrinsicWidth.coerceAtLeast(1),
        original.intrinsicHeight.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
    val bounds = android.graphics.Rect(original.bounds)
    try {
        original.setBounds(0, 0, bitmap.width, bitmap.height)
        original.draw(android.graphics.Canvas(bitmap))
    } finally { original.bounds = bounds }
    @Suppress("DEPRECATION")
    return android.graphics.drawable.BitmapDrawable(bitmap)
}
