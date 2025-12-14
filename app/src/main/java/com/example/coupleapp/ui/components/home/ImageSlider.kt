package com.example.coupleapp.ui.components.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SlideData(
    val title: String,
    val imageRes: Int,
    val gradient: Brush,
    val buttonText: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoImageSlider(
    modifier: Modifier = Modifier,
    slides: List<SlideData>,
    autoSlideDelay: Long = 4000L,
    onButtonClick: (Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    // Auto-slide disabled to improve performance
    // Users can still swipe manually
    // LaunchedEffect(pagerState.currentPage) {
    //     delay(autoSlideDelay)
    //     val nextPage = (pagerState.currentPage + 1) % slides.size
    //     coroutineScope.launch {
    //         pagerState.animateScrollToPage(nextPage,
    //             animationSpec = tween(600)
    //         )
    //     }
    // }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // HorizontalPager for swipeable slides
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            SlideContent(
                slide = slides[page],
                onButtonClick = { onButtonClick(page) }
            )
        }
        
        // Page indicators
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(slides.size) { index ->
                val isSelected = pagerState.currentPage == index
                // Simplified - no animation for better performance
                val width = if (isSelected) 24.dp else 6.dp
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(width)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isSelected) Color.White
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

@Composable
private fun SlideContent(
    slide: SlideData,
    onButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(slide.gradient)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Text and Button
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp
                ),
                color = Color(0xFF2D2D2D),
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Compact Button
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5E3C)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = slide.buttonText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
        
        // Right side: Image
        Image(
            painter = painterResource(id = slide.imageRes),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun rememberSlides(): List<SlideData> {
    return remember {
        listOf(
            SlideData(
                title = "Locket\nShare your moments",
                imageRes = R.drawable.funny_moment,
                gradient = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFFF4E0),
                        Color(0xFFFFE8C5)
                    )
                ),
                buttonText = "Join now"
            ),
            SlideData(
                title = "Sleep\nTrack and compare sleep",
                imageRes = R.drawable.sleep_tracker,
                gradient = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE8D6FF),
                        Color(0xFFD4C5F9)
                    )
                ),
                buttonText = "Track sleep"
            ),
            SlideData(
                title = "Schedule \n Manage time together",
                imageRes = R.drawable.schedular,
                gradient = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFFE8F0),
                        Color(0xFFFFC9DC)
                    )
                ),
                buttonText = "View calendar"
            )
        )
    }
}
