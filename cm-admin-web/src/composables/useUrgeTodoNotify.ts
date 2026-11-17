import { onBeforeUnmount, ref } from 'vue';
import { fetchUrgeTodoNotifications, type UrgeTodoRow } from '@/api/gov';

const POLL_INTERVAL_MS = 10000;

/**
 * 809 督办轮询。旧系统「报警督办」表格 10 秒刷新一次，这里沿用同一节奏：
 * 首次调用只建立基线，之后发现未应答的新督办就交给页面自动打开详情窗口。
 */
export function useUrgeTodoNotify(onNewTodo: (row: UrgeTodoRow) => void) {
  const pending = ref<UrgeTodoRow[]>([]);

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
      const rows = await fetchUrgeTodoNotifications(sinceId, 60);
      const baseline = !baselineDone;
      baselineDone = true;
      if (rows.length === 0) {
        return;
      }
      sinceId = rows[rows.length - 1].id;
      if (baseline) {
        return;
      }
      pending.value = rows;
      // 多个督办同时到达时，与旧系统一致逐个提示；新系统只自动打开最新一条，其余留在页签列表。
      onNewTodo(rows[rows.length - 1]);
    } catch {
      // 静默重试。
    } finally {
      polling = false;
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

  onBeforeUnmount(stop);

  return { pending, start, stop };
}
