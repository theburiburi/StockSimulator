import asyncio
import aiohttp
import time
import random
from typing import List

# 💡 실행 전 설치가 필요합니다: pip install aiohttp
# (IDE에서 빨간 줄이 보인다면 Terminal에서 위 명령어를 실행해 주세요)

BASE_URL = "http://localhost:8080/api/orders"
STOCK_CODE = "005930" 
MEMBER_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
TOTAL_REQUESTS = 10000
CONCURRENCY = 10000  # 동시 요청 수를 정확히 10000건으로 지정해 한 번에 쏟아붓습니다.

class TestStats:
    def __init__(self):
        self.success: int = 0
        self.fail: int = 0
        self.latencies: List[float] = []

stats = TestStats()

async def place_order(session: aiohttp.ClientSession, semaphore: asyncio.Semaphore, idx: int):
    async with semaphore:
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
            timeout = aiohttp.ClientTimeout(total=120)
            async with session.post(f"{BASE_URL}/trade", json=payload, timeout=timeout) as response:
                latency = time.time() - start
                if response.status == 200:
                    stats.success += 1
                else:
                    stats.fail += 1
                stats.latencies.append(latency)
        except Exception:
            stats.fail += 1

async def run_test():
    print(f"🚀 Starting 10,000 Requests Async Test with Concurrency {CONCURRENCY}...")
    start_time = time.time()
    
    # TCP 커넥션 제한 해제 및 세마포어 할당
    connector = aiohttp.TCPConnector(limit=CONCURRENCY, ttl_dns_cache=300)
    semaphore = asyncio.Semaphore(CONCURRENCY)
    
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [place_order(session, semaphore, i) for i in range(TOTAL_REQUESTS)]
        await asyncio.gather(*tasks)
            
    total_time = time.time() - start_time
    avg_latency = (sum(stats.latencies) / len(stats.latencies)) if stats.latencies else 0
    tps = TOTAL_REQUESTS / total_time
    
    print("\n" + "="*40)
    print(f"📊 Results for {TOTAL_REQUESTS} Requests (Async):")
    print(f"⏱️  Total Time: {total_time:.2f}s")
    print(f"⚡  TPS (Trans/Sec): {tps:.2f}")
    print(f"✅  Success: {stats.success}")
    print(f"❌  Failures: {stats.fail}")
    print(f"🕒  Avg Latency: {avg_latency*1000:.2f}ms")
    print("="*40)

if __name__ == "__main__":
    asyncio.run(run_test())

