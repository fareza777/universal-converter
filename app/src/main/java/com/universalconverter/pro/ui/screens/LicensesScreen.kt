package com.universalconverter.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.UCTopBar
import com.universalconverter.pro.ui.theme.UC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LicenseBlock(val title: String, val subtitle: String, val asset: String)

private val licenses = listOf(
    LicenseBlock("Filee", "MIT · format taxonomy and conversion concepts", "Filee-MIT.txt"),
    LicenseBlock("Apache License 2.0", "AndroidX, Jetpack Compose, Kotlin, PDFBox-Android, AndroidSVG, Commons Compress, Coroutines", "Apache-2.0.txt"),
    LicenseBlock("Jsoup", "MIT · HTML and XML parsing", "Jsoup-MIT.txt"),
    LicenseBlock("Instrument Serif", "SIL Open Font License 1.1", "OFL-InstrumentSerif.txt"),
    LicenseBlock("Manrope", "SIL Open Font License 1.1", "OFL-Manrope.txt"),
    LicenseBlock("Google SDKs", "AdMob, Play Billing and consent � Google terms", "Google-SDKs.txt"),
    LicenseBlock("Project notice", "Attribution, advertising and privacy statement", "NOTICE.txt"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val palette = UC.palette
    val context = LocalContext.current
    val loaded by produceState<Map<String, String>>(initialValue = emptyMap()) {
        value = withContext(Dispatchers.IO) {
            licenses.associate { block ->
                block.asset to runCatching {
                    context.assets.open("licenses/${block.asset}").bufferedReader().use { it.readText() }
                }.getOrDefault("License text is missing from the build.")
            }
        }
    }
    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            UCTopBar(
                title = "Licenses",
                subtitle = "Open-source software and fonts inside this app",
                onBack = onBack,
                backIcon = Icons.Rounded.ArrowBack,
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Universal Converter is built with permissively licensed components plus Google's advertising and billing SDKs. Filee is used as an MIT-licensed reference for format taxonomy and conversion concepts; its C# code is not executed on Android. Conversion files are never uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                }
                items(licenses, key = { it.asset }) { block ->
                    HairlineCard {
                        Column(Modifier.padding(14.dp)) {
                            SectionEyebrow(block.subtitle)
                            Spacer(Modifier.height(4.dp))
                            Text(block.title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                loaded[block.asset] ?: "Loading…",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 13.sp,
                                ),
                                color = palette.muted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}