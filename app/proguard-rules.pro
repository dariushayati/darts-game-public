# BullTrack resolves its JNI entry points from the Java_<package>_<class>_<method>
# symbol names, so the native methods and their class must keep their names.
-keep class io.github.thomasff.bulltrack.core.vision.NativeDartDetector {
    native <methods>;
}
-keepclasseswithmembernames class * {
    native <methods>;
}
