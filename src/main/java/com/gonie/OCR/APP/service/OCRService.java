package com.gonie.OCR.APP.service;

import com.gonie.OCR.APP.model.MeterReading;
import com.gonie.OCR.APP.repository.MeterReadingRepository;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;

@Service
public class OCRService {

    private final ITesseract tesseract;
    private final MeterReadingRepository meterReadingRepository;

    @Autowired
    public OCRService(MeterReadingRepository meterReadingRepository) {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath("C:/Users/BAKARI ABDILLAH ISMA/Downloads/tesseract-5.4.1/tesseract-5.4.1/tessdata"); // Change to your tessdata path
        this.tesseract.setLanguage("eng");
        this.tesseract.setTessVariable("tessedit_char_whitelist", "0123456789.");
        this.meterReadingRepository = meterReadingRepository;
    }

    public MeterReading extractAndSaveUnits(MultipartFile file, Double units) throws IOException, TesseractException {
        // Convert MultipartFile to File
        File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
        file.transferTo(convFile);

        // Preprocess the image
        BufferedImage preprocessedImage = preprocessImage(convFile);

        // Perform OCR on the preprocessed image
        String extractedText = tesseract.doOCR(preprocessedImage);

        // Parse the extracted text to get the meter reading value if units are not provided
        if (units == null) {
            units = parseUnits(extractedText);
        }

        // Create and save MeterReading
        MeterReading meterReading = new MeterReading();
        meterReading.setUnits(units);
        meterReading.setImageUnits(extractedText);

        return meterReadingRepository.save(meterReading);
    }

    private BufferedImage preprocessImage(File imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);

        // Increase DPI
        image = setDPI(image, 300);

        // Convert to grayscale
        BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Enhance contrast
        RescaleOp rescaleOp = new RescaleOp(2.0f, 30, null);
        rescaleOp.filter(grayImage, grayImage);

        // Apply Gaussian blur to reduce noise
        grayImage = applyGaussianBlur(grayImage);

        // Apply adaptive thresholding
        grayImage = applyAdaptiveThresholding(grayImage);

        // Crop to region of interest (ROI)
        int roiX = (int) (grayImage.getWidth() * 0.6);
        int roiY = (int) (grayImage.getHeight() * 0.3);
        int roiWidth = (int) (grayImage.getWidth() * 0.35);
        int roiHeight = (int) (grayImage.getHeight() * 0.4);
        BufferedImage roiImage = grayImage.getSubimage(roiX, roiY, roiWidth, roiHeight);

        return roiImage;
    }

    private BufferedImage setDPI(BufferedImage image, int dpi) throws IOException {
        File tempFile = new File("temp.jpg");
        ImageOutputStream ios = ImageIO.createImageOutputStream(tempFile);
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.ImageWriteParam writeParam = writer.getDefaultWriteParam();
        javax.imageio.metadata.IIOMetadata metadata = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(image), writeParam);

        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_jpeg_image_1.0");
        IIOMetadataNode jfif = new IIOMetadataNode("app0JFIF");
        jfif.setAttribute("majorVersion", "1");
        jfif.setAttribute("minorVersion", "2");
        jfif.setAttribute("thumbWidth", "0");
        jfif.setAttribute("thumbHeight", "0");
        root.appendChild(jfif);

        IIOMetadataNode dpiNode = new IIOMetadataNode("app0JFIF");
        dpiNode.setAttribute("Xdensity", Integer.toString(dpi));
        dpiNode.setAttribute("Ydensity", Integer.toString(dpi));
        dpiNode.setAttribute("resUnits", "1"); // 1 = dots/inch
        root.appendChild(dpiNode);

        metadata.mergeTree("javax_imageio_jpeg_image_1.0", root);

        writer.setOutput(ios);
        writer.write(metadata, new javax.imageio.IIOImage(image, null, metadata), writeParam);
        ios.close();
        writer.dispose();

        BufferedImage resultImage = ImageIO.read(tempFile);
        tempFile.delete();

        return resultImage;
    }

    private BufferedImage applyGaussianBlur(BufferedImage image) {
        float[] matrix = {
                1/16f, 2/16f, 1/16f,
                2/16f, 4/16f, 2/16f,
                1/16f, 2/16f, 1/16f,
        };
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, matrix));
        return op.filter(image, null);
    }

    private BufferedImage applyAdaptiveThresholding(BufferedImage image) {
        BufferedImage thresholdedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int gray = image.getRGB(x, y) & 0xFF;
                int threshold = gray < 128 ? 0 : 255;
                thresholdedImage.setRGB(x, y, (threshold << 16) | (threshold << 8) | threshold);
            }
        }
        return thresholdedImage;
    }

    private Double parseUnits(String extractedText) {
        // Use regex or other text processing methods to extract the meter reading value
        String[] lines = extractedText.split("\n");
        for (String line : lines) {
            if (line.matches("\\d+\\.\\d+")) { // Match decimal numbers like 3.94
                return Double.parseDouble(line);
            }
        }
        return 0.0; // Default value if no number is found
    }
}
