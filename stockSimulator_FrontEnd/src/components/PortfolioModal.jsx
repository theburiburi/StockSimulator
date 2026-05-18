import { useState, useEffect } from 'react';

const fmt = (n) => new Intl.NumberFormat('ko-KR').format(n);

export default function PortfolioModal({ open, onClose, stocks }) {
  if (!open) return null;

  return (
    <div className="modal-overlay open" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-content">
        <div className="modal-header">
          <h2>📊 내 보유 주식 현황</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="portfolio-list">
          {!stocks || stocks.length === 0 ? (
            <div style={{ textAlign: 'center', color: '#8e9bb0', padding: '20px' }}>보유 중인 주식이 없습니다.</div>
          ) : (
            stocks.filter(s => s.quantity > 0).map(stock => {
              const totalInvest = stock.averagePrice * stock.quantity;
              const profitRate = totalInvest > 0
                ? (((stock.currentValue || totalInvest) - totalInvest) / totalInvest) * 100
                : 0;
              const rateColor = profitRate > 0 ? '#ff3366' : profitRate < 0 ? '#0077ff' : '#8e9bb0';

              return (
                <div key={stock.stockCode} className="portfolio-item">
                  <div>
                    <div style={{ fontWeight: 'bold', fontSize: '1.1rem', color: '#fff' }}>{stock.stockCode}</div>
                    <div style={{ fontSize: '.85rem', color: '#8e9bb0', marginTop: '4px' }}>
                      보유: {fmt(stock.quantity)}주 | 평단가: {fmt(stock.averagePrice)}원
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontWeight: 'bold', color: '#fff' }}>평단: {fmt(stock.averagePrice)}원</div>
                    <div style={{ fontSize: '.9rem', fontWeight: 'bold', color: rateColor, marginTop: '4px' }}>
                      수익률: {profitRate.toFixed(2)}%
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
