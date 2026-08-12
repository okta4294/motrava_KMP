package com.myapp.motrava.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.motrava.presentation.theme.GradientPurple

@Composable
fun SocialLoginButtonsSection(
    onGoogleClick: () -> Unit,
    isLoading: Boolean,
    isRegister: Boolean = false
) {
    val actionText = if (isRegister) "Sign up with" else "Continue with"

    OutlinedButton(
        onClick = onGoogleClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GradientPurple)
        } else {
            Icon(
                imageVector = GoogleIcon,
                contentDescription = "Google",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 0.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$actionText Google",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EmailDivider(text: String = "or continue with email") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    }
}

val GoogleIcon: ImageVector
    get() {
        if (_googleIcon != null) return _googleIcon!!
        _googleIcon = ImageVector.Builder(
            name = "Google",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF4285F4))) {
                moveTo(22.56f, 12.25f)
                curveTo(22.56f, 11.47f, 22.49f, 10.72f, 22.36f, 10.0f)
                lineTo(12.0f, 10.0f)
                lineTo(12.0f, 14.26f)
                lineTo(17.92f, 14.26f)
                curveTo(17.66f, 15.63f, 16.88f, 16.79f, 15.71f, 17.57f)
                lineTo(15.71f, 20.34f)
                lineTo(19.28f, 20.34f)
                curveTo(21.36f, 18.42f, 22.56f, 15.6f, 22.56f, 12.25f)
                close()
            }
            path(fill = SolidColor(Color(0xFF34A853))) {
                moveTo(12.0f, 23.0f)
                curveTo(14.97f, 23.0f, 17.46f, 22.02f, 19.28f, 20.34f)
                lineTo(15.71f, 17.57f)
                curveTo(14.73f, 18.23f, 13.48f, 18.63f, 12.0f, 18.63f)
                curveTo(9.14f, 18.63f, 6.71f, 16.7f, 5.84f, 14.1f)
                lineTo(2.15f, 14.1f)
                lineTo(2.15f, 16.96f)
                curveTo(3.96f, 20.56f, 7.68f, 23.0f, 12.0f, 23.0f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFBBC05))) {
                moveTo(5.84f, 14.1f)
                curveTo(5.62f, 13.44f, 5.49f, 12.73f, 5.49f, 12.0f)
                curveTo(5.49f, 11.27f, 5.62f, 10.56f, 5.84f, 9.9f)
                lineTo(5.84f, 7.04f)
                lineTo(2.15f, 7.04f)
                curveTo(1.4f, 8.53f, 1.0f, 10.22f, 1.0f, 12.0f)
                curveTo(1.0f, 13.78f, 1.4f, 15.47f, 2.15f, 16.96f)
                lineTo(5.84f, 14.1f)
                close()
            }
            path(fill = SolidColor(Color(0xFFEA4335))) {
                moveTo(12.0f, 5.38f)
                curveTo(13.62f, 5.38f, 15.06f, 5.94f, 16.2f, 7.03f)
                lineTo(19.36f, 3.87f)
                curveTo(17.45f, 2.09f, 14.97f, 1.0f, 12.0f, 1.0f)
                curveTo(7.68f, 1.0f, 3.96f, 3.44f, 2.15f, 7.04f)
                lineTo(5.84f, 9.9f)
                curveTo(6.71f, 7.3f, 9.14f, 5.38f, 12.0f, 5.38f)
                close()
            }
        }.build()
        return _googleIcon!!
    }
private var _googleIcon: ImageVector? = null


