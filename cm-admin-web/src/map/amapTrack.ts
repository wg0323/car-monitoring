import type { AmapNamespace } from './amap';
import { wgs84ToGcj02 } from './coord';

/**
 * 轨迹地图适配层。与实时监控的 MapAdapter 分开：那边是车标增删，
 * 这里是一条折线加一个回放标记，合并接口只会让两边都用不到一半方法。
 *
 * 坐标全部在本地由 WGS-84 转 GCJ-02，不逐点调 AMap.convertFrom，
 * 否则上万点的轨迹会触发接口频控。
 */

export interface TrackMapPoint {
  latitude: number;
  longitude: number;
  label: string;
}

export interface TrackMapAdapter {
  drawTrack(points: TrackMapPoint[]): void;

  moveMarker(point: TrackMapPoint): void;

  fitTrack(): void;

  resize(): void;

  destroy(): void;
}

type AmapMapInstance = InstanceType<AmapNamespace['Map']>;

type AmapMarkerInstance = InstanceType<AmapNamespace['Marker']>;

type AmapPolylineInstance = InstanceType<AmapNamespace['Polyline']>;

const FIT_PADDING = [60, 60, 60, 60];

const TRACK_STROKE_WEIGHT = 5;

const TRACK_STROKE_OPACITY = 0.85;

/** 地图 SDK 不认 CSS 变量，运行时从设计令牌里取一次实际颜色。 */
function tokenColor(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function createAmapTrackAdapter(
  container: HTMLElement,
  center: { latitude: number; longitude: number; zoom: number },
  amap: AmapNamespace,
): TrackMapAdapter {
  const map: AmapMapInstance = new amap.Map(container, {
    zoom: center.zoom,
    center: [center.longitude, center.latitude],
    resizeEnable: true,
    viewMode: '2D',
  });
  const strokeColor = tokenColor('--cm-color-brand', 'blue');
  let polyline: AmapPolylineInstance | null = null;
  let marker: AmapMarkerInstance | null = null;

  function toPath(points: TrackMapPoint[]): number[][] {
    return points.map((point) => {
      const coordinate = wgs84ToGcj02(point.longitude, point.latitude);
      return [coordinate.longitude, coordinate.latitude];
    });
  }

  function markerContent(label: string): string {
    return `<div style="padding:1px 6px;border-radius:var(--cm-radius);background:var(--cm-color-card);`
      + `border:1px solid var(--cm-color-brand);font-size:12px;font-family:var(--cm-font-mono);`
      + `color:var(--cm-color-text);white-space:nowrap;">${escapeHtml(label)}</div>`;
  }

  function clear(): void {
    const overlays: unknown[] = [];
    if (polyline) {
      overlays.push(polyline);
      polyline = null;
    }
    if (marker) {
      overlays.push(marker);
      marker = null;
    }
    if (overlays.length > 0) {
      map.remove(overlays);
    }
  }

  return {
    drawTrack(points) {
      clear();
      if (points.length === 0) {
        return;
      }
      const path = toPath(points);
      polyline = new amap.Polyline({
        path,
        strokeColor,
        strokeWeight: TRACK_STROKE_WEIGHT,
        strokeOpacity: TRACK_STROKE_OPACITY,
        lineJoin: 'round',
      });
      marker = new amap.Marker({
        position: path[0],
        content: markerContent(points[0].label),
        anchor: 'center',
        zIndex: 200,
      });
      map.add([polyline, marker]);
      map.setFitView([polyline], false, FIT_PADDING);
    },

    moveMarker(point) {
      const coordinate = wgs84ToGcj02(point.longitude, point.latitude);
      if (!marker) {
        marker = new amap.Marker({
          position: [coordinate.longitude, coordinate.latitude],
          content: markerContent(point.label),
          anchor: 'center',
          zIndex: 200,
        });
        map.add([marker]);
        return;
      }
      marker.setPosition([coordinate.longitude, coordinate.latitude]);
      marker.setContent(markerContent(point.label));
    },

    fitTrack() {
      if (polyline) {
        map.setFitView([polyline], false, FIT_PADDING);
        return;
      }
      map.setZoomAndCenter(center.zoom, [center.longitude, center.latitude]);
    },

    resize() {
      map.resize();
    },

    destroy() {
      clear();
      map.destroy();
    },
  };
}

