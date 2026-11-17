import { onBeforeUnmount, ref } from 'vue';

/** 与后端 MonitorFrames 对齐的下行帧。 */
export interface PositionFrame {
  type: 'POSITION';
  vehicleId: string;
  simNo: string;
  latitude: number;
  longitude: number;
  speedKph: number;
  course: number;
  deviceTime: string;
  online: boolean;
}

interface ErrorFrame {
  type: 'ERROR';
  code: number;
  message: string;
}

type InboundFrame = PositionFrame | ErrorFrame | { type: 'PONG' };

const HEARTBEAT_INTERVAL_MS = 25000;
const MAX_BACKOFF_MS = 30000;
const INITIAL_BACKOFF_MS = 1000;

/**
 * 实时监控 WebSocket。
 *
 * <p>三条纪律：只在页面存在时连接；重连必须重新握手并重订阅（服务端不记忆旧授权）；
 * 卸载时把定时器、重连与连接一起清掉，不留后台心跳。
 */
export function useMonitorSocket(onPosition: (frame: PositionFrame) => void) {
  const connected = ref(false);
  const errorText = ref('');

  let socket: WebSocket | null = null;
  let heartbeatTimer: number | null = null;
  let reconnectTimer: number | null = null;
  let backoff = INITIAL_BACKOFF_MS;
  let closedByUs = false;
  let subscribed = new Set<string>();

  function wsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws/monitor`;
  }

  function send(payload: unknown): void {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(payload));
    }
  }

  function startHeartbeat(): void {
    stopHeartbeat();
    heartbeatTimer = window.setInterval(() => send({ type: 'PING' }), HEARTBEAT_INTERVAL_MS);
  }

  function stopHeartbeat(): void {
    if (heartbeatTimer !== null) {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function scheduleReconnect(): void {
    if (closedByUs || reconnectTimer !== null) {
      return;
    }
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, backoff);
    backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
  }

  function connect(): void {
    closedByUs = false;
    socket = new WebSocket(wsUrl());
    socket.onopen = () => {
      connected.value = true;
      errorText.value = '';
      backoff = INITIAL_BACKOFF_MS;
      startHeartbeat();
      if (subscribed.size > 0) {
        send({ type: 'SUBSCRIBE', vehicleIds: [...subscribed] });
      }
    };
    socket.onmessage = (event) => {
      let frame: InboundFrame;
      try {
        frame = JSON.parse(event.data as string) as InboundFrame;
      } catch {
        return;
      }
      if (frame.type === 'POSITION') {
        onPosition(frame);
      } else if (frame.type === 'ERROR') {
        errorText.value = frame.message;
      }
    };
    socket.onclose = () => {
      connected.value = false;
      stopHeartbeat();
      scheduleReconnect();
    };
    socket.onerror = () => {
      // 具体原因由 onclose 统一处理：这里只保证界面不显示假在线。
      connected.value = false;
    };
  }

  function subscribe(vehicleIds: string[]): void {
    vehicleIds.forEach((id) => subscribed.add(id));
    if (vehicleIds.length > 0) {
      send({ type: 'SUBSCRIBE', vehicleIds });
    }
  }

  function unsubscribe(vehicleIds: string[]): void {
    vehicleIds.forEach((id) => subscribed.delete(id));
    if (vehicleIds.length > 0) {
      send({ type: 'UNSUBSCRIBE', vehicleIds });
    }
  }

  function reset(): void {
    const current = [...subscribed];
    subscribed = new Set();
    if (current.length > 0) {
      send({ type: 'UNSUBSCRIBE', vehicleIds: current });
    }
  }

  function disconnect(): void {
    closedByUs = true;
    stopHeartbeat();
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    subscribed = new Set();
    if (socket) {
      socket.onclose = null;
      socket.close();
      socket = null;
    }
    connected.value = false;
  }

  onBeforeUnmount(disconnect);

  return { connected, errorText, connect, disconnect, subscribe, unsubscribe, reset };
}
