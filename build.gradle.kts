// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.kotlin.dokka) apply false
	
	alias(libs.plugins.taisau.dokka) apply true

}

//dokka {
//	moduleName.set("Nordic Common Libraries")
//	pluginsConfiguration.html {
//		homepageLink.set("https://github.com/NordicPlayground/Android-Common-Libraries")
//	}
//}