// Top-level build file. Settings shared by every module go here.
//
// "apply false" = make the Android plugin (version taken from gradle/libs.versions.toml) available
// to the modules, without applying it to this top-level project itself. The app module applies it.
plugins {
    alias(libs.plugins.android.application) apply false
}
