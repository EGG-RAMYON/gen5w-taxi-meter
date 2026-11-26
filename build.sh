#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_JAR:?Set ANDROID_JAR to platforms/android-19/android.jar}"
: "${ANDROID_BUILD_TOOLS:?Set ANDROID_BUILD_TOOLS to the build-tools directory}"
: "${HMG_API_SOURCE:?Set HMG_API_SOURCE to the HMG-API v1.0.2 checkout}"
: "${TAXI_SIGNING_DIR:?Set TAXI_SIGNING_DIR to a persistent private key directory outside the project}"
: "${TAXI_APK_PASSWORD:?Set TAXI_APK_PASSWORD to your APK signing password}"
bt=$(cd "$ANDROID_BUILD_TOOLS" && pwd)
engine="$HMG_API_SOURCE/core/runtime/java/hmgapi/engine"
export TAXI_APK_PASSWORD
umask 077
mkdir -p "$TAXI_SIGNING_DIR"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/classes" "$work/host" "$work/test" out
if [[ -n ${ECJ_JAR:-} ]]; then
    compiler=(java -jar "$ECJ_JAR")
else
    compiler=(javac)
fi

"${compiler[@]}" -source 8 -target 8 -encoding UTF-8 -d "$work/host" "$engine"/*.java
signing=(java -cp "$work/host" hmgapi.engine.Signing)
apk_key="$TAXI_SIGNING_DIR/app.p12"
mod_key="$TAXI_SIGNING_DIR/author.key"
if [[ ! -f "$apk_key" ]]; then
    keytool -genkeypair -storetype PKCS12 -keystore "$apk_key" -storepass:env TAXI_APK_PASSWORD \
        -alias gen5w-taxi-meter -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=gen5w-taxi-meter'
fi
if [[ ! -f "$mod_key" ]]; then "${signing[@]}" keygen "$mod_key"; fi

mapfile -t sources < <(find app/src -name '*.java' | sort)
"${compiler[@]}" -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$work/classes" "${sources[@]}"
(cd "$work/classes" && zip -qr "$work/classes.jar" .)
"$bt/d8" --min-api 19 --lib "$ANDROID_JAR" --output "$work" "$work/classes.jar"

"${compiler[@]}" -source 8 -target 8 -encoding UTF-8 -d "$work/test" \
    app/src/com/veloster/vehicle/Taxi{Meter,Tariff,Zones,ZoneData}.java \
    app/src/dev/gen5w/taxi/SpeedIntervals.java tests/*.java
for test in TaxiMeterTest TaxiZonesTest dev.gen5w.taxi.SpeedIntervalsTest; do
    java -cp "$work/test" "$test"
done

for variant in diag meter; do
    id=gen5w-taxi-meter
    pkg=dev.gen5w.taxi.meter
    label='Gen5W 택시 미터기'
    "$bt/aapt2" compile --dir app/res -o "$work/res.zip"
    resources=(-R "$work/res.zip")
    if [[ "$variant" == diag ]]; then
        id+=-diag
        pkg=dev.gen5w.taxi.diag
        label='Gen5W 차량 데이터 진단'
    fi
    sed -e "s/@PACKAGE@/$pkg/" -e "s/@LABEL@/$label/" app/AndroidManifest.xml.in > "$work/AndroidManifest.xml"
    "$bt/aapt2" link -I "$ANDROID_JAR" --manifest "$work/AndroidManifest.xml" -o "$work/$id.apk" "${resources[@]}"
    (cd "$work" && zip -q "$id.apk" classes.dex)
    "$bt/zipalign" -f 4 "$work/$id.apk" "$work/aligned.apk"
    "$bt/apksigner" sign --ks "$apk_key" --ks-pass env:TAXI_APK_PASSWORD --min-sdk-version 19 \
        --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false \
        --out "out/$id.apk" "$work/aligned.apk"
    "$bt/apksigner" verify --min-sdk-version 19 -v "out/$id.apk"
    "$bt/aapt" dump badging "out/$id.apk" > "$work/badging"
    grep -q "package: name='$pkg'" "$work/badging"
    grep -q "application-icon-.*taxi_meter_icon" "$work/badging"
    grep -q "sdkVersion:'19'" "$work/badging"

    stage="$work/$id"
    mkdir -p "$stage/app"
    cp "out/$id.apk" "$stage/app/$id.apk"
    cp "mod/$variant.json" "$stage/mod.json"
    "${signing[@]}" sign "$stage" "$mod_key"
    "${signing[@]}" verify "$stage"
    archive="$PWD/out/$id-0.1.12.zip"
    (cd "$stage" && find . -type f | LC_ALL=C sort | zip -q -X -D "$work/$id.zip" -@)
    mv "$work/$id.zip" "$archive"
    unzip -tq "$archive"
done
(cd out && sha256sum ./*.apk ./*-0.1.12.zip > SHA256SUMS)
