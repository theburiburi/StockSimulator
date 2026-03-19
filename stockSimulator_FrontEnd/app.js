const SOCKET_URL = 'http://localhost:8080/ws-stock';
const TOPIC = '/topic/stock';

let stompClient = null;
const stockGrid = document.getElementById('stockGrid');
const statusText = document.querySelector('.status-text');
const statusIndicator = document.querySelector('.status-indicator');

// Format price with commas
const formatPrice = (price) => {
    return new Intl.NumberFormat('ko-KR').format(price);
};

// Calculate change rate format
const getChangeInfo = (current, open) => {
    if (!open || open === 0) return { rate: 0, status: 'neutral' };
    
    const diff = current - open;
    const rate = (diff / open) * 100;
    
    if (diff > 0) return { rate: rate.toFixed(2), status: 'up' };
    if (diff < 0) return { rate: Math.abs(rate).toFixed(2), status: 'down' };
    return { rate: '0.00', status: 'neutral' };
};

// Create a new stock card element
const createStockCard = (stock) => {
    const card = document.createElement('div');
    card.className = 'stock-card neutral';
    card.id = `stock-${stock.stockCode}`;
    
    card.innerHTML = `
        <div class="card-decor"></div>
        <div class="card-header">
            <div class="stock-name">${stock.companyName}</div>
            <div class="stock-code">${stock.stockCode}</div>
        </div>
        <div class="price-container">
            <div class="current-price" id="price-${stock.stockCode}">${formatPrice(stock.currentPrice)}</div>
            <div class="change-rate" id="rate-${stock.stockCode}">0.00%</div>
        </div>
        <div class="card-footer">
            <div class="footer-item">시가<span id="open-${stock.stockCode}">${formatPrice(stock.openingPrice)}</span></div>
            <div class="footer-item">고가<span id="high-${stock.stockCode}">${formatPrice(stock.highPrice || stock.currentPrice)}</span></div>
            <div class="footer-item">저가<span id="low-${stock.stockCode}">${formatPrice(stock.lowPrice || stock.currentPrice)}</span></div>
        </div>
    `;
    
    stockGrid.appendChild(card);
    updateStockData(stock, card);
};

// Update existing stock card
const updateStockData = (stock, card) => {
    const priceEl = document.getElementById(`price-${stock.stockCode}`);
    const rateEl = document.getElementById(`rate-${stock.stockCode}`);
    const openEl = document.getElementById(`open-${stock.stockCode}`);
    const highEl = document.getElementById(`high-${stock.stockCode}`);
    const lowEl = document.getElementById(`low-${stock.stockCode}`);
    
    // Check old price for animation
    const oldPriceStr = priceEl.innerText.replace(/,/g, '');
    const oldPrice = parseInt(oldPriceStr, 10);
    const newPrice = stock.currentPrice;
    
    // Apply texts
    priceEl.innerText = formatPrice(newPrice);
    openEl.innerText = formatPrice(stock.openingPrice);
    if(stock.highPrice) highEl.innerText = formatPrice(stock.highPrice);
    if(stock.lowPrice) lowEl.innerText = formatPrice(stock.lowPrice);
    
    // Calculate Rates
    const changeInfo = getChangeInfo(newPrice, stock.openingPrice);
    rateEl.innerText = `${changeInfo.rate}%`;
    
    // Update base status class (up/down/neutral)
    card.className = `stock-card ${changeInfo.status}`;
    
    // Trigger flash animation if price changed
    if (oldPrice !== newPrice) {
        // Remove old animation classes to restart animation
        card.classList.remove('flash-up', 'flash-down');
        // Force reflow
        void card.offsetWidth;
        
        if (newPrice > oldPrice) {
            card.classList.add('flash-up');
        } else {
            card.classList.add('flash-down');
        }
    }
};

const connect = () => {
    const socket = new SockJS(SOCKET_URL);
    stompClient = Stomp.over(socket);
    
    // Disable console logging from STOMP
    stompClient.debug = null;
    
    stompClient.connect({}, (frame) => {
        statusText.innerText = '실시간 연결됨';
        statusIndicator.className = 'status-indicator connected';
        
        stompClient.subscribe(TOPIC, (message) => {
            const stockData = JSON.parse(message.body);
            const existingCard = document.getElementById(`stock-${stockData.stockCode}`);
            
            if (existingCard) {
                updateStockData(stockData, existingCard);
            } else {
                createStockCard(stockData);
            }
        });
    }, (error) => {
        statusText.innerText = '연결 끊김. 재연결 시도 중...';
        statusIndicator.className = 'status-indicator disconnected';
        setTimeout(connect, 5000); // Retry after 5s
    });
};

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    connect();
});
