package com.gonie.OCR.APP.service;

import com.gonie.OCR.APP.model.MeterReading;
import com.gonie.OCR.APP.repository.MeterReadingRepository;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class OCRService {

    private final ITesseract tesseract;
    private final MeterReadingRepository meterReadingRepository;

    static {
        // Load OpenCV native library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Autowired
    public OCRService(MeterReadingRepository meterReadingRepository) {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath("C:/Users/BAKARI ABDILLAH ISMA/Downloads/tesseract-5.4.1/tesseract-5.4.1/tessdata"); // Change to your tessdata path
        this.tesseract.setLanguage("eng");
        this.meterReadingRepository = meterReadingRepository;
    }

    public MeterReading extractAndSaveUnits(MultipartFile file, Double units) throws IOException, TesseractException {
        // Convert MultipartFile to File
        File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
        file.transferTo(convFile);

        // Preprocess the image
        preprocessImage(convFile.getAbsolutePath());

        // Perform OCR on the file
        String extractedText = tesseract.doOCR(convFile);

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

    private void preprocessImage(String imagePath) {
        Mat src = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_COLOR);
        Mat gray = new Mat(src.size(), CvType.CV_8UC1);
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(gray, gray, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        // Save the preprocessed image
        Imgcodecs.imwrite(imagePath, gray);
    }

    private Double parseUnits(String extractedText) {
        // Use regex or other text processing methods to extract the meter reading value
        // For simplicity, let's assume the extracted text contains the number directly
        String[] lines = extractedText.split("\n");
        for (String line : lines) {
            if (line.matches("\\d+\\.\\d+")) { // Match decimal numbers like 3.94
                return Double.parseDouble(line);
            }
        }
        return 0.0; // Default value if no number is found
    }
}
