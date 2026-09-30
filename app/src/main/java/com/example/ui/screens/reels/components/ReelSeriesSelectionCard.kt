package com.example.ui.screens.reels.components

import android.widget.Toast
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
import androidx.compose.material.icons.filled.MovieFilter
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var myPlaylists by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }
    var isPlaylistsLoading by remember { mutableStateOf(false) }

    var showCreateSeriesDialog by remember { mutableStateOf(false) }
    var newSeriesTitleInput by remember { mutableStateOf("") }
    var newSeriesDescInput by remember { mutableStateOf("") }
    var isCreatingSeries by remember { mutableStateOf(false) }

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
        border = BorderStroke(0.8.dp, if (isAddToSeriesEnabled) CyanAccent.copy(alpha = 0.7f) else BorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        Text("Add to Series / Playlist", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text("Organize related reels into a serial mini-drama", color = TextMuted, fontSize = 11.sp)
                    }
                }

                Switch(
                    checked = isAddToSeriesEnabled,
                    onCheckedChange = onToggleSeries,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ActionGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF1E2838)
                    )
                )
            }

            AnimatedVisibility(visible = isAddToSeriesEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedPlaylistTitle != null) "Series: $selectedPlaylistTitle" else "Select a Series:",
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
                            Text("+ New Series", color = ActionGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isPlaylistsLoading) {
                        LinearProgressIndicator(color = CyanAccent, modifier = Modifier.fillMaxWidth().height(2.dp))
                    } else if (myPlaylists.isEmpty()) {
                        Text(
                            text = "No series created yet. Tap '+ New Series' to start your first playlist!",
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

                    OutlinedTextField(
                        value = episodeNumText,
                        onValueChange = onEpisodeNumChange,
                        label = { Text("Episode Number (Ep 1, Ep 2...)", color = TextMuted) },
                        placeholder = { Text("1", color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
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

    if (showCreateSeriesDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreatingSeries) showCreateSeriesDialog = false },
            containerColor = Color(0xFF141924),
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = CyanAccent)
                    Text("Create Mini-Drama Series", color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Create a series title to group your reels sequentially.",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )

                    OutlinedTextField(
                        value = newSeriesTitleInput,
                        onValueChange = { newSeriesTitleInput = it },
                        label = { Text("Series Title *", color = TextMuted) },
                        placeholder = { Text("e.g. CEO Love Story Season 1", color = Color(0xFF475569)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newSeriesDescInput,
                        onValueChange = { newSeriesDescInput = it },
                        label = { Text("Description (Optional)", color = TextMuted) },
                        placeholder = { Text("Short synopsis about this drama...", color = Color(0xFF475569)) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanTitle = newSeriesTitleInput.trim()
                        if (cleanTitle.length < 2) {
                            Toast.makeText(context, "Title must be at least 2 characters", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isCreatingSeries = true
                        coroutineScope.launch {
                            val res = repository.createPlaylist(
                                pageId = pageId,
                                title = cleanTitle,
                                description = newSeriesDescInput.trim().ifBlank { null }
                            )
                            isCreatingSeries = false

                            if (res.isSuccess) {
                                val createdId = res.getOrNull()?.playlistId ?: 0
                                onSelectPlaylist(createdId, cleanTitle, 1)
                                showCreateSeriesDialog = false
                                newSeriesTitleInput = ""
                                newSeriesDescInput = ""
                                refreshPlaylists()
                                Toast.makeText(context, "🎉 Series '$cleanTitle' created!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to create", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isCreatingSeries,
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                ) {
                    if (isCreatingSeries) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Create & Select", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateSeriesDialog = false },
                    enabled = !isCreatingSeries
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
