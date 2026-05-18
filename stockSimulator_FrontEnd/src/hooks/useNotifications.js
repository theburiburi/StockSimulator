import { useState, useEffect, useCallback } from 'react';

/**
 * 개인 알림 상태 관리 훅
 * - REST API로 초기 미읽음 알림 조회
 * - WebSocket push로 새 알림 추가
 * - 토스트 표시용 queue 관리
 */
export function useNotifications(memberId, subscribePersonal) {
  const [notifications, setNotifications] = useState([]);
  const [toasts, setToasts] = useState([]);

  // 초기 미읽음 알림 fetch
  useEffect(() => {
    if (!memberId) return;
    fetch(`/api/notifications/unread?memberId=${memberId}`)
      .then(r => r.json())
      .then(data => setNotifications(data))
      .catch(() => {});
  }, [memberId]);

  // WebSocket 개인 채널 구독
  useEffect(() => {
    if (!memberId || !subscribePersonal) return;
    const sub = subscribePersonal(
      `/user/${memberId}/queue/notifications`,
      (notif) => {
        setNotifications(prev => [notif, ...prev]);
        addToast(notif);
      }
    );
    return () => sub && sub.unsubscribe();
  }, [memberId, subscribePersonal]);

  const addToast = (notif) => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, ...notif }]);
    setTimeout(() => removeToast(id), 4000);
  };

  const removeToast = useCallback((id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  const unreadCount = notifications.filter(n => !n.read).length;

  const markAllRead = useCallback(async () => {
    if (!memberId) return;
    await fetch(`/api/notifications/read-all?memberId=${memberId}`, { method: 'POST' });
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  }, [memberId]);

  return { notifications, toasts, unreadCount, markAllRead, removeToast };
}
