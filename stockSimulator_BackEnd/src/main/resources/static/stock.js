const urlParams = new URLSearchParams(window.location.search);
const stockCode = urlParams.get('code');

if (!stockCode) {
    alert("잘못된 접근입니다. 종목 코드가 없습니다.");
    window.location.href = 'index.html';
}

let currentMember = null;
let currentStockData = null;
let stompClient = null;
let currentOrderType = 'LIMIT';

// Utilities
const formatPrice = (price) => new Intl.NumberFormat('ko-KR').format(price);

const getChangeInfo = (current, open) => {
    if (!open || open === 0) return { rate: '0.00', status: 'neutral' };
    const diff = current - open;
    const rate = (diff / open) * 100;
    if (diff > 0) return { rate: `+${rate.toFixed(2)}`, status: 'up' };
    if (diff < 0) return { rate: `${rate.toFixed(2)}`, status: 'down' };
    return { rate: '0.00', status: 'neutral' };
};

// Fetch User
const fetchUser = async () => {
    try {
        const res = await fetch('/api/auth/me');
        const text = await res.text();
        if (text) currentMember = JSON.parse(text);
    } catch(e) {
        console.error("Not logged in");
    }
};

// Setup Base Stock Info
const loadStockBaseline = async () => {
    try {
        const res = await fetch('/api/stocks');
        const stocks = await res.json();
        const stock = stocks.find(s => s.stockCode === stockCode);
        if (!stock) {
            alert("존재하지 않는 종목입니다.");
            window.location.href = 'index.html';
            return;
        }
        currentStockData = stock;
        document.getElementById('stockName').innerText = stock.companyName;
        document.getElementById('stockCodeStr').innerText = stock.stockCode;
        
        // Auto-fill price input to current price
        document.getElementById('tradePrice').value = stock.currentPrice;
        
        renderCurrentPrice(stock.currentPrice, stock.openingPrice);
    } catch(e) {
        console.error("Error loading baseline stocks:", e);
    }
};

// Update Price UI
const renderCurrentPrice = (current, open) => {
    const changeInfo = getChangeInfo(current, open);
    document.getElementById('currentPrice').innerText = formatPrice(current);
    document.getElementById('changeRate').innerText = `${changeInfo.rate}%`;
    document.getElementById('priceColorWrap').className = changeInfo.status;
};

// Order Book Polling
const fetchOrderBook = async () => {
    try {
        const res = await fetch(`/api/orders/depth/${stockCode}`);
        const depths = await res.json();
        renderOrderBook(depths);
    } catch(e) {
        console.error("Order book fetch error:", e);
    }
};

const renderOrderBook = (depths) => {
    const grid = document.getElementById('orderBookGrid');
    grid.innerHTML = '';
    
    // Separate Buys and Sells, then sort correctly if needed
    const sells = depths.filter(d => d.side === 'SELL').sort((a,b) => b.price - a.price); // Descending (highest sell at top)
    const buys = depths.filter(d => d.side === 'BUY').sort((a,b) => b.price - a.price);   // Descending (highest buy directly under lowest sell)

    // Slice to top 10 depths each for clean UI
    const finalSells = sells.slice(-10); // Take lowest 10 sells
    const finalBuys = buys.slice(0, 10);  // Take highest 10 buys
    
    // Render Sells
    finalSells.forEach(d => {
        grid.innerHTML += `
            <div class="order-book-row">
                <div class="sell-vol">${formatPrice(d.totalQuantity)}</div>
                <div class="price-cell down" onclick="setPrice(${d.price})">${formatPrice(d.price)}</div>
                <div></div>
            </div>
        `;
    });
    
    // Render Buys
    finalBuys.forEach(d => {
        grid.innerHTML += `
            <div class="order-book-row">
                <div></div>
                <div class="price-cell up" onclick="setPrice(${d.price})">${formatPrice(d.price)}</div>
                <div class="buy-vol">${formatPrice(d.totalQuantity)}</div>
            </div>
        `;
    });
    
    if (depths.length === 0) {
        grid.innerHTML = '<div style="text-align:center; padding:30px; color:#8e9bb0;">현재 대기중인 매물이 없습니다.</div>';
    }
};

// Order type toggle mechanism
window.setOrderType = (type) => {
    currentOrderType = type;
    if (type === 'LIMIT') {
        document.getElementById('btnLimit').style.opacity = '1';
        document.getElementById('btnMarket').style.opacity = '0.3';
        document.getElementById('priceInputGroup').style.display = 'block';
    } else {
        document.getElementById('btnLimit').style.opacity = '0.3';
        document.getElementById('btnMarket').style.opacity = '1';
        document.getElementById('priceInputGroup').style.display = 'none';
    }
};

// Price click handler in Order Book
window.setPrice = (price) => {
    document.getElementById('tradePrice').value = price;
    if (currentOrderType === 'MARKET') {
        window.setOrderType('LIMIT'); // auto-switch to limit if they select a specific price
    }
};

// Place Trade
window.submitTrade = async (side) => {
    if (!currentMember) {
        alert("로그인이 필요합니다.");
        return;
    }
    
    const qty = document.getElementById('tradeQty').value;
    const price = currentOrderType === 'LIMIT' ? document.getElementById('tradePrice').value : 0;
    
    if (!qty) {
        alert("수량을 입력하세요.");
        return;
    }
    
    if (currentOrderType === 'LIMIT' && (!price || parseInt(price) % 100 !== 0)) {
        alert("가격은 100원 단위로만 입력 가능합니다!");
        return;
    }

    const payload = {
        memberId: currentMember.id,
        stockCode: stockCode,
        orderType: currentOrderType,
        price: parseInt(price),
        qty: parseInt(qty),
        side: side
    };

    try {
        const res = await fetch('/api/orders/trade', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const text = await res.text();
        if (!res.ok) alert("에러: " + text);
        else {
            alert(side === 'BUY' ? "매수 주문이 접수되었습니다." : "매도 주문이 접수되었습니다.");
            document.getElementById('tradeQty').value = '';
            fetchOrderBook(); // instant refresh
        }
    } catch(e) {
        alert("요청 실패: " + e);
    }
};

// WebSocket for real-time prices
const connectWebSocket = () => {
    const socket = new SockJS('/ws-stock');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;
    stompClient.connect({}, () => {
        stompClient.subscribe('/topic/stock', (message) => {
            const data = JSON.parse(message.body);
            if (data.stockCode === stockCode) {
                currentStockData = data;
                renderCurrentPrice(data.currentPrice, data.openingPrice);
            }
        });
    });
};

// Bootstrap
document.addEventListener('DOMContentLoaded', async () => {
    await fetchUser();
    await loadStockBaseline();
    await fetchOrderBook();
    setInterval(fetchOrderBook, 1500); // Poll Order Book every 1.5 seconds
    connectWebSocket();
});
