package com.myapp.motrava.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myapp.motrava.presentation.navigation.Screen
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Solid-color bottom navigation bar with a smooth top cutout notch
 * that slides to the active tab, matching light and dark mode variants.
 */
@Composable
fun CurvedBottomNavBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.80f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_cutout_index"
    )

    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f
    val barColor = MaterialTheme.colorScheme.primary
    val cutoutBgColor = MaterialTheme.colorScheme.background
    val activeIconColor = if (isDarkTheme) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground

    // Balanced, sleek proportions with a true concentric gap around the active circle
    val topClearance = 16.dp
    val barHeight = 52.dp
    val totalBarHeight = topClearance + barHeight
    val activeBadgeSize = 38.dp       // radius R = 19.dp
    val ringGap = 5.dp                // even concentric gap
    val cradleRadius = 24.dp          // C = R + ringGap = 24.dp
    val badgeCenterOffsetFromTop = 4.dp
    val cutoutDepth = badgeCenterOffsetFromTop + cradleRadius // 28.dp

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalBarHeight)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { totalBarHeight.toPx() }
            val topY = with(density) { topClearance.toPx() }
            val cradleRadiusPx = with(density) { cradleRadius.toPx() }
            val cutoutDepthPx = with(density) { cutoutDepth.toPx() }
            val badgeCenterYPx = topY + with(density) { badgeCenterOffsetFromTop.toPx() }

            val itemCount = items.size.coerceAtLeast(1)
            val slotWidthPx = widthPx / itemCount
            val activeCenterXPx = (slotWidthPx * animatedIndex) + (slotWidthPx / 2f)

            val notchHalfWidthPx = cradleRadiusPx * 1.55f
            val notchStart = (activeCenterXPx - notchHalfWidthPx).coerceAtLeast(0f)
            val notchEnd = (activeCenterXPx + notchHalfWidthPx).coerceAtMost(widthPx)

            // 1. Mask behind the concentric notch + solid orange bar with smooth cradle cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Opaque circular mask prevents scrolling content behind the bar from bleeding into the notch ring
                drawCircle(
                    color = cutoutBgColor,
                    radius = cradleRadiusPx + 1.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(activeCenterXPx, badgeCenterYPx)
                )

                val path = Path().apply {
                    moveTo(0f, topY)
                    lineTo(notchStart, topY)

                    // Left fillet & circular cradle descent
                    cubicTo(
                        activeCenterXPx - cradleRadiusPx * 1.12f, topY,
                        activeCenterXPx - cradleRadiusPx * 1.05f, topY + cutoutDepthPx,
                        activeCenterXPx, topY + cutoutDepthPx
                    )

                    // Right circular cradle ascent & fillet
                    cubicTo(
                        activeCenterXPx + cradleRadiusPx * 1.05f, topY + cutoutDepthPx,
                        activeCenterXPx + cradleRadiusPx * 1.12f, topY,
                        notchEnd, topY
                    )

                    lineTo(widthPx, topY)
                    lineTo(widthPx, heightPx)
                    lineTo(0f, heightPx)
                    close()
                }
                drawPath(path = path, color = barColor, style = Fill)
            }

            // 2. Active icon circle nestled concentrically inside the cutout
            val activeItem = items.getOrNull(selectedIndex) ?: items.first()
            val badgeSizePx = with(density) { activeBadgeSize.toPx() }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (activeCenterXPx - badgeSizePx / 2f).roundToInt(),
                            y = (badgeCenterYPx - badgeSizePx / 2f).roundToInt()
                        )
                    }
                    .size(activeBadgeSize)
                    .clip(CircleShape)
                    .background(if (isDarkTheme) Color.White else cutoutBgColor),
                contentAlignment = Alignment.Center
            ) {
                if (activeItem.icon != null) {
                    Icon(
                        imageVector = activeItem.icon,
                        contentDescription = activeItem.title,
                        tint = activeIconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 3. Clickable tab slots + crisp white inactive icons
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topClearance)
            ) {
                items.forEachIndexed { index, screen ->
                    val distance = abs(index - animatedIndex)
                    val inactiveAlpha = ((distance - 0.15f) / 0.5f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onItemClick(screen)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (inactiveAlpha > 0.05f && screen.icon != null) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = Color.White.copy(alpha = inactiveAlpha),
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }
            }
        }

        // Extend solid bar color cleanly behind system gesture/navigation bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(barColor)
                .navigationBarsPadding()
        )
    }
}
