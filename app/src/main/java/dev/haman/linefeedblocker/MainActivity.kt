package dev.haman.linefeedblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
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
    private lateinit var treeStatus: TextView
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
        content.addView(label(R.string.production_intro, 15f), matchWrap(top = 12))

        serviceStatus = label(R.string.service_status_disabled, 16f)
        content.addView(serviceStatus, matchWrap(top = 16))
        content.addView(button(R.string.open_accessibility_settings) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWrap(top = 10))

        section(content, R.string.section_operation)
        val featureEnabled = checkBox(R.string.feature_enabled, settings.enabled) { checked ->
            InspectorPreferences.setFeatureEnabled(this, checked)
            refreshStatus()
        }
        content.addView(featureEnabled, matchWrap())

        content.addView(button(R.string.emergency_stop) {
            InspectorPreferences.setFeatureEnabled(this, false)
            featureEnabled.isChecked = false
            Toast.makeText(this, R.string.emergency_stopped, Toast.LENGTH_SHORT).show()
            refreshStatus()
        }, matchWrap(top = 6))

        section(content, R.string.section_overlay)
        content.addView(label(R.string.top_offset_label, 14f), matchWrap(top = 6))
        val topOffset = numberField(R.string.top_offset_hint, settings.topOffsetDp)
        watchNumberField(topOffset) { InspectorPreferences.setTopOffsetDp(this, it) }
        content.addView(topOffset, matchWrap())
        content.addView(label(R.string.bottom_offset_label, 14f), matchWrap(top = 8))
        val bottomOffset = numberField(R.string.bottom_offset_hint, settings.bottomOffsetDp)
        watchNumberField(bottomOffset) { InspectorPreferences.setBottomOffsetDp(this, it) }
        content.addView(bottomOffset, matchWrap())
        content.addView(label(R.string.horizontal_inset_label, 14f), matchWrap(top = 8))
        val horizontalInset = numberField(R.string.horizontal_inset_hint, settings.horizontalInsetDp)
        watchNumberField(horizontalInset) { InspectorPreferences.setHorizontalInsetDp(this, it) }
        content.addView(horizontalInset, matchWrap())

        val opacityValue = label(getString(R.string.opacity_value, settings.opacityPercent), 14f)
        content.addView(opacityValue, matchWrap(top = 12))
        val opacity = SeekBar(this).apply {
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
        }
        content.addView(opacity, matchWrap())

        val colors = listOf(
            getString(R.string.color_white) to Color.WHITE,
            getString(R.string.color_black) to Color.BLACK,
            getString(R.string.color_gray) to Color.rgb(128, 128, 128),
        )
        content.addView(label(R.string.background_color, 14f), matchWrap(top = 10))
        val colorSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, colors.map { it.first })
            setSelection(colors.indexOfFirst { it.second == settings.backgroundColor }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    InspectorPreferences.setBackgroundColor(this@MainActivity, colors[position].second)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        content.addView(colorSpinner, matchWrap())

        val touchBlocking = checkBox(R.string.touch_blocking, settings.touchBlocking) {
            InspectorPreferences.setTouchBlocking(this, it)
        }
        val debugBorder = checkBox(R.string.debug_border, settings.debugBorder) {
            InspectorPreferences.setDebugBorder(this, it)
        }
        val showText = checkBox(R.string.show_overlay_text, settings.showText) {
            InspectorPreferences.setShowText(this, it)
        }
        content.addView(touchBlocking, matchWrap(top = 6))
        content.addView(debugBorder, matchWrap())
        content.addView(showText, matchWrap())

        content.addView(button(R.string.reset_settings) {
            InspectorPreferences.resetOperationalSettings(this)
            Toast.makeText(this, R.string.settings_reset, Toast.LENGTH_SHORT).show()
            recreate()
        }, matchWrap(top = 6))

        section(content, R.string.section_inspector)
        content.addView(checkBox(
            R.string.enable_ui_tree_logs,
            InspectorPreferences.isInspectorEnabled(this),
        ) { checked -> InspectorPreferences.setInspectorEnabled(this, checked) }, matchWrap())

        content.addView(button(R.string.request_ui_tree_dump) {
            if (!InspectorPreferences.isInspectorEnabled(this)) {
                Toast.makeText(this, R.string.enable_logs_first, Toast.LENGTH_SHORT).show()
                return@button
            }
            InspectorPreferences.requestDump(this)
            Toast.makeText(this, R.string.open_line_prompt, Toast.LENGTH_LONG).show()
        }, matchWrap(top = 6))

        treeStatus = label(R.string.tree_status_initial, 13f).apply { setTextIsSelectable(true) }
        content.addView(treeStatus, matchWrap(top = 8))
        content.addView(label(R.string.log_instructions, 13f).apply { setTextIsSelectable(true) }, matchWrap(top = 10))

        setContentView(ScrollView(this).apply { addView(content) })
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

    private fun refreshStatus() {
        val enabled = isServiceEnabled()
        serviceStatus.setText(if (enabled) R.string.service_status_enabled else R.string.service_status_disabled)
        serviceStatus.setTextColor(if (enabled) Color.rgb(0, 120, 55) else Color.rgb(180, 40, 40))
        val treeText = getString(R.string.tree_status_value, InspectorPreferences.lastTreePath(this))
        if (treeStatus.text.toString() != treeText) treeStatus.text = treeText
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
        setText(getString(R.string.integer_value, value))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        setSelectAllOnFocus(true)
    }

    private fun watchNumberField(field: EditText, save: (Int) -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(text: Editable?) {
                text?.toString()?.toIntOrNull()?.let(save)
            }
        })
    }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
