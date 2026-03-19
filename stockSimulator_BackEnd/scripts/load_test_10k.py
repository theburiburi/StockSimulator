import requests
import json
import time
import random
from concurrent.futures import ThreadPoolExecutor
from typing import List

# 💡 실행 전 설치가 필요할 수 있습니다: pip install requests
# (IDE에서 빨간 줄이 보인다면 Terminal에서 위 명령어를 실행해 주세요)

BASE_URL = "http://localhost:8080/api/orders"
STOCK_CODE = "005930" 
MEMBER_IDS = [1, 2]
TOTAL_REQUESTS = 10000
CONCURRENCY = 50

class TestStats:
    def __init__(self):
        self.success: int = 0
        self.fail: int = 0
        self.latencies: List[float] = []

stats = TestStats()

def place_order(idx: int):
    m_id = random.choice(MEMBER_IDS)
    side = random.choice(["BUY", "SELL"])
    o_type = "LIMIT" if random.random() > 0.3 else "MARKET"
    price = random.randint(710, 730) * 100 
    qty = random.randint(1, 10)
    
    payload = {
        "memberId": m_id,
        "stockCode": STOCK_CODE,
        "orderType": o_type,
        "price": price,
        "qty": qty,
        "side": side
    }
    
    start = time.time()
    try:
        response = requests.post(f"{BASE_URL}/trade", json=payload, timeout=10)
        latency = time.time() - start
        if response.status_code == 200:
            stats.success += 1
        else:
            stats.fail += 1
        stats.latencies.append(latency)
    except Exception:
        stats.fail += 1

def run_test():
    print(f"🚀 Starting 10,000 Requests Test with Concurrency {CONCURRENCY}...")
    start_time = time.time()
    
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
        for i in range(TOTAL_REQUESTS):
            executor.submit(place_order, i)
            
    total_time = time.time() - start_time
    avg_latency = (sum(stats.latencies) / len(stats.latencies)) if stats.latencies else 0
    tps = TOTAL_REQUESTS / total_time
    
    print("\n" + "="*40)
    print(f"📊 Results for {TOTAL_REQUESTS} Requests:")
    print(f"⏱️  Total Time: {total_time:.2f}s")
    print(f"⚡  TPS (Trans/Sec): {tps:.2f}")
    print(f"✅  Success: {stats.success}")
    print(f"❌  Failures: {stats.fail}")
    print(f"🕒  Avg Latency: {avg_latency*1000:.2f}ms")
    print("="*40)

if __name__ == "__main__":
    run_test()
