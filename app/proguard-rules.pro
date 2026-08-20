# AutoFlow R8 rules.
#
# Most of what AutoFlow needs is already covered by the consumer rules that
# ship with Room, WorkManager and Compose. These rules cover the places where
# the app itself is reached reflectively by the Android framework.

# Components the system instantiates by name from the manifest. R8 keeps
# manifest-referenced classes automatically, but their no-arg constructors and
# framework callbacks must survive too.
-keep class com.dhruw.autoflow.AutoFlowApplication { *; }
-keep class com.dhruw.autoflow.services.accessibility.AutoFlowAccessibilityService { *; }
-keep class com.dhruw.autoflow.services.notification.AutoFlowNotificationListenerService { *; }
-keep class com.dhruw.autoflow.services.accessibility.UiAutomationControlReceiver { *; }
-keep class com.dhruw.autoflow.services.system.BootReceiver { *; }
-keep class com.dhruw.autoflow.services.system.BluetoothEventReceiver { *; }

# WorkManager instantiates workers reflectively with (Context, WorkerParameters).
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room entities are read/written through generated code; keep their members so
# column mapping cannot be broken by renaming.
-keep class com.dhruw.autoflow.data.local.entity.** { *; }

# Workflow JSON uses type tags rather than reflection, so the model classes do
# not need keeping — but enum valueOf() calls in the decoder do.
-keepclassmembers enum com.dhruw.autoflow.automation.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Line numbers make a user-reported crash readable without exposing sources.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
