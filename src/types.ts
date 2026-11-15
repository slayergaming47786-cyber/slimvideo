export type PresetMode = 'auto' | 'ultra' | 'balanced' | 'high' | 'target-size' | 'custom';

export interface VideoMetadata {
  file: File;
  name: string;
  size: number; // bytes
  duration: number; // seconds
  width: number;
  height: number;
  codec: string;
  fps: number;
  thumbnailUrl: string;
}

export interface CompressionSettings {
  mode: PresetMode;
  targetWidth: number;
  targetHeight: number;
  targetFps: number;
  targetVideoBitrate: number; // in bps
  targetAudioBitrate: number; // in bps
  targetSizeMB?: number; // for target-size mode
  hardwareAcceleration: 'prefer-hardware' | 'prefer-software' | 'no-preference';
}

export interface CompressionProgress {
  percent: number; // 0 to 100
  processedTime: number; // seconds
  totalTime: number; // seconds
  currentSpeed: number; // fps or x-realtime
  etaSeconds: number;
}

export interface CompressionResult {
  blob: Blob;
  url: string;
  size: number;
  savedBytes: number;
  savedPercent: number;
  duration: number;
  width: number;
  height: number;
  format?: 'mp4' | 'webm';
  usedHardware?: boolean;
}
