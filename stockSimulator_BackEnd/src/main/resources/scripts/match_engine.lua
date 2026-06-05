-- KEYS: (없음. 동적 키 생성으로 인해 ARGV에서 처리하거나 StringRedisTemplate에서 제어)
-- ARGV:
-- 1: memberId (Long)
-- 2: stockCode (String)
-- 3: orderId (Long)
-- 4: orderType (LIMIT / MARKET)
-- 5: side (BUY / SELL)
-- 6: price (Long)
-- 7: qty (Integer)

local memberId = ARGV[1]
local stockCode = ARGV[2]
local orderId = ARGV[3]
local orderType = ARGV[4]
local side = ARGV[5]
local price = tonumber(ARGV[6])
local qty = tonumber(ARGV[7])

local buyBookKey = "orderbook:buy:" .. stockCode
local sellBookKey = "orderbook:sell:" .. stockCode
local tradeEventStreamKey = "trade:events"

-- Helper functions
local function getBalanceKey(mid) return "member:" .. mid .. ":balance" end
local function getStockKey(mid, code) return "member:" .. mid .. ":stock:" .. code end
local function getOrderListKey(code, sde, prc) return "orders:" .. sde .. ":" .. code .. ":" .. prc end

local balanceKey = getBalanceKey(memberId)
local stockKey = getStockKey(memberId, stockCode)

local balance = tonumber(redis.call('GET', balanceKey) or "0")
local stockQty = tonumber(redis.call('GET', stockKey) or "0")

-- 1. 잔고 / 보유 주식 검증 및 즉시 선차감
if side == "BUY" then
    if orderType == "LIMIT" then
        local requiredAmount = price * qty
        if balance < requiredAmount then
            return {err = "INSUFFICIENT_BALANCE"}
        end
        redis.call('DECRBY', balanceKey, requiredAmount)
    end
else
    if stockQty < qty then
        return {err = "INSUFFICIENT_STOCK"}
    end
    redis.call('DECRBY', stockKey, qty)
end

-- 2. 매칭 엔진 동작
local remainingQty = qty
local matchedTrades = {} -- 포맷: "buyOrderId:sellOrderId:buyerId:sellerId:price:tradeQty"

if side == "BUY" then
    -- 매수 주문인 경우: 가장 저렴한 매도 호가들과 매칭
    while remainingQty > 0 do
        local bestSellPrices = redis.call('ZRANGE', sellBookKey, 0, 0)
        if #bestSellPrices == 0 then break end
        
        local bestPrice = tonumber(bestSellPrices[1])
        if orderType == "LIMIT" and bestPrice > price then break end
        
        local sellListKey = getOrderListKey(stockCode, "sell", bestPrice)
        local oppOrderData = redis.call('LPOP', sellListKey)
        
        if oppOrderData then
            local oppOrderId, oppSellerId, oppQty = string.match(oppOrderData, "([^:]+):([^:]+):([^:]+)")
            oppQty = tonumber(oppQty)
            
            local tradeQty = math.min(remainingQty, oppQty)
            local tradePrice = bestPrice
            local tradeAmount = tradePrice * tradeQty
            
            -- 체결 이벤트를 Redis Stream에 먼저 기록하여 DB 반영 전 장애가 나도 재처리 가능하게 한다.
            local tradeEvent = orderId .. ":" .. oppOrderId .. ":" .. memberId .. ":" .. oppSellerId .. ":" .. tradePrice .. ":" .. tradeQty
            local tradeEventId = redis.call('XADD', tradeEventStreamKey, '*', 'trade', tradeEvent)
            table.insert(matchedTrades, tradeEventId .. "|" .. tradeEvent)
            
            -- Redis 잔액/자산 실시간 이전
            redis.call('INCRBY', getBalanceKey(oppSellerId), tradeAmount)
            redis.call('INCRBY', getStockKey(memberId, stockCode), tradeQty)
            
            if orderType == "MARKET" then
                redis.call('DECRBY', balanceKey, tradeAmount)
            else
                -- 지정가 매수 선차감 금액 중 차액 환불 (호가가 더 저렴하게 체결된 경우)
                local refund = (price - tradePrice) * tradeQty
                if refund > 0 then
                    redis.call('INCRBY', balanceKey, refund)
                end
            end
            
            remainingQty = remainingQty - tradeQty
            local oppRemaining = oppQty - tradeQty
            
            if oppRemaining > 0 then
                redis.call('LPUSH', sellListKey, oppOrderId .. ":" .. oppSellerId .. ":" .. oppRemaining)
            end
        else
            -- 큐가 비어있으면 호가 목록 ZSET에서 제거
            redis.call('ZREM', sellBookKey, bestPrice)
        end
    end
    
    -- 미체결 잔량이 있고 지정가 주문이면 매수 호가창에 등록
    if orderType == "LIMIT" and remainingQty > 0 then
        local buyListKey = getOrderListKey(stockCode, "buy", price)
        redis.call('RPUSH', buyListKey, orderId .. ":" .. memberId .. ":" .. remainingQty)
        redis.call('ZADD', buyBookKey, price, price)
    end
    
else
    -- 매도 주문인 경우: 가장 비싼 매수 호가들과 매칭
    while remainingQty > 0 do
        local bestBuyPrices = redis.call('ZREVRANGE', buyBookKey, 0, 0)
        if #bestBuyPrices == 0 then break end
        
        local bestPrice = tonumber(bestBuyPrices[1])
        if orderType == "LIMIT" and bestPrice < price then break end
        
        local buyListKey = getOrderListKey(stockCode, "buy", bestPrice)
        local oppOrderData = redis.call('LPOP', buyListKey)
        
        if oppOrderData then
            local oppOrderId, oppBuyerId, oppQty = string.match(oppOrderData, "([^:]+):([^:]+):([^:]+)")
            oppQty = tonumber(oppQty)
            
            local tradeQty = math.min(remainingQty, oppQty)
            local tradePrice = bestPrice
            local tradeAmount = tradePrice * tradeQty
            
            -- 체결 이벤트를 Redis Stream에 먼저 기록하여 DB 반영 전 장애가 나도 재처리 가능하게 한다.
            local tradeEvent = oppOrderId .. ":" .. orderId .. ":" .. oppBuyerId .. ":" .. memberId .. ":" .. tradePrice .. ":" .. tradeQty
            local tradeEventId = redis.call('XADD', tradeEventStreamKey, '*', 'trade', tradeEvent)
            table.insert(matchedTrades, tradeEventId .. "|" .. tradeEvent)
            
            -- Redis 잔액/자산 실시간 이전
            redis.call('INCRBY', getBalanceKey(memberId), tradeAmount)
            redis.call('INCRBY', getStockKey(oppBuyerId, stockCode), tradeQty)
            
            remainingQty = remainingQty - tradeQty
            local oppRemaining = oppQty - tradeQty
            
            if oppRemaining > 0 then
                redis.call('LPUSH', buyListKey, oppOrderId .. ":" .. oppBuyerId .. ":" .. oppRemaining)
            end
        else
            redis.call('ZREM', buyBookKey, bestPrice)
        end
    end
    
    -- 미체결 잔량이 있고 지정가 주문이면 매도 호가창에 등록
    if orderType == "LIMIT" and remainingQty > 0 then
        local sellListKey = getOrderListKey(stockCode, "sell", price)
        redis.call('RPUSH', sellListKey, orderId .. ":" .. memberId .. ":" .. remainingQty)
        redis.call('ZADD', sellBookKey, price, price)
    end
end

-- 리턴 형식: { 남은 주문 수량, 체결 상세 목록 리스트 }
return {tostring(remainingQty), matchedTrades}
