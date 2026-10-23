package com.flexkeys.settings

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "FlexKeys" })
    }
}
