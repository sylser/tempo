# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes SourceFile, LineNumberTable
-keep public class * extends java.lang.Exception
-keep class retrofit2.** { *; }

-keep class **.reflect.TypeToken { *; }
-keep class * extends **.reflect.TypeToken
# 保留所有 View 和动画相关类
-keep class android.view.View { *; }
-keep class android.view.ViewPropertyAnimator { *; }

## 保留自定义 TextView、LyricsView 等方法
-keep class com.cappielloantonio.tempo.** { *; }

# 保留 Kotlin lambda / 扩展函数（重要）
-keepclassmembers class ** {
    <init>(...);
    void *(...);
}

# 保留 post / Runnable
-keepclassmembers class ** implements java.lang.Runnable {
    public void run();
}
