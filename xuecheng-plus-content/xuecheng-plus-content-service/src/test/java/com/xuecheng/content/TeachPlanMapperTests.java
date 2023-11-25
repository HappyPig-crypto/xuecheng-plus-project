package com.xuecheng.content;

import com.baomidou.mybatisplus.annotation.TableId;
import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.model.dto.TeachplanDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * ClassName: TeachPlanMapperTests
 * Description:
 *
 * @Author HappyPig
 * @Create 2023/11/19 10:27
 * @Version 1.0
 */
@SpringBootTest
public class TeachPlanMapperTests {
    @Autowired
    private TeachplanMapper teachplanMapper;
    @Test
    public void testTeachPlanMapper() {
        List<TeachplanDto> teachplanDtos =  teachplanMapper.selectTreeNodes(117L);
        System.out.println(teachplanDtos);
    }
}
