package com.myapp.motrava.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.motrava.presentation.navigation.Screen
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Curved / Fluid Scooped Bottom Navigation Bar
 * Desain ceruk organik melengkung simetris yang rapi, mulus, dan presisi:
 * - Menghitung offset tepi proporsional (edgeOffset = 60.dp) agar Tab 0 (Dashboard) dan Tab 3 (Profile)
 *   memiliki ceruk yang sempurna tanpa menabrak atau terpotong sudut membulat bilah (margin 6 dp).
 * - Menggunakan kurva Bezier mulus C^1 kontinu dengan tangen horizontal di semua titik transisi.
 * - Floating circular bubble bertengger anggun di atas ceruk (cradle), memperlihatkan lengkungan
 *   bersih di bawah dan sekeliling tombol.
 * - Dilengkapi bayangan ambient berlapis (multi-tier drop shadow) yang halus pada Light dan Dark mode.
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

    // Smooth spring physics for sliding scoop and active bubble
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_active_index"
    )

    // Tactile bouncy scale pop when switching tabs
    val bubbleScale = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex) {
        bubbleScale.snapTo(0.72f)
        bubbleScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.52f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f
    // Frosted glass: semi-transparent surface so content behind the bar is visible
    val barColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.72f else 0.78f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.35f else 0.25f)
    val activeAccentColor = MaterialTheme.colorScheme.primary

    // Balanced Geometric Dimensions
    val totalHeight = 86.dp
    val topOffset = 22.dp
    val scoopRadius = 34.dp
    val scoopDepth = 28.dp
    val cornerRadius = 20.dp
    val bubbleSize = 46.dp
    val edgeOffset = 60.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .height(totalHeight)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { totalHeight.toPx() }
        val topOffsetPx = with(density) { topOffset.toPx() }
        val scoopRadiusPx = with(density) { scoopRadius.toPx() }
        val scoopDepthPx = with(density) { scoopDepth.toPx() }
        val cornerRadiusPx = with(density) { cornerRadius.toPx() }
        val edgeOffsetPx = with(density) { edgeOffset.toPx() }

        val itemCount = items.size.coerceAtLeast(1)
        val availableWidthPx = widthPx - (edgeOffsetPx * 2f)
        val stepPx = if (itemCount > 1) availableWidthPx / (itemCount - 1) else 0f
        val activeCenterXPx = edgeOffsetPx + (stepPx * animatedIndex)

        // Scoop boundaries
        val scoopStart = activeCenterXPx - scoopRadiusPx
        val scoopEnd = activeCenterXPx + scoopRadiusPx

        // 1. Draw Scooped Background Path with organic cubic Beziers & Multi-tier Shadow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(cornerRadiusPx, topOffsetPx)

                // Top edge line to scoop start
                lineTo(scoopStart, topOffsetPx)

                // S-curve into trough with horizontal tangents
                cubicTo(
                    activeCenterXPx - scoopRadiusPx * 0.65f, topOffsetPx,
                    activeCenterXPx - scoopRadiusPx * 0.35f, topOffsetPx + scoopDepthPx,
                    activeCenterXPx, topOffsetPx + scoopDepthPx
                )

                // S-curve up to flat top edge with horizontal tangents
                cubicTo(
                    activeCenterXPx + scoopRadiusPx * 0.35f, topOffsetPx + scoopDepthPx,
                    activeCenterXPx + scoopRadiusPx * 0.65f, topOffsetPx,
                    scoopEnd, topOffsetPx
                )

                // Top edge line to top-right corner
                lineTo(widthPx - cornerRadiusPx, topOffsetPx)

                // Top-right corner
                quadraticTo(widthPx, topOffsetPx, widthPx, topOffsetPx + cornerRadiusPx)

                // Right edge
                lineTo(widthPx, heightPx - cornerRadiusPx)

                // Bottom-right corner
                quadraticTo(widthPx, heightPx, widthPx - cornerRadiusPx, heightPx)

                // Bottom edge
                lineTo(cornerRadiusPx, heightPx)

                // Bottom-left corner
                quadraticTo(0f, heightPx, 0f, heightPx - cornerRadiusPx)

                // Left edge
                lineTo(0f, topOffsetPx + cornerRadiusPx)

                // Top-left corner
                quadraticTo(0f, topOffsetPx, cornerRadiusPx, topOffsetPx)

                close()
            }

            // Lighter shadow to preserve the see-through feel
            val shadowSteps = 3
            for (step in 1..shadowSteps) {
                val dy = (step * 1.2f).dp.toPx()
                val baseAlpha = if (isDarkTheme) 0.055f else 0.025f
                val alpha = baseAlpha * (shadowSteps - step + 1)
                val shadowPath = Path().apply {
                    addPath(path, Offset(0f, dy))
                }
                drawPath(path = shadowPath, color = Color.Black.copy(alpha = alpha))
            }

            // Fill body with surface color
            drawPath(path = path, color = barColor, style = Fill)

            // Outline border around the entire clean silhouette
            drawPath(path = path, color = borderColor, style = Stroke(width = 1.2.dp.toPx()))
        }

        // 2. Interactive Tab Items (Inactive tabs centered in bar)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topOffset)
        ) {
            items.forEachIndexed { index, screen ->
                val tabCenterXPx = edgeOffsetPx + (stepPx * index)
                val distance = abs(index - animatedIndex)
                val inactiveAlpha = (distance / 0.65f).coerceIn(0f, 1f)

                // Tab Clickable Area
                val clickWidthPx = if (itemCount > 1) {
                    if (index == 0) edgeOffsetPx + (stepPx / 2f)
                    else if (index == itemCount - 1) edgeOffsetPx + (stepPx / 2f)
                    else stepPx
                } else widthPx

                val clickStartXPx = if (index == 0) 0f
                else if (index == itemCount - 1) widthPx - clickWidthPx
                else tabCenterXPx - (stepPx / 2f)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(clickStartXPx.roundToInt(), 0)
                        }
                        .width(with(density) { clickWidthPx.toDp() })
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = inactiveAlpha * 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = screen.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = inactiveAlpha * 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 3. Elevated Floating Active Bubble (Circle Button) nestled in the scoop
        val activeItem = items.getOrNull(selectedIndex) ?: items.first()
        val bubbleRadiusPx = with(density) { (bubbleSize / 2).toPx() }
        val bubbleCenterYPx = topOffsetPx + with(density) { 2.dp.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (activeCenterXPx - bubbleRadiusPx).roundToInt(),
                        y = (bubbleCenterYPx - bubbleRadiusPx).roundToInt()
                    )
                }
                .size(bubbleSize)
                .scale(bubbleScale.value),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                // Bubble stays fully opaque for clarity & contrast
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Subtle matching circular border
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = borderColor,
                            radius = size.minDimension / 2f - 0.6.dp.toPx(),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    if (activeItem.icon != null) {
                        Icon(
                            imageVector = activeItem.icon,
                            contentDescription = activeItem.title,
                            tint = activeAccentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 4. Active Tab Label below the scoop
        val labelYOffsetPx = topOffsetPx + scoopDepthPx + with(density) { 5.dp.toPx() }
        val labelWidthPx = with(density) { 80.dp.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (activeCenterXPx - labelWidthPx / 2).roundToInt(),
                        y = labelYOffsetPx.roundToInt()
                    )
                }
                .width(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = activeItem.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else activeAccentColor,
                maxLines = 1
            )
        }
    }
}
