package com.universalconverter.pro.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.universalconverter.pro.BuildConfig
import com.universalconverter.pro.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AdUiState(
    val initialized: Boolean = false,
    val canRequestAds: Boolean = false,
    val rewardedReady: Boolean = false,
    val privacyOptionsAvailable: Boolean = false,
)

class MonetizationManager(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(AdUiState())
    val state: StateFlow<AdUiState> = _state.asStateFlow()

    private var initialized = false
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var conversionsSinceAd = 0
    private var lastInterstitialAt = 0L

    fun adsFree(): Boolean = settingsStore.settings.value.adsFree()

    fun initialize(activity: Activity) {
        if (initialized) return
        initialized = true
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w("Ads", "Consent form error: ${formError.message}")
                    }
                    finishInitialization(activity, consentInformation)
                }
            },
            { requestError ->
                Log.w("Ads", "Consent update failed: ${requestError.message}")
                finishInitialization(activity, consentInformation)
            },
        )
    }

    private fun finishInitialization(activity: Activity, consentInformation: ConsentInformation) {
        val canRequest = consentInformation.canRequestAds()
        val privacyAvailable = consentInformation.isConsentFormAvailable()
        _state.update {
            it.copy(
                canRequestAds = canRequest,
                privacyOptionsAvailable = privacyAvailable,
            )
        }
        if (!canRequest) {
            Log.i("Ads", "Consent does not allow ad requests; ads stay hidden.")
            return
        }
        MobileAds.initialize(appContext) {
            _state.update { it.copy(initialized = true) }
            preloadInterstitial()
            preloadRewarded()
        }
    }

    fun showPrivacyOptions(activity: Activity, onFinished: (Boolean) -> Unit = {}) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (!info.isConsentFormAvailable()) {
            onFinished(false)
            return
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) Log.w("Ads", "Privacy form error: ${formError.message}")
            val canRequest = info.canRequestAds()
            _state.update { it.copy(canRequestAds = canRequest) }
            if (canRequest && !_state.value.initialized) {
                finishInitialization(activity, info)
            }
            onFinished(formError == null)
        }
    }

    private fun preloadInterstitial() {
        if (adsFree()) return
        InterstitialAd.load(
            appContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitial = null
                            preloadInterstitial()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitial = null
                            preloadInterstitial()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    Log.w("Ads", "Interstitial failed: ${error.message}")
                }
            },
        )
    }

    private fun preloadRewarded() {
        if (adsFree()) return
        RewardedAd.load(
            appContext,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    _state.update { it.copy(rewardedReady = true) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    _state.update { it.copy(rewardedReady = false) }
                    Log.w("Ads", "Rewarded failed: ${error.message}")
                }
            },
        )
    }

    fun maybeShowInterstitial(activity: Activity): Boolean {
        if (adsFree() || !_state.value.initialized || !_state.value.canRequestAds) return false
        conversionsSinceAd++
        if (conversionsSinceAd < 3) return false
        val now = System.currentTimeMillis()
        if (now - lastInterstitialAt < 120_000L) return false
        val ad = interstitial ?: run {
            preloadInterstitial()
            return false
        }
        conversionsSinceAd = 0
        lastInterstitialAt = now
        ad.show(activity)
        return true
    }

    fun showRewarded(activity: Activity, onReward: (Boolean) -> Unit) {
        if (!_state.value.initialized || !_state.value.canRequestAds) {
            onReward(false)
            return
        }
        val ad = rewarded
        if (ad == null) {
            preloadRewarded()
            onReward(false)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                _state.update { it.copy(rewardedReady = false) }
                preloadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
                _state.update { it.copy(rewardedReady = false) }
                preloadRewarded()
            }
        }
        ad.show(activity) {
            onReward(true)
        }
    }

    fun refreshRewarded() {
        if (_state.value.initialized && _state.value.canRequestAds && rewarded == null) {
            preloadRewarded()
        }
    }
}
