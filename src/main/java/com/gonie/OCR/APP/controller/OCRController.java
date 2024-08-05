package com.gonie.OCR.APP.controller;

import com.gonie.OCR.APP.model.MeterReading;
import com.gonie.OCR.APP.service.OCRService;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/ocr")
public class OCRController {

    private final OCRService ocrService;

    @Autowired
    public OCRController(OCRService ocrService) {
        this.ocrService = ocrService;
    }

    // Endpoint for file with units parameter
    @PostMapping("/uploadWithUnits")
    public ResponseEntity<MeterReading> uploadFileWithUnits(@RequestParam("file") MultipartFile file,
                                                            @RequestParam(value = "units", required = false) Double units)
            throws IOException, TesseractException {
        MeterReading meterReading = ocrService.extractAndSaveUnits(file, units);
        return ResponseEntity.ok(meterReading);
    }
}
