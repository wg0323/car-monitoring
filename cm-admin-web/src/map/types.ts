/** 地图上要画的一辆车。经纬度已是平台口径（WGS-84 度），GCJ-02 转换由适配层负责。 */
export interface MapVehicle {
  id: string;
  plateNo: string;
  online: boolean;
  latitude: number;
  longitude: number;
  course: number;
}

/** 绘制选项。fit=true 时在标记真正落地后自适应视野，用于首屏与翻页。 */
export interface DrawOptions {
  fit?: boolean;
}

/**
 * 地图引擎适配接口。业务只依赖这五个方法，切换引擎不改页面。
 * 车标与轨迹由地图 SDK 自绘，不引入第三方 UI 组件库（14.3）。
 */
export interface MapAdapter {
  setCenter(latitude: number, longitude: number, zoom: number): void;

  /** 全量刷新车标：新增、移动与移除都在这里收敛，避免页面自己记账。 */
  drawVehicles(vehicles: MapVehicle[], options?: DrawOptions): void;

  /** 视野自适应到全部车标；没有车标时退回初始中心。 */
  fitVehicles(vehicles: MapVehicle[]): void;

  /**
   * 把视野中心移到指定车辆并保持当前缩放，用于实时跟随。
   * 车标还没落地（异步坐标转换未完成）时返回 false，调用方不需要重试。
   */
  centerOnVehicle(vehicleId: string): boolean;

  /**
   * 定位到指定坐标（WGS-84 度）并打一个临时高亮点，用于报警弹窗的地图定位。
   * 坐标转换在适配层内完成，页面只传平台口径的经纬度。
   */
  focusPoint(latitude: number, longitude: number, zoom: number): void;

  resize(): void;

  destroy(): void;
}
