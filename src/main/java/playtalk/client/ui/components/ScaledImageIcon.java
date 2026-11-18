package playtalk.client.ui.components;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 리소스(src/main/resources) 안의 PNG 그림을 지정한 칸(maxWidth x maxHeight) 안에 비율을 지켜 그리는 아이콘.
 * 그림을 못 찾으면 빈 칸으로 나오고 콘솔에 위치를 알려준다.
 */
public class ScaledImageIcon implements Icon {

    private static final Map<String, BufferedImage> ORIGINALS = new HashMap<>();
    private static final Map<String, BufferedImage> SCALED = new HashMap<>();

    private final String path;
    private final BufferedImage original;
    private final int width;
    private final int height;

    /** @param path 리소스 경로 (예: "/games/rps.png") */
    public ScaledImageIcon(String path, int maxWidth, int maxHeight) {
        this.path = path;
        this.original = load(path);
        if (original == null) {
            width = maxWidth;
            height = maxHeight;
        } else {
            double ratio = Math.min((double) maxWidth / original.getWidth(), (double) maxHeight / original.getHeight());
            width = Math.max(1, (int) Math.round(original.getWidth() * ratio));
            height = Math.max(1, (int) Math.round(original.getHeight() * ratio));
        }
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (original == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        // 레티나 화면이면 화면 픽셀 수에 맞춰 줄여야 선명하다
        double deviceScale = Math.max(1.0, g2.getTransform().getScaleX());
        int pw = (int) Math.round(width * deviceScale);
        int ph = (int) Math.round(height * deviceScale);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(scaled(path, original, pw, ph), x, y, width, height, null);
        g2.dispose();
    }

    private static synchronized BufferedImage load(String path) {
        if (ORIGINALS.containsKey(path)) return ORIGINALS.get(path);
        BufferedImage image = null;
        try (InputStream in = ScaledImageIcon.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[ScaledImageIcon] 이미지를 찾을 수 없습니다: src/main/resources" + path);
            } else {
                image = ImageIO.read(in);
            }
        } catch (IOException e) {
            System.err.println("[ScaledImageIcon] 이미지를 읽지 못했습니다: " + path + " (" + e.getMessage() + ")");
        }
        ORIGINALS.put(path, image);
        return image;
    }

    /** 원본을 w x h 픽셀로 줄인다. 반씩 여러 번 줄여야 거칠어지지 않는다. */
    private static synchronized BufferedImage scaled(String path, BufferedImage original, int w, int h) {
        String key = path + ":" + w + "x" + h;
        BufferedImage cached = SCALED.get(key);
        if (cached != null) return cached;

        BufferedImage current = original;
        while (current.getWidth() / 2 >= w && current.getHeight() / 2 >= h) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != w || current.getHeight() != h) current = resize(current, w, h);
        SCALED.put(key, current);
        return current;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}