package com.universalconverter.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.core.convert.DeviceCaps
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.ui.components.FamilyMark
import com.universalconverter.pro.ui.components.FormatBadge
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.UCTopBar
import com.universalconverter.pro.ui.theme.UC

@Composable
fun CapabilitiesScreen(onBack: () -> Unit) {
    val palette = UC.palette
    val context = LocalContext.current
    val caps = (context.applicationContext as ConverterApp).container.caps
    val grouped = FormatRegistry.known.groupBy { it.family }.toSortedMap(compareBy { it.order })

    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            UCTopBar(
                title = "Format guide",
                subtitle = "Exactly what is supported, and what is not",
                onBack = onBack,
                backIcon = Icons.Rounded.ArrowBack,
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    HairlineCard {
                        Column(Modifier.padding(16.dp)) {
                            SectionEyebrow("This device")
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CapTag("AAC", caps.hasAac)
                                CapTag("H.264", caps.videoAvcEncoder)
                                CapTag("OGG muxer", caps.oggMuxer)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${caps.platformLabel}. Audio and video targets are hidden when the device has no matching encoder. Raw photo and HEIF decoding may also depend on vendor codecs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                            )
                        }
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.ink)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CloudOff, null, tint = palette.ember, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Local by construction", style = MaterialTheme.typography.titleSmall, color = palette.paper)
                            Text(
                                "Every conversion is a local file operation. Network access is used only by optional ads and Google Play billing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.paper.copy(alpha = 0.62f),
                            )
                        }
                    }
                }
                for ((family, formats) in grouped) {
                    item(key = "family-${family.name}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FamilyMark(family, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(family.label, style = MaterialTheme.typography.titleLarge, color = palette.ink)
                        }
                    }
                    items(formats, key = { it.id }) { spec ->
                        HairlineCard {
                            Column(Modifier.padding(13.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall, color = palette.ink)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        spec.extensions.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (spec.readable) FormatBadge("READ", color = palette.sage)
                                    if (spec.writable) {
                                        Spacer(Modifier.width(4.dp))
                                        FormatBadge("WRITE", color = palette.ember)
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(spec.note, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                            }
                        }
                    }
                }
                item {
                    HairlineCard {
                        Column(Modifier.padding(16.dp)) {
                            SectionEyebrow("Honest limits")
                            Spacer(Modifier.height(7.dp))
                            Limit("STEP, IGES and native CAD solids", "DXF is supported as a 2D drawing; solid CAD kernels are not bundled.")
                            Limit("RAR and password archives", "RAR is not supported. Encrypted ZIP/7Z entries cannot be read.")
                            Limit("OCR", "PDF text extraction works when the PDF already contains text. Scanned pages are not OCR'd.")
                            Limit("Exotic codecs", "AVIF/HEIC and some audio codecs depend on device hardware. The app probes encoders and hides unavailable outputs.")
                            Limit("Draco/meshopt", "Compressed glTF requires re-export without Draco or meshopt compression.")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun CapTag(label: String, available: Boolean) {
    val palette = UC.palette
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (available) palette.sage.copy(alpha = 0.12f) else palette.parchment)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (available) palette.sage else palette.muted))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = if (available) palette.sage else palette.muted)
    }
}

@Composable
private fun Limit(title: String, body: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = UC.palette.ink)
        Text(body, style = MaterialTheme.typography.bodySmall, color = UC.palette.muted)
    }
}