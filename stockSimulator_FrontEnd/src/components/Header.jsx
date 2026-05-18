import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import NotificationBell from './NotificationBell';
import PortfolioModal from './PortfolioModal';
import OrderHistoryModal from './OrderHistoryModal';

const fmt = (n) => new Intl.NumberFormat('ko-KR').format(n);

export default function Header({ connected, notifications, unreadCount, onMarkAllRead }) {
  const navigate = useNavigate();
  const [member, setMember] = useState(null);
  const [portfolio, setPortfolio] = useState([]);
  const [orders, setOrders] = useState([]);
  const [showPortfolio, setShowPortfolio] = useState(false);
  const [showOrders, setShowOrders] = useState(false);

  useEffect(() => {
    fetch('/api/auth/me')
      .then(r => r.text())
      .then(t => { if (t) setMember(JSON.parse(t)); })
      .catch(() => {});
  }, []);

  const fetchPortfolio = useCallback(() => {
    fetch('/api/auth/portfolio').then(r => r.json()).then(setPortfolio).catch(() => {});
  }, []);

  const fetchOrders = useCallback(() => {
    fetch('/api/auth/orders').then(r => r.json()).then(setOrders).catch(() => {});
  }, []);

  const openPortfolio = () => { fetchPortfolio(); setShowPortfolio(true); };
  const openOrders    = () => { fetchOrders();    setShowOrders(true); };

  const adminOpenMarket = () =>
    fetch('/api/admin/market/open', { method: 'POST' }).then(r => r.text()).then(t => alert('Admin: ' + t));
  const adminCloseMarket = () =>
    fetch('/api/admin/market/close', { method: 'POST' }).then(r => r.text()).then(t => alert('Admin: ' + t));

  return (
    <>
      <header className="dashboard-header">
        {/* Left: Logo */}
        <div className="header-content">
          <div className="logo-container">
            <div className="pulse-dot" />
            <h1 style={{ cursor: 'pointer' }} onClick={() => navigate('/')}>
              StockSimulator <span className="badge">LIVE</span>
            </h1>
          </div>
          <p className="subtitle">실시간 주가 감지 시스템</p>
          {member?.role === 'ADMIN' && (
            <div style={{ display: 'flex', gap: '10px', marginTop: '15px' }}>
              <button className="btn-admin primary"  onClick={adminOpenMarket}>  [Admin] 장 시작 (유동성 공급)</button>
              <button className="btn-admin secondary" onClick={adminCloseMarket}>[Admin] 장 마감 (주문 취소)</button>
            </div>
          )}
        </div>

        {/* Right: Status + Auth + Bell */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div className="connection-status">
              <span className={`status-indicator ${connected ? 'connected' : 'disconnected'}`} />
              <span>{connected ? '실시간 연결됨' : '연결 대기중...'}</span>
            </div>
            {member && (
              <NotificationBell
                notifications={notifications}
                unreadCount={unreadCount}
                onMarkAllRead={onMarkAllRead}
              />
            )}
          </div>

          {!member ? (
            <button
              onClick={() => window.location.href = '/oauth2/authorization/kakao'}
              style={{ padding: '10px 15px', background: '#fee500', color: '#000', fontWeight: 'bold', border: 'none', borderRadius: '8px', cursor: 'pointer' }}
            >
              카카오 로그인
            </button>
          ) : (
            <div style={{ background: 'rgba(255,255,255,.05)', padding: '10px', borderRadius: '8px' }}>
              <div>👤 <strong>{member.name}</strong> 님</div>
              <div style={{ fontSize: '.9rem', color: '#8e9bb0', marginTop: '4px' }}>💰 잔고: {fmt(member.balance)}원</div>
              <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
                <button onClick={openPortfolio} style={{ flex: 1, padding: '6px 12px', background: '#0077ff', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}>내 주식</button>
                <button onClick={openOrders}    style={{ flex: 1, padding: '6px 12px', background: '#ff3366', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}>체결 내역</button>
              </div>
            </div>
          )}
        </div>
      </header>

      <PortfolioModal open={showPortfolio} onClose={() => setShowPortfolio(false)} stocks={portfolio} />
      <OrderHistoryModal
        open={showOrders}
        onClose={() => setShowOrders(false)}
        orders={orders}
        memberId={member?.id}
        onRefresh={() => { fetchOrders(); fetchPortfolio(); }}
      />
    </>
  );
}
