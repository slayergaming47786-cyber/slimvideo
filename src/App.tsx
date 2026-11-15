import React, { useState, useRef } from 'react';
import { Header } from './components/Header';
import { Dropzone } from './components/Dropzone';
import { VideoInfoCard } from './components/VideoInfoCard';
import { CompressionControls } from './components/CompressionControls';
import { CompressingModal } from './components/CompressingModal';
import { ResultCard } from './components/ResultCard';
import { VideoMetadata, CompressionSettings, CompressionProgress, CompressionResult } from './types';
import {
  analyzeVideo,
  compressVideo,
  CompressionController
} from './services/videoCompressor';
import { AlertCircle, RefreshCw } from 'lucide-react';

export const App: React.FC = () => {
  const [status, setStatus] = useState<'idle' | 'analyzing' | 'ready' | 'compressing' | 'completed' | 'error'>('idle');
  const [metadata, setMetadata] = useState<VideoMetadata | null>(null);
  const [progress, setProgress] = useState<CompressionProgress>({
    percent: 0,
    processedTime: 0,
    totalTime: 0,
    currentSpeed: 1,
    etaSeconds: 0
  });
  const [result, setResult] = useState<CompressionResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const controllerRef = useRef<CompressionController | null>(null);

  // Handle file chosen
  const handleFileSelected = async (file: File) => {
    try {
      setErrorMessage(null);
      setStatus('analyzing');
      const meta = await analyzeVideo(file);
      setMetadata(meta);
      setStatus('ready');
    } catch (err: any) {
      console.error('Analysis error:', err);
      setErrorMessage(err.message || 'Failed to read video file. Please ensure it is a valid video format.');
      setStatus('error');
    }
  };

  // Start Compression
  const handleStartCompress = async (settings: CompressionSettings) => {
    if (!metadata) return;

    try {
      setErrorMessage(null);
      setStatus('compressing');
      setProgress({
        percent: 0,
        processedTime: 0,
        totalTime: metadata.duration,
        currentSpeed: 1,
        etaSeconds: 0
      });

      const controller = new CompressionController();
      controllerRef.current = controller;

      const compResult = await compressVideo(
        metadata,
        settings,
        (p) => setProgress(p),
        controller
      );

      setResult(compResult);
      setStatus('completed');
    } catch (err: any) {
      console.error('Compression error:', err);
      if (err.message && err.message.includes('cancelled')) {
        setStatus('ready');
      } else {
        setErrorMessage(
          err.message || 'An error occurred during compression. Please try a different preset.'
        );
        setStatus(metadata ? 'ready' : 'error');
      }
    } finally {
      controllerRef.current = null;
    }
  };

  // Cancel Compression
  const handleCancelCompress = () => {
    if (controllerRef.current) {
      controllerRef.current.abort();
    }
  };

  // Reset to initial state
  const handleReset = () => {
    if (result?.url) {
      URL.revokeObjectURL(result.url);
    }
    setMetadata(null);
    setResult(null);
    setErrorMessage(null);
    setStatus('idle');
  };

  return (
    <div className="min-h-screen bg-[#090d16] text-slate-100 flex flex-col font-sans">
      <Header />

      <main className="flex-1 w-full max-w-2xl mx-auto px-4 py-6 sm:py-10 flex flex-col items-center">
        {/* Error notification */}
        {errorMessage && (
          <div className="w-full mb-6 p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm flex items-start gap-3 shadow-lg">
            <AlertCircle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-semibold mb-0.5">Compression Notice</p>
              <p className="text-xs text-rose-300/90 leading-relaxed">{errorMessage}</p>
            </div>
            <button
              onClick={() => setErrorMessage(null)}
              className="text-xs text-rose-400 hover:text-white"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Step 1: File Dropzone (Idle state) */}
        {status === 'idle' && (
          <Dropzone onFileSelected={handleFileSelected} />
        )}

        {/* Step 1b: Analyzing indicator */}
        {status === 'analyzing' && (
          <div className="w-full py-16 flex flex-col items-center justify-center text-center">
            <div className="w-12 h-12 border-3 border-cyan-400 border-t-transparent rounded-full animate-spin mb-4" />
            <h3 className="text-base font-bold text-white mb-1">Analyzing Video...</h3>
            <p className="text-xs text-slate-400">Reading frame metrics, resolution, and audio tracks</p>
          </div>
        )}

        {/* Step 2: Configure & Start (Ready state) */}
        {status === 'ready' && metadata && (
          <div className="w-full flex flex-col gap-5">
            <VideoInfoCard
              metadata={metadata}
              onChangeVideo={handleReset}
            />
            <CompressionControls
              metadata={metadata}
              onStartCompress={handleStartCompress}
              isCompressing={false}
            />
          </div>
        )}

        {/* Step 3: Compressing Modal / Overlay */}
        {status === 'compressing' && (
          <CompressingModal
            progress={progress}
            onCancel={handleCancelCompress}
          />
        )}

        {/* Step 4: Finished / Result Card */}
        {status === 'completed' && metadata && result && (
          <ResultCard
            metadata={metadata}
            result={result}
            onReset={handleReset}
          />
        )}

        {/* Error state with retry */}
        {status === 'error' && (
          <div className="w-full py-12 flex flex-col items-center text-center">
            <button
              onClick={handleReset}
              className="px-5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-semibold text-xs transition-all flex items-center gap-2"
            >
              <RefreshCw className="w-4 h-4" />
              <span>Try Again</span>
            </button>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="w-full py-4 border-t border-slate-800/60 text-center text-xs text-slate-500">
        <p>100% Client-Side Processing • Zero Data Upload • WebCodecs Hardware Accelerated</p>
      </footer>
    </div>
  );
};
