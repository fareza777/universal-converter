package com.universalconverter.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.ui.DraftItem
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.FamilyMark
import com.universalconverter.pro.ui.components.FormatBadge
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.UCGhostButton
import com.universalconverter.pro.ui.components.UCSheet
import com.universalconverter.pro.ui.theme.UC

@Composable
fun FormatSheet(
    viewModel: MainViewModel,
    item: DraftItem,
    batchSize: Int,
    onDismiss: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val current = ui.draft.firstOrNull { it.id == item.id } ?: item
    val targets = viewModel.targetsFor(current)
    val grouped = targets.groupBy { it.family }.toSortedMap(compareBy { it.order })
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isBlank()) grouped else grouped.mapValues { (_, list) ->
        list.filter { it.label.contains(search, true) || it.blurb.contains(search, true) || it.extension.contains(search, true) }
    }.filterValues { it.isNotEmpty() }

    UCSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight(0.88f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionEyebrow("Output format")
                    Text("Convert to", style = MaterialTheme.typography.headlineMedium, color = UC.palette.ink)
                    Text(
                        current.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = UC.palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = UC.palette.muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Search formats", color = UC.palette.muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = UC.palette.muted) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UC.palette.ember,
                    unfocusedBorderColor = UC.palette.line,
                    focusedContainerColor = UC.palette.paper,
                    unfocusedContainerColor = UC.palette.paper,
                ),
            )
            if (batchSize > 1 && current.target != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Apply ${current.target.label} to the whole batch",
                        style = MaterialTheme.typography.bodySmall,
                        color = UC.palette.muted,
                        modifier = Modifier.weight(1f),
                    )
                    UCGhostButton("Apply to all", onClick = {
                        val skipped = viewModel.applyTargetToAll(current.target)
                        viewModel.showMessage(
                            if (skipped == 0) "${current.target.label} applied to all files"
                            else "${current.target.label} applied to ${batchSize - skipped}; $skipped not compatible",
                        )
                        onDismiss()
                    })
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((family, list) in filtered) {
                    item(key = "header-${family.name}") {
                        Text(
                            family.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            color = UC.palette.muted,
                            modifier = Modifier.padding(top = 12.dp, bottom = 3.dp),
                        )
                    }
                    items(list, key = { it.id }) { target ->
                        val selected = current.target?.id == target.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) UC.palette.emberSoft.copy(alpha = 0.55f) else UC.palette.paper)
                                .clickable {
                                    viewModel.setTarget(current.id, target)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FamilyMark(family, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(target.label, style = MaterialTheme.typography.titleSmall, color = UC.palette.ink)
                                    Spacer(Modifier.width(7.dp))
                                    FormatBadge(target.extension.uppercase().ifBlank { "FOLDER" }, color = UC.palette.muted)
                                }
                                Text(target.blurb, style = MaterialTheme.typography.bodySmall, color = UC.palette.muted)
                            }
                            if (selected) {
                                Icon(Icons.Rounded.CheckCircle, "Selected", tint = UC.palette.ember, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}