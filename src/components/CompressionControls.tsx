import React, { useState, useEffect } from 'react';
import { VideoMetadata, CompressionSettings, PresetMode } from '../types';
import {
  getCompressionPreset,
  estimateOutputSize,
  formatBytes
} from '../services/videoCompressor';
import {
  Sparkles,
  Zap,
  Sliders,
  Target,
  CheckCircle2,
  TrendingDown,
  Layers,
  ArrowRight
} from 'lucide-react';

interface CompressionControlsProps {
  metadata: VideoMetadata;
  onStartCompress: (settings: CompressionSettings) => void;
  isCompressing?: boolean;
}

export const CompressionControls: React.FC<CompressionControlsProps> = ({
  metadata,
  onStartCompress,
  isCompressing
}) => {
  const [selectedMode, setSelectedMode] = useState<PresetMode>('auto');
  const [targetMB, setTargetMB] = useState<number>(10);
  const [settings, setSettings] = useState<CompressionSettings>(() =>
    getCompressionPreset(metadata, 'auto')
  );

  // Recalculate when mode or targetMB changes
  useEffect(() => {
    if (selectedMode === 'custom') return;
    const newSettings = getCompressionPreset(metadata, selectedMode, targetMB);
    setSettings(newSettings);
  }, [selectedMode, targetMB, metadata]);

  const estimatedBytes = estimateOutputSize(settings, metadata.duration);
  const savedBytes = Math.max(0, metadata.size - estimatedBytes);
  const savedPercent = Math.min(99.5, Math.max(0, ((savedBytes / metadata.size) * 100))).toFixed(1);

  const presets = [
    {
      id: 'auto' as PresetMode,
      name: 'Smart Auto',
      tag: 'Recommended',
      icon: Sparkles,
      desc: 'Smartly balances maximum storage saving with crisp visuals',
      badgeColor: 'text-cyan-400 bg-cyan-500/10 border-cyan-500/30'
    },
    {
      id: 'ultra' as PresetMode,
      name: 'Ultra Saver',
      tag: 'Lowest Size',
      icon: Zap,
      desc: 'Extreme reduction for chat/sharing (e.g., ~4–6 MB for 4 min)',
      badgeColor: 'text-amber-400 bg-amber-500/10 border-amber-500/30'
    },
    {
      id: 'balanced' as PresetMode,
      name: 'Balanced',
      tag: '720p HD',
      icon: Layers,
      desc: 'Crisp 720p HD playback while cutting ~85% of file size',
      badgeColor: 'text-indigo-400 bg-indigo-500/10 border-indigo-500/30'
    },
    {
      id: 'high' as PresetMode,
      name: 'High Quality',
      tag: '1080p FHD',
      icon: CheckCircle2,
      desc: 'Preserves fine detail with moderate compression (~25–35 MB)',
      badgeColor: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30'
    },
    {
      id: 'target-size' as PresetMode,
      name: 'Target Size',
      tag: 'Exact MB',
      icon: Target,
      desc: 'Specify the exact target size you want (e.g. 10 MB)',
      badgeColor: 'text-purple-400 bg-purple-500/10 border-purple-500/30'
    },
    {
      id: 'custom' as PresetMode,
      name: 'Custom',
      tag: 'Manual',
      icon: Sliders,
      desc: 'Fine-tune resolution, video bitrate slider, and framerate',
      badgeColor: 'text-slate-300 bg-slate-700/40 border-slate-600'
    }
  ];

  return (
    <div className="w-full flex flex-col gap-5">
      {/* Mode Selection Grid */}
      <div>
        <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-2.5">
          Select Compression Mode
        </label>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
          {presets.map((preset) => {
            const isSelected = selectedMode === preset.id;
            const Icon = preset.icon;

            return (
              <button
                key={preset.id}
                type="button"
                onClick={() => setSelectedMode(preset.id)}
                className={`p-3 rounded-xl border text-left transition-all relative flex flex-col justify-between ${
                  isSelected
                    ? 'bg-gradient-to-br from-indigo-950/60 to-slate-900 border-indigo-500/80 shadow-lg shadow-indigo-500/15 ring-1 ring-indigo-500/40'
                    : 'bg-slate-900/40 hover:bg-slate-900/80 border-slate-800/80 hover:border-slate-700'
                }`}
              >
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <div
                      className={`p-1.5 rounded-lg ${
                        isSelected ? 'bg-indigo-500/20 text-indigo-300' : 'bg-slate-800 text-slate-400'
                      }`}
                    >
                      <Icon className="w-4 h-4" />
                    </div>
                    <span
                      className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${preset.badgeColor}`}
                    >
                      {preset.tag}
                    </span>
                  </div>
                  <h4 className="font-bold text-slate-100 text-xs sm:text-sm">{preset.name}</h4>
                  <p className="text-[11px] text-slate-400 line-clamp-2 mt-1 leading-snug">
                    {preset.desc}
                  </p>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Target Size Mode Controls */}
      {selectedMode === 'target-size' && (
        <div className="p-4 rounded-xl bg-slate-900/60 border border-purple-500/30 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-purple-300 flex items-center gap-1.5">
              <Target className="w-3.5 h-3.5" /> Target File Size
            </span>
            <span className="text-sm font-bold text-white font-mono bg-purple-500/20 px-2 py-0.5 rounded border border-purple-500/30">
              {targetMB} MB
            </span>
          </div>

          <input
            type="range"
            min={2}
            max={Math.min(100, Math.round(metadata.size / (1024 * 1024)))}
            step={1}
            value={targetMB}
            onChange={(e) => setTargetMB(Number(e.target.value))}
            className="w-full accent-purple-500 cursor-pointer h-2 bg-slate-800 rounded-lg appearance-none"
          />

          <div className="flex justify-between text-[11px] text-slate-400 font-mono">
            <span>2 MB (Tiny)</span>
            <span>10 MB (Recommended)</span>
            <span>25 MB (Email/Discord)</span>
            <span>{Math.min(100, Math.round(metadata.size / (1024 * 1024)))} MB</span>
          </div>
        </div>
      )}

      {/* Custom Mode Controls */}
      {selectedMode === 'custom' && (
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 flex flex-col gap-4">
          {/* Resolution Selector */}
          <div>
            <label className="text-xs font-semibold text-slate-300 block mb-1.5">Resolution</label>
            <div className="grid grid-cols-3 gap-2">
              {[
                { label: '480p (Fastest)', width: 854, height: 480 },
                { label: '720p (HD)', width: 1280, height: 720 },
                { label: '1080p (Full HD)', width: 1920, height: 1080 }
              ].map((res) => {
                const isSelected =
                  settings.targetWidth === res.width || settings.targetHeight === res.height;
                return (
                  <button
                    key={res.label}
                    type="button"
                    onClick={() => {
                      const isPortrait = metadata.height > metadata.width;
                      const w = isPortrait ? res.height : res.width;
                      const h = isPortrait ? res.width : res.height;
                      setSettings((prev) => ({ ...prev, targetWidth: w, targetHeight: h }));
                    }}
                    className={`py-2 px-2.5 rounded-lg text-xs font-medium border text-center transition-all ${
                      isSelected
                        ? 'bg-indigo-600 text-white border-indigo-500 shadow-md'
                        : 'bg-slate-800/80 text-slate-300 border-slate-700 hover:bg-slate-800'
                    }`}
                  >
                    {res.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Bitrate Slider */}
          <div>
            <div className="flex justify-between items-center mb-1">
              <label className="text-xs font-semibold text-slate-300">Video Bitrate</label>
              <span className="text-xs font-mono font-bold text-cyan-400">
                {Math.round(settings.targetVideoBitrate / 1000)} kbps
              </span>
            </div>
            <input
              type="range"
              min={100_000}
              max={2_500_000}
              step={50_000}
              value={settings.targetVideoBitrate}
              onChange={(e) =>
                setSettings((prev) => ({ ...prev, targetVideoBitrate: Number(e.target.value) }))
              }
              className="w-full accent-cyan-400 cursor-pointer h-2 bg-slate-800 rounded-lg appearance-none"
            />
            <div className="flex justify-between text-[10px] text-slate-400 font-mono mt-1">
              <span>100 kbps (Super Small)</span>
              <span>500 kbps (Balanced)</span>
              <span>2500 kbps (Sharp)</span>
            </div>
          </div>

          {/* Framerate selector */}
          <div>
            <label className="text-xs font-semibold text-slate-300 block mb-1.5">Framerate (FPS)</label>
            <div className="grid grid-cols-2 gap-2">
              {[24, 30].map((fps) => (
                <button
                  key={fps}
                  type="button"
                  onClick={() => setSettings((prev) => ({ ...prev, targetFps: fps }))}
                  className={`py-1.5 rounded-lg text-xs font-medium border ${
                    settings.targetFps === fps
                      ? 'bg-cyan-600 text-white border-cyan-500'
                      : 'bg-slate-800 text-slate-300 border-slate-700'
                  }`}
                >
                  {fps} FPS {fps === 24 ? '(Cinema Standard - Saves Space)' : '(Smooth)'}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Real-time Dynamic Estimation & Space Saved Banner */}
      <div className="p-4 rounded-xl bg-gradient-to-br from-indigo-950/40 via-slate-900/60 to-purple-950/30 border border-indigo-500/20 shadow-lg">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <span className="text-[11px] font-medium text-slate-400 block mb-0.5">
              Estimated Result
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-extrabold text-white tracking-tight font-mono">
                ~{formatBytes(estimatedBytes)}
              </span>
              <span className="text-xs text-slate-400 line-through">
                {formatBytes(metadata.size)}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1">
              Resolution: <span className="text-slate-200 font-mono">{settings.targetWidth}×{settings.targetHeight}</span> @ <span className="text-slate-200 font-mono">{settings.targetFps}fps</span>
            </p>
          </div>

          {/* Saved Badge */}
          <div className="p-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/25 flex items-center gap-2.5 sm:self-center">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/20 flex items-center justify-center text-emerald-400 flex-shrink-0">
              <TrendingDown className="w-4 h-4" />
            </div>
            <div>
              <span className="text-[10px] uppercase font-bold text-emerald-400 tracking-wider block">
                Storage Saved
              </span>
              <span className="text-sm font-extrabold text-emerald-300">
                ~{formatBytes(savedBytes)} ({savedPercent}%)
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Start Button */}
      <button
        type="button"
        disabled={isCompressing}
        onClick={() => onStartCompress(settings)}
        className="w-full py-4 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-500 to-purple-600 hover:from-cyan-400 hover:via-indigo-400 hover:to-purple-500 disabled:opacity-50 text-white font-bold text-base shadow-xl shadow-indigo-500/25 transition-all duration-200 active:scale-[0.99] flex items-center justify-center gap-2.5 cursor-pointer"
      >
        <span>Start Compression</span>
        <ArrowRight className="w-5 h-5" />
      </button>
    </div>
  );
};
