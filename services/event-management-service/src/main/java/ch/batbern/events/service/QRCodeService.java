package ch.batbern.events.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for QR code generation
 * <p>
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * <p>
 * Generates QR codes for event registration tickets using Google ZXing library.
 * QR codes contain registration details in JSON format for easy scanning at event check-in.
 */
@Service
@Slf4j
public class QRCodeService {

    private static final String IMAGE_FORMAT = "PNG";
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;

    /**
     * Generate QR code image as PNG bytes.
     * <p>
     * Uses high error correction level (Level H - 30% recovery) for better readability
     * even if QR code is partially damaged or obscured.
     *
     * @param data Data to encode in QR code (typically JSON with registration details)
     * @param width QR code width in pixels
     * @param height QR code height in pixels
     * @return PNG image bytes
     * @throws RuntimeException if QR code generation fails
     */
    public byte[] generateQRCode(String data, int width, int height) {
        log.debug("Generating QR code: {}x{} for data length: {}", width, height, data.length());

        try {
            // Configure QR code generation
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High error correction
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1); // Minimal margin for better space usage

            // Generate QR code matrix
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hints);

            // Convert to PNG bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, IMAGE_FORMAT, outputStream);
            byte[] qrCodeBytes = outputStream.toByteArray();

            log.debug("Generated QR code: {} bytes", qrCodeBytes.length);
            return qrCodeBytes;

        } catch (WriterException e) {
            log.error("Failed to encode QR code data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("Failed to write QR code image: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate QR code image: " + e.getMessage(), e);
        }
    }

    /**
     * Generate QR code with default dimensions (300x300).
     *
     * @param data Data to encode in QR code
     * @return PNG image bytes
     */
    public byte[] generateQRCode(String data) {
        return generateQRCode(data, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
}
