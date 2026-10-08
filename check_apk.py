import sys
import os
import zipfile
import subprocess

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

def check_apk(apk_path):
    if not os.path.exists(apk_path):
        print(f"❌ File not found: {apk_path}")
        return False

    size_bytes = os.path.getsize(apk_path)
    size_mb = round(size_bytes / (1024 * 1024), 2)
    size_gb = round(size_bytes / (1024 * 1024 * 1024), 2)

    print("=" * 55)
    print("📱 PRE-FLIGHT APK HARDWARE COMPATIBILITY CHECK")
    print("Target Device: 32-bit ARM (armeabi-v7a) • Android 8.1 (SDK 27)")
    print("=" * 55)
    print(f"📁 File: {os.path.basename(apk_path)}")
    print(f"💾 Size: {size_mb} MB ({size_gb} GB)")

    aapt = r"C:\Users\admin\AppData\Local\Android\Sdk\build-tools\35.0.0\aapt.exe"
    pkg = "Unknown"
    min_sdk = "Unknown"
    native_code = None

    try:
        out = subprocess.check_output([aapt, "dump", "badging", apk_path], stderr=subprocess.STDOUT, text=True, errors='ignore')
        for line in out.splitlines():
            if line.startswith("package: name="):
                pkg = line
            elif line.startswith("sdkVersion:"):
                min_sdk = line
            elif line.startswith("native-code:"):
                native_code = line
    except Exception as e:
        print(f"⚠️ aapt warning: {e}")

    # Inspect native architectures inside zip
    archs = set()
    has_wrapper_apks = False
    try:
        with zipfile.ZipFile(apk_path, 'r') as z:
            for item in z.namelist():
                if item.startswith("lib/"):
                    parts = item.split('/')
                    if len(parts) > 1 and parts[1]:
                        archs.add(parts[1])
                elif "assets/apks/" in item:
                    has_wrapper_apks = True
    except Exception as e:
        print(f"⚠️ zip inspection error: {e}")

    print(f"📦 Package: {pkg}")
    print(f"⚙️ Target SDK: {min_sdk}")
    
    if native_code:
        print(f"🏗️ aapt native-code: {native_code}")
    if archs:
        print(f"📚 lib/ architectures: {list(archs)}")
    if has_wrapper_apks:
        print("⚠️ Detected split-APK wrapper bundle in assets/")

    is_compatible = False
    if not archs and not native_code:
        print("ℹ️ Architecture: Pure Java / Universal (No native C++ libs)")
        is_compatible = True
    elif "armeabi-v7a" in archs or "armeabi" in archs or (native_code and ("armeabi-v7a" in native_code or "armeabi" in native_code)):
        is_compatible = True
    else:
        is_compatible = False

    print("-" * 55)
    if not is_compatible:
        print("❌ INCOMPATIBLE CPU ARCHITECTURE!")
        print(f"   The APK only supports 64-bit ARM ({list(archs) if archs else native_code}).")
        print("   The tablet processor is strictly 32-bit (armeabi-v7a).")
        print("=" * 55)
        return False

    # Check disk space headroom
    avail_mb = 2300 # ~2.3 GB unmasked free space on tablet
    needed_mb = size_mb * 2.5
    if needed_mb > avail_mb:
        print("⚠️ INSUFFICIENT STORAGE FOR INSTALLATION!")
        print(f"   Requires ~{round(needed_mb/1024, 2)} GB free to extract and compile.")
        print(f"   Tablet only has ~{round(avail_mb/1024, 2)} GB real free space.")
        print("=" * 55)
        return False

    print("✅ COMPATIBLE! This APK matches the tablet's 32-bit CPU and storage.")
    print("=" * 55)
    return True

if __name__ == "__main__":
    if len(sys.argv) > 1:
        check_apk(sys.argv[1])
    else:
        print("Usage: python check_apk.py <path_to_apk>")
