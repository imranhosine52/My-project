package com.example.ui.screens.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ContentItemDto
import com.example.ui.screens.CategoryGridDramaCard

@Composable
fun BanglaDubCategoryScreen(
    dramas: List<ContentItemDto>,
    statusBarTop: Dp,
    onDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarTop + 94.dp,
            bottom = 72.dp,
            start = 12.dp,
            end = 12.dp
        )
    ) {
        items(dramas, key = { it.id }) { drama ->
            CategoryGridDramaCard(
                drama = drama,
                onClick = { onDramaClick(drama.slug) }
            )
        }
    }
}
