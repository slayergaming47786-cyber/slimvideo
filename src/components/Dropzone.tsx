import React, { useRef, useState } from 'react';
import { UploadCloud, Film, Zap, Lock, Smartphone } from 'lucide-react';

interface DropzoneProps {
  onFileSelected: (file: File) => void;
  isLoading?: boolean;
}

export const Dropzone: React.FC<DropzoneProps> = ({ onFileSelected, isLoading }) => {
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      if (file.type.startsWith('video/') || /\.(mp4|mov|mkv|webm|avi|m4v)$/i.test(file.name)) {
        onFileSelected(file);
      }
    }
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      onFileSelected(e.target.files[0]);
    }
  };

  return (
    <div className="w-full max-w-xl mx-auto flex flex-col items-center">
      <input
        ref={fileInputRef}
        type="file"
        accept="video/*,.mp4,.mov,.mkv,.webm,.avi,.m4v"
        className="hidden"
        onChange={handleFileInputChange}
      />

      <div
        onClick={() => !isLoading && fileInputRef.current?.click()}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        className={`w-full relative cursor-pointer rounded-2xl p-6 sm:p-10 transition-all duration-300 border-2 text-center group ${
          isDragOver
            ? 'border-cyan-400 bg-cyan-950/20 scale-[1.01]'
            : 'border-dashed border-slate-700/80 hover:border-slate-500 bg-slate-900/40 hover:bg-slate-900/60'
        }`}
      >
        {/* Glow ambient effect */}
        <div className="absolute inset-0 bg-gradient-to-br from-cyan-500/5 via-indigo-500/5 to-purple-500/5 rounded-2xl pointer-events-none group-hover:opacity-100 opacity-60 transition-opacity" />

        <div className="relative z-10 flex flex-col items-center">
          {/* Animated Icon Circle */}
          <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl bg-gradient-to-br from-slate-800 to-slate-900 border border-slate-700/70 shadow-xl flex items-center justify-center mb-5 group-hover:scale-105 group-hover:border-cyan-500/50 transition-all duration-300">
            {isLoading ? (
              <div className="w-8 h-8 border-3 border-cyan-400 border-t-transparent rounded-full animate-spin" />
            ) : (
              <UploadCloud className="w-8 h-8 sm:w-10 sm:h-10 text-cyan-400 group-hover:text-cyan-300 transition-colors" />
            )}
          </div>

          <h2 className="text-lg sm:text-xl font-bold text-white mb-2 tracking-tight">
            Select or Drop Your Video
          </h2>
          <p className="text-slate-400 text-xs sm:text-sm max-w-sm mb-5 leading-relaxed">
            Choose any video from gallery or files. We'll optimize and shrink its size to save your storage.
          </p>

          {/* Action Button & Demo Button */}
          <div className="flex flex-col sm:flex-row items-center gap-3">
            <button
              type="button"
              className="px-6 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-500 to-purple-600 hover:from-cyan-400 hover:to-purple-500 text-white font-semibold text-sm shadow-lg shadow-indigo-500/25 transition-all active:scale-95 flex items-center gap-2 cursor-pointer"
            >
              <Film className="w-4 h-4" />
              <span>Choose Video</span>
            </button>

            <button
              type="button"
              onClick={async (e) => {
                e.stopPropagation();
                if (isLoading) return;
                try {
                  const { createDemoVideo } = await import('../services/videoCompressor');
                  const demoFile = await createDemoVideo();
                  onFileSelected(demoFile);
                } catch (err) {
                  console.error('Demo video error:', err);
                }
              }}
              className="px-4 py-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700/60 text-slate-300 hover:text-white font-medium text-xs transition-all active:scale-95 flex items-center gap-1.5 cursor-pointer"
            >
              <span>✨ Try Demo Video</span>
            </button>
          </div>

          {/* Supported formats */}
          <p className="text-[11px] text-slate-500 mt-4 font-mono">
            Supports MP4, MOV, MKV, WebM • Up to 4K
          </p>
        </div>
      </div>

      {/* Feature Micro-Badges */}
      <div className="grid grid-cols-3 gap-3 w-full mt-6 text-center">
        <div className="p-3 rounded-xl bg-slate-900/50 border border-slate-800/80 flex flex-col items-center gap-1">
          <Zap className="w-4 h-4 text-amber-400" />
          <span className="text-xs font-semibold text-slate-200">Hardware Fast</span>
          <span className="text-[10px] text-slate-400">GPU accelerated</span>
        </div>

        <div className="p-3 rounded-xl bg-slate-900/50 border border-slate-800/80 flex flex-col items-center gap-1">
          <Lock className="w-4 h-4 text-emerald-400" />
          <span className="text-xs font-semibold text-slate-200">100% Private</span>
          <span className="text-[10px] text-slate-400">Never leaves phone</span>
        </div>

        <div className="p-3 rounded-xl bg-slate-900/50 border border-slate-800/80 flex flex-col items-center gap-1">
          <Smartphone className="w-4 h-4 text-cyan-400" />
          <span className="text-xs font-semibold text-slate-200">Massive Saving</span>
          <span className="text-[10px] text-slate-400">700MB → ~10MB</span>
        </div>
      </div>
    </div>
  );
};
