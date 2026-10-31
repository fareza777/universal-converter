package com.universalconverter.pro.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalconverter.pro.ui.components.UCLogo
import com.universalconverter.pro.ui.theme.UC

@Composable
fun SplashScreen() {
    val palette = UC.palette
    Box(Modifier.fillMaxSize().background(palette.paper)) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.42f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.ember.copy(alpha = 0.10f), androidx.compose.ui.graphics.Color.Transparent),
                    center = center,
                    radius = size.width * 0.72f,
                ),
                radius = size.width * 0.72f,
                center = center,
            )
            drawCircle(
                color = palette.ember.copy(alpha = 0.10f),
                radius = size.width * 0.36f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = palette.line.copy(alpha = 0.7f),
                radius = size.width * 0.52f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            UCLogo(size = 94.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                "Universal",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 44.sp),
                color = palette.ink,
            )
            Text(
                "CONVERTER",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.2.sp, fontSize = 12.sp),
                color = palette.muted,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .width(46.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.ember),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Native offline studio",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
            )
        }
        Text(
            "MIT engine · every conversion local",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.4.sp),
            color = palette.muted.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
        )
    }
}
