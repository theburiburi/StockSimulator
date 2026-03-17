-- KEYS[1]: 종목 호가 큐 (예: orders:005930:10000)
-- ARGV[1]: 매수 요청 수량
-- ARGV[2]: 매수자 ID

local requestQty = tonumber(ARGV[1])
local totalExecutedQty = 0
local tradeResults = {} -- 체결된 주문 ID와 수량을 담을 테이블

-- 1. 매도 주문 큐에서 순차적으로 꺼내기 (FIFO: 시간 우선)
while requestQty > 0 do
    local orderData = redis.call('LPOP', KEYS[1]) -- 가장 오래된 매도 주문 추출
    if not orderData then break end -- 매도 물량 없음

    -- orderData 형식 예시: "orderId:sellerId:quantity"
    local orderId, sellerId, qty = string.match(orderData, "([^:]+):([^:]+):([^:]+)")
    qty = tonumber(qty)

    if qty <= requestQty then
        -- 매도 주문 전체 체결
        totalExecutedQty = totalExecutedQty + qty
        requestQty = requestQty - qty
        table.insert(tradeResults, orderId .. ":" .. sellerId .. ":" .. qty)
    else
        -- 매도 주문 일부 체결 (남은 수량은 다시 큐 맨 앞에 넣음)
        local remainingQty = qty - requestQty
        totalExecutedQty = totalExecutedQty + requestQty
        redis.call('LPUSH', KEYS[1], orderId .. ":" .. sellerId .. ":" .. remainingQty)
        table.insert(tradeResults, orderId .. ":" .. sellerId .. ":" .. requestQty)
        requestQty = 0
    end
end

-- 2. 체결 결과 반환 (누구와 얼마나 체결됐는지 리스트로 반환)
return {totalExecutedQty, tradeResults}