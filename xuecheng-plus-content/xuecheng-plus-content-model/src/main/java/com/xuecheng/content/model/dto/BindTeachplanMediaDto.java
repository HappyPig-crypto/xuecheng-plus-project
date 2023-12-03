package com.xuecheng.content.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * ClassName: BindTeachplanMediaDto
 * Description:
 *
 * @Author HappyPig
 * @Create 2023/12/3 13:40
 * @Version 1.0
 */
@Data
@ApiModel(value = "BindTeachplanMediaDto", description = "教学计划-媒资绑定DTO")
public class BindTeachplanMediaDto {
    @ApiModelProperty(value = "媒资文件id", required = true)
    private String mediaId;

    @ApiModelProperty(value = "媒资文件名称", required = true)
    private String fileName;

    @ApiModelProperty(value = "课程计划标识", required = true)
    private Long teachplanId;
}