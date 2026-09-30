package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.SuggestedPageDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.launch

private val CardBg = Color(0xFF161B26)
private val BorderColor = Color(0xFF242E40)
private val TikTokRed = Color(0xFFFE2C55)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun SuggestedCreatorsRow(
    onCreatorClick: (pageId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    var suggestedList by remember { mutableStateOf<List<SuggestedPageDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val res = repository.getSuggestedPages()
            suggestedList = res.getOrDefault(emptyList())
            isLoading = false
        }
    }

    if (!isLoading && suggestedList.isNotEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌟 Suggested Creators",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(suggestedList, key = { it.pageId }) { creator ->
                    var isFollowing by remember { mutableStateOf(creator.isFollowing) }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBg,
                        border = BorderStroke(0.8.dp, BorderColor),
                        modifier = Modifier
                            .width(125.dp)
                            .clickable { onCreatorClick(creator.pageId.toLong()) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2838)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(creator.avatar)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = creator.pageName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Text(
                                text = creator.pageName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "${creator.formattedFollowers} fans",
                                color = TextMuted,
                                fontSize = 10.sp
                            )

                            // 1-Click Follow Button
                            Button(
                                onClick = {
                                    val newState = !isFollowing
                                    isFollowing = newState
                                    coroutineScope.launch {
                                        repository.toggleFollowPage(creator.pageId.toLong(), creator.userId)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) Color(0xFF263248) else TikTokRed
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                            ) {
                                Text(
                                    text = if (isFollowing) "Following" else "+ Follow",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
