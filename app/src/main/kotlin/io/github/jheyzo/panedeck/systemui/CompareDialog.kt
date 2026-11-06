package io.github.jheyzo.panedeck.systemui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.jheyzo.panedeck.R
import io.github.jheyzo.panedeck.compare.CompareIntents
import io.github.jheyzo.panedeck.config.AppLanguage
import io.github.jheyzo.panedeck.framework.WorkspaceIntentResolver
import io.github.jheyzo.panedeck.logging.NeXtepLog

/**
 * In-workspace compare dialog, ported from Panes Workbench 3.0.5
 * WorkbenchToolsController.showSearch (yishisanren, Apache License 2.0),
 * with attribution retained in DESIGN_COMPARE.md. Panes' themed
 * appearance and payment UI are not ported.
 *
 * Look (Panes, v17; frosted since v18): the dialog is the workbench
 * panel itself, not a separate white card — window background is the
 * panel tone at 80% opacity (28dp corners, 1dp outline, 16dp side
 * inset), so the wallpaper shows faintly through and it reads as part
 * of the frosted workbench instead of a solid card. Title bold, hint grey;
 * 购物/外卖 is a pill segmented control on a card-coloured track, the
 * selected half an accent pill; the app icons are display-only (no
 * selection — Panes searches every installed target); the query field
 * is a rounded card box with a search glyph; 取消 is a card button and
 * 搜索 a filled accent button, 1 : 1.5 wide. Default mode is 外卖
 * (Panes' enum order), shown first. The dialog runs in the SystemUI
 * process, window type from SystemUiWindowTypeResolver (same as the
 * panels). ColorOS force-dark is off so it cannot re-tint the accent,
 * and the ROM's own rounded panel inside the window is removed so
 * there is no second frame.
 *
 * Behaviour departs from Panes in one place (user 2026-11-06): Panes
 * keeps the dialog open while the round runs (busy state, failure
 * status line). Here the dialog hides the moment 搜索 is tapped, so
 * the user watches the apps open instead of waiting behind the
 * dialog; a round that cannot even start reports with a toast.
 */
internal class CompareDialog(
    private val context: Context,
    private val onSearch: (CompareIntents.Mode, String) -> Boolean,
) {
    private var dialog: AlertDialog? = null

    fun show() {
        dismiss()
        val a = WorkbenchAppearance.resolve(context)
        // Panes defaults to delivery, and its enum order puts delivery first.
        var mode = CompareIntents.Mode.DELIVERY
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(20))
        }
        fun label(text: CharSequence, size: Float, color: Int, bold: Boolean = false) =
            TextView(context).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        fun rounded(fill: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (stroke > 0) setStroke(stroke, a.outline)
        }
        val onAccent = if (a == WorkbenchAppearance.DARK) 0xFF0C131F.toInt() else Color.WHITE
        val fill = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        panel.addView(label(AppLanguage.string(context, R.string.compare_title), 20f, a.text, bold = true), fill)
        panel.addView(label(AppLanguage.string(context, R.string.compare_summary), 13f, a.secondary),
            fill.apply { topMargin = dp(4) })

        // Segmented choice: a card-coloured track with an accent pill on the selected half.
        val choices = mutableListOf<Pair<CompareIntents.Mode, TextView>>()
        val apps = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun renderApps() {
            apps.removeAllViews()
            runCatching { CompareIntents.searches(mode, "x") }.getOrDefault(emptyList()).forEach { target ->
                // A target counts as installed when its primary intent
                // resolves, or its https fallback does.
                val installed = WorkspaceIntentResolver.resolve(
                    context.packageManager, target.intent,
                ) != null || (target.httpsFallback != null &&
                    WorkspaceIntentResolver.resolve(
                        context.packageManager, target.httpsFallback,
                    ) != null)
                val icon = target.intent.`package`?.let { pkg ->
                    runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
                }
                apps.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    alpha = if (installed) 1f else .35f
                    if (icon != null) {
                        addView(ImageView(context).apply {
                            setImageDrawable(icon)
                            contentDescription = target.name
                        }, LinearLayout.LayoutParams(dp(40), dp(40)))
                    }
                    addView(label(target.name, 11f, a.secondary).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(6) })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        fun renderChoices() {
            choices.forEach { (choice, view) ->
                val selected = choice == mode
                view.background = if (selected) rounded(a.accent, 10) else null
                view.setTextColor(if (selected) onAccent else a.secondary)
                view.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD
                    else android.graphics.Typeface.DEFAULT
            }
            renderApps()
        }
        val segments = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(a.card, 13, dp(1))
            setPadding(dp(3), dp(3), dp(3), dp(3))
            // Panes order: delivery first, shopping second.
            listOf(CompareIntents.Mode.DELIVERY, CompareIntents.Mode.SHOPPING).forEach { choice ->
                val view = label(AppLanguage.string(context,
                    if (choice == CompareIntents.Mode.DELIVERY) R.string.compare_delivery
                    else R.string.compare_shopping), 15f, a.secondary).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { if (mode != choice) { mode = choice; renderChoices() } }
                }
                choices += choice to view
                addView(view, LinearLayout.LayoutParams(0, dp(40), 1f))
            }
        }
        panel.addView(segments, fill.apply { topMargin = dp(18) })
        panel.addView(apps, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(18) })

        val query = EditText(context).apply {
            hint = AppLanguage.string(context, R.string.compare_query_hint)
            contentDescription = hint
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(200))
            textSize = 16f
            setTextColor(a.text)
            setHintTextColor(a.secondary)
            background = rounded(a.card, 14, dp(1))
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
            val glyph = context.getDrawable(android.R.drawable.ic_menu_search)?.mutate()?.apply {
                setBounds(0, 0, dp(20), dp(20))
                setTint(a.secondary)
            }
            setCompoundDrawablesRelative(glyph, null, null, null)
            compoundDrawablePadding = dp(12)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        panel.addView(query, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50),
        ).apply { topMargin = dp(18) })

        val cancel = label(AppLanguage.string(context, R.string.action_cancel), 15f, a.secondary).apply {
            gravity = Gravity.CENTER
            background = rounded(a.card, 12, dp(1))
            setOnClickListener { dismiss() }
        }
        val go = label(AppLanguage.string(context, R.string.compare_search), 15f, onAccent, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(a.accent, 12)
        }
        panel.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f))
            addView(go, LinearLayout.LayoutParams(0, dp(46), 1.5f).apply { marginStart = dp(12) })
        }, fill.apply { topMargin = dp(18) })
        renderChoices()

        val window = AlertDialog.Builder(context).setView(panel).create()
        dialog = window
        runCatching {
            window.window?.apply {
                SystemUiWindowTypeResolver.resolve()?.let { setType(it) }
                // The frame follows the workbench panels (same tone, 28dp corners) in both appearances.
                // Frosted, not opaque: a.dialogPanel is the panel tone at 80% opacity.
                setBackgroundDrawable(InsetDrawable(
                    GradientDrawable().apply {
                        cornerRadius = dp(28).toFloat()
                        setColor(a.dialogPanel)
                        setStroke(dp(1), a.outline)
                    }, dp(16), 0, dp(16), 0))
            }
            window.setOnDismissListener {
                if (dialog === window) dialog = null
            }
            window.show()
            window.window?.decorView?.let { decor ->
                // Our colours are final: ColorOS force-dark would re-tint the light accent, and the
                // ROM's own rounded panel inside the window would show as a second frame.
                decor.isForceDarkAllowed = false
                (decor.findViewById<View>(android.R.id.content) as? ViewGroup)
                    ?.getChildAt(0)?.background = null
            }
        }.onFailure { error ->
            NeXtepLog.warn("compare_dialog", "Unable to show compare dialog", error)
            dialog = null
            return
        }
        fun submit() {
            val term = query.text.toString().trim()
            val requested = runCatching { CompareIntents.searches(mode, term) }.getOrNull()
            if (requested == null) {
                query.error = AppLanguage.string(context, R.string.compare_empty)
                return
            }
            val available = requested.filter { target ->
                WorkspaceIntentResolver.resolve(context.packageManager, target.intent) != null ||
                    (target.httpsFallback != null &&
                        WorkspaceIntentResolver.resolve(context.packageManager, target.httpsFallback) != null)
            }
            if (available.isEmpty()) {
                Toast.makeText(context, R.string.compare_missing, Toast.LENGTH_LONG).show()
                return
            }
            // Hide the moment 搜索 is tapped; the apps open behind it.
            dismiss()
            if (!onSearch(mode, term)) {
                Toast.makeText(context, R.string.compare_launch_failed, Toast.LENGTH_LONG).show()
            }
        }
        go.setOnClickListener { submit() }
        query.setOnEditorActionListener { _, _, _ -> submit(); true }
        query.requestFocus()
    }

    fun dismiss() {
        val previous = dialog
        dialog = null
        previous?.dismiss()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
