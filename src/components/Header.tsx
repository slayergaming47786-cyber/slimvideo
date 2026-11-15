import React, { useEffect, useState } from 'react';
import { ShieldCheck, Download, Sparkles, Zap, Cpu, HelpCircle, X } from 'lucide-react';
import { detectHardwareEncoding } from '../services/videoCompressor';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export const Header: React.FC = () => {
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState(false);
  const [hasHwEncoder, setHasHwEncoder] = useState<boolean | null>(null);
  const [showHwNotice, setShowHwNotice] = useState(false);

  useEffect(() => {
    // Check hardware encoding capability
    detectHardwareEncoding().then((supported) => setHasHwEncoder(supported));

    // Check if running in standalone mode (already installed as PWA)
    if (window.matchMedia('(display-mode: standalone)').matches || (window.navigator as any).standalone) {
      setIsInstalled(true);
    }

    const handleBeforeInstall = (e: Event) => {
      e.preventDefault();
      setInstallPrompt(e as BeforeInstallPromptEvent);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstall);
    return () => window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
  }, []);

  const handleInstallClick = async () => {
    if (!installPrompt) return;
    await installPrompt.prompt();
    const { outcome } = await installPrompt.userChoice;
    if (outcome === 'accepted') {
      setIsInstalled(true);
      setInstallPrompt(null);
    }
  };

  return (
    <header className="w-full border-b border-slate-800/80 bg-[#090d16]/80 backdrop-blur-md sticky top-0 z-40 px-4 py-3 sm:px-6">
      <div className="max-w-4xl mx-auto flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-cyan-500 via-indigo-500 to-purple-600 p-0.5 shadow-lg shadow-indigo-500/20 flex items-center justify-center">
            <div className="w-full h-full bg-[#090d16] rounded-[10px] flex items-center justify-center">
              <Sparkles className="w-4 h-4 text-cyan-400" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-base tracking-tight text-white">SlimVideo</span>
              <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                Offline
              </span>
            </div>
            <p className="text-[11px] text-slate-400 hidden xs:block">Smart On-Device Video Shrinker</p>
          </div>
        </div>

        {/* Right Badges */}
        <div className="flex items-center gap-2">
          {/* Hardware Acceleration Indicator */}
          {hasHwEncoder === true && (
            <div className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 text-xs font-medium">
              <Zap className="w-3 h-3 text-cyan-400" />
              <span className="hidden sm:inline">GPU Accelerated</span>
              <span className="sm:hidden">GPU</span>
            </div>
          )}

          {hasHwEncoder === false && (
            <button
              type="button"
              onClick={() => setShowHwNotice(!showHwNotice)}
              className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/20 text-amber-300 text-xs font-medium transition-colors cursor-pointer"
              title="Click to learn how to unlock GPU encoding"
            >
              <Cpu className="w-3 h-3 text-amber-400" />
              <span>CPU Mode</span>
              <HelpCircle className="w-3 h-3 text-amber-400/80 ml-0.5" />
            </button>
          )}

          {/* Privacy Badge */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
            <ShieldCheck className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">100% Private</span>
            <span className="sm:hidden">Private</span>
          </div>

          {/* Install PWA Button (Visible if prompt available) */}
          {installPrompt && !isInstalled && (
            <button
              onClick={handleInstallClick}
              className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-cyan-500 to-indigo-600 hover:from-cyan-400 hover:to-indigo-500 text-white text-xs font-semibold shadow-md shadow-indigo-500/20 transition-all active:scale-95"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Install</span>
            </button>
          )}
        </div>
      </div>

      {/* GPU Unlock Popover / Notice */}
      {showHwNotice && (
        <div className="max-w-4xl mx-auto mt-2 p-3.5 rounded-xl bg-slate-900 border border-amber-500/30 text-xs text-slate-300 relative shadow-2xl">
          <button
            onClick={() => setShowHwNotice(false)}
            className="absolute top-2 right-2 text-slate-400 hover:text-white"
          >
            <X className="w-4 h-4" />
          </button>
          <div className="flex items-start gap-2.5 pr-6">
            <Cpu className="w-4 h-4 text-amber-400 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-bold text-amber-300 mb-1">Why is this device in CPU Mode?</p>
              <p className="text-slate-300 mb-2 leading-relaxed">
                Chromium/Brave automatically blocklists hardware video encoding on older Intel GPUs (like Intel HD 4400) to protect against legacy driver bugs. On phones and newer PCs, GPU is active by default.
              </p>
              <p className="text-slate-300 font-medium mb-1">To unlock GPU on this PC:</p>
              <ol className="list-decimal list-inside text-slate-400 space-y-0.5 font-mono text-[11px]">
                <li>Open <span className="text-cyan-300">brave://flags</span> in address bar</li>
                <li>Search &amp; Enable: <span className="text-cyan-300">Override software rendering list</span></li>
                <li>Search &amp; Enable: <span className="text-cyan-300">Accelerated video encode</span></li>
                <li>Click <span className="text-white font-bold">Relaunch</span> Brave</li>
              </ol>
            </div>
          </div>
        </div>
      )}
    </header>
  );
};
