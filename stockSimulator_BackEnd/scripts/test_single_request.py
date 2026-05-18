import requests
import json

url = "http://localhost:8080/api/orders/trade"
payload = {
    "memberId": 1,
    "stockCode": "005930",
    "orderType": "LIMIT",
    "price": 72000,
    "qty": 5,
    "side": "BUY"
}

print("sending request...")
try:
    response = requests.post(url, json=payload, timeout=5)
    print("HTTP Status Code:", response.status_code)
    print("Response Headers:", response.headers)
    print("Response Body:")
    print(response.text)
except Exception as e:
    print("Failed to send request:", e)
