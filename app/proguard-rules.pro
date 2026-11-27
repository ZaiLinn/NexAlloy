# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-optimizations
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepnames class * extends io.github.nexalloy.morphe.Fingerprint
-keepclassmembers class **.* {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keep public class * extends android.graphics.drawable.Drawable { public <init>(...); }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


#
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
# Runtime hooks and XML use these classes and member names. Placeholder methods
# must remain callable instead of being folded into their callers.
-keep class app.morphe.extension.** { *; }
-keep class * extends io.github.nexalloy.morphe.Fingerprint { *; }
-keep class io.github.nexalloy.morphe.**.FingerprintsKt { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,InnerClasses,EnclosingMethod
