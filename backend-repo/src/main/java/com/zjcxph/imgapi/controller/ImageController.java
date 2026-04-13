package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.*;
import com.zjcxph.imgapi.dto.req.*;
import com.zjcxph.imgapi.dto.resp.*;
import com.zjcxph.imgapi.common.*;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.PdfService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.ZipUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.validation.constraints.Pattern;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/v1/img")
@Tag(name = "IMG Controller", description = "图片管理接口")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageProperties imageProperties;
    private final ScanService scanService;
    private final PdfService pdfService;
    private final OssService ossService;

    public ImageController(ImageProperties imageProperties, ScanService scanService,
                           PdfService pdfService, OssService ossService) {
        this.imageProperties = imageProperties;
        this.scanService = scanService;
        this.pdfService = pdfService;
        this.ossService = ossService;
    }

    @Operation(summary = "服务器心跳")
    @GetMapping("/hello")
    public Result<Map<String, Object>> hello() {
        logger.info("服务正常");
        Map<String, Object> data = new HashMap<>();
        data.put("message", "服务正常");
        return Result.success(data);
    }

    @Operation(summary = "下载病案压缩包")
    @GetMapping("/download/{BAH}")
    public ResponseEntity<FileSystemResource> download(@PathVariable
                                                       @Pattern(regexp = "\\d{8}", message = "请输入正确的 8 位病案号")
                                                       @Parameter(description = "病案号", example = "00789508")
                                                       String BAH) throws IOException {
        File zipFile = scanService.createZipForBAH(BAH);
        String fileNameZip = BAH + ".zip";
        FileSystemResource fileSystemResource = new FileSystemResource(zipFile);

        logger.info("生成压缩包:{}", fileNameZip);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileNameZip);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(zipFile.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileSystemResource);
    }

//    @Operation(summary = "下载病案PDF文件(未完成)")
//    @PostMapping("/pdf")
    public ResponseEntity<?> pdf(@RequestBody IdRequest request) {

        List<String> ids = request.getId();
        logger.info("接收到 PDF 生成请求，ids: {}", ids);

        // 判断 id 是否为空
        if (ids == null || ids.isEmpty()) {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("code", "400");
            responseMap.put("message", "参数错误");
            return new ResponseEntity<>(responseMap, HttpStatus.BAD_REQUEST);
        }


        // 根据 ID 获取图片路径
        List<PathDO> imagePathList = scanService.getImagePathList(ids);
        List<String> collect = imagePathList.stream().map(detail ->
                String.format("%s/%s/%s/%s-%s/%s",
                        imageProperties.getBasePath(),
                        detail.getFolder().substring(0, 5),
                        detail.getFolder(),
                        detail.getBRXH(),
                        detail.getBAH(),
                        detail.getFilename())
        ).collect(Collectors.toList());

        if (imagePathList.isEmpty()) {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("code", "404");
            responseMap.put("message", "未找到图片");
            return new ResponseEntity<>(responseMap, HttpStatus.NOT_FOUND);
        }

        logger.info("开始生成 PDF");
        // 格式化时间
        String time = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        String outPdf = "./temp/" + time + ".pdf";

        try {
            boolean pdfFromImages = pdfService.createPdfFromImages(outPdf, collect);
            if (!pdfFromImages) {
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("code", "500");
                responseMap.put("message", "生成 PDF 失败");
                return new ResponseEntity<>(responseMap, HttpStatus.INTERNAL_SERVER_ERROR);
            }

            File file = new File(outPdf);

            // 文件为空

            if (!file.exists()) {
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("code", "404");
                responseMap.put("message", "文件不存在");
                return new ResponseEntity<>(responseMap, HttpStatus.NOT_FOUND);
            }

            FileSystemResource fileSystemResource = new FileSystemResource(file);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "inline; filename=sample.pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(fileSystemResource);


        } catch (Exception e) {
            logger.error(String.valueOf(e));
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("code", "404");
            responseMap.put("message", String.valueOf(e));
            return new ResponseEntity<>(responseMap, HttpStatus.NOT_FOUND);
//            return "生成 PDF 失败";
        }
//        return "Received IDs: " + ids;
    }

    public static String extractYearMonth(String dateStr) {
        String[] parts = dateStr.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid date format");
        }
        return parts[0] + "." + parts[1];
    }

    @Operation(summary = "获取病案号下的图片数据")
    @GetMapping("/{bah}")
    public Result<List<BAHDataResponseDTO>> getDataByBAH(
            @PathVariable
            @Pattern(regexp = "\\d{8}", message = "请输入正确的 8 位病案号")
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        List<Scan> imageListByBAH = scanService.getImageListByBAH(bah);
        String imgUrl = imageProperties.getUrl();

        List<BAHDataResponseDTO> items = new ArrayList<>();

        for (Scan scan : imageListByBAH) {
            String img_url = imgUrl + "/" + extractYearMonth(scan.getFolder()) + "/" +scan.getFolder() + "/" +
                    scan.getBrxh()+ "-" + scan.getBah() + "/" + scan.getFilename();
            BAHDataResponseDTO bAHDataResponseDTO = new BAHDataResponseDTO();
            BeanUtils.copyProperties(scan, bAHDataResponseDTO);
            bAHDataResponseDTO.setImg_url(img_url);

            // If OSS URL exists, generate a signed URL for private read access
            if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
                try {
                    String signedUrl = ossService.generatePresignedUrl(scan.getOssUrl());
                    bAHDataResponseDTO.setOssUrl(signedUrl);
                } catch (Exception e) {
                    logger.warn("Failed to generate signed URL for scan {}: {}", scan.getId(), e.getMessage());
                }
            }

            items.add(bAHDataResponseDTO);
        }
        logger.info("获取 {} 病案号下的图片数据", bah);
        return Result.success(items).message(bah + " 数据获取成功");
    }

    @Operation(summary = "获取病案号下的对应单张图片")
    @GetMapping("image/{BAH}/{BRXH}/{FOLDER}/{FILENAME}")
    public ResponseEntity<?> getImage(
            @Parameter(description = "病案号", example = "00789508")
            @PathVariable String BAH,
            @Parameter(description = "病人序号", example = "605746")
            @PathVariable String BRXH,
            @Parameter(description = "文件夹", example = "24.04.30")
            @PathVariable String FOLDER,
            @Parameter(description = "文件名", example = "0072.jpg")
            @PathVariable String FILENAME) {

        String folderName = BRXH + "-" + BAH;
        String parentFolder = FOLDER.substring(0, 5);

        Path filePath = Paths.get(imageProperties.getBasePath(), parentFolder, FOLDER, folderName, FILENAME);

        logger.info("获取图片:{}", filePath);
        LocalDateTime timestamp = LocalDateTime.now();

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            logger.error("文件不存在:{}", filePath);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("code", "404");
            responseMap.put("timestamp", timestamp);
            responseMap.put("message", "图片不存在");
            return new ResponseEntity<>(responseMap, HttpStatus.NOT_FOUND);
        }

        FileSystemResource resource = new FileSystemResource(filePath.toFile());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=" + FILENAME);
        headers.add("Cache-Control", "public, max-age=86400, immutable");
        headers.setContentType(MediaType.IMAGE_JPEG);

        try {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(Files.size(filePath))
                    .body(resource);
        } catch (IOException e) {
            logger.error("文件读取错误:{}", filePath, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", "500");
            errorResponse.put("timestamp", timestamp);
            errorResponse.put("message", "图片读取错误");
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "根据图片id修改对应图片类型")
    @PutMapping("/updateImageType/{id}")
    public Result<Void> updateImageType(
            @PathVariable
            @Parameter(description = "图片id", example = "1")
            Integer id,
            @RequestBody ImageRequest  req) {
        Integer imageType = req.getBtype();
        if (imageType == null) {
            return Result.fail("图片类型不能为空");
        }
        if (id == null) {
            return Result.fail("图片id不能为空");
        }
        if (imageType < 0 || imageType > 14) {
            return Result.fail("图片类型错误");
        }
        int result = scanService.updateImageType(id, imageType);
        if (result != 1) {
            logger.error("修改图片 {} 的类型为 {}", id, imageType);
            return Result.fail("修改图片类型失败");
        }

        logger.info("修改图片 {} 的类型为 {}", id, imageType);

        return Result.success("修改图片类型成功");
    }

    @Operation(summary = "通过后端代理获取 OSS 图片")
    @GetMapping("/oss-image/{id}")
    public ResponseEntity<?> getOssImage(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        Scan scan = scanService.findById(id);
        if (scan == null) {
            return ResponseEntity.notFound().build();
        }

        String ossKey = scan.getOssUrl();
        if (ossKey == null || ossKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("该记录未迁移到 OSS"));
        }

        try {
            String signedUrl = ossService.generatePresignedUrl(ossKey);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, signedUrl)
                    .header("Cache-Control", "private, max-age=3600")
                    .build();
        } catch (Exception e) {
            logger.error("获取 OSS 图片失败：id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Result.fail("获取 OSS 图片失败：" + e.getMessage()));
        }
    }
}
