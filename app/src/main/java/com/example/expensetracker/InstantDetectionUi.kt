package com.example.expensetracker

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** UI helpers for the optional Instant payment detection (accessibility) feature. */
object InstantDetectionUi {

    const val REQ_CAPTURE_SAVE = 4301

    fun isServiceOn(activity: Activity): Boolean = try {
        val cr = activity.contentResolver
        val enabledFlag = Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabledFlag) false else {
            val list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val me = ComponentName(activity, PaymentAccessibilityService::class.java)
            val full = me.flattenToString()
            val short = me.flattenToShortString()
            if (list.isNullOrEmpty()) false else {
                val sp = TextUtils.SimpleStringSplitter(':')
                sp.setString(list)
                var found = false
                while (sp.hasNext()) {
                    val e = sp.next()
                    if (e.equals(full, true) || e.equals(short, true)) { found = true; break }
                    val c = ComponentName.unflattenFromString(e)
                    if (c != null && c == me) { found = true; break }
                }
                found
            }
        }
    } catch (t: Throwable) { false }

    fun refresh(activity: Activity) {
        val status = activity.findViewById<TextView>(R.id.tvInstantStatus) ?: return
        val btn = activity.findViewById<TextView>(R.id.btnInstantSetup) ?: return
        val on = isServiceOn(activity)
        status.text = if (on) (if (PaymentCapture.smsPaused(activity)) "On · SMS paused" else "On") else "Off"
        status.setTextColor(if (on) Color.parseColor("#2D6A4F") else ThemeColors.hint(activity))
        btn.text = if (on) "Manage" else "Set up"
    }

    fun setup(activity: Activity) {
        activity.findViewById<TextView>(R.id.btnInstantSetup)?.setOnClickListener { showMain(activity) }
        activity.findViewById<View>(R.id.rowInstantTitle)?.setOnLongClickListener {
            showCapture(activity); true
        }
        refresh(activity)
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    private fun dp(a: Activity, v: Int) = (v * a.resources.displayMetrics.density).toInt()

    private fun newCard(activity: Activity): Pair<AlertDialog, LinearLayout> {
        val d = activity.resources.displayMetrics.density
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * d
                setColor(Color.WHITE)
            }
            setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 16))
        }
        val scroll = ScrollView(activity).apply { addView(card) }
        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(scroll)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return Pair(dialog, card)
    }

    private fun title(activity: Activity, card: LinearLayout, t: String) {
        card.addView(TextView(activity).apply {
            text = t; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A1A2E"))
        })
    }

    private fun body(activity: Activity, card: LinearLayout, t: String, bold: Boolean = false) {
        card.addView(TextView(activity).apply {
            text = t; textSize = 14f
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (bold) Color.parseColor("#1A1A2E") else Color.parseColor("#6B7280"))
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(activity, 10), 0, 0)
        })
    }

    private fun primary(activity: Activity, card: LinearLayout, t: String, onClick: () -> Unit) {
        card.addView(TextView(activity).apply {
            text = t; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * activity.resources.displayMetrics.density
                setColor(Color.parseColor("#2D6A4F"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 46)
            ).apply { topMargin = dp(activity, 18) }
            setOnClickListener { onClick() }
        })
    }

    private fun secondary(activity: Activity, card: LinearLayout, t: String, color: String = "#2D6A4F", onClick: () -> Unit) {
        card.addView(TextView(activity).apply {
            text = t; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(color)); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 40)
            ).apply { topMargin = dp(activity, 2) }
            setOnClickListener { onClick() }
        })
    }

    private fun safeStart(activity: Activity, intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(activity, "Couldn't open that screen on this phone", Toast.LENGTH_LONG).show()
        }
    }

    private fun openBattery(activity: Activity) {
        try {
            activity.startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity.packageName}")
            ))
        } catch (e: Throwable) {
            safeStart(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun showMain(activity: Activity) {
        val on = isServiceOn(activity)
        val (dialog, card) = newCard(activity)
        title(activity, card, "Instant payment detection")

        if (on) {
            body(activity, card, "It's on. Your popup now opens the moment GPay or PhonePe shows a successful payment, without waiting for the bank SMS.")
        } else {
            body(activity, card, "Optional. Opens your popup the moment a payment succeeds, without waiting for the bank SMS.")
        }
        body(activity, card, "What it reads", bold = true)
        body(activity, card, "Only the payment-success screen of GPay and PhonePe, and only while those apps are open.")
        body(activity, card, "What it does not do", bold = true)
        body(activity, card, "It doesn't look at any other app, passwords or typing, and takes no screenshots. Nothing is stored or sent anywhere. The amount and name are only used to open your popup.")
        body(activity, card, "SMS detection keeps working either way (unless you pause it below for testing).")

        if (!on) {
            body(activity, card, "How to turn it on", bold = true)
            body(activity, card, "1. Tap \"Open accessibility settings\", find ExpenseTracker under Installed or Downloaded apps, switch it on, then tap Allow.\n\n" +
                "2. If the switch is greyed out (\"Restricted setting\", Android 13+ for apps installed outside the Play Store): open Settings > Apps > ExpenseTracker > the ⋮ menu > \"Allow restricted settings\", then come back and try again.\n\n" +
                "3. Recommended: allow the app to run in the background (battery) so detection isn't stopped.")
        }

        if (on) {
            val pause = Switch(activity).apply {
                text = "Pause SMS popups (testing)"
                textSize = 14f
                setTextColor(Color.parseColor("#1A1A2E"))
                isChecked = PaymentCapture.smsPaused(activity)
                setPadding(0, dp(activity, 14), 0, 0)
            }
            card.addView(pause)
            val pauseInfo = TextView(activity).apply {
                textSize = 12f; setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, dp(activity, 4), 0, dp(activity, 6))
                text = "While on, only GPay/PhonePe screens open the popup, so you can check that it works. Turns itself off after 60 minutes. A payment the screen reader misses gets no popup meanwhile."
            }
            card.addView(pauseInfo)
            pause.setOnCheckedChangeListener { _, checked ->
                PaymentCapture.setSmsPaused(activity, checked)
                refresh(activity)
            }
        }

        primary(activity, card, if (on) "Turn off there" else "Open accessibility settings") {
            dialog.dismiss()
            safeStart(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (!on) {
            secondary(activity, card, "App info (restricted settings)") {
                dialog.dismiss()
                safeStart(activity, Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ))
            }
        }
        secondary(activity, card, "Battery settings") {
            dialog.dismiss(); openBattery(activity)
        }
        secondary(activity, card, "Not now", "#6B7280") { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showCapture(activity: Activity) {
        val (dialog, card) = newCard(activity)
        title(activity, card, "Capture mode")
        body(activity, card, "Saves the readable text of GPay/PhonePe payment screens on this phone so the patterns can be tuned. Contains amounts and names. Turn off when done.")

        val sw = Switch(activity).apply {
            text = "Capture"
            textSize = 14f
            setTextColor(Color.parseColor("#1A1A2E"))
            isChecked = PaymentCapture.isOn(activity)
            setPadding(0, dp(activity, 14), 0, 0)
        }
        card.addView(sw)
        val info = TextView(activity).apply {
            textSize = 12f; setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, dp(activity, 6), 0, 0)
        }
        card.addView(info)
        fun updateInfo() {
            info.text = (if (sw.isChecked) "ON (turns off automatically after 60 minutes). " else "OFF. ") +
                "Saved: ${PaymentCapture.size(activity) / 1024} KB"
        }
        updateInfo()
        sw.setOnCheckedChangeListener { _, checked ->
            PaymentCapture.setOn(activity, checked); updateInfo()
        }

        primary(activity, card, "Save capture file") {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "ExpenseTracker_capture.txt")
            }
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, REQ_CAPTURE_SAVE)
                dialog.dismiss()
            } catch (e: Throwable) {
                Toast.makeText(activity, "No file picker available on this phone", Toast.LENGTH_LONG).show()
            }
        }
        secondary(activity, card, "Clear", "#EF4444") {
            PaymentCapture.clear(activity); updateInfo()
            Toast.makeText(activity, "Capture file cleared", Toast.LENGTH_SHORT).show()
        }
        secondary(activity, card, "Close", "#6B7280") { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    /** Called from SettingsActivity.onActivityResult for REQ_CAPTURE_SAVE. */
    fun writeCapture(activity: Activity, uri: Uri) {
        try {
            val f = PaymentCapture.file(activity)
            val bytes = if (f.exists()) f.readBytes() else ByteArray(0)
            activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IllegalStateException("no stream")
            Toast.makeText(activity,
                if (bytes.isEmpty()) "Saved (the capture file is empty so far)" else "Capture file saved",
                Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Toast.makeText(activity, "Couldn't save the capture file", Toast.LENGTH_LONG).show()
        }
    }
}
