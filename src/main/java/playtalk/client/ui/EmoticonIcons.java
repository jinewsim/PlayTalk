package playtalk.client.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** src/main/resources/emoticons/이름.png 이미지를 불러와 크기별로 캐시. */
public final class EmoticonIcons {

    private static final Map<String, ImageIcon> CACHE = new HashMap<>();

    private EmoticonIcons() {
    }

    /** @return 아이콘, 이미지 파일이 없으면 null */
    public static synchronized ImageIcon get(String name, int size) {
        String key = name + "@" + size;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        ImageIcon icon = null;
        try (InputStream in = EmoticonIcons.class.getResourceAsStream("/emoticons/" + name + ".png")) {
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                icon = new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
            }
        } catch (Exception ignored) {
        }
        CACHE.put(key, icon);
        return icon;
    }
}
