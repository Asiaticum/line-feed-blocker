package dev.haman.linefeedblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var serviceStatus: TextView
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (::serviceStatus.isInitialized) runOnUiThread(::refreshStatus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        val settings = InspectorPreferences.overlaySettings(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }

        content.addView(label(R.string.screen_title, 24f), matchWrap())
        serviceStatus = label(R.string.service_status_disabled, 16f)
        content.addView(serviceStatus, matchWrap(top = 16))
        content.addView(button(R.string.open_accessibility_settings) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWrap(top = 10))

        section(content, R.string.section_operation)
        content.addView(checkBox(R.string.feature_enabled, settings.enabled) {
            InspectorPreferences.setFeatureEnabled(this, it)
            refreshStatus()
        }, matchWrap())

        section(content, R.string.section_visibility)
        content.addView(label(R.string.visibility_description, 13f), matchWrap())
        content.addView(checkBox(R.string.show_friends_overlay, settings.showFriendsOverlay) {
            InspectorPreferences.setShowFriendsOverlay(this, it)
        }, matchWrap(top = 4))
        content.addView(checkBox(R.string.show_agent_overlay, settings.showAgentOverlay) {
            InspectorPreferences.setShowAgentOverlay(this, it)
        }, matchWrap())
        content.addView(checkBox(R.string.show_shopping_overlay, settings.showShoppingOverlay) {
            InspectorPreferences.setShowShoppingOverlay(this, it)
        }, matchWrap())
        content.addView(checkBox(R.string.show_app_overlay, settings.showAppOverlay) {
            InspectorPreferences.setShowAppOverlay(this, it)
        }, matchWrap())

        section(content, R.string.section_appearance)
        val opacityValue = label(getString(R.string.opacity_value, settings.opacityPercent), 14f)
        content.addView(opacityValue, matchWrap())
        content.addView(SeekBar(this).apply {
            max = 100
            progress = settings.opacityPercent
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    opacityValue.text = getString(R.string.opacity_value, progress)
                    if (fromUser) InspectorPreferences.setOpacityPercent(this@MainActivity, progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, matchWrap())

        val colors = listOf(
            getString(R.string.color_white) to Color.WHITE,
            getString(R.string.color_black) to Color.BLACK,
            getString(R.string.color_gray) to Color.rgb(128, 128, 128),
        )
        content.addView(label(R.string.background_color, 14f), matchWrap(top = 8))
        content.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                colors.map { it.first },
            )
            setSelection(colors.indexOfFirst { it.second == settings.backgroundColor }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    InspectorPreferences.setBackgroundColor(this@MainActivity, colors[position].second)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }, matchWrap())

        section(content, R.string.section_numeric_adjustment)
        content.addView(label(R.string.numeric_adjustment_description, 13f), matchWrap())
        val targets = adjustmentTargetOptions()
        var selectedTarget = settings.adjustmentTarget
        val xField = numberField(R.string.adjustment_x_hint, 0)
        val yField = numberField(R.string.adjustment_y_hint, 0)
        val widthField = numberField(R.string.adjustment_width_hint, 0)
        val heightField = numberField(R.string.adjustment_height_hint, 0)
        val fields = listOf(xField, yField, widthField, heightField)
        var updatingAdjustmentFields = false

        fun refreshAdjustmentFields() {
            val value = InspectorPreferences.overlayAdjustment(this, selectedTarget)
            updatingAdjustmentFields = true
            listOf(value.offsetXDp, value.offsetYDp, value.widthDeltaDp, value.heightDeltaDp)
                .forEachIndexed { index, number -> fields[index].setText(number.toString()) }
            updatingAdjustmentFields = false
        }

        fun saveAdjustmentIfValid() {
            if (updatingAdjustmentFields) return
            val values = fields.map { it.text.toString().trim().toIntOrNull() }
            if (values.any { it == null || it !in -1000..1000 }) return
            InspectorPreferences.setOverlayAdjustment(
                this,
                selectedTarget,
                OverlayAdjustment(values[0]!!, values[1]!!, values[2]!!, values[3]!!),
            )
        }

        fields.forEach { field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(text: Editable?) = saveAdjustmentIfValid()
            })
        }

        content.addView(label(R.string.adjustment_target, 14f), matchWrap(top = 8))
        content.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                targets.map { getString(it.second) },
            )
            setSelection(targets.indexOfFirst { it.first == selectedTarget }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedTarget = targets[position].first
                    InspectorPreferences.setAdjustmentTarget(this@MainActivity, selectedTarget)
                    refreshAdjustmentFields()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }, matchWrap())

        content.addView(label(R.string.adjustment_x_label, 14f), matchWrap(top = 8))
        content.addView(xField, matchWrap())
        content.addView(label(R.string.adjustment_y_label, 14f), matchWrap(top = 8))
        content.addView(yField, matchWrap())
        content.addView(label(R.string.adjustment_width_label, 14f), matchWrap(top = 8))
        content.addView(widthField, matchWrap())
        content.addView(label(R.string.adjustment_height_label, 14f), matchWrap(top = 8))
        content.addView(heightField, matchWrap())
        refreshAdjustmentFields()

        content.addView(button(R.string.open_line_to_check) { openLine() }, matchWrap(top = 10))
        content.addView(button(R.string.reset_selected_adjustment) {
            InspectorPreferences.clearOverlayAdjustment(this, selectedTarget)
            refreshAdjustmentFields()
            Toast.makeText(this, R.string.selected_adjustment_reset, Toast.LENGTH_SHORT).show()
        }, matchWrap(top = 6))
        content.addView(button(R.string.reset_overlay_adjustments) {
            InspectorPreferences.resetOverlayAdjustments(this)
            refreshAdjustmentFields()
            Toast.makeText(this, R.string.overlay_adjustments_reset, Toast.LENGTH_SHORT).show()
        }, matchWrap(top = 6))

        section(content, R.string.section_backup)
        content.addView(label(R.string.backup_description, 13f), matchWrap())
        lateinit var loadSavedSettingsButton: Button
        lateinit var clearSavedSettingsButton: Button
        content.addView(button(R.string.save_settings_in_app) {
            InspectorPreferences.saveSettingsSnapshot(this)
            loadSavedSettingsButton.isEnabled = true
            clearSavedSettingsButton.isEnabled = true
            Toast.makeText(this, R.string.settings_saved_in_app, Toast.LENGTH_SHORT).show()
        }, matchWrap(top = 8))
        loadSavedSettingsButton = button(R.string.load_settings_from_app) {
            if (InspectorPreferences.loadSavedSettingsSnapshot(this)) {
                Toast.makeText(this, R.string.settings_loaded, Toast.LENGTH_SHORT).show()
                recreate()
            } else {
                Toast.makeText(this, R.string.no_saved_settings, Toast.LENGTH_SHORT).show()
            }
        }.apply {
            isEnabled = InspectorPreferences.hasSavedSettingsSnapshot(this@MainActivity)
        }
        content.addView(loadSavedSettingsButton, matchWrap(top = 6))
        clearSavedSettingsButton = button(R.string.clear_saved_settings) {
            InspectorPreferences.clearSavedSettingsSnapshot(this)
            loadSavedSettingsButton.isEnabled = false
            clearSavedSettingsButton.isEnabled = false
            Toast.makeText(this, R.string.saved_settings_cleared, Toast.LENGTH_SHORT).show()
        }.apply {
            isEnabled = InspectorPreferences.hasSavedSettingsSnapshot(this@MainActivity)
        }
        content.addView(clearSavedSettingsButton, matchWrap(top = 6))
        content.addView(button(R.string.export_settings) {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "line-feed-blocker-settings.json")
                },
                REQUEST_EXPORT_SETTINGS,
            )
        }, matchWrap(top = 6))
        content.addView(button(R.string.import_settings) {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                },
                REQUEST_IMPORT_SETTINGS,
            )
        }, matchWrap(top = 6))
        content.addView(button(R.string.reset_settings) {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_settings_confirm_title)
                .setMessage(R.string.reset_settings_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset_settings) { _, _ ->
                    InspectorPreferences.resetOperationalSettings(this)
                    Toast.makeText(this, R.string.settings_reset, Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .show()
        }, matchWrap(top = 12))

        addInspectorSection(content)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    @Deprecated("Uses the platform document picker result API for minSdk compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT_SETTINGS -> runCatching {
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(InspectorPreferences.settingsSnapshotJson(this))
                } ?: error("Could not open export destination")
            }.onSuccess {
                Toast.makeText(this, R.string.settings_exported, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, R.string.settings_export_failed, Toast.LENGTH_LONG).show()
            }

            REQUEST_IMPORT_SETTINGS -> runCatching {
                val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: error("Could not open import source")
                require(json.length <= MAX_IMPORT_CHARS)
                require(InspectorPreferences.applySettingsSnapshotJson(this, json))
            }.onSuccess {
                Toast.makeText(this, R.string.settings_imported, Toast.LENGTH_SHORT).show()
                recreate()
            }.onFailure {
                Toast.makeText(this, R.string.settings_import_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        InspectorPreferences.preferences(this).registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onStop() {
        InspectorPreferences.preferences(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onStop()
    }

    private fun addInspectorSection(content: LinearLayout) {
        section(content, R.string.section_inspector)
        val debugOptionsVisible = InspectorPreferences.areDebugOptionsVisible(this)
        if (!debugOptionsVisible && InspectorPreferences.isInspectorEnabled(this)) {
            InspectorPreferences.setInspectorEnabled(this, false)
        }
        val debugPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (debugOptionsVisible) View.VISIBLE else View.GONE
        }
        content.addView(checkBox(R.string.show_debug_options, debugOptionsVisible) { checked ->
            InspectorPreferences.setDebugOptionsVisible(this, checked)
            if (!checked) InspectorPreferences.setInspectorEnabled(this, false)
            debugPanel.visibility = if (checked) View.VISIBLE else View.GONE
        }, matchWrap())
        debugPanel.addView(checkBox(
            R.string.enable_ui_tree_logs,
            InspectorPreferences.isInspectorEnabled(this),
        ) { checked -> InspectorPreferences.setInspectorEnabled(this, checked) }, matchWrap())
        debugPanel.addView(button(R.string.request_ui_tree_dump) {
            if (!InspectorPreferences.isInspectorEnabled(this)) {
                Toast.makeText(this, R.string.enable_logs_first, Toast.LENGTH_SHORT).show()
                return@button
            }
            InspectorPreferences.requestDump(this)
            Toast.makeText(this, R.string.open_line_prompt, Toast.LENGTH_LONG).show()
        }, matchWrap(top = 6))
        debugPanel.addView(
            label(R.string.log_instructions, 13f).apply { setTextIsSelectable(true) },
            matchWrap(top = 10),
        )
        content.addView(debugPanel, matchWrap())
    }

    private fun openLine() {
        val launchIntent = packageManager.getLaunchIntentForPackage(LINE_PACKAGE)
        if (launchIntent == null) {
            Toast.makeText(this, R.string.line_not_found, Toast.LENGTH_SHORT).show()
        } else {
            startActivity(launchIntent)
        }
    }

    private fun adjustmentTargetOptions() = listOf(
        InspectorPreferences.TARGET_HOME_FEED to R.string.target_home_feed,
        InspectorPreferences.TARGET_BLOCKED_TAB to R.string.target_blocked_tab,
        InspectorPreferences.TARGET_FRIENDS to R.string.target_friends,
        InspectorPreferences.TARGET_AGENT to R.string.target_agent,
        InspectorPreferences.TARGET_SHOPPING to R.string.target_shopping,
        InspectorPreferences.TARGET_APP to R.string.target_app,
        InspectorPreferences.TARGET_TALK_PROMOTED to R.string.target_talk_promoted,
    )

    private fun refreshStatus() {
        val enabled = isServiceEnabled()
        serviceStatus.setText(if (enabled) R.string.service_status_enabled else R.string.service_status_disabled)
        serviceStatus.setTextColor(if (enabled) Color.rgb(0, 120, 55) else Color.rgb(180, 40, 40))
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == LineInspectorService::class.java.name
            }
    }

    private fun section(parent: LinearLayout, title: Int) {
        parent.addView(label(title, 19f).apply { setTextColor(Color.BLACK) }, matchWrap(top = 24))
    }

    private fun label(textResource: Int, size: Float) = TextView(this).apply {
        setText(textResource)
        textSize = size
    }

    private fun label(textValue: String, size: Float) = TextView(this).apply {
        text = textValue
        textSize = size
    }

    private fun button(textResource: Int, action: () -> Unit) = Button(this).apply {
        setText(textResource)
        setOnClickListener { action() }
    }

    private fun checkBox(textResource: Int, checked: Boolean, change: ((Boolean) -> Unit)? = null) =
        CheckBox(this).apply {
            setText(textResource)
            isChecked = checked
            change?.let { listener -> setOnCheckedChangeListener { _, value -> listener(value) } }
        }

    private fun numberField(hintResource: Int, value: Int) = EditText(this).apply {
        setHint(hintResource)
        setText(value.toString())
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        setSelectAllOnFocus(true)
    }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val LINE_PACKAGE = "jp.naver.line.android"
        private const val REQUEST_EXPORT_SETTINGS = 1001
        private const val REQUEST_IMPORT_SETTINGS = 1002
        private const val MAX_IMPORT_CHARS = 1_000_000
    }
}
