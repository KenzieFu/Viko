# Bundled native libraries

## `libc++_shared.so` (arm64-v8a, NDK r27c)

`ai.djl.android:tokenizer-native` ships `libdjl_tokenizer.so`, which links against
the shared LLVM C++ runtime — and does **not** ship that runtime. Without this file
the tokenizer dies at first use with:

```
UnsatisfiedLinkError: dlopen failed: library "libc++_shared.so" not found:
  needed by .../lib/arm64-v8a/libdjl_tokenizer.so
```

which surfaces one frame later as a confusing `NoClassDefFoundError` on
`ai.djl.huggingface.tokenizers.jni.LibUtils`, because the failed static initialiser
poisons the class.

ONNX Runtime is unaffected — it links its C++ runtime statically.

Taken verbatim from the official NDK at
`toolchains/llvm/prebuilt/*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so`.
Vendored rather than pulled from a local NDK so the build works on a machine with no
NDK installed, which is the normal state of this project — there is no native code
here to build.

**arm64-v8a only.** Every phone this ships to is arm64. Add `armeabi-v7a` from the
same NDK path if a 32-bit device ever needs supporting.
