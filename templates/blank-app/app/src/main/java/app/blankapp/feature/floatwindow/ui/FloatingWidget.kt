package app.blankapp.feature.floatwindow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

/**
 * 悬浮窗及展开面板 Compose 视图
 */
@Composable
fun FloatingWidget(
    onDrag: (dx: Int, dy: Int) -> Unit,
    onDragEnd: () -> Unit,
    onActionScreenshot: () -> Unit,
    onActionClose: () -> Unit,
    onActionBackToApp: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var totalDragDistance by remember { mutableStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EasyInOutSineEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .wrapContentSize()
            .padding(8.dp), 
        contentAlignment = Alignment.Center
    ) {
        if (!isExpanded) {
            Box(
                modifier = Modifier
                    .scale(pulseScale) 
                    .size(56.dp)
                    .shadow(
                        elevation = 8.dp, 
                        shape = CircleShape, 
                        ambientColor = Color(0xFFE94057).copy(alpha = 0.5f),
                        spotColor = Color(0xFF8A2387).copy(alpha = 0.5f)
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8A2387), 
                                Color(0xFFE94057), 
                                Color(0xFFF27121)  
                            )
                        ),
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                totalDragDistance = 0f
                            },
                            onDragEnd = {
                                if (totalDragDistance < 15f) {
                                    isExpanded = true
                                } else {
                                    onDragEnd()
                                }
                            },
                            onDragCancel = {
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragDistance += sqrt(dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y)
                                onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .shadow(
                            elevation = 12.dp, 
                            shape = RoundedCornerShape(24.dp),
                            clip = false
                        )
                        .background(
                            color = Color(0xF21F1F2E), 
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "便捷助理",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowLeft,
                            contentDescription = "折叠",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    isExpanded = false
                                    onDragEnd()
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActionButton(
                            icon = Icons.Rounded.Crop,
                            label = "截图",
                            backgroundColor = Color(0xFF6C5CE7).copy(alpha = 0.2f),
                            tint = Color(0xFFA8A3FF),
                            onClick = {
                                isExpanded = false
                                onActionScreenshot()
                            }
                        )

                        ActionButton(
                            icon = Icons.Rounded.Home,
                            label = "主页",
                            backgroundColor = Color(0xFF00B894).copy(alpha = 0.2f),
                            tint = Color(0xFF55EFC4),
                            onClick = onActionBackToApp
                        )

                        ActionButton(
                            icon = Icons.Rounded.Close,
                            label = "退出",
                            backgroundColor = Color(0xFFD63031).copy(alpha = 0.2f),
                            tint = Color(0xFFFF7675),
                            onClick = onActionClose
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    tint: Color,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonPress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isPressed = true },
                    onDragEnd = { isPressed = false },
                    onDragCancel = { isPressed = false },
                    onDrag = { _, _ -> }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(backgroundColor, shape = CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

val EasyInOutSineEasing = Easing { fraction ->
    -(cos(Math.PI * fraction) - 1f).toFloat() / 2f
}
private fun cos(x: Double): Double = kotlin.math.cos(x)
