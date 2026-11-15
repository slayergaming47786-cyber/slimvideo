import React from 'react';
import { VideoMetadata } from '../types';
import { formatBytes, formatDuration } from '../services/videoCompressor';
import { Clock, Monitor, RefreshCw, HardDrive } from 'lucide-react';

interface VideoInfoCardProps {
  metadata: VideoMetadata;
  onChangeVideo: () => void;
}

export const VideoInfoCard: React.FC<VideoInfoCardProps> = ({ metadata, onChangeVideo }) => {
  const isPortrait = metadata.height > metadata.width;

  return (
    <div className="w-full bg-slate-900/60 border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-xl relative overflow-hidden">
      <div className="flex flex-col sm:flex-row gap-4 items-center">
        {/* Thumbnail with overlay duration */}
        <div className="relative w-full sm:w-40 h-32 sm:h-28 rounded-xl overflow-hidden bg-slate-950 flex-shrink-0 border border-slate-800">
          {metadata.thumbnailUrl ? (
            <img
              src={metadata.thumbnailUrl}
              alt="Video preview"
              className="w-full h-full object-cover"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-slate-600 text-xs font-mono">
              No Preview
            </div>
          )}
          <div className="absolute bottom-1.5 right-1.5 px-1.5 py-0.5 rounded bg-black/80 backdrop-blur-sm text-[11px] font-mono font-medium text-white flex items-center gap-1">
            <Clock className="w-3 h-3 text-cyan-400" />
            <span>{formatDuration(metadata.duration)}</span>
          </div>
        </div>

        {/* Info Content */}
        <div className="flex-1 min-w-0 w-full">
          <div className="flex items-start justify-between gap-2 mb-2">
            <h3 className="font-semibold text-slate-100 text-sm sm:text-base truncate" title={metadata.name}>
              {metadata.name}
            </h3>
            <button
              onClick={onChangeVideo}
              className="text-slate-400 hover:text-slate-200 text-xs px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-slate-800 transition-colors flex items-center gap-1 flex-shrink-0"
              title="Select a different video"
            >
              <RefreshCw className="w-3 h-3" />
              <span>Change</span>
            </button>
          </div>

          {/* Stats Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 text-xs">
            {/* Size */}
            <div className="p-2 rounded-lg bg-slate-950/40 border border-slate-800/60">
              <span className="text-[10px] text-slate-400 block mb-0.5 flex items-center gap-1">
                <HardDrive className="w-3 h-3 text-indigo-400" /> Original Size
              </span>
              <span className="font-bold text-slate-100 text-sm">{formatBytes(metadata.size)}</span>
            </div>

            {/* Resolution */}
            <div className="p-2 rounded-lg bg-slate-950/40 border border-slate-800/60">
              <span className="text-[10px] text-slate-400 block mb-0.5 flex items-center gap-1">
                <Monitor className="w-3 h-3 text-cyan-400" /> Resolution
              </span>
              <span className="font-medium text-slate-200">
                {metadata.width}×{metadata.height} {isPortrait ? '(Vertical)' : ''}
              </span>
            </div>

            {/* Framerate & Codec */}
            <div className="p-2 rounded-lg bg-slate-950/40 border border-slate-800/60 col-span-2 sm:col-span-1">
              <span className="text-[10px] text-slate-400 block mb-0.5">FPS & Codec</span>
              <span className="font-medium text-slate-200 font-mono text-[11px]">
                {metadata.fps} fps • {metadata.codec.toUpperCase()}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
