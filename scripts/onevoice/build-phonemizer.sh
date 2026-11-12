#!/usr/bin/env bash
# Rebuild the 64-bit Android SEA-G2P and JNI libraries packaged in the app.
set -euo pipefail

if [[ $# != 2 ]]; then
  echo "Usage: bash $0 ANDROID_NDK_DIR SEA_G2P_V0_10_0_SOURCE_DIR" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
ndk_dir="$(cd "$1" && pwd)"
source_dir="$(cd "$2" && pwd)"
dest="$script_dir/../../app/src/main/jniLibs"
case "$(uname -s)" in
  Darwin) host=darwin-x86_64 ;;
  Linux) host=linux-x86_64 ;;
  *) echo "Use macOS or Linux to rebuild the native libraries" >&2; exit 2 ;;
esac
tools="$ndk_dir/toolchains/llvm/prebuilt/$host/bin"
grep -q '^version = "0.10.0"$' "$source_dir/Cargo.toml"
cd "$source_dir"
for target in aarch64-linux-android x86_64-linux-android; do
  if [[ "$target" == aarch64-linux-android ]]; then
    abi=arm64-v8a
    linker_var=CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER
  else
    abi=x86_64
    linker_var=CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER
  fi
  cc="$tools/${target}24-clang"
  env "$linker_var=$cc" CARGO_PROFILE_RELEASE_STRIP=symbols \
    cargo build --locked --release --target "$target" --no-default-features --features capi
  output="$dest/$abi"
  mkdir -p "$output"
  cp "target/$target/release/libsea_g2p_rs.so" "$output/"
  "$cc" -shared -O3 -ffast-math -fPIC -I"$source_dir/include" "$script_dir/vieneu-jni.c" \
    -L"$output" -lsea_g2p_rs -Wl,-soname,libviko_vieneu.so -o "$output/libviko_vieneu.so"
  "$tools/llvm-strip" --strip-unneeded "$output/libviko_vieneu.so"
done
