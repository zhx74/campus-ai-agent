package com.campus.canteen.controller.admin;

import com.campus.canteen.constant.MessageConstant;
import com.campus.canteen.result.Result;
import com.campus.canteen.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

// 通用接口
@RestController
@RequestMapping("/admin/common")
@Tag(name = "通用接口")
@Slf4j
public class CommonController {
    @Autowired
    private AliOssUtil aliOssUtil;

    @PostMapping ("/upload")
    @Operation(summary = "文件上传")
    public Result<String> upload(MultipartFile file) throws IOException {
        log.info("文件上传,{}", file);

        try {
            // 原始文件�?
            String originalFilename = file.getOriginalFilename();

            // 构造文件名�?
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = UUID.randomUUID().toString() + extension;

            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);

        } catch (IOException e) {
            log.error("文件上传失败,{}",e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}

