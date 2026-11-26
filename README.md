# gen5w-taxi-meter

Gen5W용 개인 모의 택시 미터기와 읽기 전용 차량 데이터 진단 앱입니다. Android 4.4.2(API 19)와 HMG-API v1.0.2를 대상으로 합니다.

이 프로젝트는 **reikop 및 기여자의 Veloster N Dashboard** 택시 미터기 기능을 Gen5W용으로 포팅한 것입니다. 요금 계산, 영수증, 지역 데이터와 화면 렌더러를 가져와 적용했으며, 차량 속도 입력과 화면 배치는 Gen5W 환경에 맞게 변경했습니다. 원본 MIT 저작권과 허가문을 함께 제공합니다. [Veloster N Dashboard 원본](https://github.com/reikop/veloster-n-dashboard) · [제3자 고지](THIRD_PARTY_NOTICES.md)

| 파일 | 용도 |
| --- | --- |
| `gen5w-taxi-meter-diag-0.1.12.zip` | HMG-API 진단 Mod 설치 |
| `gen5w-taxi-meter-0.1.12.zip` | HMG-API 미터기 Mod 설치 |
| `gen5w-taxi-meter-diag.apk`, `gen5w-taxi-meter.apk` | 별도 제공 APK. 차량 데이터 허용은 해당 Mod가 설치 및 활성화되어야 부여됩니다. |

- [실차 검증 절차](docs/VEHICLE_TEST.md)
- [원본 분석·파일 대응·호환성](docs/UPSTREAM.md)
- [요금표 기준 및 차이](docs/TARIFFS.md)
- [라이선스와 출처](THIRD_PARTY_NOTICES.md)
- [릴리스 노트](CHANGELOG.md)

## Windows 및 WSL 빌드

Windows에서는 WSL2 Ubuntu를 열고 아래 명령을 실행합니다. Windows 파일 탐색기에서 결과는 WSL의 프로젝트 `out` 폴더에 있습니다. WSL에서 직접 빌드하는 경우도 동일합니다. JDK 17 이상, Android SDK Platform 19, Linux Build Tools 34.0.0, Git, Bash, zip, unzip이 필요합니다. WSL 내부에 Linux판 도구를 설치하세요.

```bash
sudo apt-get update
sudo apt-get install openjdk-17-jdk git zip unzip
sdkmanager 'platforms;android-19' 'build-tools;34.0.0'
git clone --depth 1 --branch v1.0.2 https://github.com/Open-HMG/HMG-API.git ../HMG-API-1.0.2
export ANDROID_JAR="$ANDROID_HOME/platforms/android-19/android.jar"
export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
export HMG_API_SOURCE="$(realpath ../HMG-API-1.0.2)"
export TAXI_SIGNING_DIR="$(realpath -m ../gen5w-taxi-meter-signing)"
read -rs -p 'APK signing password: ' TAXI_APK_PASSWORD
export TAXI_APK_PASSWORD
./build.sh
unset TAXI_APK_PASSWORD
```

`ANDROID_HOME`에는 Linux용 Android SDK 경로를 설정합니다. `sdkmanager`는 Android SDK Command-Line Tools에 포함됩니다. 첫 빌드는 APK PKCS#12 및 HMG-API 작성자 키를 지정한 개인 디렉터리에 생성합니다. 자체 배포의 업데이트에는 두 키를 같은 값으로 유지하세요. 제공된 시험 바이너리는 별도 시험 키로 서명했습니다. 자체 재빌드본으로 바꾸려면 기존 시험 Mod를 Mod Manager에서 삭제한 후 설치합니다.

미터기에는 새로 그린 8프레임 말 스프라이트가 들어 있습니다. 원본 렌더러가 정차와 주행 속도에 따라 재생합니다. 그림은 Pillow 환경에서 `python3 scripts/draw_horse.py`로 재생성할 수 있습니다.

진단은 전면 표시 중만 수집합니다. 미터기는 홈 화면에 있는 동안에도 앱이 살아 있으면 속도·GPS 수집과 요금 누적을 계속합니다. 홈에서 다시 열면 기존 미터기 화면을 사용합니다. 앱 프로세스가 종료되면 운행 상태는 초기화되고 지역과 KR/JP 선택만 유지됩니다.
