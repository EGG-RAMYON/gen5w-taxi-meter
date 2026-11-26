# 제3자 코드 및 리소스

- **Veloster N Dashboard**(reikop 및 기여자), **i30 PD Dashboard**(sung9155 및 기여자). 원본에서 가져온 택시 Java 코드, 표시 문자열과 호스트 테스트는 MIT 조건을 유지합니다. 두 저작권 표시 및 허가문은 [third_party/veloster-MIT.txt](third_party/veloster-MIT.txt)에 있습니다. [Veloster N Dashboard](https://github.com/reikop/veloster-n-dashboard) · [i30 PD Dashboard](https://github.com/sung9155/i30-pd-dashboard)
- **Open-HMG / HMG-API** v1.0.2. 공식 `core/client/java/hmgapi/client/HmgApi.java`를 변경 없이 APK에 포함했습니다. 패키지 서명과 검증에는 해당 태그의 공식 `Signing` 호스트 도구를 사용합니다. 전체 결합 소스 배포에는 AGPL-3.0을 적용하고 원본 MIT 조건을 병기합니다. [HMG-API v1.0.2](https://github.com/Open-HMG/HMG-API/tree/v1.0.2)
- **행정구역 자료**. 통계청 통계지리정보서비스(SGIS), 공공누리 제1유형. vuski/admdongkor `sgg_20261001` 가공 자료는 CC BY 4.0. 원본 대시보드가 시·군 단위로 병합하고 200m로 단순화한 `TaxiZoneData.java`를 변경 없이 재사용합니다. [SGIS](https://sgis.kostat.go.kr) · [admdongkor](https://github.com/vuski/admdongkor) · [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- **말 이미지**. `scripts/draw_horse.py`에서 새로 그린 그림을 미터기 APK에 포함합니다. 8프레임을 표시하는 렌더러는 원본 MIT 코드입니다.
- **글꼴·소리**. 원본 미터기에서 사용하는 Android 시스템 글꼴과 플랫폼 버튼 클릭음을 사용합니다. 대시보드의 선택형 글꼴, 3D 모델 및 음악 기능은 포함하지 않습니다.
- **BatteryWatchdog**(Nicholas Torkos 및 Gregory Taylor 기여, MIT)는 비교 분석에 사용했습니다. [BatteryWatchdog](https://github.com/zpoon/gen5w-BatteryWatchdog)
