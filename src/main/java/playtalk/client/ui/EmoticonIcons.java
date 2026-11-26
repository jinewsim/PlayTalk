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

    private static final Map<String, javax.swing.Icon> INLINE = new HashMap<>();

    /**
     * 글자 사이에 넣을 작은 이모티콘. 글자 기준선에 바닥을 맞추면 떠 보여서 조금 아래로 내려 그린다.
     * @return 아이콘, 이미지 파일이 없으면 null
     */
    public static synchronized javax.swing.Icon inline(String name, int size) {
        String key = name + "@" + size;
        if (INLINE.containsKey(key)) return INLINE.get(key);
        ImageIcon base = get(name, size);
        javax.swing.Icon icon = base == null ? null : new javax.swing.Icon() {
            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                base.paintIcon(c, g, x, y + size / 5);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
        INLINE.put(key, icon);
        return icon;
    }
}
