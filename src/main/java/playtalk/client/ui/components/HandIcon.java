package playtalk.client.ui.components;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.Color;
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
 * 가위·바위·보 손 모양 아이콘. src/main/resources/hands/ 의 PNG 그림을 불러와 크기에 맞춰 그린다.
 * <ul>
 *   <li>scissors.png (가위), rock.png (바위), paper.png (보)</li>
 * </ul>
 * 이미지라서 Windows / macOS 어디서나 똑같이 보인다.
 */
public class HandIcon implements Icon {

    public enum Kind {
        SCISSORS("scissors"), ROCK("rock"), PAPER("paper");

        private final String fileName;

        Kind(String fileName) {
            this.fileName = fileName;
        }
    }

    /** 원본 이미지 (한 번만 읽는다) */
    private static final Map<Kind, BufferedImage> ORIGINALS = new HashMap<>();
    /** 크기별로 줄여 둔 이미지 (매번 줄이면 느리고 거칠어 보여서 보관한다) */
    private static final Map<String, BufferedImage> SCALED = new HashMap<>();

    private final Kind kind;
    private final int size;

    /** color, fill 인자는 예전 코드와의 호환용이다. 이미지라서 사용하지 않는다. */
    public HandIcon(Kind kind, int size, Color color, Color fill) {
        this(kind, size);
    }

    public HandIcon(Kind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        BufferedImage original = load(kind);
        if (original == null) return; // 파일을 못 찾으면 빈 칸 (원인은 콘솔에 출력됨)
        Graphics2D g2 = (Graphics2D) g.create();
        // 레티나 화면이면 화면 픽셀 수에 맞춰 줄여야 선명하다
        double deviceScale = Math.max(1.0, g2.getTransform().getScaleX());
        int pixels = (int) Math.round(size * deviceScale);
        BufferedImage image = scaled(kind, original, pixels);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(image, x, y, size, size, null);
        g2.dispose();
    }

    private static synchronized BufferedImage load(Kind kind) {
        if (ORIGINALS.containsKey(kind)) return ORIGINALS.get(kind);
        BufferedImage image = null;
        String path = "/hands/" + kind.fileName + ".png";
        try (InputStream in = HandIcon.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[HandIcon] 이미지를 찾을 수 없습니다: src/main/resources" + path);
            } else {
                image = ImageIO.read(in);
            }
        } catch (IOException e) {
            System.err.println("[HandIcon] 이미지를 읽지 못했습니다: " + path + " (" + e.getMessage() + ")");
        }
        ORIGINALS.put(kind, image);
        return image;
    }

    /** 원본을 target 픽셀 크기로 줄인다. 반씩 여러 번 줄여야 거칠어지지 않는다. */
    private static synchronized BufferedImage scaled(Kind kind, BufferedImage original, int target) {
        String key = kind + ":" + target;
        BufferedImage cached = SCALED.get(key);
        if (cached != null) return cached;

        BufferedImage current = original;
        while (current.getWidth() / 2 >= target) {
            current = resize(current, current.getWidth() / 2);
        }
        if (current.getWidth() != target) current = resize(current, target);
        SCALED.put(key, current);
        return current;
    }

    private static BufferedImage resize(BufferedImage src, int side) {
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, side, side, null);
        g.dispose();
        return out;
    }
}