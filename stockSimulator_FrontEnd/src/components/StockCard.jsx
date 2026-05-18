import { useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const fmt = (n) => new Intl.NumberFormat('ko-KR').format(n);

const getChangeInfo = (current, open) => {
  if (!open || open === 0) return { rate: '0.00', status: 'neutral' };
  const diff = current - open;
  const rate = (diff / open) * 100;
  if (diff > 0) return { rate: rate.toFixed(2), status: 'up' };
  if (diff < 0) return { rate: Math.abs(rate).toFixed(2), status: 'down' };
  return { rate: '0.00', status: 'neutral' };
};

export default function StockCard({ stock }) {
  const navigate = useNavigate();
  const changeInfo = getChangeInfo(stock.currentPrice, stock.openingPrice);
  const prevPriceRef = useRef(stock.currentPrice);
  const cardRef = useRef(null);

  useEffect(() => {
    const prev = prevPriceRef.current;
    const cur = stock.currentPrice;
    if (prev !== cur && cardRef.current) {
      const cls = cur > prev ? 'flash-up' : 'flash-down';
      cardRef.current.classList.remove('flash-up', 'flash-down');
      void cardRef.current.offsetWidth; // reflow
      cardRef.current.classList.add(cls);
    }
    prevPriceRef.current = cur;
  }, [stock.currentPrice]);

  return (
    <div
      ref={cardRef}
      className={`stock-card ${changeInfo.status}`}
      onClick={() => navigate(`/stock?code=${stock.stockCode}`)}
    >
      <div className="card-decor" />
      <div className="card-header">
        <div className="stock-name">{stock.companyName}</div>
        <div className="stock-code">{stock.stockCode}</div>
      </div>
      <div className="price-container">
        <div className="current-price">{fmt(stock.currentPrice)}</div>
        <div className="change-rate">{changeInfo.rate}%</div>
      </div>
      <div className="card-footer">
        <div className="footer-item">시가<span>{fmt(stock.openingPrice)}</span></div>
        <div className="footer-item">고가<span>{fmt(stock.highPrice || stock.currentPrice)}</span></div>
        <div className="footer-item">저가<span>{fmt(stock.lowPrice || stock.currentPrice)}</span></div>
      </div>
    </div>
  );
}
