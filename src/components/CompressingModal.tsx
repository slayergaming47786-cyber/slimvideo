import React from 'react';
import { CompressionProgress } from '../types';
import { formatDuration } from '../services/videoCompressor';
import { Cpu, XCircle, Zap } from 'lucide-react';

interface CompressingModalProps {
  progress: CompressionProgress;
  onCancel: () => void;
}

export const CompressingModal: React.FC<CompressingModalProps> = ({ progress, onCancel }) => {
  return (
    <div className="fixed inset-0 z-50 bg-[#090d16]/90 backdrop-blur-md flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 sm:p-8 shadow-2xl relative overflow-hidden flex flex-col items-center text-center">
        {/* Ambient Top Glow */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-48 h-24 bg-indigo-500/10 blur-3xl rounded-full pointer-events-none" />

        {/* Circular Progress Badge */}
        <div className="relative w-28 h-28 sm:w-32 sm:h-32 mb-6 flex items-center justify-center">
          <svg className="w-full h-full -rotate-90" viewBox="0 0 100 100">
            {/* Background ring */}
            <circle
              cx="50"
              cy="50"
              r="42"
              className="stroke-slate-800"
              strokeWidth="7"
              fill="transparent"
            />
            {/* Animated progress ring */}
            <circle
              cx="50"
              cy="50"
              r="42"
              className="stroke-indigo-500 transition-all duration-300 ease-out"
              strokeWidth="7"
              strokeDasharray={264}
              strokeDashoffset={264 - (264 * progress.percent) / 100}
              strokeLinecap="round"
              fill="transparent"
            />
          </svg>

          {/* Percentage Center Text */}
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-2xl sm:text-3xl font-extrabold text-white font-mono tracking-tight">
              {progress.percent}%
            </span>
            <span className="text-[10px] text-slate-400 uppercase font-semibold tracking-wider">
              Compressing
            </span>
          </div>
        </div>

        {/* Main Status Text */}
        <h3 className="text-lg font-bold text-white mb-1">Shrinking Video...</h3>
        <p className="text-xs text-slate-400 mb-6">
          Processed{' '}
          <span className="text-slate-200 font-mono font-medium">
            {formatDuration(progress.processedTime)}
          </span>{' '}
          of{' '}
          <span className="text-slate-200 font-mono font-medium">
            {formatDuration(progress.totalTime)}
          </span>
        </p>

        {/* Live Metrics Grid */}
        <div className="grid grid-cols-2 gap-2.5 w-full mb-6">
          <div className="p-2.5 rounded-xl bg-slate-950/60 border border-slate-800/80 text-left">
            <span className="text-[10px] text-slate-400 block mb-0.5 flex items-center gap-1">
              <Zap className="w-3 h-3 text-cyan-400" /> Transcode Speed
            </span>
            <span className="text-xs font-bold text-white font-mono">
              {progress.currentSpeed > 0 ? `${progress.currentSpeed}x real-time` : 'Calculating...'}
            </span>
          </div>

          <div className="p-2.5 rounded-xl bg-slate-950/60 border border-slate-800/80 text-left">
            <span className="text-[10px] text-slate-400 block mb-0.5 flex items-center gap-1">
              <Cpu className="w-3 h-3 text-purple-400" /> Time Remaining
            </span>
            <span className="text-xs font-bold text-white font-mono">
              {progress.etaSeconds > 0 ? `~${progress.etaSeconds}s remaining` : 'Almost done...'}
            </span>
          </div>
        </div>

        {/* Privacy & Hardware Note */}
        <div className="flex items-center gap-2 text-[11px] text-slate-400 mb-6 bg-slate-950/30 px-3 py-1.5 rounded-lg border border-slate-800/50">
          <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
          <span>Processing locally via device hardware chip (zero upload)</span>
        </div>

        {/* Cancel Button */}
        <button
          type="button"
          onClick={onCancel}
          className="text-xs font-medium text-slate-400 hover:text-rose-400 px-4 py-2 rounded-lg bg-slate-800/50 hover:bg-rose-500/10 border border-slate-700/50 hover:border-rose-500/30 transition-colors flex items-center gap-1.5"
        >
          <XCircle className="w-3.5 h-3.5" />
          <span>Cancel Compression</span>
        </button>
      </div>
    </div>
  );
};
