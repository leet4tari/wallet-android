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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# FFI layer
-keep class com.tari.android.wallet.ffi.** { *; }

# AIDL layer
-keep class com.tari.android.wallet.model.** { *; }

# This one is being processed via GSON inside SharedPrefsWrapper
-keep class com.tari.android.wallet.data.sharedPrefs.testnetFaucet.TestnetTariUTXOKey { *; }
-keep class com.tari.android.wallet.data.sharedPrefs.testnetFaucet.TestnetTariOutput { *; }
-keep class com.tari.android.wallet.data.sharedPrefs.testnetFaucet.TestnetTariMetadataSignature { *; }

-keep class net.danlew.android.joda.R$raw { *; }

-keep enum yat.android.data.YatRecordType { *; }