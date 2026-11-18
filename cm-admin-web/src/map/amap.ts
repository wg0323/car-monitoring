import type { DrawOptions, MapAdapter, MapVehicle } from './types';

/**
 * 高德 JS API 2.0 适配层。
 *
 * <p>坐标口径：平台存的是 808 的 WGS-84，高德底图是 GCJ-02，直接画会偏 50~500 米，
 * 所以每次刷新都先经过 `AMap.convertFrom(..., 'gps')` 再落点。
 * 车标样式用内联样式引用 --cm-* 令牌，避免为地图另外新增全局 CSS。
 */

export type AmapNamespace = {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AmapMap;
  Marker: new (options: Record<string, unknown>) => AmapMarker;
  Polyline: new (options: Record<string, unknown>) => AmapPolyline;
  LngLat: new (lng: number, lat: number) => AmapLngLat;
  convertFrom: (
    lnglats: AmapLngLat[],
    type: string,
    callback: (status: string, result: { locations: AmapLngLat[] }) => void,
  ) => void;
  plugin?: (names: string[], callback: () => void) => void;
};

interface AmapLngLat {
  getLng(): number;
  getLat(): number;
}

interface AmapMarker {
  setPosition(position: unknown): void;
  setContent(content: string): void;
  setTitle(title: string): void;
}

interface AmapPolyline {
  setPath(path: unknown): void;
}

interface AmapMap {
  add(overlays: unknown): void;
  remove(overlays: unknown): void;
  setCenter(center: unknown): void;
  setZoomAndCenter(zoom: number, center: unknown): void;
  setFitView(overlays?: unknown, immediately?: boolean, avoid?: number[]): void;
  resize(): void;
  destroy(): void;
}

declare global {
  interface Window {
    AMap?: AmapNamespace;
    _AMapSecurityConfig?: { securityJsCode: string };
  }
}

let loader: Promise<AmapNamespace> | null = null;

/** 已加载的插件，用于同一次会话内从监控页切到围栏页时补载 MouseTool。 */
const loadedPlugins = new Set<string>();

/**
 * 同一页面只加载一次 SDK；key 与安全密钥都由服务端下发，不进构建产物。
 * plugins 首次随脚本加载，二次调用时若缺插件走 AMap.plugin 动态补载。
 */
export function loadAmap(key: string, securityCode: string, plugins: string[] = []): Promise<AmapNamespace> {
  if (window.AMap) {
    return loadPlugins(window.AMap, plugins);
  }
  if (loader) {
    return loader.then((amap) => loadPlugins(amap, plugins));
  }
  if (securityCode) {
    window._AMapSecurityConfig = { securityJsCode: securityCode };
  }
  const pluginQuery = plugins.length > 0 ? `&plugin=${plugins.map(encodeURIComponent).join(',')}` : '';
  loader = new Promise<AmapNamespace>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}${pluginQuery}`;
    script.async = true;
    script.onload = () => {
      if (window.AMap) {
        plugins.forEach((name) => loadedPlugins.add(name));
        resolve(window.AMap);
      } else {
        reject(new Error('地图 SDK 未初始化'));
      }
    };
    script.onerror = () => reject(new Error('地图脚本加载失败，请检查 key 与网络'));
    document.head.appendChild(script);
  });
  return loader;
}

function loadPlugins(amap: AmapNamespace, plugins: string[]): Promise<AmapNamespace> {
  const missing = plugins.filter((name) => !loadedPlugins.has(name));
  if (missing.length === 0 || typeof amap.plugin !== 'function') {
    return Promise.resolve(amap);
  }
  return new Promise<AmapNamespace>((resolve, reject) => {
    amap.plugin?.(missing, () => {
      missing.forEach((name) => loadedPlugins.add(name));
      resolve(amap);
    });
    window.setTimeout(() => {
      const remain = plugins.filter((name) => !loadedPlugins.has(name));
      if (remain.length > 0) {
        reject(new Error(`地图插件加载失败：${remain.join(', ')}`));
      }
    }, 10000);
  });
}

export function createAmapAdapter(
  container: HTMLElement,
  center: { latitude: number; longitude: number; zoom: number },
  amap: AmapNamespace,
): MapAdapter {
  const map = new amap.Map(container, {
    zoom: center.zoom,
    center: [center.longitude, center.latitude],
    resizeEnable: true,
    viewMode: '2D',
  });
  /** position 是转成 GCJ-02 后的落点，跟随视野时直接复用，不再重复调坐标转换。 */
  const markers = new Map<string, { marker: AmapMarker; vehicle: MapVehicle; position: number[] }>();

  /** 报警定位点：同一时刻只保留一个，重新定位时改坐标而不是叠加车标。 */
  let focusMarker: AmapMarker | null = null;

  function focusContent(): string {
    return `<div style="width:14px;height:14px;border-radius:50%;background:var(--cm-color-alarm);`
      + `border:2px solid var(--cm-color-card);box-shadow:0 0 0 3px rgba(220,38,38,0.35);"></div>`;
  }

  function markerContent(vehicle: MapVehicle): string {
    const color = vehicle.online ? 'var(--cm-color-online)' : 'var(--cm-color-neutral)';
    return `<div style="display:flex;align-items:center;gap:4px;padding:1px 6px;border-radius:var(--cm-radius);`
      + `background:var(--cm-color-card);border:1px solid ${color};font-size:12px;`
      + `font-family:var(--cm-font-mono);color:var(--cm-color-text);white-space:nowrap;">`
      + `<span style="width:6px;height:6px;border-radius:50%;background:${color};"></span>`
      + `${escapeHtml(vehicle.plateNo)}</div>`;
  }

  /** 车牌来自数据库，拼进 HTML 前必须转义，否则一条带尖括号的车牌就是脚本注入。 */
  function escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  return {
    setCenter(latitude, longitude, zoom) {
      map.setZoomAndCenter(zoom, [longitude, latitude]);
    },

    drawVehicles(vehicles, options?: DrawOptions) {
      const alive = new Set(vehicles.map((vehicle) => vehicle.id));
      const removed: AmapMarker[] = [];
      markers.forEach((entry, id) => {
        if (!alive.has(id)) {
          removed.push(entry.marker);
          markers.delete(id);
        }
      });
      if (removed.length > 0) {
        map.remove(removed);
      }
      if (vehicles.length === 0) {
        return;
      }
      const positions = vehicles.map((vehicle) => new amap.LngLat(vehicle.longitude, vehicle.latitude));
      amap.convertFrom(positions, 'gps', (status, result) => {
        if (status !== 'complete' || !result || !result.locations) {
          return;
        }
        const added: AmapMarker[] = [];
        vehicles.forEach((vehicle, index) => {
          const location = result.locations[index];
          if (!location) {
            return;
          }
          const position = [location.getLng(), location.getLat()];
          const existing = markers.get(vehicle.id);
          if (existing) {
            existing.marker.setPosition(position);
            existing.marker.setContent(markerContent(vehicle));
            existing.marker.setTitle(vehicle.plateNo);
            existing.position = position;
            return;
          }
          const marker = new amap.Marker({
            position,
            content: markerContent(vehicle),
            title: vehicle.plateNo,
            anchor: 'center',
            zIndex: vehicle.online ? 120 : 100,
          });
          markers.set(vehicle.id, { marker, vehicle, position });
          added.push(marker);
        });
        if (added.length > 0) {
          map.add(added);
          // 车标是在异步坐标转换后落地的，自适应视野只能放在这里；
          // 否则页面调 fitVehicles 时 markers 还没建好，视野会停在初始中心。
          if (options?.fit) {
            map.setFitView(added, false, [60, 60, 60, 60]);
          }
        }
      });
    },

    centerOnVehicle(vehicleId) {
      const entry = markers.get(vehicleId);
      if (!entry) {
        return false;
      }
      map.setCenter(entry.position);
      return true;
    },

    fitVehicles(vehicles) {
      if (vehicles.length === 0) {
        map.setZoomAndCenter(center.zoom, [center.longitude, center.latitude]);
        return;
      }
      const overlays = vehicles
        .map((vehicle) => markers.get(vehicle.id)?.marker)
        .filter((marker): marker is AmapMarker => marker !== undefined);
      if (overlays.length > 0) {
        map.setFitView(overlays, false, [60, 60, 60, 60]);
      }
    },

    focusPoint(latitude, longitude, zoom) {
      const target = new amap.LngLat(longitude, latitude);
      amap.convertFrom([target], 'gps', (status, result) => {
        if (status !== 'complete' || !result || !result.locations || !result.locations[0]) {
          return;
        }
        const location = result.locations[0];
        const position = [location.getLng(), location.getLat()];
        if (focusMarker) {
          focusMarker.setPosition(position);
        } else {
          focusMarker = new amap.Marker({
            position,
            content: focusContent(),
            title: '报警点',
            anchor: 'center',
            zIndex: 200,
          });
          map.add(focusMarker);
        }
        map.setZoomAndCenter(zoom, position);
      });
    },

    resize() {
      map.resize();
    },

    destroy() {
      markers.clear();
      focusMarker = null;
      map.destroy();
    },
  };
}
