package ru.subsidy.package_.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.NotFoundException;

class PageRendererTest {

    private final PageRenderer renderer = new PageRenderer();

    private static byte[] pdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int idx = 0; idx < pages; idx++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void pdfPage_isRenderedToPng() throws IOException {
        byte[] png = renderer.render(pdf(2), "application/pdf", 2);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isGreaterThan(500);
        assertThatThrownBy(() -> renderer.render(pdf(2), "application/pdf", 3))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void image_isConvertedToPng() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "jpg", out);
        byte[] png = renderer.render(out.toByteArray(), "image/jpeg", 1);
        assertThat(ImageIO.read(new ByteArrayInputStream(png)).getHeight()).isEqualTo(600);
        assertThatThrownBy(() -> renderer.render(out.toByteArray(), "image/jpeg", 2))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void office_hasNoPreview() {
        assertThatThrownBy(() -> renderer.render(new byte[] {1}, "application/vnd.ms-excel", 1))
                .isInstanceOf(DomainException.class);
    }
}
