package com.example.ui.screens.localplayer.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DeleteOption
import com.example.data.model.DeleteProgressState

private val DialogBackground = Color(0xFF101522)
private val DialogBorderColor = Color(0xFF1F293D)
private val CardSurface = Color(0xFF161E30)
private val SafeGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val DangerRed = Color(0xFFFF5252)
private val TextMuted = Color(0xFF8E95A5)

// =========================================================================
// 🗑️ ১. ডুয়াল অপশন ডিলিট কনফার্মেশন ডায়ালগ (Dual-Option Delete Dialog)
// =========================================================================
@Composable
fun DeleteConfirmationDialog(
    selectedCount: Int,
    formattedTotalSize: String,
    onConfirm: (DeleteOption) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DialogBackground),
            border = BorderStroke(1.2.dp, DialogBorderColor),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // হেডার আইকন ও টাইটেল
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(DangerRed.copy(alpha = 0.12f))
                        .border(1.2.dp, DangerRed.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = DangerRed,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (selectedCount == 1) "Delete Video?" else "Delete $selectedCount Videos?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Total Size: $formattedTotalSize • Choose deletion method",
                        color = TextMuted,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = DialogBorderColor, thickness = 0.8.dp)

                // ♻️ অপশন ১: মুভ টু রিসাইকেল বিন (নিরাপদ ও রিস্টোরযোগ্য)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onConfirm(DeleteOption.MOVE_TO_RECYCLE_BIN) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SafeGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Move to Recycle Bin",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SafeGreen.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "RECOMMENDED",
                                        color = SafeGreen,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Keeps videos safe. You can restore them anytime from the Recycle Bin.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // ⚠️ অপশন ২: পার্মানেন্টলি ডিলিট (স্থায়ী অপসারণ সতর্কতা)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSurface,
                    border = BorderStroke(0.8.dp, DangerRed.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onConfirm(DeleteOption.PERMANENTLY_DELETE) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DangerRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permanently Delete",
                                color = DangerRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Erases files from device storage permanently. Cannot be undone!",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // বাতিল বাটন
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// =========================================================================
// 📊 ২. লাইভ ডিলিশন প্রগ্রেস পপআপ কার্ড (Live Deletion Progress Popup)
// =========================================================================
@Composable
fun LiveDeletionProgressDialog(
    progressState: DeleteProgressState,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressState.progressFraction,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "delete_progress"
    )

    val isTrashOp = progressState.operationType == DeleteOption.MOVE_TO_RECYCLE_BIN
    val accentColor = if (isTrashOp) SafeGreen else DangerRed

    Dialog(
        onDismissRequest = {
            if (progressState.isFinished) onDone()
        },
        properties = DialogProperties(
            dismissOnBackPress = progressState.isFinished,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DialogBackground),
            border = BorderStroke(1.2.dp, DialogBorderColor),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ১. স্টেটাস হেডার আইকন
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.2.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (progressState.isFinished) Icons.Default.CheckCircle
                        else if (isTrashOp) Icons.Default.RestoreFromTrash else Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // ২. অপারেশন টাইটেল ও সাবটাইটেল
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (progressState.isFinished) {
                            if (isTrashOp) "Moved to Recycle Bin" else "Deletion Complete"
                        } else {
                            if (isTrashOp) "Moving to Recycle Bin..." else "Deleting Videos..."
                        },
                        color = Color.White,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (progressState.isFinished) {
                            "${progressState.successfullyDeletedCount} of ${progressState.totalFiles} files processed successfully."
                        } else {
                            "Processing: ${progressState.currentProcessingFileName.ifBlank { "Please wait..." }}"
                        },
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                // ৩. লাইভ প্রগ্রেস বার ও পার্সেন্টেজ
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progress",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${progressState.progressPercent}%",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = animatedProgress)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        if (isTrashOp) listOf(CyanAccent, SafeGreen)
                                        else listOf(Color(0xFFFF9100), DangerRed)
                                    )
                                )
                        )
                    }
                }

                // ৪. লাইভ মেট্রিক্স গ্রিড (Total, Processed, Remaining, Failed)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSurface,
                    border = BorderStroke(0.8.dp, DialogBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressMetricItem(
                            label = "Total",
                            value = progressState.totalFiles.toString(),
                            valueColor = Color.White
                        )
                        ProgressMetricItem(
                            label = if (isTrashOp) "Moved" else "Deleted",
                            value = progressState.successfullyDeletedCount.toString(),
                            valueColor = SafeGreen
                        )
                        ProgressMetricItem(
                            label = "Remaining",
                            value = progressState.remainingFilesCount.toString(),
                            valueColor = CyanAccent
                        )
                        ProgressMetricItem(
                            label = "Failed",
                            value = progressState.failedCount.toString(),
                            valueColor = if (progressState.failedCount > 0) DangerRed else TextMuted
                        )
                    }
                }

                // ৫. ফেইলড ফাইলস ওয়ার্নিং (যদি থাকে)
                if (progressState.failedFilesList.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DangerRed.copy(alpha = 0.1f),
                        border = BorderStroke(0.8.dp, DangerRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "⚠️ ${progressState.failedCount} file(s) could not be modified (system write-protected):",
                                color = DangerRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 60.dp)) {
                                items(progressState.failedFilesList) { name ->
                                    Text(
                                        text = "• $name",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // ৬. অ্যাকশন বোতাম (চলমান অবস্থায় Cancel, শেষ হলে Done)
                if (progressState.isFinished) {
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(
                            text = "Done",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DialogBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressMetricItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// =========================================================================
// 🗑️ ৩. রিসাইকেল বিন সম্পূর্ণ খালি করার ডায়ালগ (Empty Recycle Bin Dialog)
// =========================================================================
@Composable
fun EmptyRecycleBinDialog(
    trashedItemCount: Int,
    onConfirmEmpty: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = DialogBackground),
            border = BorderStroke(1.dp, DialogBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DangerRed.copy(alpha = 0.15f))
                        .border(1.dp, DangerRed.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Empty Bin",
                        tint = DangerRed,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Empty Recycle Bin?",
                        color = Color.White,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "All $trashedItemCount video(s) in the Recycle Bin will be permanently deleted from device storage. This cannot be undone.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DialogBorderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onConfirmEmpty,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                    ) {
                        Text("Delete All", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
