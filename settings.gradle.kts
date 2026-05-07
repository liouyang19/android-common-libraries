pluginManagement {
	repositories {
		// 优先使用本地缓存
		mavenLocal()
		
		// 国内镜像源
		maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
		maven { setUrl("https://maven.aliyun.com/repository/central") }
		maven { setUrl("https://maven.aliyun.com/repository/google") }
		
		
		// 其他必要仓库
		google {
			content {
				includeGroupByRegex("com\\.android.*")
				includeGroupByRegex("com\\.google.*")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
		maven { setUrl("https://www.jitpack.io") }
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		
		// 优先使用本地缓存
		mavenLocal()
		maven { setUrl("https://maven.aliyun.com/repository/public") }
		maven { setUrl("https://maven.aliyun.com/repository/central") }
		maven { setUrl("https://maven.aliyun.com/repository/google") }
		google()
		mavenCentral()
		gradlePluginPortal()
		maven { setUrl("https://www.jitpack.io") }
	}
	versionCatalogs {
		create("libs") {
			from("com.github.liouyang19:android-gradle-plugins:1.1.6")
		}
	}
	
}

rootProject.name = "android-common-libraries"
include(":app")
include(":theme")
include(":navigation3")
include(":permission")
include(":camera")
