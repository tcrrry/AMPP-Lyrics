package dev.amenhancer.module.ui

import dev.amenhancer.module.i18n.ModuleText

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.amenhancer.module.CurrentSongDetails
import dev.amenhancer.module.lyrics.CustomLyricsMultiIdDraft
import dev.amenhancer.module.lyrics.CustomLyricsOnlineImportResult
import dev.amenhancer.module.lyrics.CustomLyricsOnlineImporter
import dev.amenhancer.module.model.CustomLyricsEntry
import dev.amenhancer.module.model.CustomLyricsSources

internal fun EmbeddedSettingsHost.showLyricsEditor(
        activity: Activity,
        group: CustomLyricsUiGroup?,
        song: CurrentSongDetails?,
    ) {
        val origin = embeddedSettingsClickOrigin()
        val entry = group?.primary
        var source = entry?.source ?: CustomLyricsSources.MANUAL
        val horizontalInset = dp(activity, 18)
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalInset, dp(activity, 4), horizontalInset, 0)
        }
        val idInput = embeddedLyricsEditorInput(
            activity = activity,
            hint = "Apple Music ID",
            initial = group?.appleMusicIds?.let(CustomLyricsIdParser::format)
                ?: song?.appleMusicId?.toString().orEmpty(),
        )
        val nameInput = embeddedLyricsEditorInput(
            activity = activity,
            hint = localizedText(ModuleText.DISPLAY_NAME),
            initial = entry?.displayName ?: song?.title.orEmpty(),
        )
        val ttmlInput = embeddedLyricsEditorInput(
            activity = activity,
            hint = localizedText(ModuleText.TTML_CONTENT),
            initial = entry?.let { controller.readLyrics(it.appleMusicId) }.orEmpty(),
            multiline = true,
        ).apply {
            minLines = 1
            maxLines = Int.MAX_VALUE
        }
        val sourceLabel = TextView(activity).apply {
            textSize = embeddedTextSize(activity, 14f, 13f)
            setEmbeddedTextColor(activity) { colors -> colors.onSurfaceVariant }
            setSingleLine(false)
            setPadding(0, dp(activity, 8), 0, dp(activity, 8))
        }
        fun updateSourceLabel() {
            sourceLabel.text = localizedText(ModuleText.CURRENT_SOURCE, embeddedLyricsSourceName(source))
        }
        fun importOnline(sourceToImport: EmbeddedOnlineSource) {
            importEmbeddedOnlineLyrics(
                activity = activity,
                source = sourceToImport,
                appleMusicIdInput = idInput,
                ttmlInput = ttmlInput,
            ) { importedSource ->
                source = importedSource
                updateSourceLabel()
            }
        }
        updateSourceLabel()
        fields.addView(idInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(activity, 14) })
        fields.addView(nameInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(activity, 14) })
        fields.addView(sourceLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        fields.addView(
            embeddedLyricsEditorActionRows(
                activity,
                listOf(
                    EmbeddedLyricsEditorAction(localizedText(ModuleText.IMPORT_TTML)) {
                        pendingTtmlImport = { imported ->
                            ttmlInput.setText(imported)
                            source = CustomLyricsSources.MANUAL
                            updateSourceLabel()
                        }
                        launchSafPicker(
                            activity,
                            EmbeddedSafOperation.Ttml,
                            "application/xml",
                            arrayOf("application/ttml+xml", "application/xml", "text/xml", "text/plain"),
                        )
                    },
                    EmbeddedLyricsEditorAction(localizedText(ModuleText.GET_ID)) {
                        requestCurrentSongId(activity, idInput, nameInput)
                    },
                    EmbeddedLyricsEditorAction(
                        label = localizedText(ModuleText.IMPORT_FROM_AMLL),
                        compactLabel = localizedText(ModuleText.AMLL_IMPORT),
                    ) {
                        importOnline(EmbeddedOnlineSource.AMLL)
                    },
                    EmbeddedLyricsEditorAction(
                        label = localizedText(ModuleText.IMPORT_FROM_LUNABEAT),
                        compactLabel = localizedText(ModuleText.LUNABEAT_IMPORT),
                    ) {
                        importOnline(EmbeddedOnlineSource.LUNABEAT)
                    },
                    EmbeddedLyricsEditorAction(
                        label = localizedText(ModuleText.IMPORT_FROM_AM_LYRICS),
                        compactLabel = localizedText(ModuleText.AM_LYRICS_IMPORT),
                    ) {
                        importOnline(EmbeddedOnlineSource.AM_LYRICS)
                    },
                ),
            ),
            matchWidthWrapContent(),
        )
        val fieldsScroll = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(fields)
        }
        val ttmlTopMargin = dp(activity, 8)
        val ttmlBottomMargin = dp(activity, 4)
        val editorBody = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val fieldsParams = fieldsScroll.layoutParams
                fieldsParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    // Only metadata may scroll in a small window or above the
                    // keyboard. TTML is a sibling and never enlarges that range.
                    val availableHeight = (MeasureSpec.getSize(heightMeasureSpec) -
                        ttmlTopMargin - ttmlBottomMargin).coerceAtLeast(0)
                    val textReserve = minOf(
                        ttmlInput.lineHeight * 3 + ttmlInput.compoundPaddingTop + ttmlInput.compoundPaddingBottom,
                        availableHeight / 2,
                    )
                    val fieldsLimit = availableHeight - textReserve
                    fieldsScroll.measure(
                        ViewGroup.getChildMeasureSpec(widthMeasureSpec, 0, ViewGroup.LayoutParams.MATCH_PARENT),
                        MeasureSpec.makeMeasureSpec(fieldsLimit, MeasureSpec.AT_MOST),
                    )
                    fieldsParams.height = fieldsScroll.measuredHeight
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            addView(fieldsScroll, matchWidthWrapContent())
            // The native text editor scrolls within the remaining viewport;
            // neither its line count nor content participates in outer sizing.
            addView(ttmlInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply {
                marginStart = horizontalInset
                marginEnd = horizontalInset
                topMargin = ttmlTopMargin
                bottomMargin = ttmlBottomMargin
            })
        }
        lateinit var dialog: EmbeddedLyricsEditorDialog
        var saveSubmitted = false
        fun saveLyrics() {
            if (saveSubmitted || !dialog.acceptsInput) return
            val ids = CustomLyricsIdParser.parse(idInput.text.toString())
            val ttml = ttmlInput.text.toString()
            if (ids == null) {
                idInput.error = localizedText(ModuleText.MUSIC_IDS_INVALID)
                return
            }
            if (ttml.isBlank()) {
                ttmlInput.error = localizedText(ModuleText.TTML_REQUIRED)
                return
            }
            val draft = CustomLyricsMultiIdDraft(
                appleMusicIds = ids,
                displayName = nameInput.text.toString(),
                ttml = ttml,
                source = source,
                enabled = entry?.enabled ?: true,
            )
            saveSubmitted = true
            runAsync(activity) { saveMany(draft, group?.appleMusicIds.orEmpty()) }
            dialog.dismiss()
        }
        val titleBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(horizontalInset, 0, horizontalInset, 0)
            addView(TextView(activity).apply {
                text = if (group == null) localizedText(ModuleText.NEW_LYRICS) else localizedText(ModuleText.EDIT_LYRICS)
                textSize = embeddedTextSize(activity, 19f, 18f)
                setEmbeddedTextColor(activity) { colors -> colors.onSurface }
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        val editorActions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(horizontalInset, 0, horizontalInset, 0)
        }
        fun editorAction(label: String, emphasized: Boolean = false, onClick: () -> Unit): TextView =
            Button(activity).apply {
                text = label
                bindEmbeddedDialogActionTheme(activity, emphasized)
                setOnClickListener { fromEmbeddedSettingsView(this) { onClick() } }
            }
        editorActions.addView(
            editorAction(localizedText(ModuleText.CANCEL)) { dialog.dismiss() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(activity, 48),
            ),
        )
        editorActions.addView(
            editorAction(localizedText(ModuleText.SAVE), emphasized = true, onClick = ::saveLyrics),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(activity, 48),
            ).apply { marginStart = dp(activity, 8) },
        )
        val editorRoot = object : LinearLayout(activity) {
            private val panelClip = Path()
            private val panelBounds = RectF()

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                panelBounds.set(0f, 0f, w.toFloat(), h.toFloat())
                panelClip.setEmbeddedG3Rect(panelBounds, dp(activity, 24).toFloat())
            }

            override fun dispatchDraw(canvas: Canvas) {
                val save = canvas.save()
                canvas.clipPath(panelClip)
                super.dispatchDraw(canvas)
                canvas.restoreToCount(save)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = embeddedLyricsEditorDialogHeight(activity)
            bindEmbeddedTheme(activity) { colors ->
                background = EmbeddedG3Drawable().apply {
                    setColor(colors.pageBackground)
                    cornerRadius = dp(activity, 24).toFloat()
                }
            }
            addView(titleBar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                embeddedTopBarHeight(activity),
            ))
            addView(editorBody, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
            addView(editorActions, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, if (isEmbeddedPhone(activity)) 56 else 48),
            ))
        }
        dialog = EmbeddedLyricsEditorDialog(
            host = this,
            activity = activity,
            origin = origin,
            editorRoot = editorRoot,
            editorWidth = embeddedLyricsEditorDialogWidth(activity),
        )
        dialog.setOnDismissListener {
            if (pendingTtmlImport != null) pendingTtmlImport = null
        }
        dialog.show()
    }

/**
 * Keeps one transparent window at a stable size throughout both transitions.
 * Only the centered editor panel moves; resizing the Window after the reveal
 * would start a second system surface transition.
 */
private class EmbeddedLyricsEditorDialog(
    private val host: EmbeddedSettingsHost,
    private val activity: Activity,
    private val origin: Rect?,
    private val editorRoot: LinearLayout,
    private val editorWidth: Int,
) : AlertDialog(embeddedThemedContext(activity, dialog = true)), Application.ActivityLifecycleCallbacks {
    private val scrim = ColorDrawable(Color.BLACK).apply { alpha = 0 }
    private val panelShadow = EmbeddedPanelShadow(context.resources.displayMetrics.density)
    private val motionStage = object : FrameLayout(context) {
        private val shadowBounds = RectF()

        override fun dispatchDraw(canvas: Canvas) {
            if (editorRoot.visibility == View.VISIBLE && editorRoot.alpha > 0f) {
                shadowBounds.set(0f, 0f, editorRoot.width.toFloat(), editorRoot.height.toFloat())
                val save = canvas.save()
                canvas.translate((editorRoot.left - scrollX).toFloat(), (editorRoot.top - scrollY).toFloat())
                canvas.concat(editorRoot.matrix)
                panelShadow.draw(canvas, shadowBounds, host.dp(activity, 24).toFloat(), editorRoot.alpha)
                canvas.restoreToCount(save)
            }
            super.dispatchDraw(canvas)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availableWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                editorWidth
            } else MeasureSpec.getSize(widthMeasureSpec)
            val availableHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                editorRoot.minimumHeight
            } else MeasureSpec.getSize(heightMeasureSpec)
            // Keep the original panel size and resize its editor viewport when
            // the IME or a smaller host window reduces the available area.
            editorRoot.layoutParams.width = minOf(editorWidth, availableWidth)
            editorRoot.layoutParams.height = minOf(editorRoot.minimumHeight, availableHeight)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        background = scrim
        clipChildren = false
        clipToPadding = false
        setOnClickListener { cancel() }
    }
    private var entryStop: (() -> Unit)? = null
    private var closing = false
    private var dismissed = false
    private var lifecycleRegistered = false

    val acceptsInput: Boolean
        get() = isShowing && !closing && !dismissed

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean =
        closing || dismissed || super.dispatchTouchEvent(event)

    init {
        // Consume blank space inside the panel; only taps on the surrounding
        // transparent stage should behave like taps outside a normal dialog.
        editorRoot.isClickable = true
        editorRoot.alpha = 0f
        editorRoot.scaleX = if (origin == null) 0.92f else 0.08f
        editorRoot.scaleY = editorRoot.scaleX
        motionStage.addView(
            editorRoot,
            FrameLayout.LayoutParams(
                editorWidth,
                editorRoot.minimumHeight,
                Gravity.CENTER,
            ),
        )
        host.configureEmbeddedDialogWindow(
            activity = activity,
            dialog = this,
            themedContext = context as ContextThemeWrapper,
            customPanel = true,
            animateStandardEntrance = false,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Install after AlertDialog has created its own content, so its default
        // panel cannot replace or inset the transparent animation stage.
        setContentView(motionStage, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        panelShadow.prepare(host.dp(activity, 24).toFloat())
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setWindowAnimations(0)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setDimAmount(0f)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
    }

    override fun show() {
        if (isShowing) return
        registerLifecycle()
        super.show()
        if (!closing && isShowing) beginEntrance()
    }

    private fun beginEntrance() {
        entryStop?.invoke()
        entryStop = editorRoot.revealEmbeddedSettingsPage(origin, onProgress = { progress ->
            updateScrimAlpha((255 * EMBEDDED_DIALOG_DIM_AMOUNT * progress).toInt())
        }) {
            entryStop = null
        }
    }

    @Suppress("DEPRECATION")
    private fun updateScrimAlpha(alpha: Int) {
        val next = alpha.coerceIn(0, 255)
        scrim.alpha = next
        motionStage.invalidate()
        // Decor still fits the system bars; carry the same animated scrim into
        // the navigation region without resizing the editor or its IME viewport.
        window?.navigationBarColor = Color.argb(next, 0, 0, 0)
    }

    override fun dismiss() {
        if (!isShowing || dismissed) {
            dismissImmediately()
            return
        }
        if (hostShouldCloseImmediately()) {
            dismissImmediately()
            return
        }
        if (closing) return
        closing = true
        entryStop?.invoke()
        entryStop = null
        val duration = if (origin == null) 220L else 340L
        if (!ValueAnimator.areAnimatorsEnabled()) {
            dismissImmediately()
            return
        }
        beginCloseMotion(duration)
    }

    private fun beginCloseMotion(duration: Long) {
        if (dismissed || !isShowing) return
        if (origin == null) {
            fadeEditorOut(duration)
            return
        }
        val startScrimAlpha = scrim.alpha
        val returnMotion = editorRoot.captureEmbeddedSettingsReturn(origin) { progress ->
            updateScrimAlpha((startScrimAlpha * (1f - progress)).toInt())
        }
        if (returnMotion == null) {
            fadeEditorOut(duration)
            return
        }
        editorRoot.alpha = 0f
        returnMotion(motionStage) { finishDismiss() }
    }

    private fun fadeEditorOut(duration: Long) {
        val startScrimAlpha = scrim.alpha
        editorRoot.animate().withEndAction(null).cancel()
        editorRoot.pivotX = editorRoot.width / 2f
        editorRoot.pivotY = editorRoot.height / 2f
        editorRoot.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(duration)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .setUpdateListener { updateScrimAlpha((startScrimAlpha * (1f - it.animatedFraction)).toInt()) }
            .withEndAction { finishDismiss() }
            .start()
    }

    private fun dismissImmediately() {
        if (dismissed) return
        dismissed = true
        closing = true
        entryStop?.invoke()
        entryStop = null
        updateScrimAlpha(0)
        editorRoot.animate().setUpdateListener(null).withEndAction(null).cancel()
        unregisterLifecycle()
        runCatching { super.dismiss() }
    }

    private fun finishDismiss() {
        if (dismissed) return
        dismissed = true
        updateScrimAlpha(0)
        editorRoot.animate().setUpdateListener(null)
        unregisterLifecycle()
        super.dismiss()
    }

    private fun hostShouldCloseImmediately(): Boolean =
        !host.registered || activity.isFinishing || activity.isDestroyed ||
            host.currentActivity()?.let { it !== activity } == true

    private fun registerLifecycle() {
        if (lifecycleRegistered) return
        lifecycleRegistered = true
        activity.application.registerActivityLifecycleCallbacks(this)
    }

    private fun unregisterLifecycle() {
        if (!lifecycleRegistered) return
        lifecycleRegistered = false
        activity.application.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        if (activity !== this.activity && isShowing && host.pendingTtmlImport == null) dismissImmediately()
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === this.activity) dismissImmediately()
    }
}

    /** Compatibility overload used by the legacy embedded dialog path. */

internal fun EmbeddedSettingsHost.showLyricsEditor(
        activity: Activity,
        entry: CustomLyricsEntry?,
        song: CurrentSongDetails?,
    ) = showLyricsEditor(
        activity,
        entry?.let { CustomLyricsUiGroup(listOf(it)) },
        song,
    )


internal fun EmbeddedSettingsHost.requestCurrentSongId(
        activity: Activity,
        appleMusicId: EditText,
        displayName: EditText,
    ) {
        val currentSong = controller.currentSongDetails()
        if (currentSong == null) {
            Toast.makeText(
                activity,
                localizedText(ModuleText.PLAY_SONG_FIRST),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        appleMusicId.setText(currentSong.appleMusicId.toString())
        appleMusicId.setSelection(appleMusicId.length())
        listOfNotNull(
            currentSong.title?.takeIf(String::isNotBlank),
            currentSong.artist?.takeIf(String::isNotBlank),
        ).joinToString(" - ").takeIf(String::isNotBlank)?.let { value ->
            displayName.setText(value)
            displayName.setSelection(displayName.length())
        }
        Toast.makeText(activity, localizedText(ModuleText.SONG_DETAILS_CAPTURED), Toast.LENGTH_SHORT).show()
    }

    /** Keeps the editor's multi-ID operation explicit at the host boundary. */

internal fun EmbeddedSettingsHost.saveMany(
        draft: CustomLyricsMultiIdDraft,
        replacingAppleMusicIds: List<Long>,
    ): EmbeddedActionResult = controller.saveLyrics(draft, replacingAppleMusicIds)


internal fun EmbeddedSettingsHost.importEmbeddedOnlineLyrics(
        activity: Activity,
        source: EmbeddedOnlineSource,
        appleMusicIdInput: EditText,
        ttmlInput: EditText,
        onImported: (String) -> Unit,
    ) {
        val appleMusicId = appleMusicIdInput.text.toString().toLongOrNull()
        when (source) {
            EmbeddedOnlineSource.AMLL,
            EmbeddedOnlineSource.AM_LYRICS,
            EmbeddedOnlineSource.LUNABEAT,
            -> if (appleMusicId == null || appleMusicId <= 0L) {
                appleMusicIdInput.error = localizedText(ModuleText.MUSIC_ID_INVALID)
                return
            }
        }

        Toast.makeText(activity, localizedText(ModuleText.FETCHING_LYRICS), Toast.LENGTH_SHORT).show()
        worker.execute {
            val result = runCatching {
                val importer = embeddedOnlineLyricsImporter()
                when (source) {
                    EmbeddedOnlineSource.AMLL -> importer.importAmll(requireNotNull(appleMusicId))
                    EmbeddedOnlineSource.AM_LYRICS -> importer.importAmLyrics(requireNotNull(appleMusicId))
                    EmbeddedOnlineSource.LUNABEAT -> importer.importLunabeat(requireNotNull(appleMusicId))
                }
            }.getOrElse {
                CustomLyricsOnlineImportResult.Failed(
                    it.message.orEmpty().ifBlank { localizedText(ModuleText.ONLINE_IMPORT_FAILED) },
                )
            }
            mainHandler.post {
                if (currentActivity() !== activity) return@post
                when (result) {
                    is CustomLyricsOnlineImportResult.Imported -> {
                        ttmlInput.setText(result.ttml)
                        onImported(result.source)
                        val reformatNote = if (result.reformatted) localizedText(ModuleText.CONVERTED_TO_AM_FORMAT) else ""
                        Toast.makeText(
                            activity,
                            localizedText(ModuleText.ONLINE_LYRICS_IMPORTED, embeddedLyricsSourceName(result.source), reformatNote),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    is CustomLyricsOnlineImportResult.Failed -> {
                        Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


internal fun EmbeddedSettingsHost.embeddedOnlineLyricsImporter(): CustomLyricsOnlineImporter =
        dev.amenhancer.module.lyrics.createEmbeddedOnlineLyricsImporter(application)


internal fun EmbeddedSettingsHost.embeddedLyricsSourceName(source: String): String = when (source) {
        CustomLyricsSources.AUTO_CACHE -> localizedText(ModuleText.AUTO_CACHE)
        CustomLyricsSources.AMLL -> "AMLL"
        CustomLyricsSources.AM_LYRICS -> localizedText(ModuleText.AM_LYRICS_REPOSITORY)
        CustomLyricsSources.LUNABEAT -> "Lunabeat"
        else -> localizedText(ModuleText.MANUAL_TTML)
    }

