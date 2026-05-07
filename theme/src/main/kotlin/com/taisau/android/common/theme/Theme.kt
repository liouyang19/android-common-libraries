package com.taisau.android.common.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 主题类型枚举，定义了应用中可用的所有预设主题
 * 
 * 从 `doc/theme.html` 中提取的预定义主题变量：
 * - LIGHT: 浅色默认主题
 * - DARK: 深色默认主题
 * - OCEAN: 海洋主题（蓝绿色调）
 * - FOREST: 森林主题（绿色调）
 * - SUNSET: 日落主题（橙红色调）
 */
enum class ThemeType {
	/** 浅色默认主题 */
	LIGHT,
	
	/** 深色默认主题 */
	DARK,
	
	/** 海洋主题，使用蓝绿色调 */
	OCEAN,
	
	/** 森林主题，使用绿色调 */
	FOREST,
	
	/** 日落主题，使用橙红色调 */
	SUNSET,

	/** 自定义主题，颜色由外部传入 */
	CUSTOM,
}

/**
 * 自定义主题颜色模型
 *
 * 该结构和 `doc/theme.html` 的核心变量语义一致：
 * bg-body/bg-card/text-primary/text-secondary/border/accent/button-bg
 */
data class CustomThemeColors(
	val accent: Color,
	val background: Color,
	val surface: Color,
	val textPrimary: Color,
	val textSecondary: Color,
	val border: Color,
	val buttonBg: Color,
	val onAccent: Color = Color.White,
)

/**
 * 海洋主题配色方案
 * 
 * 使用浅色系作为基础，搭配蓝绿色调的主色和辅色
 */
val OceanColorScheme: ColorScheme = lightColorScheme(
	primary = OceanPrimary,
	onPrimary = OceanOnPrimary,
	background = OceanBackground,
	onBackground = OceanOnBackground,
	surface = OceanSurface,
	onSurface = OceanOnSurface,
	surfaceVariant = OceanSurfaceVariant,
	outline = OceanOutline,
	secondary = OceanSecondary,
	onSecondary = OceanOnSecondary
)

/**
 * 森林主题配色方案
 * 
 * 使用浅色系作为基础，搭配绿色调的主色和辅色
 */
val ForestColorScheme: ColorScheme = lightColorScheme(
	primary = ForestPrimary,
	onPrimary = ForestOnPrimary,
	background = ForestBackground,
	onBackground = ForestOnBackground,
	surface = ForestSurface,
	onSurface = ForestOnSurface,
	surfaceVariant = ForestSurfaceVariant,
	outline = ForestOutline,
	secondary = ForestSecondary,
	onSecondary = ForestOnSecondary
)

/**
 * 日落主题配色方案
 * 
 * 使用浅色系作为基础，搭配橙红色调的主色和辅色
 */
val SunsetColorScheme: ColorScheme = lightColorScheme(
	primary = SunsetPrimary,
	onPrimary = SunsetOnPrimary,
	background = SunsetBackground,
	onBackground = SunsetOnBackground,
	surface = SunsetSurface,
	onSurface = SunsetOnSurface,
	surfaceVariant = SunsetSurfaceVariant,
	outline = SunsetOutline,
	secondary = SunsetSecondary,
	onSecondary = SunsetOnSecondary
)

/**
 * 根据主题类型获取对应的配色方案
 *
 * @param type 主题类型枚举值
 * @return 对应的 ColorScheme 配色方案
 */
fun colorScheme(
	type: ThemeType,
	customThemeColors: CustomThemeColors? = null,
): ColorScheme = when (type) {
	ThemeType.LIGHT -> LightDefaultColorScheme
	ThemeType.DARK -> DarkDefaultColorScheme
	ThemeType.OCEAN -> OceanColorScheme
	ThemeType.FOREST -> ForestColorScheme
	ThemeType.SUNSET -> SunsetColorScheme
	ThemeType.CUSTOM -> customThemeColors?.toColorScheme() ?: LightDefaultColorScheme
}

fun CustomThemeColors.toColorScheme(): ColorScheme = lightColorScheme(
	primary = accent,
	onPrimary = onAccent,
	background = background,
	onBackground = textPrimary,
	surface = surface,
	onSurface = textPrimary,
	surfaceVariant = buttonBg,
	outline = border,
	secondary = textSecondary,
	onSecondary = onAccent
)

/**
 * 浅色默认主题配色方案
 * 
 * 使用紫色和橙色作为主色调，适用于明亮的界面环境
 */
@VisibleForTesting
val LightDefaultColorScheme = lightColorScheme(
	primary = Purple40,
	onPrimary = Color.White,
	primaryContainer = Purple90,
	onPrimaryContainer = Purple10,
	secondary = Orange40,
	onSecondary = Color.White,
	secondaryContainer = Orange90,
	onSecondaryContainer = Orange10,
	tertiary = Blue40,
	onTertiary = Color.White,
	tertiaryContainer = Blue90,
	onTertiaryContainer = Blue10,
	error = Red40,
	onError = Color.White,
	errorContainer = Red90,
	onErrorContainer = Red10,
	background = DarkPurpleGray99,
	onBackground = DarkPurpleGray10,
	surface = DarkPurpleGray99,
	onSurface = DarkPurpleGray10,
	surfaceVariant = PurpleGray90,
	onSurfaceVariant = PurpleGray30,
	inverseSurface = DarkPurpleGray20,
	inverseOnSurface = DarkPurpleGray95,
	outline = PurpleGray50,
)

/**
 * 深色默认主题配色方案
 * 
 * 使用深紫色和深橙色作为主色调，适用于暗色界面环境
 */
@VisibleForTesting
val DarkDefaultColorScheme = darkColorScheme(
	primary = Purple80,
	onPrimary = Purple20,
	primaryContainer = Purple30,
	onPrimaryContainer = Purple90,
	secondary = Orange80,
	onSecondary = Orange20,
	secondaryContainer = Orange30,
	onSecondaryContainer = Orange90,
	tertiary = Blue80,
	onTertiary = Blue20,
	tertiaryContainer = Blue30,
	onTertiaryContainer = Blue90,
	error = Red80,
	onError = Red20,
	errorContainer = Red30,
	onErrorContainer = Red90,
	background = DarkPurpleGray10,
	onBackground = DarkPurpleGray90,
	surface = DarkPurpleGray10,
	onSurface = DarkPurpleGray90,
	surfaceVariant = PurpleGray30,
	onSurfaceVariant = PurpleGray80,
	inverseSurface = DarkPurpleGray90,
	inverseOnSurface = DarkPurpleGray10,
	outline = PurpleGray60,
)

/**
 * 浅色 Android 主题配色方案
 * 
 * 使用绿色和深绿色作为主色调，符合 Material Design 风格
 */
@VisibleForTesting
val LightAndroidColorScheme = lightColorScheme(
	primary = Green40,
	onPrimary = Color.White,
	primaryContainer = Green90,
	onPrimaryContainer = Green10,
	secondary = DarkGreen40,
	onSecondary = Color.White,
	secondaryContainer = DarkGreen90,
	onSecondaryContainer = DarkGreen10,
	tertiary = Teal40,
	onTertiary = Color.White,
	tertiaryContainer = Teal90,
	onTertiaryContainer = Teal10,
	error = Red40,
	onError = Color.White,
	errorContainer = Red90,
	onErrorContainer = Red10,
	background = DarkGreenGray99,
	onBackground = DarkGreenGray10,
	surface = DarkGreenGray99,
	onSurface = DarkGreenGray10,
	surfaceVariant = GreenGray90,
	onSurfaceVariant = GreenGray30,
	inverseSurface = DarkGreenGray20,
	inverseOnSurface = DarkGreenGray95,
	outline = GreenGray50,
)

/**
 * 深色 Android 主题配色方案
 * 
 * 使用深绿色和深青色作为主色调，符合 Material Design 暗色风格
 */
@VisibleForTesting
val DarkAndroidColorScheme = darkColorScheme(
	primary = Green80,
	onPrimary = Green20,
	primaryContainer = Green30,
	onPrimaryContainer = Green90,
	secondary = DarkGreen80,
	onSecondary = DarkGreen20,
	secondaryContainer = DarkGreen30,
	onSecondaryContainer = DarkGreen90,
	tertiary = Teal80,
	onTertiary = Teal20,
	tertiaryContainer = Teal30,
	onTertiaryContainer = Teal90,
	error = Red80,
	onError = Red20,
	errorContainer = Red30,
	onErrorContainer = Red90,
	background = DarkGreenGray10,
	onBackground = DarkGreenGray90,
	surface = DarkGreenGray10,
	onSurface = DarkGreenGray90,
	surfaceVariant = GreenGray30,
	onSurfaceVariant = GreenGray80,
	inverseSurface = DarkGreenGray90,
	inverseOnSurface = DarkGreenGray10,
	outline = GreenGray60,
)

/**
 * 浅色 Android 渐变颜色配置
 */
val LightAndroidGradientColors = GradientColors(container = DarkGreenGray95)

/**
 * 深色 Android 渐变颜色配置
 */
val DarkAndroidGradientColors = GradientColors(container = Color.Black)

/**
 * 浅色 Android 背景主题配置
 */
val LightAndroidBackgroundTheme = BackgroundTheme(color = DarkGreenGray95)

/**
 * 深色 Android 背景主题配置
 */
val DarkAndroidBackgroundTheme = BackgroundTheme(color = Color.Black)

/**
 *  主题主函数，提供完整的主题配置
 *
 * @param darkTheme 是否使用深色主题，默认为系统深色模式设置
 * @param androidTheme 是否使用 Android Material Design 风格主题，默认为 false
 * @param disableDynamicTheming 是否禁用动态主题（Material You），默认为 true（禁用）
 * @param content 主题包裹的内容
 */
@Composable
fun AppTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	androidTheme: Boolean = false,
	disableDynamicTheming: Boolean = true,
	themeType: ThemeType? = null,
	customThemeColors: CustomThemeColors? = null,
	content: @Composable () -> Unit,
) {
	// 配色方案选择逻辑
	val colorScheme = when {
		// 显式指定主题优先（含自定义主题）
		themeType != null -> colorScheme(themeType, customThemeColors)
		// 优先使用 Android Material 风格主题
		androidTheme -> if (darkTheme) DarkAndroidColorScheme else LightAndroidColorScheme
		// 其次尝试使用动态主题（Android 12+）
		!disableDynamicTheming && supportsDynamicTheming() -> {
			val context = LocalContext.current
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}
		// 最后使用默认主题
		else -> if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme
	}
	
	// 渐变颜色配置
	val emptyGradientColors = GradientColors(container = colorScheme.surfaceColorAtElevation(2.dp))
	val defaultGradientColors = GradientColors(
		top = colorScheme.inverseOnSurface,
		bottom = colorScheme.primaryContainer,
		container = colorScheme.surface,
	)
	val gradientColors = when {
		androidTheme -> if (darkTheme) DarkAndroidGradientColors else LightAndroidGradientColors
		!disableDynamicTheming && supportsDynamicTheming() -> emptyGradientColors
		else -> defaultGradientColors
	}
	
	// 背景主题配置
	val defaultBackgroundTheme = BackgroundTheme(
		color = colorScheme.surface,
		tonalElevation = 2.dp,
	)
	val backgroundTheme = when {
		androidTheme -> if (darkTheme) DarkAndroidBackgroundTheme else LightAndroidBackgroundTheme
		else -> defaultBackgroundTheme
	}
	
	// 色调主题配置
	val tintTheme = when {
		androidTheme -> TintTheme()
		!disableDynamicTheming && supportsDynamicTheming() -> TintTheme(colorScheme.primary)
		else -> TintTheme()
	}
	//这段代码使用 `CompositionLocalProvider` 将渐变颜色、背景主题和色调主题注入到组件树中，然后包裹 `MaterialTheme` 应用配色方案和字体样式。它实现了主题的层级传递，让子组件可以访问这些主题配置。
	// 应用所有主题配置到 CompositionLocal
	CompositionLocalProvider(
		LocalGradientColors provides gradientColors,
		LocalBackgroundTheme provides backgroundTheme,
		LocalTintTheme provides tintTheme,
	) {
		MaterialTheme(
			colorScheme = colorScheme,
			typography = typography,
			content = content,
		)
	}
}

/**
 * 检查当前设备是否支持动态主题（Material You）
 * 
 * 动态主题需要 Android 12 (API 31) 或更高版本
 *
 * @return 如果支持动态主题返回 true，否则返回 false
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
