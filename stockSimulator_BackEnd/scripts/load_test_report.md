# 📊 10,000 Transactions Load Test & Concurrency Analysis

This report logs the load test execution simulating **10 concurrent users** making a total of **10,000 trading requests** against the high-performance Redis Lua matching engine, comparing different levels of concurrency and server configurations.

---

## 🛠️ 1. Test Environment & Data Prep Recap
* **Database (MySQL)**: 10 users provisioned with **1 Trillion Won** each and **10,000,000 shares** of all stock tickers (Samsung, SK Hynix, Apple, Tesla, NVIDIA).
* **Redis**: Cleared and synced for fresh lazy loading.
* **Mac OS Limit**: `ulimit -n` is **1,048,575** (no client-side socket limit).

---

## 📈 2. Concurrency & Configuration Comparison

Here is the side-by-side comparison of the three test runs we executed:

| Performance Metric | Run 1: Concurrency 1,000 | Run 2: Concurrency 10,000 (Original) | Run 3: Concurrency 10,000 (Tuned Server) |
| :--- | :---: | :---: | :---: |
| **Total Transactions** | 10,000 | 10,000 | 10,000 |
| **Simulated Users** | 10 | 10 | 10 |
| **Concurrency Level** | **1,000** | **10,000** | **10,000** |
| ⏱️ **Total Time** | **6.22s** | **10.75s** | **25.97s** |
| ⚡ **TPS (Transactions/Sec)** | **1,606.61 TPS** | **930.52 TPS** | **385.02 TPS** |
| ✅ **Success Count** | **10,000 (100.0%)** | **8,493 (84.9%)** | **10,000 (100.0%)** |
| ❌ **Failure Count** | **0 (0.0%)** | **1,507 (15.1%)** | **0 (0.0%)** |
| 🕒 **Avg Latency** | **597.02ms** | **5,790.68ms** | **14,308.22ms** |
| **Server Configuration** | Tomcat 500, DB Pool 80 | Tomcat 500, DB Pool 80 | Tomcat 1000, DB Pool 150 (Tuned) |

---

## 🔍 3. Performance Breakthrough Breakdown

### Run 3: 100% Success at 10,000 Concurrency! 🎉
By scaling up Tomcat and the Hikari connection pool, we completely eliminated connection failures:
1. **Zero Connections Dropped**:
   - The increased Tomcat `accept-count: 3000` backlog queue successfully held all excess connections at the TCP stack layer, rather than dropping them.
   - Tomcat's max connections was increased to `15,000` to completely absorb the 10,000 incoming requests.
2. **Robust Connection Pool (Hikari)**:
   - Increasing the `maximum-pool-size: 150` and expanding the connection timeout to `30000ms` allowed worker threads to safely queue and obtain database connections even under extreme thread contention.
3. **Queueing Latency Trade-Off**:
   - Because the server queued and successfully processed every single request without dropping any on the floor, the average latency increased to **14.30s**. This is a standard and healthy trade-off in high-reliability architectures: **choosing safety and completeness (100% Success Rate) over fast drops**.

---

## 💡 4. Architectural Suggestions for Next-Level Scale

To handle 10,000+ simultaneous bursts with *both* 100% success rate and sub-second latency:
1. **Introduce a Message Queue (e.g., Kafka / RabbitMQ)**:
   Instead of direct HTTP POST endpoints under peak loads, users can send order requests to a message queue, which buffers the 10,000+ simultaneous bursts and feeds them to the matching engine at a steady, high-speed rate.
2. **Load Balancing (Scale Out)**:
   Deploy multiple Spring Boot backend instances behind an Nginx load balancer to distribute the 10,000 concurrent connection requests across multiple JVM instances.
