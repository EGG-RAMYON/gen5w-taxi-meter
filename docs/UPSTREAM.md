# 원본 소스와 HMG-API 호환성

## 조사 기준

| 저장소 | 검토한 기본 브랜치 커밋 | GitHub 릴리스 및 포팅 판단 |
| --- | --- | --- |
| [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard) | `0f2807c15c9b17b3b355abccf7d26fa1338d12f7` | v1.12.0. JSN용 설치기와 원본 앱 소스. 미터기 Java 코드는 MIT |
| [Open-HMG/HMG-API](https://github.com/Open-HMG/HMG-API) | `daa083717f07565ba16dc8a4d26291b7d68d3bf4` | v1.1.0-rc.10 및 안정 v1.0.2. 실제 빌드의 계약은 v1.0.2 `180185de2c57afb2cca4858d0be7828e8f1051d6`. AGPL-3.0 |
| [sung9155/i30-pd-dashboard](https://github.com/sung9155/i30-pd-dashboard) | `043cde0d584ea7568844448fd10e26076c86e0f3` | v1.17.11. HSCAN 해석, 국내 요금표 출처, 예약과 호출 표시 비교. MIT |
| [zpoon/gen5w-BatteryWatchdog](https://github.com/zpoon/gen5w-BatteryWatchdog) | `248aa5d924886f829445ec44a2cdc9ba2bf4d382` | v0.7.2. 실제 `battery.watchdog.zip`은 `NEMESIS.data`와 설치 스크립트 기반 NEMESIS 배포. MIT |

HMG-API v1.0.2의 [Mod reference](https://github.com/Open-HMG/HMG-API/blob/v1.0.2/docs/mod-reference.md), 예제 `mods/examples`, `Signing.java`, `UpdatesActivity.java`, 실제 `nemesis-shortcut-1.1.0.zip`을 대조했습니다. 배포 ZIP 루트에 `mod.json`과 `META-HMGAPI/manifest.json`, `META-HMGAPI/signature`를 두고, `apps[].apk`가 설치할 APK 경로를 지정합니다. 설치·실행은 Manager 설치 후 시스템 앱 목록의 진입점으로 연결됩니다. v1.1.0 계열에서도 키 이름과 클라이언트 형태를 확인했으며 실행 호환성은 미검증입니다.

## 데이터

| 신호 | v1.0.2 구현 | 적용 |
| --- | --- | --- |
| 속도 | HSCAN `getCarSpeed((byte) 0)`의 float km/h. 캐시 250ms. 응답 없으면 `-` | 비동기로 약 250ms마다 요청. 0~300 km/h 유효 범위는 앱의 임시 필터. 수치의 해상도, 정차 값, 실제 원본 갱신 주기는 실차에서 확인 |
| 주행계 | HEV `ODOMETER` 정수의 1/10을 km로 반환. 60초 캐시 | 100m 해상도이며 진단 비교에 사용. 미터기는 단조 증가 시계와 속도로 거리 적분 |
| 위치 | v1.0.2 공개 `vehicle.read`의 GPS 좌표 없음 | Android `GPS_PROVIDER`의 오프라인 위치. 최근 5초, 정확도 100m 이하를 요금 지역 판단에 사용 |
| 시간 | 주기 측정과 거리·요금 누적은 `elapsedRealtime`, 할증은 헤드유닛 시계의 로컬 시간 | 연속 유효 샘플 간격이 1초를 넘거나 속도가 무효면 그 구간의 요금과 거리를 누적하지 않음 |

차량 속도 수신은 설치 환경에 맞는 HMG-API 차량 맵에 의존합니다. 진단 Mod에서 정차 시 유효한 0 km/h가 들어오는지 확인할 수 있습니다.

## 원본 파일 대응

| 분류 | 파일 | 작업 |
| --- | --- | --- |
| 수정 | `TaxiRenderer.java` | 원본 화면과 영수증을 바탕으로 오른쪽 버튼 열과 반응형 배치 적용 |
| 유지 | `TaxiMeter.java`, `TaxiTariff.java`, `TaxiZones.java`, `TaxiZoneData.java` | 원본 요금·경계 판정 재사용 |
| 유지 | `UiText.java`, `TaxiMeterTest.java`, `TaxiZonesTest.java` | 원본 번역과 호스트 시험 유지 |
| 수정 | `scripts/taxi_zones.py` | 생성 데이터의 출력 경로를 새 소스 트리로 변경 |
| 대체 | `VehicleDash.java`의 택시 구성 및 샘플 공급 | `MainActivity.java`, `SpeedIntervals.java`. 독립 캔버스, 화면 비율, HMG-API, 위치 수동 선택, 진단 |
| 추가 | 공식 `HmgApi.java`, Android manifest, 두 Mod 명세, 빌드·시험 | Android 19 및 별도 배포용 진입점 |
| 제외 | `VehicleDataClient.java`, `HscanDataClient.java`, `HscanFrameDecoder.java`, 기타 대시보드 기능 | 벨로스터용 전용 신호·메뉴·다른 대시보드 페이지 제외 |
| 추가 | `app/res/drawable-nodpi/taxi_horse.png`, `scripts/draw_horse.py` | 새 말 그림 8프레임을 기본 빌드에 포함 |

원본 Veloster N은 후륜 속도 평균을 우선하고 클러스터 속도로 보완합니다. 포팅은 HMG-API 속도 하나를 사용합니다. 원본 한국·일본식 화면을 레이아웃별 논리 좌표로 표시하고 비율을 유지합니다. 예약·호출은 i30 포크에 표시등만 있고 동작 가능한 배차 연동 코드는 확인되지 않았습니다. 포팅에는 버튼으로 전환하는 앱 내부 모의 표시를 추가했습니다.
