-- KEYS: (없음)
-- ARGV:
-- 1: orderId (String)
-- 2: memberId (String)
-- 3: stockCode (String)
-- 4: side (BUY / SELL)
-- 5: price (Long)

local orderId = ARGV[1]
local memberId = ARGV[2]
local stockCode = ARGV[3]
local side = ARGV[4]
local price = tonumber(ARGV[5])

local function getBalanceKey(mid) return "member:" .. mid .. ":balance" end
local function getStockKey(mid, code) return "member:" .. mid .. ":stock:" .. code end
local function getOrderListKey(code, sde, prc) return "orders:" .. sde .. ":" .. code .. ":" .. prc end

local balanceKey = getBalanceKey(memberId)
local stockKey = getStockKey(memberId, stockCode)
local listKey = getOrderListKey(stockCode, side:lower(), price)
local bookKey = "orderbook:" .. side:lower() .. ":" .. stockCode

-- Redis 호가 큐에서 해당 주문 ID 검색 및 제거
local listData = redis.call('LRANGE', listKey, 0, -1)
local foundVal = nil

for i, val in ipairs(listData) do
    if string.sub(val, 1, string.len(orderId .. ":")) == (orderId .. ":") then
        foundVal = val
        break
    end
end

if foundVal then
    -- 포맷: "orderId:memberId:qty"
    local _, _, actualQty = string.match(foundVal, "([^:]+):([^:]+):([^:]+)")
    actualQty = tonumber(actualQty)
    
    -- 큐에서 제거
    redis.call('LREM', listKey, 1, foundVal)
    
    -- 해당 가격의 호가 큐가 완전히 비었으면 호가 ZSET에서 해당 가격 제거
    if redis.call('LLEN', listKey) == 0 then
        redis.call('ZREM', bookKey, price)
    end
    
    -- 자산 환불
    if side == "BUY" then
        redis.call('INCRBY', balanceKey, price * actualQty)
    else
        redis.call('INCRBY', stockKey, actualQty)
    end
    
    return actualQty -- 취소 완료된 실제 수량 반환
else
    return 0 -- 대기 중인 호가를 찾지 못함 (이미 체결 완료됨)
end
