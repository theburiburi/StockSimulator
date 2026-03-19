import threading
import requests
import json
import time
import random

BASE_URL = "http://localhost:8080/api/orders"
STOCK_CODE = "005930" # Samsung
MEMBER_IDS = [1, 2]

def place_order(member_id, side, order_type, price, qty):
    payload = {
        "memberId": member_id,
        "stockCode": STOCK_CODE,
        "orderType": order_type,
        "price": price,
        "qty": qty,
        "side": side
    }
    try:
        response = requests.post(f"{BASE_URL}/trade", json=payload)
        print(f"Member {member_id} | {side} | {order_type} | {price} | Qty {qty} | Status: {response.status_code} | {response.text}")
    except Exception as e:
        print(f"Error: {e}")

def run_load_test():
    threads = []
    # Mix of limit and market orders
    for _ in range(20):
        m_id = random.choice(MEMBER_IDS)
        side = random.choice(["BUY", "SELL"])
        o_type = "LIMIT" if random.random() > 0.3 else "MARKET"
        price = random.randint(700, 750) * 100 
        qty = random.randint(1, 10)
        
        t = threading.Thread(target=place_order, args=(m_id, side, o_type, price, qty))
        threads.append(t)
        t.start()
        time.sleep(0.05) # Small delay to staggered impact

    for t in threads:
        t.join()

if __name__ == "__main__":
    print("Starting Load Test...")
    start_time = time.time()
    run_load_test()
    print(f"Load Test Finished in {time.time() - start_time:.2f} seconds")
