package com.premkumar.jiwtracker.ui.utils

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object DonationLauncher {
    const val KOFI_URL = "https://ko-fi.com/premkumargara"

    fun launch(context: Context, url: String = KOFI_URL, primaryColor: Color) {
        val colorInt = primaryColor.toArgb()
        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(colorInt)
                    .build()
            )
            .setShowTitle(true)
            .build()
            
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }
}
