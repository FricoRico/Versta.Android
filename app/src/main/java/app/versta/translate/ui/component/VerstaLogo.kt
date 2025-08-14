package app.versta.translate.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import app.versta.translate.R
import app.versta.translate.ui.theme.spacing

@Composable
fun VerstaLogo(
    modifier: Modifier = Modifier,
    size: Dp = MaterialTheme.spacing.extraLarge * 1.2f,
) {
    Box(
        modifier = modifier
    ) {
        Box {
            Image(
                modifier = Modifier.height(size),
                imageVector = ImageVector.vectorResource(R.drawable.versta_logo_monochrome),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    MaterialTheme.colorScheme.primary,
                    BlendMode.SrcAtop
                )
            )
        }
    }
}