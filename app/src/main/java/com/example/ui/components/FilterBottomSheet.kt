package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CtgCategoryItem
import com.example.data.model.CtgMenusData
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    menusData: CtgMenusData,
    selectedCategory: CtgCategoryItem?,
    selectedYear: Int?,
    selectedGenre: String?,
    selectedSort: String,
    onCategoryChange: (CtgCategoryItem?) -> Unit,
    onYearChange: (Int?) -> Unit,
    onGenreChange: (String?) -> Unit,
    onSortChange: (String) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface,
        scrimColor = Color(0x99000000),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = TextMuted)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = BrandRed
                    )
                    Text(
                        text = "মুভি ফিল্টার (Filter & Sort)",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Categories
            Text(
                text = "ক্যাটাগরি (Category)",
                color = CyanAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChipItem(
                        label = "All Categories",
                        selected = selectedCategory == null,
                        onClick = { onCategoryChange(null) }
                    )
                }
                items(menusData.movieCategories) { cat ->
                    FilterChipItem(
                        label = cat.name,
                        selected = selectedCategory?.id == cat.id,
                        onClick = { onCategoryChange(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Years
            Text(
                text = "সাল / বছর (Release Year)",
                color = GoldRating,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChipItem(
                        label = "All Years",
                        selected = selectedYear == null,
                        onClick = { onYearChange(null) }
                    )
                }
                items(menusData.years.take(15)) { year ->
                    FilterChipItem(
                        label = year.toString(),
                        selected = selectedYear == year,
                        onClick = { onYearChange(year) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Genres
            Text(
                text = "জনরা (Genres)",
                color = BrandRedLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChipItem(
                        label = "All Genres",
                        selected = selectedGenre == null,
                        onClick = { onGenreChange(null) }
                    )
                }
                items(menusData.movieGenres) { genre ->
                    FilterChipItem(
                        label = genre,
                        selected = selectedGenre == genre,
                        onClick = { onGenreChange(genre) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Sort Order
            Text(
                text = "সর্ট করুন (Sort Order)",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipItem(
                    label = "নতুন রিলিজ (Latest)",
                    selected = selectedSort == "createdAt",
                    onClick = { onSortChange("createdAt") }
                )
                FilterChipItem(
                    label = "সর্বোচ্চ রেটিং (Rating)",
                    selected = selectedSort == "online_rating",
                    onClick = { onSortChange("online_rating") }
                )
                FilterChipItem(
                    label = "বছর অনুযায়ী (Year)",
                    selected = selectedSort == "year",
                    onClick = { onSortChange("year") }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onReset()
                        onApply()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("রিসেট (Reset)", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        onApply()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ফিল্টার প্রয়োগ করুন", fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
