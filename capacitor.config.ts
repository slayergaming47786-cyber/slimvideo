import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.slimvideo.app',
  appName: 'SlimVideo',
  webDir: 'dist',
  backgroundColor: '#090d16',
  server: {
    androidScheme: 'https'
  },
  android: {
    allowMixedContent: true,
    backgroundColor: '#090d16'
  }
};

export default config;
