import {
  Input,
  Output,
  ALL_FORMATS,
  BlobSource,
  BufferTarget,
  Mp4OutputFormat,
  WebMOutputFormat,
  Conversion,
  Quality
} from 'mediabunny';
import { VideoMetadata, CompressionSettings, CompressionProgress, CompressionResult } from '../types';

/**
 * Format bytes to human readable format (MB, KB, GB)
 */
export function formatBytes(bytes: number, decimals: number = 1): string {
  if (bytes <= 0) return '0 B';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

/**
 * Format seconds to MM:SS or HH:MM:SS
 */
export function formatDuration(seconds: number): string {
  if (isNaN(seconds) || seconds < 0) return '00:00';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

/**
 * Ensure dimension is an even number (required by H.264/AVC encoders)
 */
export function makeEven(num: number): number {
  const rounded = Math.round(num);
  return rounded % 2 === 0 ? rounded : rounded - 1;
}

/**
 * Calculate scaled dimensions while preserving aspect ratio
 */
export function calculateScaledDimensions(
  origW: number,
  origH: number,
  maxDimension: number
): { width: number; height: number } {
  const isPortrait = origH > origW;
  const longSide = isPortrait ? origH : origW;

  // Don't upscale if video is already smaller
  if (longSide <= maxDimension) {
    return {
      width: makeEven(origW),
      height: makeEven(origH)
    };
  }

  const scale = maxDimension / longSide;
  return {
    width: makeEven(origW * scale),
    height: makeEven(origH * scale)
  };
}

/**
 * Extract a thumbnail from a video file using HTML5 Video + Canvas
 */
export async function extractVideoThumbnail(file: File): Promise<string> {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.muted = true;
    video.playsInline = true;

    const url = URL.createObjectURL(file);
    video.src = url;

    video.onloadeddata = () => {
      // Seek slightly into the video to avoid black intro frames
      const seekTime = Math.min(1.0, video.duration / 4);
      video.currentTime = seekTime;
    };

    video.onseeked = () => {
      try {
        const canvas = document.createElement('canvas');
        const maxThumbDim = 480;
        const scale = Math.min(1, maxThumbDim / Math.max(video.videoWidth, video.videoHeight));
        canvas.width = Math.round(video.videoWidth * scale);
        canvas.height = Math.round(video.videoHeight * scale);

        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const thumbUrl = canvas.toDataURL('image/jpeg', 0.85);
          URL.revokeObjectURL(url);
          resolve(thumbUrl);
          return;
        }
      } catch {
        // Fallback
      }
      URL.revokeObjectURL(url);
      resolve('');
    };

    video.onerror = () => {
      URL.revokeObjectURL(url);
      resolve('');
    };
  });
}

/**
 * Inspect video file metadata using mediabunny and browser video
 */
export async function analyzeVideo(file: File): Promise<VideoMetadata> {
  const [thumbUrl, mediabunnyMeta] = await Promise.all([
    extractVideoThumbnail(file),
    (async () => {
      try {
        const input = new Input({
          source: new BlobSource(file),
          formats: ALL_FORMATS
        });

        const duration = await input.computeDuration();
        const primaryVideo = await input.getPrimaryVideoTrack();

        let width = 1280;
        let height = 720;
        let codec = 'H.264';
        let fps = 30;

        if (primaryVideo) {
          width = (await primaryVideo.getDisplayWidth()) || 1280;
          height = (await primaryVideo.getDisplayHeight()) || 720;
          codec = primaryVideo.codec || 'avc';

          try {
            const metrics = await primaryVideo.computeFrameRateMetrics();
            if (metrics && (metrics.bestGuessFrameRate || metrics.underlyingFrameRate)) {
              fps = Math.round(metrics.bestGuessFrameRate || metrics.underlyingFrameRate || 30);
            }
          } catch {
            fps = 30;
          }
        }

        return { duration, width, height, codec, fps };
      } catch (err) {
        console.warn('Mediabunny analysis fallback:', err);
        return null;
      }
    })()
  ]);

  if (mediabunnyMeta && mediabunnyMeta.duration > 0) {
    return {
      file,
      name: file.name,
      size: file.size,
      duration: mediabunnyMeta.duration,
      width: mediabunnyMeta.width,
      height: mediabunnyMeta.height,
      codec: mediabunnyMeta.codec,
      fps: mediabunnyMeta.fps,
      thumbnailUrl: thumbUrl
    };
  }

  // HTML5 Video fallback for duration/dimensions
  return new Promise((resolve, reject) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    const url = URL.createObjectURL(file);
    video.src = url;

    video.onloadedmetadata = () => {
      const duration = video.duration || 1;
      const width = video.videoWidth || 1280;
      const height = video.videoHeight || 720;
      URL.revokeObjectURL(url);

      resolve({
        file,
        name: file.name,
        size: file.size,
        duration,
        width,
        height,
        codec: 'AVC / H.264',
        fps: 30,
        thumbnailUrl: thumbUrl
      });
    };

    video.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('Unable to read video file format.'));
    };
  });
}

/**
 * Calculate recommended compression settings based on user choice
 */
export function getCompressionPreset(
  meta: VideoMetadata,
  mode: CompressionSettings['mode'],
  targetSizeMB?: number
): CompressionSettings {
  const duration = Math.max(1, meta.duration);
  const isPortrait = meta.height > meta.width;

  switch (mode) {
    case 'auto': {
      // Smart Auto: Balanced space saving (~10-12 MB for 4 min video)
      // Crisp 720p / 540p at ~380 kbps total bitrate
      const audioBitrate = 60_000;
      const videoBitrate = 320_000;
      const maxDim = isPortrait ? 1280 : 1280;
      const { width, height } = calculateScaledDimensions(meta.width, meta.height, maxDim);

      return {
        mode: 'auto',
        targetWidth: width,
        targetHeight: height,
        targetFps: Math.min(30, meta.fps || 30),
        targetVideoBitrate: videoBitrate,
        targetAudioBitrate: audioBitrate,
        hardwareAcceleration: 'prefer-hardware'
      };
    }

    case 'ultra': {
      // Ultra Space Saver: Lowest possible size (~4-6 MB for 4 min video)
      // 480p resolution at 24fps cinema standard
      const audioBitrate = 44_000;
      const videoBitrate = 136_000;
      const maxDim = isPortrait ? 854 : 854;
      const { width, height } = calculateScaledDimensions(meta.width, meta.height, maxDim);

      return {
        mode: 'ultra',
        targetWidth: width,
        targetHeight: height,
        targetFps: 24,
        targetVideoBitrate: videoBitrate,
        targetAudioBitrate: audioBitrate,
        hardwareAcceleration: 'prefer-hardware'
      };
    }

    case 'balanced': {
      // Balanced: ~12-14 MB for 4 min video (720p HD @ 30fps)
      const audioBitrate = 64_000;
      const videoBitrate = 386_000;
      const maxDim = isPortrait ? 1280 : 1280;
      const { width, height } = calculateScaledDimensions(meta.width, meta.height, maxDim);

      return {
        mode: 'balanced',
        targetWidth: width,
        targetHeight: height,
        targetFps: Math.min(30, meta.fps || 30),
        targetVideoBitrate: videoBitrate,
        targetAudioBitrate: audioBitrate,
        hardwareAcceleration: 'prefer-hardware'
      };
    }

    case 'high': {
      // High Quality: ~28-32 MB for 4 min video (1080p FHD @ 30fps)
      const audioBitrate = 96_000;
      const videoBitrate = 1_000_000;
      const maxDim = isPortrait ? 1920 : 1920;
      const { width, height } = calculateScaledDimensions(meta.width, meta.height, maxDim);

      return {
        mode: 'high',
        targetWidth: width,
        targetHeight: height,
        targetFps: Math.min(30, meta.fps || 30),
        targetVideoBitrate: videoBitrate,
        targetAudioBitrate: audioBitrate,
        hardwareAcceleration: 'prefer-hardware'
      };
    }

    case 'target-size': {
      const mb = Math.max(1, targetSizeMB || 10);
      const totalBits = mb * 1024 * 1024 * 8;
      const totalBitrate = Math.round(totalBits / duration);
      const audioBitrate = totalBitrate > 350_000 ? 64_000 : 48_000;
      const videoBitrate = Math.max(80_000, totalBitrate - audioBitrate);

      // Adaptive resolution based on bitrate
      let maxDim = 854; // 480p
      let fps = 24;

      if (videoBitrate >= 600_000) {
        maxDim = 1280; // 720p
        fps = 30;
      } else if (videoBitrate >= 280_000) {
        maxDim = 960; // 540p
        fps = 24;
      }

      const { width, height } = calculateScaledDimensions(meta.width, meta.height, maxDim);

      return {
        mode: 'target-size',
        targetWidth: width,
        targetHeight: height,
        targetFps: fps,
        targetVideoBitrate: videoBitrate,
        targetAudioBitrate: audioBitrate,
        targetSizeMB: mb,
        hardwareAcceleration: 'prefer-hardware'
      };
    }

    case 'custom':
    default: {
      const { width, height } = calculateScaledDimensions(meta.width, meta.height, 1280);
      return {
        mode: 'custom',
        targetWidth: width,
        targetHeight: height,
        targetFps: 30,
        targetVideoBitrate: 450_000,
        targetAudioBitrate: 64_000,
        hardwareAcceleration: 'prefer-hardware'
      };
    }
  }
}

/**
 * Estimate final output size in bytes based on bitrate and duration
 */
export function estimateOutputSize(settings: CompressionSettings, duration: number): number {
  const totalBps = settings.targetVideoBitrate + settings.targetAudioBitrate;
  // Add 2% container overhead
  const totalBytes = ((totalBps * duration) / 8) * 1.02;
  return Math.round(totalBytes);
}

/**
 * Detect whether the browser has hardware video encoding active
 */
export async function detectHardwareEncoding(): Promise<boolean> {
  if (typeof VideoEncoder === 'undefined') return false;
  try {
    const res = await VideoEncoder.isConfigSupported({
      codec: 'avc1.42001e',
      width: 1280,
      height: 720,
      bitrate: 1_000_000,
      framerate: 30,
      hardwareAcceleration: 'prefer-hardware'
    });
    return !!(res.supported && res.config?.hardwareAcceleration === 'prefer-hardware');
  } catch {
    return false;
  }
}

/**
 * Controller to allow user to abort/cancel compression
 */
export class CompressionController {
  private _aborted = false;
  private _abortListeners: Array<() => void> = [];

  abort() {
    this._aborted = true;
    this._abortListeners.forEach((fn) => fn());
  }

  get isAborted() {
    return this._aborted;
  }

  onAbort(fn: () => void) {
    this._abortListeners.push(fn);
  }
}

/**
 * Run 100% On-Device Compression using WebCodecs & mediabunny
 */
export async function compressVideo(
  meta: VideoMetadata,
  settings: CompressionSettings,
  onProgress: (p: CompressionProgress) => void,
  controller?: CompressionController
): Promise<CompressionResult> {
  const startTime = performance.now();
  let lastProgress = 0;

  // 1. Setup Input Source
  const input = new Input({
    source: new BlobSource(meta.file),
    formats: ALL_FORMATS
  });

  // Check if input has audio track
  const audioTracks = await input.getAudioTracks();
  const hasAudio = audioTracks.length > 0;

  const videoQuality = new Quality({ bitrate: settings.targetVideoBitrate });
  const audioQuality = new Quality({ bitrate: settings.targetAudioBitrate });

  let target = new BufferTarget();
  let conversion: Conversion | null = null;
  let isWebm = false;
  let usedHardware = false;

  const targetWidth = makeEven(settings.targetWidth);
  const targetHeight = makeEven(settings.targetHeight);

  // Helper to attempt Conversion.init with specific format & hardware preference
  const tryInit = async (
    format: Mp4OutputFormat | WebMOutputFormat,
    hwPreference: 'prefer-hardware' | 'no-preference',
    withAudioQuality: boolean
  ): Promise<Conversion | null> => {
    try {
      target = new BufferTarget();
      const output = new Output({ format, target });
      const conv = await Conversion.init({
        input,
        output,
        tracks: 'primary', // Only primary video & audio (ignores sensor metadata)
        video: {
          width: targetWidth,
          height: targetHeight,
          fit: 'contain',
          frameRate: settings.targetFps,
          quality: videoQuality,
          hardwareAcceleration: hwPreference,
          forceTranscode: true
        },
        audio: hasAudio
          ? (withAudioQuality ? { quality: audioQuality } : {})
          : undefined
      });

      if (conv.isValid) {
        return conv;
      }
      return null;
    } catch (e) {
      return null;
    }
  };

  // 1. First Priority: Hardware GPU Acceleration (iGPU / dGPU / Phone MediaCodec)
  // Try MP4 with prefer-hardware + audio transcoding
  conversion = await tryInit(new Mp4OutputFormat(), 'prefer-hardware', true);
  if (conversion) {
    usedHardware = true;
  }

  // 2. Try MP4 with prefer-hardware + direct audio stream copy
  if (!conversion && hasAudio) {
    conversion = await tryInit(new Mp4OutputFormat(), 'prefer-hardware', false);
    if (conversion) {
      usedHardware = true;
    }
  }

  // 3. Try WebM with prefer-hardware
  if (!conversion) {
    conversion = await tryInit(new WebMOutputFormat(), 'prefer-hardware', true);
    if (conversion) {
      usedHardware = true;
      isWebm = true;
    }
  }

  // 4. Fallback: If hardware encoder was unavailable on this device, fall back to software (no-preference)
  if (!conversion) {
    console.warn('Hardware GPU encoding unavailable, falling back to software compatibility mode...');
    conversion = await tryInit(new Mp4OutputFormat(), 'no-preference', true);
  }

  if (!conversion && hasAudio) {
    conversion = await tryInit(new Mp4OutputFormat(), 'no-preference', false);
  }

  if (!conversion) {
    conversion = await tryInit(new WebMOutputFormat(), 'no-preference', true);
    if (conversion) {
      isWebm = true;
    }
  }

  // 5. Ultimate fallback: Video-only if audio track is blocked
  if (!conversion && hasAudio) {
    try {
      target = new BufferTarget();
      const output = new Output({ format: new Mp4OutputFormat(), target });
      const conv = await Conversion.init({
        input,
        output,
        tracks: 'primary',
        video: {
          width: targetWidth,
          height: targetHeight,
          fit: 'contain',
          frameRate: settings.targetFps,
          quality: videoQuality,
          hardwareAcceleration: 'no-preference',
          forceTranscode: true
        },
        audio: { discard: true }
      });
      if (conv.isValid) {
        conversion = conv;
      }
    } catch {}
  }

  if (!conversion || !conversion.isValid) {
    throw new Error('Unable to start video encoding on this device. Please check your browser hardware acceleration settings.');
  }

  if (controller?.isAborted) {
    throw new Error('Compression was cancelled by user');
  }

  // Handle progress updates
  conversion.onProgress = (progress: number, processedTime: number) => {
    if (controller?.isAborted) return;

    const percent = Math.min(100, Math.max(0, Math.round(progress * 100)));
    const elapsedSeconds = (performance.now() - startTime) / 1000;
    
    // Estimate speed and ETA
    let speed = 1.0;
    let etaSeconds = 0;
    if (processedTime > 0 && elapsedSeconds > 0) {
      speed = parseFloat((processedTime / elapsedSeconds).toFixed(1));
      const remainingTime = Math.max(0, meta.duration - processedTime);
      etaSeconds = Math.round(remainingTime / Math.max(0.1, speed));
    }

    lastProgress = percent;
    onProgress({
      percent,
      processedTime,
      totalTime: meta.duration,
      currentSpeed: speed,
      etaSeconds
    });
  };

  // 5. Execute conversion
  const executePromise = conversion.execute();

  // Handle abort signal
  if (controller) {
    controller.onAbort(() => {
      // Abort gracefully
      try {
        input.dispose();
      } catch {}
    });
  }

  await executePromise;

  if (controller?.isAborted) {
    throw new Error('Compression was cancelled by user');
  }

  // Final progress update
  onProgress({
    percent: 100,
    processedTime: meta.duration,
    totalTime: meta.duration,
    currentSpeed: 1,
    etaSeconds: 0
  });

  // Extract result buffer
  const buffer = target.buffer;
  if (!buffer) {
    throw new Error('Failed to generate output video buffer');
  }

  const mimeType = isWebm ? 'video/webm' : 'video/mp4';
  const outputBlob = new Blob([buffer], { type: mimeType });
  const outputUrl = URL.createObjectURL(outputBlob);
  const savedBytes = Math.max(0, meta.size - outputBlob.size);
  const savedPercent = parseFloat(((savedBytes / meta.size) * 100).toFixed(1));

  return {
    blob: outputBlob,
    url: outputUrl,
    size: outputBlob.size,
    savedBytes,
    savedPercent,
    duration: meta.duration,
    width: targetWidth,
    height: targetHeight,
    format: isWebm ? 'webm' : 'mp4',
    usedHardware
  };
}

/**
 * Generate a dynamic high-definition test video directly in the browser
 * Allows instant testing of the compression engine without needing a local file
 */
export async function createDemoVideo(): Promise<File> {
  const canvas = document.createElement('canvas');
  canvas.width = 1280;
  canvas.height = 720;
  const ctx = canvas.getContext('2d')!;

  // Create subtle audio tone
  const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
  let audioCtx: AudioContext | null = null;
  let dest: MediaStreamAudioDestinationNode | null = null;
  let osc: OscillatorNode | null = null;

  try {
    audioCtx = new AudioCtxClass();
    dest = audioCtx.createMediaStreamDestination();
    osc = audioCtx.createOscillator();
    osc.frequency.setValueAtTime(220, audioCtx.currentTime);
    osc.connect(dest);
    osc.start();
  } catch {}

  const canvasStream = canvas.captureStream(30);
  const streamTracks: MediaStreamTrack[] = [...canvasStream.getVideoTracks()];
  if (dest) {
    streamTracks.push(...dest.stream.getAudioTracks());
  }

  const combinedStream = new MediaStream(streamTracks);
  const mimeType = MediaRecorder.isTypeSupported('video/mp4')
    ? 'video/mp4'
    : MediaRecorder.isTypeSupported('video/webm;codecs=vp9')
    ? 'video/webm;codecs=vp9'
    : 'video/webm';

  const recorder = new MediaRecorder(combinedStream, {
    mimeType,
    videoBitsPerSecond: 12_000_000 // 12 Mbps uncompressed high-bitrate video
  });

  const chunks: Blob[] = [];
  recorder.ondataavailable = (e) => {
    if (e.data.size > 0) chunks.push(e.data);
  };

  return new Promise((resolve, reject) => {
    recorder.onstop = () => {
      try {
        if (osc) osc.stop();
        if (audioCtx) audioCtx.close();
      } catch {}

      const ext = mimeType.includes('mp4') ? 'mp4' : 'webm';
      const blob = new Blob(chunks, { type: mimeType });
      const file = new File([blob], `sample_camera_recording.${ext}`, {
        type: mimeType
      });
      resolve(file);
    };

    recorder.onerror = (e) => reject(e);

    recorder.start(100);

    let frame = 0;
    const totalFrames = 90; // 3 seconds at 30 fps
    const interval = setInterval(() => {
      frame++;

      // Animated high detail graphics
      const t = frame / totalFrames;
      const grad = ctx.createLinearGradient(0, 0, 1280, 720);
      grad.addColorStop(0, `hsl(${Math.round(t * 360)}, 70%, 35%)`);
      grad.addColorStop(1, `hsl(${Math.round((t * 360 + 140) % 360)}, 70%, 15%)`);
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, 1280, 720);

      // Particle dots
      for (let i = 0; i < 20; i++) {
        const x = (Math.sin(frame * 0.1 + i) * 0.5 + 0.5) * 1280;
        const y = (Math.cos(frame * 0.08 + i * 2) * 0.5 + 0.5) * 720;
        ctx.beginPath();
        ctx.arc(x, y, 12 + (i % 6) * 4, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 255, 255, ${0.15 + (i % 5) * 0.1})`;
        ctx.fill();
      }

      // Title & Text
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 42px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('SlimVideo Sample Clip', 640, 320);

      ctx.fillStyle = '#38bdf8';
      ctx.font = '22px sans-serif';
      ctx.fillText('1280×720 • High Bitrate (~12 Mbps)', 640, 370);

      ctx.fillStyle = '#94a3b8';
      ctx.font = '16px monospace';
      ctx.fillText(`Frame ${frame} / ${totalFrames}`, 640, 420);

      if (frame >= totalFrames) {
        clearInterval(interval);
        setTimeout(() => recorder.stop(), 100);
      }
    }, 1000 / 30);
  });
}
