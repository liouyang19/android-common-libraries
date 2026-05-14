pluginManagement {
	repositories {
		mavenLocal()
		maven { setUrl("https://www.jitpack.io") }
		gradlePluginPortal()
		google {
			content {
				includeGroupByRegex("com\\.android.*")
				includeGroupByRegex("com\\.google.*")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		// 阿里云镜像（仅本地开发用，JitPack 环境不可达）
		maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
		maven { setUrl("https://maven.aliyun.com/repository/central") }
		maven { setUrl("https://maven.aliyun.com/repository/google") }
	}
	
	resolutionStrategy {
		eachPlugin {
			// 当 Gradle 尝试加载 id 为 com.taisau 开头的插件时
			if (requested.id.namespace?.startsWith("com.taisau") == true) {
				// 强制让它去 JitPack 的真实路径下载
				// 对应图片中的库：com.github.liouyang19:android-gradle-plugins
				useModule("com.github.liouyang19:android-gradle-plugins:${requested.version}")
			}
		}
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenLocal()
		maven { setUrl("https://www.jitpack.io") }
		google()
		mavenCentral()
		gradlePluginPortal()
		// 阿里云镜像（仅本地开发用，JitPack 环境不可达）
		maven { setUrl("https://maven.aliyun.com/repository/public") }
		maven { setUrl("https://maven.aliyun.com/repository/central") }
		maven { setUrl("https://maven.aliyun.com/repository/google") }
	}
	versionCatalogs {
		create("libs") {
			from("com.github.liouyang19.android-gradle-plugins:version-catalog:1.1.7")
		}
	}
	
}

rootProject.name = "android-common-libraries"
include(":theme")
include(":camera")
include(":bom")
//include(":app")
//include(":navigation3")
//include(":permission")
//include(":upgrade")
//include(":network")

