import java.io.File;
import java.io.InputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * 使用 Java Sound 实现的背景音乐播放器 (支持 WAV/AIFF/AU)。
 * - playLoop(): 循环播放
 * - pause()/resume(): 暂停/恢复 (不重置位置)
 * - stop(): 停止并重置到开始
 */
public class BgmPlayer {
    private Clip clip;
    private boolean paused = false;
    private long pausedFrame = 0;
    private float volume01 = 0.6f;

    public boolean load(String pathOrNull) {
        close();

        // 优先尝试用户指定的路径
        if (pathOrNull != null && !pathOrNull.isBlank()) {
            if (tryLoadFromFile(pathOrNull)) return true;
        }

        // 尝试默认路径
        if (tryLoadFromFile("assets/bgm.wav")) return true;
        if (tryLoadFromFile("bgm.wav")) return true;

        // 尝试从类路径 (Classpath) 读取资源
        try {
            InputStream in = BgmPlayer.class.getResourceAsStream("/bgm.wav");
            if (in != null) {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(in)) {
                    openClip(ais);
                    return true;
                }
            }
        } catch (Exception ignored) { }

        return false;
    }

    private boolean tryLoadFromFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return false;
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(f)) {
                openClip(ais);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void openClip(AudioInputStream sourceAis) throws Exception {
        AudioFormat baseFormat = sourceAis.getFormat();

        // 如果需要，转换为 PCM_SIGNED (提高兼容性)
        AudioFormat decoded = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
        );

        try (AudioInputStream dais = AudioSystem.getAudioInputStream(decoded, sourceAis)) {
            clip = AudioSystem.getClip();
            clip.open(dais);
            applyVolume();
        }
        paused = false;
        pausedFrame = 0;
    }

    public void playLoop() {
        if (clip == null) return;
        paused = false;
        clip.stop();
        clip.setFramePosition(0);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public void resumeLoop() {
        if (clip == null) return;
        if (!paused) return;
        paused = false;
        clip.stop();
        clip.setFramePosition((int) pausedFrame);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public void pause() {
        if (clip == null) return;
        if (paused) return;
        paused = true;
        pausedFrame = clip.getLongFramePosition();
        clip.stop();
    }

    public void stop() {
        if (clip == null) return;
        paused = false;
        pausedFrame = 0;
        clip.stop();
        clip.setFramePosition(0);
    }

    public boolean isLoaded() {
        return clip != null;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setVolume01(float v) {
        volume01 = Math.max(0f, Math.min(1f, v));
        applyVolume();
    }

    private void applyVolume() {
        if (clip == null) return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            // 将 0..1 映射到增益分贝 (dB)；避免 0 时出现负无穷
            float min = gain.getMinimum(); // 通常约 -80 dB
            float max = gain.getMaximum(); // 通常为 6 dB
            float dB;
            if (volume01 <= 0.0001f) dB = min;
            else {
                // 感官音量曲线
                float curved = (float) Math.pow(volume01, 0.6);
                dB = min + (max - min) * curved;
            }
            gain.setValue(dB);
        } catch (Exception ignored) { }
    }

    public void close() {
        try {
            if (clip != null) {
                clip.stop();
                clip.close();
            }
        } catch (Exception ignored) { }
        clip = null;
        paused = false;
        pausedFrame = 0;
    }
}

