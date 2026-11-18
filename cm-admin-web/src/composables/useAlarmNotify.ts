import { onBeforeUnmount, ref } from 'vue';
import { fetchAlarmNotifications, type AlarmNotifyRow } from '@/api/alarm';

const POLL_INTERVAL_MS = 5000;

/** 弹窗最多累计 10 条：再多就把右侧地图挡完了，超出丢弃最旧的。 */
const MAX_NOTICES = 10;

export interface AlarmNotice extends AlarmNotifyRow {
  /** 弹窗按「时:分:秒」显示，与旧系统 13:20:43 [车牌] 类型 的口径一致。 */
  timeText: string;
}

/**
 * 报警弹窗轮询。
 *
 * <p>三条纪律：首次调用只建立基线不弹历史；sinceId 用字符串避免 19 位雪花精度丢失；
 * 轮询失败静默重试，不把网络抖动变成页面错误提示。
 */
export function useAlarmNotify() {
  const notices = ref<AlarmNotice[]>([]);
  const ready = ref(false);

  let sinceId = '0';
  let timer: number | null = null;
  let disposed = false;
  let polling = false;
  let baselineDone = false;

  async function poll(): Promise<void> {
    if (polling || disposed) {
      return;
    }
    polling = true;
    try {
      const rows = await fetchAlarmNotifications(sinceId, 5);
      if (rows.length > 0) {
        sinceId = rows[rows.length - 1].id;
      }
      if (!baselineDone) {
        baselineDone = true;
        return;
      }
      if (rows.length > 0) {
        // 接口按主键升序返回，弹窗要最新在前。
        const latest = rows.map(toNotice).reverse();
        notices.value = [...latest, ...notices.value].slice(0, MAX_NOTICES);
      }
    } catch {
      // 静默：下一轮继续，接口权限或会话问题由页面其它请求统一暴露。
    } finally {
      polling = false;
      ready.value = true;
    }
  }

  function start(): void {
    if (timer !== null) {
      return;
    }
    disposed = false;
    void poll();
    timer = window.setInterval(() => void poll(), POLL_INTERVAL_MS);
  }

  function stop(): void {
    disposed = true;
    if (timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  function dismiss(): void {
    notices.value = [];
  }

  function removeNotice(id: string): void {
    notices.value = notices.value.filter((item) => item.id !== id);
  }

  onBeforeUnmount(stop);

  return { notices, ready, start, stop, dismiss, removeNotice };
}

function toNotice(row: AlarmNotifyRow): AlarmNotice {
  return { ...row, timeText: row.startTimeText ? row.startTimeText.slice(11, 19) : '' };
}
