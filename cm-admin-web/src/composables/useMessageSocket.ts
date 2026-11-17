import { onBeforeUnmount, ref, type Ref } from 'vue';

/** 与后端 MessageFrames.DeliveryFrame 对齐的下行帧。 */
export interface ChatFrame {
  type: 'CHAT';
  fromUserId: string;
  fromName: string;
  toUserId: string;
  text: string;
  timestamp: string;
}

export interface SystemFrame {
  type: 'SYSTEM';
  text: string;
  timestamp: string;
}

interface ErrorFrame {
  type: 'ERROR';
  code: number;
  message: string;
}

type InboundFrame = ChatFrame | SystemFrame | ErrorFrame | { type: 'PONG' };

/** 页面展示用的消息行。聊天与系统广播共用一份列表，靠 kind 区分气泡样式。 */
export interface MessageItem {
  id: number;
  kind: 'chat' | 'system';
  fromUserId: string;
  fromName: string;
  text: string;
  timestamp: string;
  /** 是否是当前账号自己发出的，决定气泡左右。 */
  mine: boolean;
}

const HEARTBEAT_INTERVAL_MS = 25000;
const MAX_BACKOFF_MS = 30000;
const INITIAL_BACKOFF_MS = 1000;
/** 只在内存里留最近 200 条：消息不落库，刷新即清空，与后端口径一致。 */
const MAX_MESSAGES = 200;

/**
 * 消息中心 WebSocket。
 *
 * <p>与实时监控通道同一套纪律：只在页面存在时连接；重连必须重新握手；
 * 卸载时清掉定时器、重连与连接。心跳 25 秒一次，服务端 90 秒判定超时。
 */
export function useMessageSocket(selfUserId: () => string): {
  connected: Ref<boolean>;
  errorText: Ref<string>;
  messages: Ref<MessageItem[]>;
  connect: () => void;
  disconnect: () => void;
  sendChat: (toUserId: string, text: string) => boolean;
} {
  const connected = ref(false);
  const errorText = ref('');
  const messages = ref<MessageItem[]>([]);

  let socket: WebSocket | null = null;
  let heartbeatTimer: number | null = null;
  let reconnectTimer: number | null = null;
  let backoff = INITIAL_BACKOFF_MS;
  let closedByUs = false;
  let sequence = 0;

  function append(item: Omit<MessageItem, 'id'>): void {
    sequence += 1;
    messages.value.push({ ...item, id: sequence });
    if (messages.value.length > MAX_MESSAGES) {
      messages.value.splice(0, messages.value.length - MAX_MESSAGES);
    }
  }

  function wsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws/message`;
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
    };
    socket.onmessage = (event) => {
      let frame: InboundFrame;
      try {
        frame = JSON.parse(event.data as string) as InboundFrame;
      } catch {
        return;
      }
      if (frame.type === 'CHAT') {
        append({
          kind: 'chat',
          fromUserId: frame.fromUserId,
          fromName: frame.fromName || '未知用户',
          text: frame.text,
          timestamp: frame.timestamp,
          mine: frame.fromUserId === selfUserId(),
        });
      } else if (frame.type === 'SYSTEM') {
        append({
          kind: 'system',
          fromUserId: '',
          fromName: '系统广播',
          text: frame.text,
          timestamp: frame.timestamp,
          mine: false,
        });
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
      // 具体原因交给 onclose 统一处理，界面不显示假在线。
      connected.value = false;
    };
  }

  function disconnect(): void {
    closedByUs = true;
    stopHeartbeat();
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (socket) {
      socket.onclose = null;
      socket.close();
      socket = null;
    }
    connected.value = false;
  }

  /** 发送成功时本地先落一条：服务端只投递给接收方，不会把消息回推给发送人。 */
  function sendChat(toUserId: string, text: string): boolean {
    const content = text.trim();
    if (!content || !toUserId || !socket || socket.readyState !== WebSocket.OPEN) {
      return false;
    }
    socket.send(JSON.stringify({ type: 'CHAT', toUserId, text: content }));
    append({
      kind: 'chat',
      fromUserId: selfUserId(),
      fromName: '我',
      text: content,
      timestamp: new Date().toLocaleString('zh-CN', { hour12: false }),
      mine: true,
    });
    return true;
  }

  onBeforeUnmount(disconnect);

  return { connected, errorText, messages, connect, disconnect, sendChat };
}
