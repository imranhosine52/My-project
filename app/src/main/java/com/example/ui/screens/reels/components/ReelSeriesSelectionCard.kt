package com.example.ui.screens.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CreatorPlaylistDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)
private val CardBg = Color(0xFF131822)
private val BorderColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)
private val CyanAccent = Color(0xFF00E5FF)

@Composable
fun ReelSeriesSelectionCard(
    pageId: Int,
    isAddToSeriesEnabled: Boolean,
    onToggleSeries: (Boolean) -> Unit,
    selectedPlaylistId: Int?,
    selectedPlaylistTitle: String?,
    onSelectPlaylist: (id: Int, title: String, nextEpNum: Int) -> Unit,
    episodeNumText: String,
    onEpisodeNumChange: (String) -> Unit,
    repository: ReelsRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var myPlaylists by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }
    var isPlaylistsLoading by remember { mutableStateOf(false) }
    var showCreateSeriesDialog by remember { mutableStateOf(false) }

    fun refreshPlaylists() {
        if (pageId > 0) {
            isPlaylistsLoading = true
            coroutineScope.launch {
                val res = repository.getPlaylists(pageId)
                myPlaylists = res.getOrDefault(emptyList())
                isPlaylistsLoading = false
            }
        }
    }

    LaunchedEffect(pageId) {
        refreshPlaylists()
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(
            0.8.dp,
            if (isAddToSeriesEnabled) CyanAccent.copy(alpha = 0.7f) else BorderColor
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ১. সিরিজ এনাবল/ডিসএবল সুইচ রো
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = if (isAddToSeriesEnabled) CyanAccent else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Add to Series / Playlist",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Group related reels into an episodic mini-drama",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isAddToSeriesEnabled,
                    onCheckedChange = onToggleSeries,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = CyanAccent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF1E2838)
                    )
                )
            }

            // ২. সিরিজ ড্রপডাউন/লিস্ট এরিয়া
            AnimatedVisibility(visible = isAddToSeriesEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                    // বর্ধিত লিমিট নোটিশ
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F1B2B),
                        border = BorderStroke(0.6.dp, CyanAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚡ Series Mode Enabled: Max Duration 10 Minutes • Max Size 200 MB",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedPlaylistTitle != null) "Selected: $selectedPlaylistTitle" else "Select a Series:",
                            color = CyanAccent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = { showCreateSeriesDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "+ Create Series",
                                color = ActionGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isPlaylistsLoading) {
                        LinearProgressIndicator(
                            color = CyanAccent,
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                    } else if (myPlaylists.isEmpty()) {
                        Text(
                            text = "No series created yet. Tap '+ Create Series' to add Poster & Banner!",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(myPlaylists, key = { it.effectiveId }) { pl ->
                                val isSelected = (selectedPlaylistId == pl.effectiveId)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) CyanAccent.copy(alpha = 0.2f) else Color(0xFF1A2230),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.2.dp else 0.8.dp,
                                        color = if (isSelected) CyanAccent else BorderColor
                                    ),
                                    modifier = Modifier.clickable {
                                        onSelectPlaylist(pl.effectiveId, pl.title, pl.totalEpisodes + 1)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = if (isSelected) CyanAccent else TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = pl.title,
                                            color = if (isSelected) CyanAccent else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // পর্ব নম্বর ইনপুট
                    OutlinedTextField(
                        value = episodeNumText,
                        onValueChange = onEpisodeNumChange,
                        label = { Text("Episode Number (Ep 1, Ep 2, Ep 3...)", color = TextMuted) },
                        placeholder = { Text("1", color = Color.Gray) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FormatListNumbered,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // =========================================================================
    // 🖼️ নতুন ডুয়েল ইমেজ পিকার সিরিজ ডায়ালগ (9:16 পোস্টার ও 16:9 ব্যানার)
    // =========================================================================
    if (showCreateSeriesDialog) {
        CreateSeriesDialog(
            pageId = pageId.toLong(),
            onDismiss = { showCreateSeriesDialog = false },
            onSeriesCreated = { createdId, title ->
                onSelectPlaylist(createdId, title, 1)
                refreshPlaylists()
                showCreateSeriesDialog = false
            }
        )
    }
}
