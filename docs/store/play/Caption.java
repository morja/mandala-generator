import java.awt.*; import java.awt.font.*; import java.awt.geom.*; import java.awt.image.BufferedImage; import java.io.File; import java.text.AttributedString; import javax.imageio.ImageIO; import java.util.*;
/** Captioned Play screenshot: 1080x1920, caption block on top, phone screenshot below with rounded corners. */
public class Caption {
  static final int W = 1080, H = 1920, PAD = 72, SHOT_TOP = 400, RADIUS = 44;
  static final Color BG = new Color(0x0A0908), TEXT = new Color(0xE6E1DB), MUTED = new Color(0x9C948B), OUTLINE = new Color(0x2E2A26);
  public static void main(String[] a) throws Exception {
    String out = a[0], shot = a[1], titleFont = a[2], subFont = a[3], title = a[4], sub = a[5];
    BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setColor(BG); g.fillRect(0, 0, W, H);
    Font ft = new Font(titleFont, Font.PLAIN, 66), fs = new Font(subFont, Font.PLAIN, 40);
    if (ft.getFamily().equals("Dialog") || fs.getFamily().equals("Dialog")) throw new RuntimeException("font missing: " + titleFont + " -> " + ft.getFamily());
    java.util.List<TextLayout> lines = new ArrayList<>(); java.util.List<Boolean> isTitle = new ArrayList<>();
    wrap(g, title, ft, W - 2 * PAD, lines, isTitle, true);
    if (!sub.isEmpty()) wrap(g, sub, fs, W - 2 * PAD, lines, isTitle, false);
    float total = 0; for (int i = 0; i < lines.size(); i++) { TextLayout l = lines.get(i); total += l.getAscent() + l.getDescent() + (isTitle.get(i) ? 10 : 8); } total += 12;
    float y = (SHOT_TOP - 40 - total) / 2 + 40; boolean afterTitle = false;
    for (int i = 0; i < lines.size(); i++) {
      TextLayout l = lines.get(i); boolean t = isTitle.get(i);
      if (!t && !afterTitle) { y += 12; afterTitle = true; }
      y += l.getAscent(); float x = (W - (float) l.getBounds().getWidth()) / 2 - (float) l.getBounds().getX();
      g.setColor(t ? TEXT : MUTED); l.draw(g, x, y); y += l.getDescent() + (t ? 10 : 8);
    }
    BufferedImage src = ImageIO.read(new File(shot)); int sw = W - 2 * PAD, sh = (int) Math.round((double) src.getHeight() * sw / src.getWidth());
    RoundRectangle2D rr = new RoundRectangle2D.Float(PAD, SHOT_TOP, sw, sh, RADIUS, RADIUS);
    Shape clip = g.getClip(); g.setClip(rr); g.drawImage(src, PAD, SHOT_TOP, sw, sh, null); g.setClip(clip);
    g.setColor(OUTLINE); g.setStroke(new BasicStroke(2)); g.draw(rr);
    ImageIO.write(img, "png", new File(out));
  }
  static void wrap(Graphics2D g, String text, Font f, int width, java.util.List<TextLayout> lines, java.util.List<Boolean> flags, boolean title) {
    AttributedString as = new AttributedString(text); as.addAttribute(TextAttribute.FONT, f);
    LineBreakMeasurer m = new LineBreakMeasurer(as.getIterator(), g.getFontRenderContext());
    while (m.getPosition() < text.length()) { lines.add(m.nextLayout(width)); flags.add(title); }
  }
}
