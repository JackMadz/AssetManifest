import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

public class Main {

    private static final String INPUT_DIR = "./sprites";
    private static final String OUTPUT_FILE = "AI_ASSETS_CONTEXT.md";
    
    private static final String CHAR_RAMP = " .:-=+*#%@";
    private static final int THUMB_SIZE = 16;

    public record AssetMeta(
            String relativePath,
            int width,
            int height,
            String hexColor,
            boolean hasAlpha,
            double brightness,
            String asciiArt
    ) {}

    public static void main(String[] args) {
        Path rootDir = Path.of(INPUT_DIR);
        Path manifestPath = Path.of(OUTPUT_FILE);

        try {
            if (!Files.exists(rootDir)) {
                Files.createDirectories(rootDir);
                System.out.println("Создана директория под ассеты: " + rootDir.toAbsolutePath());
                System.out.println("Закиньте туда PNG-файлы и повторите запуск.");
                return;
            }

            System.out.println("Сканируем директорию на предмет спрайтов...");
            List<Path> pngFiles;
            try (Stream<Path> paths = Files.walk(rootDir)) {
                pngFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".png"))
                        .toList();
            }

            if (pngFiles.isEmpty()) {
                System.out.println("В директории " + INPUT_DIR + " не найдено PNG файлов.");
                return;
            }

            StringBuilder md = new StringBuilder();
            md.append("# AI-Context Asset Ledger\n");
            md.append("> Автоматически сгенерированная карта графических ресурсов для контекста разработки.\n\n");

            for (Path file : pngFiles) {
                BufferedImage img = ImageIO.read(file.toFile());
                if (img == null) {
                    System.err.println("Не удалось прочитать изображение: " + file);
                    continue;
                }

                String relPath = rootDir.relativize(file).toString().replace("\\", "/");
                AssetMeta meta = processAsset(relPath, img);
                
                md.append(formatAssetMarkdown(meta));
            }

            Files.writeString(manifestPath, md.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Манифест успешно обновлен: " + manifestPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода при обработке ассетов:");
            e.printStackTrace();
        }
    }

    private static AssetMeta processAsset(String path, BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        long rSum = 0, gSum = 0, bSum = 0;
        long alphaCount = 0;
        long totalPixels = (long) w * h;

        int step = Math.max(1, Math.max(w, h) / 32);
        long sampled = 0;

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;

                if (a < 15) {
                    alphaCount++;
                } else {
                    rSum += (rgb >> 16) & 0xFF;
                    gSum += (rgb >> 8) & 0xFF;
                    bSum += rgb & 0xFF;
                }
                sampled++;
            }
        }

        long activePixels = Math.max(1, sampled - alphaCount);
        int avgR = (int) (rSum / activePixels);
        int avgG = (int) (gSum / activePixels);
        int avgB = (int) (bSum / activePixels);

        String hex = String.format("#%02X%02X%02X", avgR, avgG, avgB);
        boolean hasAlpha = alphaCount > 0;
        double brightness = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB) / 255.0;

        String ascii = generateNearestNeighborAscii(img, THUMB_SIZE, THUMB_SIZE);

        return new AssetMeta(path, w, h, hex, hasAlpha, brightness, ascii);
    }

    private static String generateNearestNeighborAscii(BufferedImage img, int targetW, int targetH) {
        StringBuilder sb = new StringBuilder();
        int srcW = img.getWidth();
        int srcH = img.getHeight();

        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int srcX = x * srcW / targetW;
                int srcY = y * srcH / targetH;

                int rgb = img.getRGB(srcX, srcY);
                int a = (rgb >> 24) & 0xFF;

                if (a < 20) {
                    sb.append(' ');
                    continue;
                }

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double gray = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                int idx = (int) (gray * (CHAR_RAMP.length() - 1));
                sb.append(CHAR_RAMP.charAt(idx));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String formatAssetMarkdown(AssetMeta meta) {
        return String.format(
                "## Asset: `%s`\n" +
                "- **Размер:** %dx%d px\n" +
                "- **Цвет:** `%s`\n" +
                "- **Прозрачность:** %s\n" +
                "- **Яркость:** %.1f%%\n" +
                "- **Превью:**\n```text\n%s\n```\n\n",
                meta.relativePath(),
                meta.width(),
                meta.height(),
                meta.hexColor(),
                meta.hasAlpha() ? "есть" : "нет",
                meta.brightness() * 100,
                meta.asciiArt()
        );
    }
}