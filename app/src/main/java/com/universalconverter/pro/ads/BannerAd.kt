package com.universalconverter.pro.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.universalconverter.pro.BuildConfig
import com.universalconverter.pro.ui.theme.UC

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun BannerAd(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = UC.palette
    val widthDp = LocalConfiguration.current.screenWidthDp
    var loaded by remember { mutableStateOf(false) }
    val adView = remember(activity) {
        AdView(activity).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = false
                }
            }
        }
    }

    LaunchedEffect(adView, widthDp) {
        val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
        adView.setAdSize(size)
        adView.loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    if (loaded) {
        AndroidView(
            factory = { adView },
            modifier = modifier
                .fillMaxWidth()
                .background(palette.ivory)
                .padding(vertical = 6.dp)
                .heightIn(min = 54.dp),
        )
    }
}
