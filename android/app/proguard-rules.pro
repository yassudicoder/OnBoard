# Project-specific ProGuard/R8 rules.
# They are only used when code shrinking is switched on (isMinifyEnabled = true in app/build.gradle.kts).
# It is off for now, so this file is intentionally (almost) empty.
#
# More details: https://developer.android.com/build/shrink-code

# Keep line numbers in crash stack traces readable:
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to hide the original source file name:
#-renamesourcefileattribute SourceFile
