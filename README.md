# 📈 StockSimulator: High-Concurrency Backend System

이 프로젝트는 대규모 트래픽 환경에서도 데이터의 정합성을 유지하며, 실시간으로 주식 주문을 초고속 매칭하고 체결을 처리하는 초고성능 가상 주식 거래 시스템 백엔드입니다.

## 🏗️ 시스템 아키텍처 및 기술 스택

### 🛠 Backend Tech Stack
- **Framework**: Spring Boot 3.2.x, Java 17
- **Security**: Spring Security & **JWT** (Stateless Authentication)
- **Database**: MySQL (JPA/Hibernate)
- **Matching Engine**: **Redis Lua Script** (Price-Time Priority In-Memory Order Matching)
- **Async Processing**: Spring Async (`@Async`) & **ThreadPoolTaskExecutor with CallerRunsPolicy Backpressure**
- **Concurrency Control**: **Redis Lua Atomic Execution** (No Optimistic/Pessimistic Locking in Matching Path)
- **Real-time Messaging**: Spring WebSocket (STOMP) & **Redis Pub/Sub** (Broadcasting)
- **Build Tool**: Gradle

### 📡 System Flow (High-Performance Engine)
```mermaid
sequenceDiagram
    autonumber
    actor User as 클라이언트
    participant Controller as TradeController
    participant MatchService as MatchTradeService
    participant Redis as Redis (Lua Engine)
    participant Processor as AsyncTradeProcessor (ThreadPool)
    participant DB as MySQL (RDBMS)

    User->>Controller: 주문 요청 (BUY/SELL)
    Controller->>MatchService: placeMatchOrder() 호출 (트랜잭션 해제)
    MatchService->>DB: [Short Tx] 신규 주문 생성 (saveAndFlush)
    MatchService->>Redis: Redis Lua 스크립트 실행 (인메모리 매칭 연산)
    Redis-->>MatchService: 매칭 체결 결과 반환
    MatchService->>DB: [Short Tx] 발주자 주문 상태/미체결 수량 업데이트 (save)
    MatchService->>Processor: processMatchedTrades(matchedTrades) 비동기 위임
    MatchService-->>User: 200 OK ("주문 접수 완료") 즉각 응답
    
    note over Redis, DB: Redis가 실시간 체결 원장, MySQL은 확정 결과를 비동기 영속화
    note over Processor, DB: 백그라운드 스레드에서 RDB 영속성 및 알림 처리
    Processor->>DB: [Background Tx] 원자 UPDATE/UPSERT로 잔고, 보유주식, 상대 주문 상태 반영
    Processor->>User: WebSocket 실시간 전광판 송출 & 개인 체결 알림 발송
```

### ✅ 현재 주문 체결 흐름 요약
1. **주문 접수**: `TradeController`가 요청을 받고 `MatchTradeService.placeMatchOrder()`로 넘깁니다.
2. **주문 ID 선발급**: MySQL에 `StockOrder`를 짧게 INSERT하여 주문 ID를 확보합니다. 이 단계는 체결 락을 잡지 않습니다.
3. **Redis Lua 원자 실행**: `match_engine.lua`가 Redis 안에서 잔고/보유주식 검증, 선차감, 호가 매칭, 체결 자산 이전, 미체결 주문 큐 등록까지 한 번에 처리합니다.
4. **즉시 응답**: Lua 결과로 발주 주문의 잔량/상태만 갱신하고 HTTP 요청은 빠르게 종료합니다.
5. **비동기 DB 반영**: `AsyncTradeProcessor`가 Lua에서 확정된 체결 목록을 받아 MySQL에 원자 `UPDATE`/`UPSERT`로 반영합니다.
6. **실시간 알림**: DB 트랜잭션이 끝난 뒤 Redis 현재가, WebSocket 전광판, 개인 알림을 전송합니다.

이 구조에서 실시간 매매 정합성의 기준은 Redis Lua입니다. MySQL은 체결 결과를 영속화하는 저장소 역할을 하며, 매칭 경로에서 낙관적 락이나 비관적 락을 사용하지 않습니다.

---

## ⚙️ 핵심 기술 구현 (Core Backend Logic)

### 1. Redis Lua 스크립트 기반 고성능 인메모리 매칭 & 취소 엔진 (Atomic Trade Engine)

호가창(Order Book) 조회, 자산 검증, 대기 주문 큐 관리, 매칭 체결, 그리고 주문 취소까지의 모든 비즈니스 로직을 원자적(Atomicity)으로 보장받기 위해 **Redis Lua 스크립트**를 탑재하여 설계했습니다. Single-threaded로 동작하는 Redis 내에서 Lua 스크립트는 원자적으로 단일 실행되므로 **동시성 경쟁 상태(Race Condition)를 원천적으로 무력화**합니다.

#### 🗂️ Redis 데이터 구조 설계 (Keys & Data Structures)
*   **호가 정보 (Price Book)**: `ZSET` (Sorted Set) 구조를 사용하여 최유리 호가를 O(log N)으로 신속하게 탐색합니다.
    *   `orderbook:buy:<stockCode>`: 매수 가격대 목록 (Score = 가격, Value = 가격)
    *   `orderbook:sell:<stockCode>`: 매도 가격대 목록 (Score = 가격, Value = 가격)
*   **대기 주문 큐 (Order Queue)**: `LIST` 구조를 활용하여 동일 가격대 내에서 시간 순(First-In-First-Out) 우선순위를 O(1)으로 엄격히 지켜냅니다.
    *   `orders:buy:<stockCode>:<price>`: 해당 가격대 매수 대기 큐 (Format: `"orderId:memberId:qty"`)
    *   `orders:sell:<stockCode>:<price>`: 해당 가격대 매도 대기 큐 (Format: `"orderId:memberId:qty"`)
*   **가상 자산 원장 (Asset Ledger)**: `String` 구조로 관리되며, 체결 연산 직전 정합성 검증 및 즉각 차감 처리를 수행합니다.
    *   `member:<memberId>:balance`: 원화 가용 잔고
    *   `member:<memberId>:stock:<stockCode>`: 개별 주식 보유량

---

#### 🔄 주문 체결 라이프사이클 (`match_engine.lua`)

```mermaid
flowchart TD
    Start([주문 요청 유입]) --> Validate{주문 Side 구분}
    
    %% BUY 분기
    Validate -- BUY --> BuyCheck{지정가 LIMIT 여부}
    BuyCheck -- LIMIT --> BuyAmtCheck{가용 잔고 >= price * qty}
    BuyAmtCheck -- 부족 --> ErrInsBal[err: INSUFFICIENT_BALANCE 반환]
    BuyAmtCheck -- 충분 --> DeductCash[가용 잔고 즉시 선차감 DECRBY] --> BuyLoop
    BuyCheck -- MARKET --> BuyLoop[매수 체결 루프]
    
    BuyLoop --> SellBookCheck{매도 호가창 ZSET 조회}
    SellBookCheck -- 비어있음/지정가초과 --> RemLimitCheckBuy{LIMIT 주문 & 잔량 존재?}
    RemLimitCheckBuy -- Yes --> RegBuyQueue[매수 호가 큐 등록 RPUSH & ZSET 등록 ZADD] --> End[체결 결과 & 잔량 반환]
    RemLimitCheckBuy -- No --> End
    
    SellBookCheck -- 최선 매도 호가 발견 --> MatchBuy[최선 호가 LIST에서 상대 주문 LPOP]
    MatchBuy --> MatchCalcBuy[최소 체결 수량 tradeQty 결정]
    MatchCalcBuy --> RelocateBuy[Redis 자산 즉시 동기화: 
 상대 Seller 원화 가상 잔고 증가 INCRBY 
 본인 Buyer 주식 보유량 증가 INCRBY]
    RelocateBuy --> RefundCheck{지정가 환불 refund 발생?}
    RefundCheck -- Yes --> Refund[Buyer 가용 잔고 환불 INCRBY] --> LoopUpdateBuy
    RefundCheck -- No --> LoopUpdateBuy[잔량 갱신 & 루프 재진입] --> BuyLoop

    %% SELL 분기
    Validate -- SELL --> SellStockCheck{보유 주식 수량 >= qty}
    SellStockCheck -- 부족 --> ErrInsStk[err: INSUFFICIENT_STOCK 반환]
    SellStockCheck -- 충분 --> DeductStock[보유 주식 즉시 선차감 DECRBY] --> SellLoop[매도 체결 루프]
    
    SellLoop --> BuyBookCheck{매수 호가창 ZSET 조회}
    BuyBookCheck -- 비어있음/지정가미달 --> RemLimitCheckSell{LIMIT 주문 & 잔량 존재?}
    RemLimitCheckSell -- Yes --> RegSellQueue[매도 호가 큐 등록 RPUSH & ZSET 등록 ZADD] --> End
    RemLimitCheckSell -- No --> End
    
    BuyBookCheck -- 최선 매수 호가 발견 --> MatchSell[최선 호가 LIST에서 상대 주문 LPOP]
    MatchSell --> MatchCalcSell[최소 체결 수량 tradeQty 결정]
    MatchCalcSell --> RelocateSell[Redis 자산 즉시 동기화: 
 본인 Seller 원화 가상 잔고 증가 INCRBY 
 상대 Buyer 주식 보유량 증가 INCRBY]
    RelocateSell --> LoopUpdateSell[잔량 갱신 & 루프 재진입] --> SellLoop
```

1.  **자산 선검증 & 선차감 (Asset Validation & Pre-deduction)**:
    *   **매수(BUY 지정가)**: `price * qty` 만큼의 원화가 부족하면 즉시 `INSUFFICIENT_BALANCE` 에러를 리턴하고 실행을 마칩니다. 충분할 경우, 원화 잔고를 즉시 선차감(`DECRBY`)하여 중복 출금이나 초과 구매를 물리적으로 방지합니다.
    *   **매도(SELL)**: 보유 주식 수량이 부족하면 `INSUFFICIENT_STOCK` 에러를 리턴하고, 충분할 경우 주식 수량을 즉시 선차감합니다.
2.  **시간-가격 우선순위 매칭 루프 (Priority Matching Loop)**:
    *   **매수(BUY)**: 매도 호가창(`ZSET`)에서 가장 가격이 낮은 매도 호가를 찾습니다. 지정가 매수인 경우 상대 호가가 본인 주문가보다 크면 루프를 탈출합니다. 해당 호가 큐(`LIST`)의 머리(`LPOP`)에서 가장 먼저 등록된 매도 대기 주문을 뽑아와 체결을 실행합니다.
    *   **매도(SELL)**: 매수 호가창(`ZSET`)에서 역방향 정렬(`ZREVRANGE`)로 가장 가격이 높은 매수 호가를 가져와 대기 매수 주문을 LPOP하여 O(1)으로 순차 체결합니다.
3.  **가상 자산 실시간 이전 (Real-Time Asset Sync)**:
    *   체결 성사 즉시 본인의 차감된 자산을 상대방의 자산(원화/주식)으로 이체(`INCRBY`)합니다. 지정가 매수 시 본인의 주문 가격보다 저렴하게 상대방 호가와 체결된 경우, 발생한 차액(Refund)을 본인의 원화 잔고로 즉각 환불 조치합니다.
4.  **대기 호가창 등록 (Designated Limit Queueing)**:
    *   지정가 주문(`LIMIT`)에 한하여 매칭 루프를 다 돌고도 남은 미체결 잔량(`remainingQty > 0`)이 있다면, 해당 가격대의 큐(`RPUSH`) 및 호가창 ZSET(`ZADD`)에 신규 등록하여 가격-시간 우선순위를 이어받습니다. (시장가 주문 `MARKET`은 미체결 잔량을 즉시 소멸시킵니다)

---

#### ❌ 안전한 원자적 주문 취소 (`cancel_order.lua`)
주문 취소 시에도 다른 주문과의 체결 경합이나 더블 환불(Double Refund) 레이스 컨디션을 방지하도록 주문 취소 로직 전체를 Lua 스크립트로 분리했습니다.

1.  **호가 큐 실시간 탐색 (Queue Iteration)**:
    *   `LRANGE` 명령어로 지정 가격대 대기 큐(`LIST`) 전체를 탐색하여 해당 `orderId` 접두사가 포함된 원소(예: `orderId:memberId:qty`)를 탐색합니다 (O(N) 탐색).
2.  **원자적 큐 제거 및 호가창 청소 (Atomic Dequeue & Purge)**:
    *   대기 중인 주문이 존재한다면 `LREM`을 통해 정확히 해당 주문 데이터를 O(1)로 제거합니다.
    *   이때 주문이 제거된 직후 해당 가격대 대기 큐의 길이(`LLEN`)가 `0`이 될 경우, 매수/매도 호가창 ZSET(`ZREM`)에서 해당 가격대를 즉시 완전히 파기하여 빈 가격 정보 조회를 사전에 차단합니다.
3.  **안전 자산 환불 (Risk-Free Refund)**:
    *   제거된 주문 원소에 기록된 대기 수량(`actualQty`)을 분석하여, **매수 주문 취소 시에는 `price * actualQty` 만큼의 원화를 본인의 balance 원장으로, 매도 주문 취소 시에는 `actualQty` 만큼의 보유 주식을 stock 원장으로 완벽히 복구**시킵니다.
    *   만약 대기 주문 큐에 해당 주문 ID가 없는 경우(이미 루프 도중 실시간 체결이 끝난 상태), 이미 자산이 이체 완료되었으므로 환불 수량 `0`을 반환하여 어떠한 상황에서도 **더블 환불이 발생하지 않는 원자적 정합성**을 달성했습니다.

---

### 2. Redis Lua 확정 체결 + 비동기 Write-Behind 패턴
- **Redis가 실시간 체결 원장**: 주문 가능 여부, 대기 주문 등록, 체결 수량 계산, 자산 이전은 모두 `match_engine.lua`에서 원자적으로 완료됩니다. Redis Lua 스크립트는 실행 중 다른 Redis 명령과 interleave되지 않으므로, 동시 주문에서도 초과 매수/초과 매도와 더블 환불을 차단합니다.
- **MySQL은 확정 결과 영속화**: `AsyncTradeProcessor`는 Lua가 반환한 체결 결과만 받아 MySQL에 반영합니다. 이때 회원/보유주식 엔티티를 조회해서 락을 잡고 수정하지 않고, `UPDATE balance = balance + ?`, `INSERT ... ON DUPLICATE KEY UPDATE`, 주문 잔량 원자 `UPDATE`로 짧게 반영합니다.
- **트랜잭션 전면 분리**: API 요청 스레드가 RDB 커넥션을 오랫동안 점유하여 발생하는 **HikariCP 커넥션 고갈을 방지**하기 위해, 핵심 매칭 로직은 Redis Lua에 두고 DB 반영은 비동기 write-behind로 분리했습니다.

### 3. Graceful Backpressure (자가 조율 스레드 풀 설계)
- **스레드 풀 사양 확장**: `corePoolSize(50)`, `maxPoolSize(100)`, `queueCapacity(100,000)`로 폭발적인 주문 요청 하에서도 대기 버퍼를 충분히 확보하도록 설계했습니다.
- **CallerRunsPolicy 탑재**: 만에 하나 10만 개의 메모리 큐가 포화 상태에 이르면, API 요청 스레드(Tomcat 스레드)가 직접 백그라운드 DB 반영 연산을 돕게 하여 **단 1건의 요청 누수 및 거부 없이 시스템 자가 조율(Self-throttling)을 통해 안정적으로 처리**하도록 보장합니다.

---

## 🚀 성능 및 부하 테스트 (Load Testing)

`Python aiohttp` 비동기 부하 테스트 스크립트를 통해 극도의 고동시성 상황에서의 성능 지표를 검증했습니다. **10명의 동시 유저가 총 10,000건의 거래 요청**을 무작위로 보내는 극한의 스트레스 상황에서, 서버 설정의 최적화 전후 성능을 다각도로 비교 분석한 결과입니다.

### 📊 10,000건 대량 부하 테스트 비교 결과 (Bulk Test)

| 성능 지표 (Performance Metric) | Run 1: 동시성 1,000 | Run 2: 동시성 10,000 (기본 WAS) | Run 3: 동시성 10,000 (설정 최적화) | Run 4: 동시성 10,000 (설정 다이어트) | Run 5: 동시성 10,000 (최종 아키텍처 - 외부 I/O 격리 & 풀 확장) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **총 트랜잭션 (Total Transactions)** | 10,000 | 10,000 | 10,000 | 10,000 | **10,000** |
| **가상 사용자 수 (Simulated Users)** | 10명 | 10명 | 10명 | 10명 | **60명 (60 Threads)** |
| **동시성 수준 (Concurrency Level)** | **1,000** | **10,000** | **10,000** | **10,000** | **10,000** |
| ⏱️ **총 소요 시간 (Total Time)** | **6.22초** | **10.75초** | **25.97초** | **56.20초** | **21.46초** (2.6배 단축! ⚡) |
| ⚡ **초당 처리량 (TPS)** | **1,606.61 TPS** | **930.52 TPS** | **385.02 TPS** | **177.94 TPS** | **465.99 TPS** (최고 성능 달성! 🚀) |
| ✅ **성공 건수 (Success Count)** | **10,000 (100.0%)** | **8,493 (84.9%)** | **10,000 (100.0%)** | **10,000 (100.0%)** | **10,000 (100.0%)** |
| ❌ **실패 건수 (Failure Count)** | **0 (0.0%)** | **1,507 (15.1%)** | **0 (0.0%)** | **0 (0.0%)** | **0 (0.0%)** |
| 🕒 **평균 대기 시간 (Avg Latency)** | **597.02ms** | **5,790.68ms** | **14,308.22ms** | **28,321.85ms** | **128.75ms** (극적인 레이턴시 개선) |
| **서버 설정 (Server Configuration)** | Tomcat 500, DB Pool 80 | Tomcat 500, DB Pool 80 | Tomcat 1000, DB Pool 150 (Tuned) | Tomcat 150, DB Pool 15 (Optimized), Lettuce Pool 150, TransactionTemplate | **Tomcat 150, DB Pool 60, Lettuce Pool 150, TransactionTemplate (외부 I/O 분리)** |

---

### 🔍 성능 최적화 돌파구 분석 (Performance Insights)

*   **Run 3: 설정 최적화를 통한 유실률 0% 달성**
    *   WAS 소켓 대기열 및 HikariCP 커넥션 풀을 대량으로 확장하여 동시 10,000건의 유입 소켓 누수와 연결 거절 문제를 방어하고 클라이언트 성공률 100%를 처음으로 확보했습니다.
    *   하지만 백그라운드 DB 동기화 처리 루프 전체가 단일 트랜잭션으로 묶여 있어, 많은 체결 건이 한 번에 커밋되며 RDB 쓰기 지연과 롤백 예외가 발생할 수 있는 구조적 위험을 내포하고 있었습니다.

*   **Run 4: 설정 다이어트 & 트랜잭션 격리**
    *   **자원 경합 오버헤드 해소**: 물리 스펙(4 CPU Cores, 1 HDD)에 정렬되도록 Tomcat 스레드를 150개, HikariCP를 15개로 다이어트시켜 컨텍스트 스위칭 비용과 HDD의 임의 쓰기 병목을 제어했습니다.
    *   **TransactionTemplate 개별 트랜잭션 분할**: 기존 메소드 수준의 트랜잭션을 걷어내고 체결 건 단위로 트랜잭션을 완전 분리하여 RDB 점유 범위를 최소화했습니다.
    *   **자금 보존 법칙 완벽 검증**: 테스트 완료 후 회원들의 예수금 합계가 단 1원의 누락 없이 **정확히 10조 원으로 온전히 보존**됨으로써, 실제 상용 주식 시뮬레이터 시스템이 갖추어야 할 최고 수준의 무결성을 증명해 냈습니다.

*   **Run 5: 최종 아키텍처 - 외부 I/O 격리 및 리소스 최적 정렬로 성능 극대화! 🚀**
    *   **커넥션 점유 오버헤드 해제 (Connection Pinning 해결)**: 기존 Run 4에서는 트랜잭션을 격리했으나, 트랜잭션 범위 내부의 웹소켓/알림 전송 등 느린 네트워크 I/O 작업으로 인해 커넥션이 묶이는 병목이 존재했습니다. 이를 트랜잭션 외부로 추방해 DB 점유 시간(Hold Time)을 극도로 감소시켰습니다.
    *   **Redis Lua + 원자 SQL Write-Behind**: 실시간 체결과 자산 이전은 Redis Lua에서 끝내고, MySQL에는 확정 결과만 원자 `UPDATE`/`UPSERT`로 반영하여 매칭 경로에서 DB 락 의존성을 제거했습니다.
    *   **스레드-커넥션 정렬을 통한 고사(Starvation) 해소**: 비동기 처리를 담당하는 `taskExecutor`의 코어 스레드 수(50)를 편안하게 수용할 수 있도록 HikariCP의 `maximum-pool-size`를 60개로 증설하여 커넥션 대기 시간 경쟁을 완전 제거했습니다.
    *   **2.6배 속도 혁신 달성**: 동일 부하 조건에서 전체 체결 완주 시간을 **56.20초에서 21.46초로 단축**하고, **초당 465.99 TPS**에 육박하는 폭발적인 데이터 수용 속도 및 100% 무결성을 입증했습니다.


---

## 🛠️ 최근 개선 사항 (Refactoring & Clean Code)

### 1. SOLID 원칙 적용
- **SRP (Single Responsibility Principle)**: 거대한 단일 서비스 클래스에서 벗어나, 매칭 엔진(`MatchTradeService`), 알림 전송(`NotificationService`), 실시간 시세 관리(`StockService`), HTTP 요청/응답 처리(`TradeController`) 로직을 각각의 전용 클래스로 책임을 분리하여 코드의 응집도와 가독성을 극대화했습니다.
- **DIP (Dependency Inversion Principle)**: 구체적인 구현이 아닌 인터페이스에 의존하도록 설계하여 확장성과 테스트의 유연성을 확보했습니다.

### 2. 보안 및 확장성
- **JWT Authentication**: OAuth2 확장성을 고려한 JWT 기반 인증 시스템 구축.
- **Global Exception Handling**: `@RestControllerAdvice`를 통한 체계적인 에러 응답 구조 설계.

---

## 👨‍🏫 Backend Interview Points
1. **왜 DB 락이 아니라 Redis Lua인가요?**
   - 주문 매칭은 같은 종목/가격대에 요청이 몰리기 때문에 DB row 락이나 낙관적 재시도 방식은 대기와 재시도 비용이 커집니다. Redis Lua는 스크립트 실행 중 다른 명령과 섞이지 않으므로, 잔고 검증부터 체결 자산 이전까지 하나의 원자 연산으로 처리할 수 있습니다.
2. **MySQL 정합성은 어떻게 맞추나요?**
   - 실시간 체결의 기준은 Redis Lua 결과입니다. MySQL은 `AsyncTradeProcessor`가 확정 체결 목록을 받아 원자 `UPDATE`/`UPSERT`로 뒤따라 반영하는 write-behind 저장소 역할을 합니다.
3. **Redis Pub/Sub을 사용한 이유는?**
   - 단일 서버의 WebSocket만으로는 서버 대수가 늘어날 때 알림 전파가 불가능합니다. Redis를 메시지 브로커로 사용하여 Multi-node 환경에서도 모든 유저에게 실시간 알림이 도달하도록 설계했습니다.
## 🛠️ 트러블슈팅 (Troubleshooting)

### 1. [Issue] 대량 주문 시 HikariCP 커넥션 풀 고갈 및 RDB 경합 현상
- **원인 분석**: 
  - 기존에는 주문 접수 API 실행 중 DB 자산 조회/수정, 실시간 웹소켓 알림, Redis I/O가 단일 동기식 트랜잭션 안에서 수행되어 찰나의 순간에 모든 커넥션이 블로킹되었습니다.
  - 특히 단 2명의 회원 ID(`[1, 2]`)만으로 10,000건의 교차 주문을 유도하면 회원 자산 row에 쓰기가 집중되어 RDBMS가 매칭 엔진의 병목이 되었습니다.
- **해결 과정**:
  - **Redis Lua 체결 엔진 전환**: 잔고/보유주식 검증, 선차감, 호가 매칭, 자산 이전을 `match_engine.lua` 안에서 원자적으로 처리하도록 옮겼습니다.
  - **비동기 DB 영속화**: API 요청 경로에서는 주문 ID와 발주 주문 상태만 짧게 저장하고, 확정 체결 결과는 `AsyncTradeProcessor`가 원자 SQL로 백그라운드 반영합니다.
- **교훈**: 실시간 매칭의 일관성은 Redis Lua처럼 짧고 단일한 원자 실행 단위에 맡기고, RDBMS는 영속성 저장소로 분리하는 편이 고동시성 환경에서 더 단순하고 빠릅니다.

### 2. [Issue] 부하 테스트 시 TaskRejectedException 발생
- **원인 분석**: 10,000건의 부하가 유입될 때 비동기 스레드 풀의 기본 큐(1,000)를 초과하여 작업이 반려되며 HTTP 500 에러가 발생함.
- **해결 과정**: 스레드 풀의 버퍼 큐를 100,000개로 대폭 늘렸으며, **`ThreadPoolExecutor.CallerRunsPolicy`** 백프레셔를 도입하여 큐 포화 시 Tomcat 스레드가 직접 체결 DB 기록 작업을 도와주어 처리 속도 조율 및 에러 0% 달성을 이루어냈습니다.

### 3. [Issue] 극단적인 동시 10,000건 요청 시 소켓 끊김 및 일부 요청 누락 현상 (Tomcat Backlog Overflow)
- **원인 분석**:
  - `ulimit -n`과 OS 파일 디스크립터 한계는 충분히 늘려두었으나, WAS(Embedded Tomcat)의 기본 소켓 대기 큐(`accept-count`)와 스레드 풀 수용 한계를 넘어서는 10,000건의 동시 폭발적인 커넥션 유입으로 인해 OS TCP 핸드셰이크 단계에서 `Connection refused` 혹은 `Timeout`으로 일부 클라이언트 커넥션이 강제 종료되었습니다 (기존 15.1% 유실 발생).
- **해결 과정**:
  - **Tomcat 네트워크 커널 및 스레드 튜닝**: `application.yaml` 설정을 변경하여 `server.tomcat.accept-count`를 `3000`, `server.tomcat.threads.max`를 `1000`으로 튜닝하고, 최대 소켓 연결 수를 `15000`으로 확장해 유입 소켓 유실을 원천 방어했습니다.
  - **HikariCP 최적 크기 설계**: 늘어난 Tomcat 스레드가 DB 커넥션을 획득하려고 경합하는 상황을 해결하고자 `maximum-pool-size: 150` 및 `connection-timeout: 30000ms`로 조율하여 10,000건 전부 정상 도달 및 체결을 보장하게 되었습니다.

### 4. [Issue] 비동기 체결 반영 중 RDBMS 경합 및 데이터 정합성 누수 현상
- **원인 분석**:
  - **1대다(1:N) 체결 특성과 트랜잭션 범위 불일치**: 주식 매칭 엔진 특성상 한 번의 주문으로 여러 상대방의 대기 주문과 매칭되어 `List<String> matchedTrades` 형태로 다수의 체결 건이 비동기 프로세서(`AsyncTradeProcessor`)에 한 번에 인입됩니다.
  - **선언적 트랜잭션(@Transactional)의 함정**: 원래 비동기 체결 처리 메소드 레벨에 `@Transactional` 어노테이션이 달려 있었습니다. 이로 인해 개념적으로는 독립적인 여러 개의 1대1 체결 건들이 **물리적인 단 하나의 데이터베이스 트랜잭션**으로 묶여서 처리되었습니다.
  - **엔티티 조회-수정 방식의 쓰기 경합**: 루프를 돌며 회원과 보유주식 엔티티를 읽고 수정하는 방식은 같은 row에 쓰기가 몰릴 때 트랜잭션 대기와 롤백 가능성을 키웠습니다.
- **해결 과정**:
  - **트랜잭션 미세 격리 (`TransactionTemplate` 도입)**: 기존 메소드 단위의 넓은 `@Transactional` 선언을 제거하고, 루프 내부에서 개별 체결 건 단위로만 물리 트랜잭션을 적용했습니다.
  - **원자 SQL 반영**: 회원 잔고는 `UPDATE balance = balance + :amount`, 매수 포지션은 `INSERT ... ON DUPLICATE KEY UPDATE`, 매도 수량과 주문 잔량은 단일 `UPDATE`로 반영해 DB 처리 시간을 줄였습니다.

### 5. [Issue] 극단적인 동시 10,000건 인입 시 Redis Lettuce 커넥션 고갈 및 소켓 예외 (`Execution failed`)
- **원인 분석**:
  - 10,000건의 동시 폭발적인 HTTP 요청이 발생할 때, 스프링 부트의 기본 Redis 드라이버인 Lettuce가 단일 공유 소켓 커넥션으로 작동하여 다중 스레드의 요청 처리가 몰리며 소켓 채널이 포화 상태에 이르렀습니다.
  - 이로 인해 Netty 입출력 파이프라인에서 커넥션 획득 지연 및 소켓 해제 상태가 초래되어 `Redis Lua Script Execution failed` 및 비즈니스 매칭 오류가 속출했습니다.
- **해결 과정**:
  - **Lettuce 커넥션 풀링 구축**: Gradle에 `commons-pool2` 의존성을 즉시 보강하고, `application.yaml`에 `lettuce.pool.max-active: 150`, `max-idle: 50` 설정을 부여하여 스레드별 병렬 Redis 소켓 할당을 확립했습니다.
  - **소켓 커넥션 타임아웃 상향**: Redis 데이터 송수신 타임아웃 한계를 10초(`10000ms`)로 튜닝하여, 1초 만에 쏟아지는 폭발적인 연산 요청들이 유실 없이 큐에서 꺼내어 처리될 수 있도록 안전 마진을 마련했습니다.

### 6. [Issue] HikariCP 15개 최적화 적용 후 급격한 성능 저하 및 커넥션 고사 현상
- **원인 분석**:
  1. **스레드 풀과 커넥션 풀 크기 불일치 (Connection Starvation)**: 비동기 체결 처리 스레드 풀([AppConfig.java](file:///Users/jouijae/Desktop/GitHub/StockSimulator/stockSimulator_BackEnd/src/main/java/com/stock/stockSimulator/config/AppConfig.java#L35-L46))은 동시에 50~100개 스레드가 대량으로 돌도록 동작하나, DB 커넥션 풀은 물리 스펙 기반의 '이론적 최적값'인 15개로 지나치게 제한되어 다중 스레드가 커넥션을 획득하지 못하고 줄줄이 밀리며 지연 시간(Latency) 폭증 및 획득 타임아웃이 발생했습니다.
  2. **트랜잭션 내 외부 I/O 유입 (Connection Pinning)**: [AsyncTradeProcessor.java](file:///Users/jouijae/Desktop/GitHub/StockSimulator/stockSimulator_BackEnd/src/main/java/com/stock/stockSimulator/service/AsyncTradeProcessor.java) 내의 트랜잭션 범위(`transactionTemplate`) 안에서 Redis I/O, WebSocket 실시간 브로드캐스트, 개인 체결 푸시 알림 전송 API 등 지연 시간이 높은 외부 네트워크 I/O들이 동기식으로 호출되면서 커넥션 점유 시간(Hold Time)이 대폭 길어져 풀이 순식간에 고갈되는 최악의 악순환이 발생했습니다.
- **해결 과정**:
  - **트랜잭션 미세 분리 및 네트워크 I/O 격리**: RDBMS 데이터 정합성 업데이트(잔고 이체, 주식 수량 변경, 주문 상태 갱신)만 트랜잭션 내부로 밀어 넣고, 트랜잭션 완료(커넥션 반환 완료) 후에만 외부 네트워크 I/O(Redis, WebSocket, Notification)를 비동기적으로 수행하도록 [AsyncTradeProcessor.java](file:///Users/jouijae/Desktop/GitHub/StockSimulator/stockSimulator_BackEnd/src/main/java/com/stock/stockSimulator/service/AsyncTradeProcessor.java)를 리팩토링하여 커넥션 점유 시간을 마이크로초 단위로 단축시켰습니다.
  - **리소스 비율의 균형화 (Resource Alignment)**: 코어 비동기 스레드 개수(50)를 지연 없이 충분히 수용하여 커넥션 고사 현상을 원천 방지하도록 [application.yaml](file:///Users/jouijae/Desktop/GitHub/StockSimulator/stockSimulator_BackEnd/src/main/resources/application.yaml)의 HikariCP 설정을 `maximum-pool-size: 60`, `minimum-idle: 30`으로 최적 정렬했습니다.
- **개선 효과 (실제 동시 10,000건 스트레스 테스트 결과)**:
  - **10,000건의 동시 고부하 거래 체결 주문 완주 시간**: **`21,459ms` (약 21.4초, 초당 약 465 TPS)**
  - **커넥션 획득 실패율 0%** 및 DB 쓰기 경합 감소로 시스템 가용량 극대화, 자금 불일치 0원의 완벽한 일관성 확보!

### 7. [Issue] 동시성 부하 테스트 중 초과 매도(Over-selling)로 인한 RDBMS 주식 잔고 마이너스(-) 현상
- **원인 분석**:
  - **자산 검증과 주문 접수의 비원자성**: 초기 설계에서는 주문을 넣을 때 MySQL에서 보유 주식을 조회하고 주문을 접수하도록 처리했습니다. 
  - **Race Condition 발생**: 10,000건의 동시성 테스트 상황에서 동일 회원이 매도 주문을 거의 동시에 다중 요청(따닥 요청)하는 경우, 각각의 요청 스레드가 동일한 DB 잔고(예: 100주)를 바라보고 둘 다 정상 주문으로 접수해버리는 경쟁 상태가 발생했습니다.
  - **초과 체결 발생**: 이 주문들이 각각 매칭되어 비동기 프로세서(`AsyncTradeProcessor`)로 유입되었고, 한 트랜잭션이 50주를 차감하고(`100 - 50 = 50`), 연이어 다른 트랜잭션이 70주를 차감하면서(`50 - 70 = -20`) RDBMS의 주식 잔고가 비정상적인 음수(-)가 되는 초과 매도 현상이 포착되었습니다.
- **해결 과정**:
  - **Redis Lua 스크립트 기반 원자적 자산 선차감 (Pre-deduction)**: 모든 자산 검증 및 주문 처리를 싱글 스레드로 작동하는 Redis Lua 스크립트(`match_engine.lua`)로 이전했습니다. 주문 접수 즉시 Redis 메모리 상의 잔고를 즉각 원자적으로 차감(`DECRBY`)하도록 설계했습니다. 첫 100주 매도 주문이 접수되는 즉시 Redis 잔고는 0이 되므로, 동시에 요청된 추가 70주 주문은 Redis 검증 단계에서 즉시 `INSUFFICIENT_STOCK` 에러로 차단되어 초과 주문 자체가 물리적으로 발생하지 못하도록 차단했습니다.
  - **실제 체결 수량(tradeQty) 기준 비동기 DB 차감**: 비동기 프로세서가 차감하는 수량은 원본 주문 수량(100주)이 아니라, 레디스에서 안전하게 매칭 완료된 **실제 조각난 체결 수량(`tradeQty` = 50주, 50주)**입니다. 두 비동기 트랜잭션이 순차적으로 실행되며 `100 - 50 = 50`, `50 - 50 = 0` 순으로 안전하게 차감하여 마이너스 버그를 완벽하게 정복했습니다.

---

## ⚡ Redis & WebSocket 활용

- **Redis**: 
  - `Hash` 구조를 사용해 각 주식의 실시간 현재가, 시가, 등락률을 캐싱하여 DB 부하를 최소화합니다.
  - `admin-open-market` 시점에 기준가를 Redis에 즉시 반영하여 대시보드 속도를 최적화했습니다.
- **WebSocket (STOMP)**:
  - 매칭 엔진에서 체결이 발생할 때마다 `/topic/stock` 채널로 실시간 시세를 브로드캐스트합니다.
  - 프론트엔드는 이를 수신하여 화면 새로고침 없이 호가창과 차트 정보를 갱신합니다.

---

## 👨‍🏫 Backend Interview Points / Q&A

1. **왜 Pessimistic Lock이 아니라 Redis Lua인가요?**
   - 주식 거래는 잔액과 보유주식 정합성이 최우선입니다. 빠른 호가 매칭과 주문 정합성 검증은 원자적인 **Redis Lua 스크립트**에서 처리하고, MySQL은 Lua가 확정한 결과를 비동기로 영속화합니다. 그래서 매칭 경로에서는 낙관적/비관적 락을 사용하지 않습니다.
2. **데드락 방지 전략은 무엇인가요?**
   - 체결 자체는 Redis Lua 단일 실행으로 끝내 데드락 가능성을 제거합니다. DB 반영 단계도 row를 잠그고 엔티티를 수정하는 대신 짧은 원자 `UPDATE`/`UPSERT` 위주로 수행해 트랜잭션 점유 시간을 줄입니다.
3. **비동기 설계 시 발생할 수 있는 데이터 정합성 이슈 해결 방법은?**
   - 사용자의 "가용 자산" 및 "대기 주문"은 메모리(Redis)를 신뢰성 있는 단일 소스(Single Source of Truth)로 삼아 Lua 스크립트 상에서 철저히 검증 및 동기화합니다. RDB는 비동기적으로 이를 뒤따라가며(Write-Behind) 최종 완결성을 맞추므로 사용자는 체결 여부를 지연 없이 즉각 통보받게 됩니다.
4. **Redis Pub/Sub을 사용한 이유는?**
   - 단일 서버의 WebSocket만으로는 서버 대수가 늘어날 때 알림 전파가 불가능합니다. Redis를 메시지 브로커로 사용하여 Multi-node 환경에서도 모든 유저에게 실시간 알림이 도달하도록 설계했습니다.
5. **시장가 주문의 잔여 수량 처리는?**
   - 시장가 주문은 "현재 가격으로 즉시 체결"이 목적이므로, 물량이 부족해 남은 양은 호가창에 남기지 않고 즉시 취소시키는 것이 일반적인 거래소의 스펙입니다. 프로젝트에도 이를 반영했습니다.
6. **Redis의 역할은?**
   - 단순한 시세 조회 성능을 높이기 위한 캐시 역할과, 스케일 아웃 환경에서 여러 서버가 동일한 시세를 공유할 수 있는 공유 메모리 역할을 수행합니다.
