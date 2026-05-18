const fmt = (n) => new Intl.NumberFormat('ko-KR').format(n);

export default function OrderHistoryModal({ open, onClose, orders, memberId, onRefresh }) {
  if (!open) return null;

  const cancelOrder = async (orderId) => {
    if (!confirm('주문을 취소하시겠습니까?')) return;
    const res = await fetch(`/api/orders/${orderId}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ memberId }),
    });
    const text = await res.text();
    if (!res.ok) alert('취소 실패: ' + text);
    else { alert('취소 완료'); onRefresh(); }
  };

  const statusMap = {
    WAITING: { label: '대기중', color: '#f5a623' },
    PARTIAL: { label: '부분체결', color: '#00e676' },
    COMPLETED: { label: '체결완료', color: '#0077ff' },
    CANCELLED: { label: '주문취소', color: '#ff3366' },
  };

  return (
    <div className="modal-overlay open" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-content">
        <div className="modal-header">
          <h2>📜 내 주문 내역</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="portfolio-list">
          {!orders || orders.length === 0 ? (
            <div style={{ textAlign: 'center', color: '#8e9bb0', padding: '20px' }}>주문 내역이 없습니다.</div>
          ) : (
            orders.map(order => {
              const st = statusMap[order.status] || { label: order.status, color: '#8e9bb0' };
              const sideColor = order.side === 'BUY' ? '#ff3366' : '#0077ff';
              const sideText = order.side === 'BUY' ? '매수' : '매도';
              const canCancel = order.status === 'WAITING' || order.status === 'PARTIAL';

              return (
                <div key={order.id} className="portfolio-item">
                  <div>
                    <div style={{ fontWeight: 'bold', fontSize: '1.05rem', color: '#fff' }}>
                      <span style={{ color: sideColor, marginRight: '6px' }}>[{sideText}]</span>
                      {order.stockCode}
                    </div>
                    <div style={{ fontSize: '.82rem', color: '#8e9bb0', marginTop: '4px' }}>
                      주문 {fmt(order.quantity)}주 | 체결 {fmt(order.quantity - order.remainingQuantity)}주 | 잔여 {fmt(order.remainingQuantity)}주
                    </div>
                    <div style={{ fontSize: '.82rem', color: '#8e9bb0', marginTop: '2px' }}>
                      주문단가: {fmt(order.price)}원
                    </div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '8px' }}>
                    <div style={{ fontWeight: 'bold', fontSize: '1rem', color: st.color, border: `1px solid ${st.color}`, padding: '3px 10px', borderRadius: '20px' }}>
                      {st.label}
                    </div>
                    {canCancel && (
                      <button
                        onClick={() => cancelOrder(order.id)}
                        style={{ padding: '4px 10px', background: '#ff3366', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '.8rem' }}
                      >
                        주문 취소
                      </button>
                    )}
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
