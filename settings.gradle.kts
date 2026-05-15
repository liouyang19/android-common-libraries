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
			if (requested.id.id.startsWith("com.taisau.")) {
				useModule("com.github.liouyang19.android-gradle-plugins:taisau-convention-plugins:${requested.version}")
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
			from("com.github.liouyang19.android-gradle-plugins:version-catalog:1.2.5")
		}
	}
	
}

rootProject.name = "android-common-libraries"
include(":theme")
include(":camera")
include(":bom")

//include(":navigation3")
include(":permission")
include(":upgrade")
include(":download")
include(":network")
include(":serialport")
include(":rabbitmq")

