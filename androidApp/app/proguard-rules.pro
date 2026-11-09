# Room, Koin and Compose ship their own rules. Add app-specific ones here if a release build misbehaves.

# Glance creates widget tap handlers (ActionCallback) by class name, so R8 must keep them and their empty constructors.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
