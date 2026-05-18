import { useState, useRef, useEffect } from 'react';

const formatTime = (dt) => {
  if (!dt) return '';
  const d = new Date(dt);
  return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
};

export default function NotificationBell({ notifications, unreadCount, onMarkAllRead }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleMarkAll = async () => {
    await onMarkAllRead();
    setOpen(false);
  };

  return (
    <div className="notif-bell-wrapper" ref={ref}>
      <button
        className="notif-bell-btn"
        onClick={() => setOpen(o => !o)}
        title="알림"
      >
        🔔
        {unreadCount > 0 && (
          <span className="notif-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </button>

      {open && (
        <div className="notif-dropdown">
          <div className="notif-dropdown-header">
            <h3>🔔 알림 {unreadCount > 0 && <span style={{ color: '#ff3366' }}>({unreadCount})</span>}</h3>
            {unreadCount > 0 && (
              <button className="notif-read-all-btn" onClick={handleMarkAll}>
                모두 읽음
              </button>
            )}
          </div>
          <div className="notif-list">
            {notifications.length === 0 ? (
              <div className="notif-empty">알림이 없습니다</div>
            ) : (
              notifications.map(n => (
                <div key={n.id} className={`notif-item ${!n.read ? 'unread' : ''}`}>
                  <div className="notif-item-msg">
                    {n.type === 'TRADE_EXECUTED' ? '✅ ' : '❌ '}
                    {n.message}
                  </div>
                  <div className="notif-item-time">{formatTime(n.createdAt)}</div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
