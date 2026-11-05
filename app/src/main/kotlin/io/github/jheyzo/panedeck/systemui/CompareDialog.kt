package io.github.jheyzo.panedeck.systemui

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import io.github.jheyzo.panedeck.R
import io.github.jheyzo.panedeck.compare.CompareIntents
import io.github.jheyzo.panedeck.config.AppLanguage
import io.github.jheyzo.panedeck.framework.WorkspaceIntentResolver
import io.github.jheyzo.panedeck.logging.NeXtepLog

/**
 * In-workspace compare dialog, adapted from Panes Workbench 3.0.5
 * WorkbenchToolsController.showSearch (yishisanren, Apache License 2.0):
 * a query field, a shopping / food-delivery toggle, and a row showing
 * which target apps are installed for the chosen mode (dimmed when
 * missing). Panes' themed appearance and payment UI are not ported;
 * this dialog uses a plain AlertDialog in the SystemUI process, with
 * the window type from SystemUiWindowTypeResolver (the same type the
 * workspace panels use).
 *
 * Submitting calls [onSearch] with the chosen mode and query and
 * dismisses; the caller runs the compare round. An empty query only
 * shows a toast and keeps the dialog open.
 */
internal class CompareDialog(
    private val context: Context,
    private val onSearch: (CompareIntents.Mode, String) -> Boolean,
) {
    private var dialog: AlertDialog? = null

    fun show() {
        dismiss()
        var mode = CompareIntents.Mode.SHOPPING
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val summary = TextView(context).apply {
            text = AppLanguage.string(context, R.string.compare_summary)
            textSize = 13f
        }
        panel.addView(summary, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        val appsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun renderApps() {
            appsRow.removeAllViews()
            val targets = runCatching {
                CompareIntents.searches(mode, "x")
            }.getOrDefault(emptyList())
            targets.forEach { target ->
                val installed = WorkspaceIntentResolver.resolve(
                    context.packageManager, target.intent,
                ) != null
                val icon = target.intent.`package`?.let { pkg ->
                    runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
                }
                appsRow.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    alpha = if (installed) 1f else .35f
                    if (icon != null) {
                        addView(ImageView(context).apply {
                            setImageDrawable(icon)
                            contentDescription = target.name
                        }, LinearLayout.LayoutParams(dp(40), dp(40)))
                    }
                    addView(TextView(context).apply {
                        text = target.name
                        textSize = 11f
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
        val modeGroup = RadioGroup(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(RadioButton(context).apply {
                id = View.generateViewId()
                text = AppLanguage.string(context, R.string.compare_shopping)
                isChecked = true
            })
            addView(RadioButton(context).apply {
                id = View.generateViewId()
                text = AppLanguage.string(context, R.string.compare_delivery)
            })
            setOnCheckedChangeListener { _, checkedId ->
                val shoppingId = (getChildAt(0) as RadioButton).id
                mode = if (checkedId == shoppingId) {
                    CompareIntents.Mode.SHOPPING
                } else {
                    CompareIntents.Mode.DELIVERY
                }
                renderApps()
            }
        }
        val query = EditText(context).apply {
            hint = AppLanguage.string(context, R.string.compare_query_hint)
            contentDescription = hint
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(200))
            textSize = 16f
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        panel.addView(modeGroup, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })
        panel.addView(appsRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })
        panel.addView(query, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })
        renderApps()
        val built = AlertDialog.Builder(context)
            .setTitle(AppLanguage.string(context, R.string.compare_title))
            .setView(panel)
            .setPositiveButton(AppLanguage.string(context, R.string.compare_search), null)
            .setNegativeButton(AppLanguage.string(context, R.string.action_cancel), null)
            .create()
        dialog = built
        runCatching {
            built.window?.apply {
                SystemUiWindowTypeResolver.resolve()?.let { setType(it) }
            }
            built.show()
        }.onFailure { error ->
            NeXtepLog.warn("compare_dialog", "Unable to show compare dialog", error)
            dialog = null
            return
        }
        built.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val term = query.text?.toString().orEmpty().trim()
            if (term.isEmpty()) {
                Toast.makeText(context, R.string.compare_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dismiss()
            onSearch(mode, term)
        }
        query.requestFocus()
    }

    fun dismiss() {
        val previous = dialog
        dialog = null
        previous?.dismiss()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
