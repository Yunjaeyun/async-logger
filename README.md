# 대용량 트래픽 로깅 시스템의 병목 현상 분석 및 튜닝

## 1. 🚀 프로젝트 배경: "선착순 이벤트와 RDB의 INSERT 병목"

**"선착순 이벤트로 사용자가 몰릴 때, RDB에 과도한 `INSERT`가 발생하면 어떻게 될까요?"**

웹사이트 페이지 방문 시 (ip, url, 시간) 로그를 RDB에 `INSERT`하는 단순한 기능은, 트래픽이 몰리는 순간 다음과 같은 **"연쇄 붕괴"**의 첫 번째 도미노가 됩니다.

1.  **[문제 상황]** 선착순 이벤트 등으로 트래픽이 폭증하면, 초당 수천 건의 `INSERT` 쿼리가 DB로 전송됩니다.
2.  **[병목 발생]** RDB는 `INSERT` 시 B-Tree 인덱스 페이지에 락(Lock)을 겁니다. 이 락을 기다리는 "톰캣 스레드들"이 DB 커넥션을 반납하지 못한 채 **대기 상태**에 빠집니다.
3.  **[서버 마비]** DB 커넥션 풀(DBCP)이 고갈되고, `INSERT`와 무관한 **`SELECT` API(조회)**까지 마비되며 서버 전체가 "즉사"합니다.

본 프로젝트는 (nGrinder VUser 500명, 1분간) "RDB(MySQL)"를 사용하는 레거시 시스템이 바로 이 `INSERT` 병목 문제로 인해 "즉사(V1)"하는 문제를 V4까지 **"데이터"**에 기반하여 점진적으로 튜닝하고 **"해결"**한 과정을 담고 있습니다.
## 📊 최종 튜닝 결과 (V1 vs V4)

| 버전 | 아키텍처 |    TPS (처리량) | Error Rate | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **V1** | 동기 + `save()` | 24.1 | 80.5% | "서버 즉사" |
| **V2** | 비동기 + Queue | 100+ | 2.0% | "GC Hell" (큐 폭증) |
| **V3** | Queue + Batch | 1,000+ | 0.9% | "GC Hell" (가끔 멈춤) |
| **V4** | **V3 + GC 튜닝 (String)** | **5,000+** | **0.0%** | **"최종 해결"** |

<br>

## 🚗 튜닝 여정 (The Journey: V1 ➔ V4)

"Error 80.5%"에서 "Error 0.0%"에 도달하기까지, "새로운" 병목을 "데이터"로 발견하고 "가설"을 검증하며 해결한 "전체 과정"입니다.

### 1️⃣ V1: 동기 처리 (즉사) - 문제 정의

-   **nGrinder (VUser 500):** `[V1의 "즉사" ]`

  <img width="1592" height="61" alt="image" src="https://github.com/user-attachments/assets/a84ba1df-7dfa-4196-bd07-7102042042a6" />
  <img width="1595" height="51" alt="image" src="https://github.com/user-attachments/assets/85efdd5d-7881-499f-afc1-01703ff472ae" />
  <img width="740" height="1024" alt="image" src="https://github.com/user-attachments/assets/c1cefced-078a-4b8a-a9dc-bf1ce0536ce4" style="width:20%; max-width:600px; display:block;" alt="image" />



-   **분석:** TPS 24, **Error 80.5%**. "카운터(톰캣 스레드)"가 "느린" DB 작업(save)을 "동기"로 기다리면서, "커넥션 풀"과 "스레드 풀"이 "전부" 고갈되어 INSERT와 무관한 **SELECT API까지 마비**되는 "연쇄 붕괴" 발생.
<img width="2559" height="1439" alt="v1 병목증거(db스레드풀 고갈)스크린샷 2025-11-04 175637 (2)" src="https://github.com/user-attachments/assets/add55f5b-c084-41be-bbb0-519e44a7e044" style="width:20%; max-width:600px; display:block;" alt="image" />




<details>
<summary><b>🔍📖👇[V1 딥다이브] "왜" INSERT가 SELECT까지 마비시켰는가? (핫스팟 분석)</b></summary>
<br>
<blockquote>
V1의 "연쇄 붕괴"는 "DB 커넥션 풀" 고갈이 "현상"일 뿐, "근본 원인"은 RDB의 AUTO_INCREMENT PK가 유발하는 **"B-Tree 인덱스 핫스팟"**일 것이라 "가설"을 세웠습니다.

1.  **핫스팟 발생:** INSERT가 DB 인덱스(B-Tree)의 마지막 리프 노드 페이지("1번 화구")에 몰림.
2.  **락 점유:** "스레드 1"이 "1번 화구"(Lock)를 잡고 DB 작업을 시작.
3.  **커넥션 고갈:** 나머지 스레드은 락이 풀리길 기다리며 DB 커넥션을 쥔 채 대기.
4.  **커넥션 풀 소진:** "DB 커넥션 풀"은 0/10 상태로 고갈.

<img width="800" height="400" alt="image" src="https://github.com/user-attachments/assets/89aad612-7812-4c6c-ab37-1f84a6bc09e7" />

6.  **연쇄 붕괴:** INSERT와 무관한 **SELECT API**("샐러드 손님")가 도착했으나, 가용 커넥션이 없어 "즉시" 실패.

--


* db락으로 인해 나머지 톰캣스레드들이 모두 db커넥션을 반납하지못하고 대기중. 이때 새로운api요청시 가용한 톰캣스레드가(응답(카운터))을 받으러 갈수없다. - >
* **기존 톰캣스레드의 역할(카운터직원+주방요리사)을 분리해야 하지 않을까?** ->
    * 카운터직원(사용자에게 선착순이벤트 페이지를 보여주는 로직)
    * 주방요리사(db insert 처리 로직)


* **-> 큐라는 자료구조(수취카운터)를 이용해 분리한다.**
* 톰캣스레드는 카운터직원역할만 수행
* 더이상 톰캣스레드가 db커넥션을 반납하지못하고 대기하는 상황은 존재하지않음.
* insert 손님이 오면 , 큐에 주문서를 꽂고 사용자에게 응답처리한다.
* 워커스레드 1개가 큐 주문서 확인-db커넥션 획득-db insert 처리 - db 커넥션 반납 과정을 반복한다.
V1의 "동기" 방식이 이 "핫스팟" 문제를 "증폭"시킨다고 판단, **"결합 분리(Decoupling)"**를 위해 V2(비동기 큐)를 도입했습니다.

</blockquote>
</details>
---

### 2️⃣ V2: 비동기 (Queue) - 1차 해결 (그러나 "새로운" 병목)

-   **nGrinder (VUser 500):** `[V2의 "Error 2.0%"]`
   <img width="1592" height="61" alt="image" src="https://github.com/user-attachments/assets/a84ba1df-7dfa-4196-bd07-7102042042a6" />
   <img width="1597" height="45" alt="image" src="https://github.com/user-attachments/assets/5009233f-4e6e-4e9c-bb2f-5e9856848834" />

   <img width="726" height="1006" alt="image" src="https://github.com/user-attachments/assets/fd281682-daf7-40c0-9177-8052b1b4d09f" style="width:20%; max-width:600px; display:block;" alt="image"/>

-   **분석:** TPS 1,261, **Error 2.0%**. "비동기(Queue)"로 "서버 즉사(V1)"는 막았지만, "Error 2.0%" (Client-Side Timeout)가 발생.
-   **원인:** "요리사(소비자, 142 TPS)"가 "카운터(생산자, 1261 TPS)"보다 "압도적으로" 느려서, "큐(RAM)"가 7만 개씩 쌓이며 **"GC Hell (Stop-the-World)"** 발생.

<details>
<summary><b>🔍📖👇[V2 딥다이브] "GC Hell"이 "Error 2.0%"를 유발한 원리</b></summary>
<br>
<blockquote>
  
1.  **큐 폭증:** "요리사(142 TPS)"가 너무 느려 "큐(RAM)"에 `DTO` 객체 7만 개가 쌓임.
2.  **Stop-the-World:** "JVM"이 "RAM"이 터질까 봐 **"모두 멈춰!(Stop-the-World)"**를 외치고 "0.3초"간 "대청소(Full GC)" 시작.
3.  **Timeout 발생:** "GC"가 터진 "0.3초" 동안 "카운터(톰캣 스레드)"도 **"얼음"**이 됨.
4.  **결과:** "손님(nGrinder)"이 "0.1초"간 응답이 없자 "전화를 끊어버림(Timeout)". 이것이 `Error 2.0%`의 "진짜" 원인.


V2(비동기 큐)를 도입해 "서버 즉사(V1)"는 막았지만, "요리사(워커 스레드)"의 처리량(142 TPS)이 "카운터(생산자)"보다 현저히 느려 **"새로운 병목"**이 되었습니다.

이 병목의 원인을 분석했습니다.

**[V2의 한계]**
1.  워커 스레드 1개가 큐(Queue)에서 주문서(로그) **1개**를 꺼냅니다.
2.  DB 커넥션을 획득합니다.
3.  `INSERT` 쿼리 **1개**를 실행합니다.
4.  DB 커넥션을 반납합니다.
5.  다시 1번으로 돌아가 이 과정을 반복합니다.

**[문제점]**
이 방식은 1개의 로그를 처리할 때마다 **"DB 커넥션 획득 ➔ 쿼리 전송 ➔ 커넥션 반납"**이라는 비싼 네트워크 I/O 작업을 반복합니다. 이는 마치 햄버거 100개를 주문받아 1개씩 따로 배달하는 것과 같습니다.

**[V3 가설: Batch] "여러 개를 한 번에 처리할 수는 없을까?"**
"주문서(로그)가 `BATCH_SIZE`(예: 500개)만큼 쌓이면, 한 번의 DB 커넥션으로 500개를 한 번에 `INSERT` 처리하자!"

**[해결책 (V3)]**
**`JdbcTemplate`의 `batchUpdate`** 기술을 도입했습니다.

큐에서 `BATCH_SIZE`만큼 데이터를 `List`로 모아, 단 한 번의 DB I/O로 대량의 `INSERT`를 처리하도록 "요리사(워커 스레드)"의 로직을 개선했습니다.

   
</blockquote>
</details>

---

### 3️⃣ V3: Queue + Batch - "진짜 범인"을 찾아서

-   **nGrinder (VUser 500):** `[V3의 "Error 0.9%"]
    <img width="1592" height="61" alt="image" src="https://github.com/user-attachments/assets/a84ba1df-7dfa-4196-bd07-7102042042a6" />
    <img width="1598" height="59" alt="image" src="https://github.com/user-attachments/assets/1217dad2-1712-43b5-9c2b-bc74e2f98742" />

    <img width="737" height="1014" alt="image" src="https://github.com/user-attachments/assets/4f09bf23-2357-4010-8135-6ac969ed1790" style="width:20%; max-width:600px; display:block;" alt="image"/>

-   **분석:** TPS 1,573, **Error 0.9%**. V2(142 TPS) ➔ V3(4000+ TPS)로 "요리사"는 "압도적으로" 빨라졌음. **"그런데도"** Error 0.9%와 "250ms 멈춤 로그"가 "가끔" 발생.

<details>
<summary><b>🔍📖👇[V3 딥다이브] "진짜 범인" 찾기: DB 락(가설 A) vs GC Hell(가설 B)</b></summary>
<br>
<blockquote>
#### 가설 A (폐기): "DB 락"이 범인인가?
`54개=260ms`, `377개=255ms` "이상치" 로그 2개를 발견했습니다.
"요리 개수"와 상관없이 "0.25초"가 걸리는 것을 보고 V1에서 의심했던 "DB 락(핫스팟)"을 "1차" 용의자로 **"추정"**했습니다. 

#### 가설 B (확정): "GC Hell"이 범인이다.
**"반박 증거"**를 찾았습니다: `19개=5ms`, `80개=16ms` 등 "99%"의 "초고속" 로그를 발견.
**"가설 A 폐기":** "DB 락"이 범인이었다면 "16ms"는 "절대" 불가능했습니다. **"데이터"**로 "가설 A"를 **"폐기"**했습니다.
**"최종 결론":** "요리사(4000+)"가 "카운터(1573)"보다 "압도적으로" 빠른데도, "요리사"와 "카운터"가 "동시에" 0.25초씩 "가끔" 멈추는 "범인"은 **"GC Hell"**이 "유일"함을 **"확정"**했습니다.
</blockquote>
</details>

---

### 4️⃣ V4: GC 튜닝 (DTO ➔ String) - 최종 해결

-   **진단:** V3의 "GC Hell"은 "큐"가 쌓여서가 아니라, "카운터"와 "요리사"가 1초에 수천 개의 **"무거운 DTO(쓰레기)"**를 만들고 버려서 "GC(대청소)"가 "너무 자주" 터진 것이 "원인"임을 "확정"함.
-   **해결:** "JVM 옵션(DevOps)"이 아닌, "백엔드 개발자"로서 "코드"를 수정함. "카운터"와 "요리사" 사이의 "약속(큐)"을 `BlockingQueue<LogDataDto>` (무거운 컵) ➔ **`BlockingQueue<String>` (가벼운 냅킨)**으로 변경.
-   **증명:** `jstat` (JVM 감시 카메라)로 V4 테스트 시, "Stop-the-World"를 유발하는 **FGC (Full GC 횟수)가 0**을 유지함을 "증명"함.
-   **nGrinder (VUser 500):** `[V4의 "Error 0.0%" 최종 nGrinder 그래프 이미지]`
-   **결과:** **Error 0.0%**, **TPS 5,000+** 달성.

<br>

## 🚀 핵심 학습 및 결론 (Key Takeaways)

1.  **"데이터"는 "가설"보다 강하다:** "V3" 로그 분석 시 "DB 락(가설 A)"이라는 "성급한" 추측을 했으나, **16ms "초고속 로그(데이터)"**를 "반박 증거"로 찾아내 "가설 A"를 "폐기"하고 "GC Hell(가설 B)"이라는 "진짜" 원인을 찾았습니다.
2.  **"병목"은 "연쇄적"이다:** V1(핫스팟) ➔ V2(소비자 속도) ➔ V3(GC Hell)처럼, 하나의 병목을 해결하면 숨어있던 "다음" 병목이 드러납니다.
3.  **"GC Hell" 튜닝의 "핵심":** "GC(청소부)"를 튜닝하는 것보다, "GC"가 "일할 필요가 없도록" **"쓰레기(객체 생성)" 자체를 줄이는 "애플리케이션(코드)" 튜닝**이 더 "근본적인" 해결책임을 "증명"했습니다.

<br>

## 🧐 튜닝 여정 이후 (Trade-offs & Next Steps)

V4(GC 튜닝)로 Error 0.0%와 TPS 5,000+를 달성했지만, 100% "완벽한" 시스템은 없습니다. V4가 "여전히" 안고 있는 "Trade-off"와 "다음" 병목입니다.

1. Trade-off: "V1 ➔ V4로 바로 가면 안 됐나?" 실시간성을 고려.
무조권 V3부터 시작하면 되는거 아님?
"정답"은 없습니다. "V1 ➔ V4(String)"는 **"코드"**로 "GC"까지 튜닝한 "백엔드 개발자"의 "최적화"입니다.
"V1 ➔ V2(큐)"에서 멈추는 것은, "GC Hell(Error 2.0%)"을 방치한 "미완성"입니다.
"V1 ➔ V3(배치)"에서 멈추는 것은, "GC Hell(Error 0.9%)"의 "진짜" 원인을 "데이터"로 파고들지 못한 "타협"입니다.
결론: V4는 "현실적인(RDB)" 제약 하에서 "Error 0.0%"를 달성하기 위한 "논리적인" 최종 단계였습니다.

### (1) Trade-off: "기업 상황별 의사결정"

* **(비용 중시):** V4(String 튜닝)는 "서버 증설(돈)" 없이 "코드(무료)"로 "GC"까지 튜닝했으므로, "가장 비용 효율적인" 해결책입니다.
* **(응답 속도 중시):** V4는 "카운터(API)"와 "요리사(DB)"가 "같은 JVM"에 있어 "GC"가 "서로" 영향을 줍니다. "응답 속도(P99)"가 1ms라도 중요한 "광고/금융" 서비스라면, "Next Step (Kafka)"으로 가야 합니다.

### (2) Next Step 1: "로그 유실" (In-Memory Queue의 한계)

* **[V4의 치명적 한계]** V4가 사용한 `BlockingQueue`는 100% **"In-Memory(RAM)"**입니다.
* **[문제 상황]** 만약 "요리사(5000 TPS)"가 "카운터(1573 TPS)"보다 "잠깐" 느려져서 큐에 "3,000개"가 쌓인 "순간", "**V4 서버가 '다운'된다면?**"
* **[결과]** "RAM"에 있던 "로그 3,000개"는 **"영원히 유실(Data Loss)"**됩니다.
* **[다음 단계 (V5): 유실 방지]** "로그 유실이 절대 안 된다"는 "비즈니스 요구사항"이 있다면, "큐"를 "서버(RAM)" 안이 아닌, "서버 밖"의 "안전한(Disk-Based)" 전문 "물류 창고"로 "외주"를 줘야 합니다.
    * ➔ **Kafka / RabbitMQ / AWS SQS** 도입의 "필요성"이 "증명"됩니다.

### (3) Next Step 2: "새로운 병목" DELETE (DB 최적화)

* **[V6의 문제]** V4는 INSERT만 해결했습니다. "로그"는 "**삭제(DELETE)**"가 "진짜" 병목입니다. 로그가 "10억 건" 쌓였을 때, "오래된" 로그를 지우기 위해 `DELETE FROM log WHERE ...` 쿼리를 실행하면, "V1(즉사)" 때보다 "더 끔찍한" **"DB 락(Lock)"**이 발생하여 "서버 전체"가 "즉사"합니다.
* **[해결책 1: 인덱스 최소화]** "조회"가 없는 `ip_address` 등의 "불필요한" 인덱스를 "전부 제거"해서, INSERT 속도를 "추가로" 높이고 "DB 용량"을 아낍니다.
* **[해결책 2: 파티셔닝 (Partitioning)]** "DB 락" 없이 "0.1초" 만에 "삭제"하는 "유일한" 방법입니다.
    * **방법:** "로그 테이블"을 "날짜별"로 "미리 쪼개"둡니다. (e.g., `PARTITION BY RANGE (TO_DAYS(created_at))`)
    * **삭제:** "오래된" 파티션(e.g., `log_2025_10_01`)을 `ALTER TABLE ... DROP PARTITION` 명령어로 "삭제"합니다.
    * **결과:** "DB 락" 없이, "물리적"으로 "파일"을 "삭제"하므로 "0.1초" 만에 "1억 건" 삭제가 "완료"됩니다.

> **결론:** "Kafka(V5)"나 "파티셔닝(V6)"을 "구현"할 필요는 없습니다.
> V4가 "GC Hell"은 해결했지만, **"로그 유실"**과 **"DELETE 병목"**이라는 "새로운" 문제를 "남겼다"는 것을 **"인지"**하고, 그 "해결책"으로 "Kafka"와 "파티셔닝"을 "제시"하는 것,
>
.


