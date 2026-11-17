# Conscrypt and OkHttp reference optional platform classes that do not exist on Android.
-dontwarn org.conscrypt.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
-keep class org.conscrypt.** { *; }
