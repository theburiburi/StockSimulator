const SOCKET_URL = 'http://localhost:8080/ws-stock';
const TOPIC = '/topic/stock';

let stompClient = null;
let currentMember = null;
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
        </div>
    `;
    
    stockGrid.appendChild(card);
    updateStockData(stock, card);
    
    card.style.cursor = 'pointer';
    card.onclick = (e) => {
        // Prevent navigation if interacting with inputs or buttons
        if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') return;
        window.location.href = `stock.html?code=${stock.stockCode}`;
    };
    
    // Removed trade panel logic
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

// Add Ticker Item
const addTickerItem = (stockData) => {
    const tickerWrap = document.getElementById('tickerWrap');
    tickerWrap.style.display = 'block';

    const tickerContent = document.getElementById('tickerContent');
    const tickerItem = document.createElement('span');
    
    const changeInfo = getChangeInfo(stockData.currentPrice, stockData.openingPrice);
    
    tickerItem.className = `ticker-item ${changeInfo.status}`;
    tickerItem.innerHTML = `[실시간 체결] ${stockData.companyName} ${formatPrice(stockData.currentPrice)}원 (${changeInfo.rate}%) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`;
    
    tickerContent.appendChild(tickerItem);
    
    if (tickerContent.children.length > 30) {
        tickerContent.removeChild(tickerContent.children[0]);
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
            
            // Add to Ticker Event
            addTickerItem(stockData);
        });
    }, (error) => {
        statusText.innerText = '연결 끊김. 재연결 시도 중...';
        statusIndicator.className = 'status-indicator disconnected';
        setTimeout(connect, 5000); // Retry after 5s
    });
};

// Fetch initial stocks
const fetchInitialStocks = () => {
    fetch('/api/stocks')
        .then(res => res.json())
        .then(data => {
            data.forEach(stockData => {
                const existingCard = document.getElementById(`stock-${stockData.stockCode}`);
                if (existingCard) {
                    updateStockData(stockData, existingCard);
                } else {
                    createStockCard(stockData);
                }
            });
        })
        .catch(err => console.error("Error fetching initial stocks:", err));
};

// Authentication setup
const fetchUser = () => {
    fetch('/api/auth/me')
        .then(res => res.text())
        .then(text => {
            if(!text) return; // not logged in
            
            const member = JSON.parse(text);
            currentMember = member;
            
            // Show user section
            document.getElementById('loginSection').style.display = 'none';
            document.getElementById('userSection').style.display = 'block';
            document.getElementById('userName').innerText = member.name;
            document.getElementById('userBalance').innerText = formatPrice(member.balance);
            
            // If admin
            if (member.role === 'ADMIN') {
                document.getElementById('adminControls').style.display = 'flex';
            }
            
            // Removed trade panel logic
        })
        .catch(e => console.error("Not logged in."));
};

// Fetch Portfolio
const fetchPortfolio = () => {
    fetch('/api/auth/portfolio')
        .then(res => res.json())
        .then(stocks => {
            const listEl = document.getElementById('portfolioList');
            if (!stocks || stocks.length === 0) {
                listEl.innerHTML = '<div style="text-align: center; color: #8e9bb0; padding: 20px;">보유 중인 주식이 없습니다.</div>';
                return;
            }
            
            listEl.innerHTML = '';
            stocks.forEach(stock => {
                if (stock.quantity <= 0) return; // Hide empty quantities
                
                const nameDiv = document.querySelector(`#stock-${stock.stockCode} .stock-name`);
                const companyName = nameDiv ? nameDiv.innerText : stock.stockCode;
                
                const priceEl = document.getElementById(`price-${stock.stockCode}`);
                const currentPrice = priceEl ? parseInt(priceEl.innerText.replace(/,/g, '').replace(/[^0-9-]/g, '')) : 0;
                
                const totalValue = currentPrice * stock.quantity;
                const totalInvest = stock.averagePrice * stock.quantity;
                const profitRate = totalInvest > 0 ? ((totalValue - totalInvest) / totalInvest) * 100 : 0;
                const rateColor = profitRate > 0 ? '#ff3366' : (profitRate < 0 ? '#0077ff' : '#8e9bb0');
                
                const item = document.createElement('div');
                item.className = 'portfolio-item';
                item.innerHTML = `
                    <div>
                        <div style="font-weight: bold; font-size: 1.1rem; color: white;">${companyName}</div>
                        <div style="font-size: 0.85rem; color: #8e9bb0; margin-top: 4px;">보유: ${formatPrice(stock.quantity)}주 | 평단가: ${formatPrice(stock.averagePrice)}원</div>
                    </div>
                    <div style="text-align: right;">
                        <div style="font-weight: bold; font-size: 1.1rem; color: white;">평가금: ${formatPrice(totalValue)}원</div>
                        <div style="font-size: 0.9rem; font-weight: bold; color: ${rateColor}; margin-top: 4px;">수익률: ${profitRate.toFixed(2)}%</div>
                    </div>
                `;
                listEl.appendChild(item);
            });
            
            if (listEl.innerHTML === '') {
                 listEl.innerHTML = '<div style="text-align: center; color: #8e9bb0; padding: 20px;">보유 중인 주식이 없습니다.</div>';
            }
        })
        .catch(err => console.error("Error fetching portfolio:", err));
};

// Fetch Order History
const fetchOrderHistory = () => {
    fetch('/api/auth/orders')
        .then(res => res.json())
        .then(orders => {
            const listEl = document.getElementById('orderHistoryList');
            if (!orders || orders.length === 0) {
                listEl.innerHTML = '<div style="text-align: center; color: #8e9bb0; padding: 20px;">주문 내역이 없습니다.</div>';
                return;
            }
            
            listEl.innerHTML = '';
            orders.forEach(order => {
                const nameDiv = document.querySelector(`#stock-${order.stockCode} .stock-name`);
                const companyName = nameDiv ? nameDiv.innerText : order.stockCode;
                
                let statusText = order.status;
                let statusColor = '#8e9bb0';
                
                if (order.status === 'WAITING') { statusText = '대기중'; statusColor = '#f5a623'; }
                else if (order.status === 'PARTIAL') { statusText = '부분체결'; statusColor = '#00e676'; }
                else if (order.status === 'COMPLETED') { statusText = '체결완료'; statusColor = '#0077ff'; }
                else if (order.status === 'CANCELLED') { statusText = '주문취소'; statusColor = '#ff3366'; }
                
                if (order.status === 'WAITING' || order.status === 'PARTIAL') {
                    statusText += ` <button onclick="cancelOrder(${order.id})" style="margin-left:8px; padding:4px 8px; background:#ff3366; color:white; border:none; border-radius:4px; font-size:0.8rem; cursor:pointer;">주문 취소</button>`;
                }
                
                const sideText = order.side === 'BUY' ? '매수' : '매도';
                const sideColor = order.side === 'BUY' ? '#ff3366' : '#0077ff';
                
                const item = document.createElement('div');
                item.className = 'portfolio-item';
                item.innerHTML = `
                    <div>
                        <div style="font-weight: bold; font-size: 1.1rem; color: white;">
                            <span style="color: ${sideColor}; margin-right: 5px;">[${sideText}]</span>
                            ${companyName}
                        </div>
                        <div style="font-size: 0.85rem; color: #8e9bb0; margin-top: 4px;">
                            주문: ${formatPrice(order.quantity)}주 | 체결: ${formatPrice(order.quantity - order.remainingQuantity)}주 | 잔여: ${formatPrice(order.remainingQuantity)}주
                        </div>
                        <div style="font-size: 0.85rem; color: #8e9bb0; margin-top: 4px;">
                            주문단가: ${formatPrice(order.price)}원
                        </div>
                    </div>
                    <div style="text-align: right;">
                        <div style="font-weight: bold; font-size: 1.1rem; color: ${statusColor}; border: 1px solid ${statusColor}; padding: 4px 10px; border-radius: 20px;">
                            ${statusText}
                        </div>
                    </div>
                `;
                listEl.appendChild(item);
            });
        })
        .catch(err => console.error("Error fetching orders:", err));
};

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    fetchUser();
    fetchInitialStocks();
    connect();
});

// APIs
function adminOpenMarket() {
    fetch('/api/admin/market/open', { method: 'POST' })
        .then(res => res.text())
        .then(t => alert('Admin: ' + t))
        .catch(err => alert('Error: ' + err));
}

function adminCloseMarket() {
    fetch('/api/admin/market/close', { method: 'POST' })
        .then(res => res.text())
        .then(t => alert('Admin: ' + t))
        .catch(err => alert('Error: ' + err));
}

window.cancelOrder = (orderId) => {
    if(!confirm("주문을 취소하시겠습니까?")) return;
    fetch(`/api/orders/${orderId}/cancel`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ memberId: currentMember.id })
    })
    .then(async res => {
        const text = await res.text();
        if(!res.ok) alert("취소 실패: " + text);
        else { 
            alert("취소 완료: " + text); 
            fetchOrderHistory(); 
            fetchPortfolio(); // Update balance/stocks
        }
    })
    .catch(err => alert('Error: ' + err));
};
