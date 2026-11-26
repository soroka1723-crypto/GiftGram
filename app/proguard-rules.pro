# GiftGram — правила ProGuard
-keepclassmembers class com.giftgram.messenger.MainActivity$Bridge {
    public *;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontwarn android.webkit.**
