import type { FenceAreaType } from '@/api/fence';
import type { AmapNamespace } from './amap';

/** 地图上已保存的区域。points 为 GCJ-02 点串，与后端存库口径一致。 */
export interface FenceShape {
  id: string;
  name: string;
  areaType: FenceAreaType;
  points: string;
  radius: number;
}

/** 绘制完成后的几何草稿，由页面补名称、部门与报警参数后再保存。 */
export interface FenceDraft {
  areaType: FenceAreaType;
  points: string;
  radius: number;
  centerLat: number;
  centerLng: number;
}

/** 地图当前视野，用于「设为默认中心」。 */
export interface FenceView {
  centerLat: number;
  centerLng: number;
  zoom: number;
}

/**
 * 围栏页依赖的地图能力：工具栏动作、绘制、回显与视野控制。
 * 业务只依赖这组方法，不直接碰 AMap 对象。
 */
export interface FenceMapAdapter {
  setCenter(latitude: number, longitude: number, zoom: number): void;

  getView(): FenceView;

  zoomIn(): void;

  zoomOut(): void;

  /** 退出绘制/量测模式，恢复地图拖动。 */
  enableDrag(): void;

  /** 进入距离量测模式，双击结束。 */
  startMeasure(): void;

  startDraw(type: FenceAreaType, onDone: (draft: FenceDraft) => void): void;

  cancelDraw(): void;

  /** 全量重绘已保存区域；选中项用报警色高亮，点击图元回传 id。 */
  drawShapes(shapes: FenceShape[], selectedId: string | null, onSelect: (id: string) => void): void;

  focusShape(id: string): void;

  resize(): void;

  destroy(): void;
}

interface FenceLngLat {
  getLng(): number;
  getLat(): number;
}

interface FenceBounds {
  getSouthWest(): FenceLngLat;
  getNorthEast(): FenceLngLat;
}

interface FenceOverlay {
  setOptions(options: Record<string, unknown>): void;
  on(event: string, handler: () => void): void;
}

interface FenceCircleOverlay extends FenceOverlay {
  getCenter(): FenceLngLat;
  getRadius(): number;
}

interface FencePathOverlay extends FenceOverlay {
  getPath(): FenceLngLat[];
}

interface FenceRectOverlay extends FenceOverlay {
  getBounds(): FenceBounds;
}

interface FenceMapInstance {
  add(overlay: unknown): void;
  remove(overlay: unknown): void;
  setZoomAndCenter(zoom: number, center: [number, number]): void;
  setFitView(overlay?: unknown, immediately?: boolean, avoid?: number[]): void;
  getCenter(): FenceLngLat;
  getZoom(): number;
  zoomIn(): void;
  zoomOut(): void;
  setStatus(status: Record<string, boolean>): void;
  resize(): void;
  destroy(): void;
}

interface FenceMouseTool {
  circle(options?: Record<string, unknown>): void;
  rectangle(options?: Record<string, unknown>): void;
  polygon(options?: Record<string, unknown>): void;
  rule(options?: Record<string, unknown>): void;
  close(): void;
  on(event: 'draw', handler: (event: { obj: unknown }) => void): void;
}

interface FenceAmap {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => FenceMapInstance;
  Circle: new (options: Record<string, unknown>) => FenceCircleOverlay;
  Polygon: new (options: Record<string, unknown>) => FencePathOverlay;
  MouseTool: new (map: FenceMapInstance) => FenceMouseTool;
  LngLat: new (lng: number, lat: number) => FenceLngLat;
}

const FIT_PADDING = [80, 80, 80, 80];

const DRAW_STYLE = {
  strokeWeight: 2,
  fillOpacity: 0.15,
};

/** 地图 SDK 的绘制颜色只能是真值，不能在 canvas 里解析 CSS 变量，所以从根元素令牌读。 */
function token(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function formatPoint(lng: number, lat: number): string {
  return `${lng.toFixed(6)},${lat.toFixed(6)}`;
}

function parsePoints(points: string): Array<[number, number]> {
  return points
    .split(';')
    .filter((segment) => segment.trim().length > 0)
    .map((segment) => {
      const [lng, lat] = segment.split(',').map((value) => Number.parseFloat(value.trim()));
      return [lng, lat] as [number, number];
    })
    .filter(([lng, lat]) => Number.isFinite(lng) && Number.isFinite(lat));
}

function boundingCenter(points: Array<[number, number]>): { centerLat: number; centerLng: number } {
  let minLng = points[0][0];
  let maxLng = minLng;
  let minLat = points[0][1];
  let maxLat = minLat;
  for (const [lng, lat] of points) {
    minLng = Math.min(minLng, lng);
    maxLng = Math.max(maxLng, lng);
    minLat = Math.min(minLat, lat);
    maxLat = Math.max(maxLat, lat);
  }
  return { centerLat: (minLat + maxLat) / 2, centerLng: (minLng + maxLng) / 2 };
}

/**
 * 高德围栏适配层。绘制用官方 MouseTool，回显用 Circle / Polygon，
 * 区域几何统一以 GCJ-02 点串出入，与后端存库口径一致。
 */
export function createFenceMapAdapter(
  container: HTMLElement,
  center: FenceView,
  amap: AmapNamespace,
): FenceMapAdapter {
  const sdk = amap as unknown as FenceAmap;
  const map = new sdk.Map(container, {
    zoom: center.zoom,
    center: [center.centerLng, center.centerLat],
    resizeEnable: true,
    viewMode: '2D',
  });
  const mouseTool = new sdk.MouseTool(map);
  const overlays = new Map<string, FenceOverlay>();
  let draftOverlay: FenceOverlay | null = null;
  let drawType: FenceAreaType = 'circle';
  let drawing = false;
  let onDrawDone: ((draft: FenceDraft) => void) | null = null;

  function shapeOptions(selected: boolean): Record<string, unknown> {
    const color = selected ? token('--cm-color-alarm') : token('--cm-color-brand');
    return {
      strokeColor: color,
      strokeWeight: 2,
      strokeOpacity: 0.9,
      fillColor: color,
      fillOpacity: selected ? 0.22 : 0.12,
      zIndex: selected ? 120 : 100,
    };
  }

  function draftFromOverlay(type: FenceAreaType, overlay: unknown): FenceDraft | null {
    if (type === 'circle') {
      const circle = overlay as FenceCircleOverlay;
      const position = circle.getCenter();
      const lng = position.getLng();
      const lat = position.getLat();
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
        return null;
      }
      return {
        areaType: type,
        points: formatPoint(lng, lat),
        radius: Math.max(1, Math.round(circle.getRadius())),
        centerLat: lat,
        centerLng: lng,
      };
    }
    if (type === 'rect') {
      const bounds = (overlay as FenceRectOverlay).getBounds();
      const sw = bounds.getSouthWest();
      const ne = bounds.getNorthEast();
      const corners: Array<[number, number]> = [
        [sw.getLng(), sw.getLat()],
        [ne.getLng(), sw.getLat()],
        [ne.getLng(), ne.getLat()],
        [sw.getLng(), ne.getLat()],
      ];
      return {
        areaType: type,
        points: corners.map(([lng, lat]) => formatPoint(lng, lat)).join(';'),
        radius: 0,
        ...boundingCenter(corners),
      };
    }
    const path = (overlay as FencePathOverlay).getPath().map((point) => [point.getLng(), point.getLat()] as [number, number]);
    if (path.length < 3) {
      return null;
    }
    return {
      areaType: type,
      points: path.map(([lng, lat]) => formatPoint(lng, lat)).join(';'),
      radius: 0,
      ...boundingCenter(path),
    };
  }

  function removeDraftOverlay(): void {
    if (draftOverlay) {
      map.remove(draftOverlay);
      draftOverlay = null;
    }
  }

  /** 退出绘制或量测，恢复地图拖动；不清理已保存图元。 */
  function restoreDrag(): void {
    mouseTool.close();
    map.setStatus({ dragEnable: true, scrollWheel: true, doubleClickZoom: true });
  }

  mouseTool.on('draw', (event) => {
    if (!drawing) {
      // 量测结束也会触发 draw，不能当成区域草稿。
      return;
    }
    drawing = false;
    draftOverlay = event.obj as FenceOverlay;
    restoreDrag();
    const draft = draftFromOverlay(drawType, event.obj);
    const done = onDrawDone;
    onDrawDone = null;
    if (!draft) {
      removeDraftOverlay();
      return;
    }
    done?.(draft);
  });

  return {
    setCenter(latitude, longitude, zoom) {
      map.setZoomAndCenter(zoom, [longitude, latitude]);
    },

    getView() {
      const position = map.getCenter();
      return { centerLat: position.getLat(), centerLng: position.getLng(), zoom: map.getZoom() };
    },

    zoomIn() {
      map.zoomIn();
    },

    zoomOut() {
      map.zoomOut();
    },

    enableDrag() {
      removeDraftOverlay();
      drawing = false;
      onDrawDone = null;
      restoreDrag();
    },

    startMeasure() {
      removeDraftOverlay();
      drawing = false;
      onDrawDone = null;
      mouseTool.close();
      mouseTool.rule();
    },

    startDraw(type, onDone) {
      removeDraftOverlay();
      restoreDrag();
      drawType = type;
      drawing = true;
      onDrawDone = onDone;
      const style = {
        ...DRAW_STYLE,
        strokeColor: token('--cm-color-brand-secondary'),
        fillColor: token('--cm-color-brand-secondary'),
      };
      if (type === 'circle') {
        mouseTool.circle(style);
        return;
      }
      if (type === 'rect') {
        mouseTool.rectangle(style);
        return;
      }
      mouseTool.polygon(style);
    },

    cancelDraw() {
      removeDraftOverlay();
      drawing = false;
      onDrawDone = null;
      restoreDrag();
    },

    drawShapes(shapes, selectedId, onSelect) {
      const existing = Array.from(overlays.values());
      if (existing.length > 0) {
        map.remove(existing);
      }
      overlays.clear();
      for (const shape of shapes) {
        const points = parsePoints(shape.points);
        if (points.length === 0) {
          continue;
        }
        const options = shapeOptions(shape.id === selectedId);
        let overlay: FenceOverlay;
        if (shape.areaType === 'circle') {
          overlay = new sdk.Circle({
            ...options,
            center: points[0],
            radius: shape.radius > 0 ? shape.radius : 100,
          });
        } else {
          overlay = new sdk.Polygon({ ...options, path: points });
        }
        overlay.on('click', () => onSelect(shape.id));
        map.add(overlay);
        overlays.set(shape.id, overlay);
      }
    },

    focusShape(id) {
      const overlay = overlays.get(id);
      if (overlay) {
        map.setFitView(overlay, false, FIT_PADDING);
      }
    },

    resize() {
      map.resize();
    },

    destroy() {
      removeDraftOverlay();
      mouseTool.close();
      overlays.clear();
      map.destroy();
    },
  };
}
