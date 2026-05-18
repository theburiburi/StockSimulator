import { useEffect, useState } from 'react';

export default function NotificationToast({ toasts, onRemove }) {
  return (
    <div className="toast-container">
      {toasts.map(toast => (
        <Toast key={toast.id} toast={toast} onRemove={onRemove} />
      ))}
    </div>
  );
}

function Toast({ toast, onRemove }) {
  const [fading, setFading] = useState(false);

  const dismiss = () => {
    setFading(true);
    setTimeout(() => onRemove(toast.id), 300);
  };

  // 3.7초 후 fade out 시작
  useEffect(() => {
    const timer = setTimeout(() => setFading(true), 3700);
    const removeTimer = setTimeout(() => onRemove(toast.id), 4000);
    return () => { clearTimeout(timer); clearTimeout(removeTimer); };
  }, [toast.id, onRemove]);

  return (
    <div className={`toast ${fading ? 'fading' : ''}`}>
      <div className="toast-icon">
        {toast.type === 'TRADE_EXECUTED' ? '✅' : '❌'}
      </div>
      <div className="toast-body">
        <div className="toast-title">
          {toast.type === 'TRADE_EXECUTED' ? '체결 완료' : '주문 취소'}
        </div>
        <div className="toast-msg">{toast.message}</div>
      </div>
      <button className="toast-close" onClick={dismiss}>×</button>
    </div>
  );
}
