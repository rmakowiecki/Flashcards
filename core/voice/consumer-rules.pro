# ONNX Runtime's native library looks up its Java classes and their members by name over JNI
# (FindClass/GetMethodID on ai/onnxruntime/TensorInfo, OrtSession$Result, OrtException and others).
# The AAR's own consumer rules keep only its telemetry classes, so R8 renames or strips the rest and
# release builds abort with "JNI DETECTED ERROR IN APPLICATION: java_class == null" on the first
# OrtSession.run().
-keep class ai.onnxruntime.** { *; }
