package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.pojo.*;
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

import jakarta.validation.constraints.Pattern;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/v1/img-api")
@Tag(name = "IMG Controller", description = "图片管理接口")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageProperties imageProperties;
    private final ScanService scanService;
    private final PdfService pdfService;

    public ImageController(ImageProperties imageProperties, ScanService scanService, PdfService pdfService) {
        this.imageProperties = imageProperties;
        this.scanService = scanService;
        this.pdfService = pdfService;
    }

    @Operation(summary = "服务器心跳")
    @GetMapping("/hello")
    public Result<Object> hello() {
        logger.info("服务正常");
        Map<String, Object> data = new HashMap<>();
        data.put("message", "服务正常");
        return Result.<Object>success("服务正常").data(data);
    }

    @Operation(summary = "下载病案压缩包")
    @GetMapping("/download/{BAH}")
    public ResponseEntity<FileSystemResource> download(@PathVariable
                                                       @Pattern(regexp = "\\d{8}", message = "请输入正确的 8 位病案号")
                                                       @Parameter(description = "病案号", example = "00789508")
                                                       String BAH) throws IOException {
        Path imagePath = scanService.getImagePath(BAH);
        // temp 文件夹放本地，定时删除
        String fileNameTemp = BAH + ".temp";
        String fileNameZip = BAH + ".zip";
        ZipUtil.zipJpgFiles(imagePath.toString(), "./temp/" + fileNameTemp);

        File zipFile = new File("./temp/" + fileNameTemp);
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
        System.out.println("ids: " + ids);

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
    public Result<Object> getDataByBAH(
            @PathVariable
            @Pattern(regexp = "\\d{8}", message = "请输入正确的 8 位病案号")
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        List<Scan> imageListByBAH = scanService.getImageListByBAH(bah);
        String imgUrl = imageProperties.getUrl();

        List<BAHDataResponseDTO> items = new ArrayList<>();

        for (Scan scan : imageListByBAH) {
//            String img_url = imgUrl + "/" + scan.getBah() + "/" + scan.getBrxh() + "/" +
//                    scan.getFolder() + "/" + scan.getFilename();
            String img_url = imgUrl + "/" + extractYearMonth(scan.getFolder()) + "/" +scan.getFolder() + "/" +
                    scan.getBrxh()+ "-" + scan.getBah() + "/" + scan.getFilename();
            BAHDataResponseDTO bAHDataResponseDTO = new BAHDataResponseDTO();
            BeanUtils.copyProperties(scan, bAHDataResponseDTO);
            bAHDataResponseDTO.setImg_url(img_url);
            items.add(bAHDataResponseDTO);
        }
        logger.info("获取 {} 病案号下的图片数据", bah);
        Result<Object> objectResult = new Result<>();
        objectResult.code(200).message(bah + " 数据获取成功").data(items);
        return objectResult;
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

        InputStream inputStream;
        try {
            inputStream = new FileInputStream(String.valueOf(filePath));
        } catch (FileNotFoundException e) {
            logger.error("文件不存在:{}", filePath);
            // 完善 404 错误信息
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("code", "404");
            responseMap.put("timestamp", timestamp);
            responseMap.put("message", "图片不存在");
            return new ResponseEntity<>(responseMap, HttpStatus.NOT_FOUND);
        }

        InputStreamResource resource = new InputStreamResource(inputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=" + FILENAME);
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        // TODO 注意文件格式检查
        headers.setContentType(MediaType.IMAGE_JPEG);

        try {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(inputStream.available()) // 设置内容长度
                    .body(resource);
        } catch (IOException e) {
            // 处理文件读取异常
            logger.error("文件读取错误:{}", filePath);
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
}
