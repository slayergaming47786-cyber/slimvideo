import React, { useState } from 'react';
import { CompressionResult, VideoMetadata } from '../types';
import { formatBytes } from '../services/videoCompressor';
import {
  Download,
  Share2,
  RefreshCw,
  CheckCircle,
  Play,
  TrendingDown,
  Sparkles
} from 'lucide-react';

interface ResultCardProps {
  metadata: VideoMetadata;
  result: CompressionResult;
  onReset: () => void;
}

export const ResultCard: React.FC<ResultCardProps> = ({ metadata, result, onReset }) => {
  const [isPlaying, setIsPlaying] = useState(false);

  // Generate clean filename (e.g. video_slim.mp4)
  const baseName = metadata.name.replace(/\.[^/.]+$/, '');
  const ext = result.format || 'mp4';
  const downloadFileName = `${baseName}_slim.${ext}`;

  const handleDownload = () => {
    const a = document.createElement('a');
    a.href = result.url;
    a.download = downloadFileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const handleShare = async () => {
    if (navigator.share && navigator.canShare) {
      try {
        const file = new File([result.blob], downloadFileName, { type: 'video/mp4' });
        if (navigator.canShare({ files: [file] })) {
          await navigator.share({
            title: 'Compressed Video',
            text: `Compressed with SlimVideo - saved ${formatBytes(result.savedBytes)}!`,
            files: [file]
          });
          return;
        }
      } catch (err) {
        console.warn('Share error:', err);
      }
    }
    // Fallback: download if share is unsupported or cancelled
    handleDownload();
  };

  return (
    <div className="w-full max-w-xl mx-auto flex flex-col gap-5">
      {/* Celebration Header */}
      <div className="p-5 rounded-2xl bg-gradient-to-br from-emerald-950/40 via-slate-900 to-indigo-950/40 border border-emerald-500/30 shadow-xl text-center relative overflow-hidden">
        <div className="w-12 h-12 rounded-2xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center mx-auto mb-3 text-emerald-400">
          <CheckCircle className="w-6 h-6" />
        </div>

        <h3 className="text-xl font-extrabold text-white mb-1">
          Video Compressed Successfully!
        </h3>
        <p className="text-xs text-slate-300">
          Ready to save or share. High visual clarity preserved.
        </p>

        {/* Big Savings Highlight */}
        <div className="mt-4 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 inline-flex items-center gap-2 text-emerald-300">
          <TrendingDown className="w-5 h-5 text-emerald-400" />
          <span className="text-sm sm:text-base font-extrabold">
            {result.savedBytes > 0
              ? `Saved ${formatBytes(result.savedBytes)} (${result.savedPercent}% space saved)`
              : 'Optimized with Universal Fast Codecs'}
          </span>
        </div>
      </div>

      {/* Comparison Grid */}
      <div className="grid grid-cols-2 gap-3">
        {/* Original */}
        <div className="p-3.5 rounded-xl bg-slate-900/60 border border-slate-800 text-left">
          <span className="text-[11px] font-semibold text-slate-400 block mb-1">Original Video</span>
          <span className="text-lg font-extrabold text-slate-300 block line-through decoration-rose-500/80">
            {formatBytes(metadata.size)}
          </span>
          <span className="text-xs text-slate-400 font-mono mt-0.5 block">
            {metadata.width}×{metadata.height}
          </span>
        </div>

        {/* Compressed */}
        <div className="p-3.5 rounded-xl bg-indigo-950/40 border border-indigo-500/40 text-left relative overflow-hidden">
          <span className="text-[11px] font-semibold text-indigo-300 block mb-1 flex items-center gap-1">
            <Sparkles className="w-3 h-3 text-indigo-400" /> New Slim Video
          </span>
          <span className="text-lg font-extrabold text-white block font-mono text-cyan-300">
            {formatBytes(result.size)}
          </span>
          <div className="flex items-center justify-between mt-0.5">
            <span className="text-xs text-slate-300 font-mono">
              {result.width}×{result.height} ({(result.format || 'mp4').toUpperCase()})
            </span>
            {result.usedHardware && (
              <span className="text-[10px] font-semibold text-cyan-400 bg-cyan-500/10 border border-cyan-500/20 px-1.5 py-0.5 rounded">
                ⚡ GPU
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Video Preview Player */}
      <div className="rounded-2xl overflow-hidden bg-black border border-slate-800 shadow-xl relative aspect-video flex items-center justify-center">
        <video
          src={result.url}
          controls
          playsInline
          className="w-full h-full object-contain"
          onPlay={() => setIsPlaying(true)}
          onPause={() => setIsPlaying(false)}
        />
      </div>

      {/* Action Buttons */}
      <div className="flex flex-col gap-2.5">
        {/* Download Button (Primary) */}
        <button
          type="button"
          onClick={handleDownload}
          className="w-full py-4 rounded-xl bg-gradient-to-r from-emerald-500 via-teal-500 to-cyan-500 hover:from-emerald-400 hover:to-cyan-400 text-white font-bold text-base shadow-xl shadow-emerald-500/25 transition-all active:scale-[0.99] flex items-center justify-center gap-2 cursor-pointer"
        >
          <Download className="w-5 h-5" />
          <span>Save Video ({formatBytes(result.size)})</span>
        </button>

        {/* Share Button (Mobile friendly) */}
        {typeof navigator !== 'undefined' && 'share' in navigator && (
          <button
            type="button"
            onClick={handleShare}
            className="w-full py-3 rounded-xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700 text-white font-semibold text-sm transition-all flex items-center justify-center gap-2 cursor-pointer"
          >
            <Share2 className="w-4 h-4 text-cyan-400" />
            <span>Share to WhatsApp / Apps</span>
          </button>
        )}

        {/* Compress Another Video */}
        <button
          type="button"
          onClick={onReset}
          className="w-full py-3 rounded-xl text-slate-400 hover:text-slate-200 text-xs font-medium transition-colors flex items-center justify-center gap-1.5"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Compress Another Video</span>
        </button>
      </div>
    </div>
  );
};
