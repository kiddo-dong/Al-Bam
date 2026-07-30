package com.example.albam.global.file;

import com.example.albam.global.exception.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 엑셀(xlsx/xls)·CSV 파일을 LLM 프롬프트에 넣을 수 있는 행 단위 텍스트로 변환한다.
 * 셀 병합·수식·서식은 화면에 보이는 값 기준으로 평탄화한다 (수식은 계산 결과 값으로).
 */
@Component
public class SpreadsheetTextExtractor {

    /** LLM 프롬프트에 넣을 수 있는 최대 텍스트 길이. 초과하면 파일을 나눠 올리도록 안내한다. */
    private static final int MAX_TEXT_LENGTH = 60_000;
    private static final String CELL_DELIMITER = " | ";

    public String extract(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String text;
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            text = extractExcel(file);
        } else if (filename.endsWith(".csv")) {
            text = extractCsv(file);
        } else {
            throw new InvalidRequestException("xlsx, xls, csv 파일만 업로드할 수 있습니다.");
        }
        if (text.isBlank()) {
            throw new InvalidRequestException("파일에서 내용을 읽지 못했습니다. 파일이 비어있지 않은지 확인해 주세요.");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new InvalidRequestException("파일이 너무 큽니다. 시트를 나눠서 여러 번 업로드해 주세요.");
        }
        return text;
    }

    private String extractExcel(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            StringBuilder sb = new StringBuilder();
            for (Sheet sheet : workbook) {
                sb.append("### 시트: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(formatter.formatCellValue(cell, evaluator).trim());
                    }
                    String line = String.join(CELL_DELIMITER, cells).trim();
                    if (!line.replace("|", "").isBlank()) {
                        sb.append(row.getRowNum() + 1).append("행: ").append(line).append('\n');
                    }
                }
            }
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            throw new InvalidRequestException("엑셀 파일을 읽지 못했습니다. 파일이 손상되지 않았는지 확인해 주세요.");
        }
    }

    /** 한국어 엑셀에서 내보낸 CSV는 EUC-KR(MS949)인 경우가 많아, UTF-8 해석 실패 시 MS949로 재시도한다. */
    private String extractCsv(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String content = decode(bytes, StandardCharsets.UTF_8);
            if (content == null) {
                content = decode(bytes, Charset.forName("MS949"));
            }
            if (content == null) {
                throw new InvalidRequestException("CSV 파일의 문자 인코딩을 해석하지 못했습니다. UTF-8로 저장해 주세요.");
            }
            StringBuilder sb = new StringBuilder();
            String[] lines = content.split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].isBlank()) {
                    sb.append(i + 1).append("행: ").append(lines[i].trim()).append('\n');
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new InvalidRequestException("CSV 파일을 읽지 못했습니다.");
        }
    }

    private String decode(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return null;
        }
    }
}
