import { useEffect, useRef, useCallback, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * STOMP WebSocket 연결 훅
 * /topic/stock 채널을 구독하고 업데이트된 주식 데이터를 콜백으로 전달합니다.
 */
export function useWebSocket(onStockUpdate) {
  const clientRef = useRef(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws-stock'),
      reconnectDelay: 5000,
      debug: () => {},
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/stock', (message) => {
          try {
            const data = JSON.parse(message.body);
            onStockUpdate(data);
          } catch (e) {
            console.error('WS parse error', e);
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => client.deactivate();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const subscribePersonal = useCallback((destination, callback) => {
    if (!clientRef.current) return;
    return clientRef.current.subscribe(destination, (msg) => {
      try { callback(JSON.parse(msg.body)); } catch (e) { console.error(e); }
    });
  }, []);

  return { connected, subscribePersonal, clientRef };
}
