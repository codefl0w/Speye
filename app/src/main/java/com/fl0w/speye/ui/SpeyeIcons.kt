package com.fl0w.speye.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object SpeyeIcons {
    val Logo: ImageVector
        get() = ImageVector.Builder(
            name = "SpeyeLogo",
            defaultWidth = 40.dp,
            defaultHeight = 24.dp,
            viewportWidth = 40f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd // Handles Shell -> Hole -> Pupil
        ) {
            // 1. THE OUTER SHELL (The Pointy "Eye" Boundary)
            // Mathematically precise for sharp points at 1,12 and 39,12
            moveTo(20f, 2f)
            curveTo(28f, 2f, 35f, 6f, 39f, 12f)      // Right Point
            curveTo(35f, 18f, 28f, 22f, 20f, 22f)    // Bottom
            curveTo(12f, 22f, 5f, 18f, 1f, 12f)      // Left Point
            curveTo(5f, 6f, 12f, 2f, 20f, 2f)        // Top
            close()

            // 2. THE INNER EYE HOLE (Material Visibility Cutout)
            moveTo(20.0f, 4.5f)
            curveTo(15.0f, 4.5f, 10.73f, 7.61f, 9.0f, 12.0f)
            curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
            reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
            curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
            close()

            // 3. THE PUPIL (Solid Core)
            moveTo(20.0f, 17.0f)
            curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
            reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
            reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
            reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
            close()
        }.build()

    val Crown: ImageVector
        get() = ImageVector.Builder(
            name = "Crown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color(0xFFFFD700))) {
            moveTo(5f, 16f)
            lineTo(3f, 5f)
            lineTo(8.5f, 10f)
            lineTo(12f, 4f)
            lineTo(15.5f, 10f)
            lineTo(21f, 5f)
            lineTo(19f, 16f)
            lineTo(5f, 16f)
            close()
            moveTo(5f, 18f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(5f)
            close()
        }.build()
}
