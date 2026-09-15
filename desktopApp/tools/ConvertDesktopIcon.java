import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** Converts the reviewed PNG artwork to the platform icon containers. Run from the repository root. */
class ConvertDesktopIcon {
    public static void main(String[] args) throws Exception {
        Path icons = Path.of("desktopApp/icons");
        BufferedImage source = ImageIO.read(icons.resolve("RikkaHub.png").toFile());
        if (source == null || source.getWidth() != source.getHeight() || !source.getColorModel().hasAlpha()) {
            throw new IllegalArgumentException("RikkaHub.png must be a square image with transparency");
        }

        int[] windowsSizes = {16, 24, 32, 48, 64, 128, 256};
        var windowsImages = new ArrayList<byte[]>();
        for (int size : windowsSizes) windowsImages.add(png(source, size));
        int offset = 6 + 16 * windowsSizes.length;
        int totalSize = offset;
        for (byte[] bytes : windowsImages) totalSize += bytes.length;
        ByteBuffer ico = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) windowsSizes.length);
        for (int i = 0; i < windowsSizes.length; i++) {
            int size = windowsSizes[i];
            byte[] bytes = windowsImages.get(i);
            ico.put((byte) (size == 256 ? 0 : size)).put((byte) (size == 256 ? 0 : size));
            ico.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            ico.putInt(bytes.length).putInt(offset);
            offset += bytes.length;
        }
        windowsImages.forEach(ico::put);
        Files.write(icons.resolve("RikkaHub.ico"), ico.array());

        int[] macSizes = {16, 32, 64, 128, 256, 512, 1024, 32, 64, 256, 512};
        String[] macTypes = {"icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10",
                "ic11", "ic12", "ic13", "ic14"};
        var chunks = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(chunks)) {
            for (int i = 0; i < macSizes.length; i++) {
                byte[] bytes = png(source, macSizes[i]);
                data.writeBytes(macTypes[i]);
                data.writeInt(8 + bytes.length);
                data.write(bytes);
            }
        }
        try (var data = new DataOutputStream(Files.newOutputStream(icons.resolve("RikkaHub.icns")))) {
            data.writeBytes("icns");
            data.writeInt(8 + chunks.size());
            chunks.writeTo(data);
        }

        Path windowIcon = Path.of("desktopApp/src/jvmMain/resources/icons/RikkaHub.png");
        Files.createDirectories(windowIcon.getParent());
        Files.write(windowIcon, png(source, 256));
    }

    private static byte[] png(BufferedImage source, int size) throws Exception {
        BufferedImage result = source;
        // Reduce in steps so the mark remains legible at taskbar and title-bar sizes.
        while (result.getWidth() != size) {
            int next = Math.max(size, result.getWidth() / 2);
            BufferedImage scaled = new BufferedImage(next, next, BufferedImage.TYPE_INT_ARGB);
            var graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(result, 0, 0, next, next, null);
            } finally {
                graphics.dispose();
            }
            result = scaled;
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(result, "png", bytes);
        return bytes.toByteArray();
    }
}
