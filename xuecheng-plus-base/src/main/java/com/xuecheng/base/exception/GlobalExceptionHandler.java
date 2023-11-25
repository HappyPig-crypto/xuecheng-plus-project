package com.xuecheng.base.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2023/2/12 17:01
 */
@Slf4j
@RestControllerAdvice
//@RestControllerAdvice
public class GlobalExceptionHandler {

 //对项目的自定义异常类型进行处理

   @ExceptionHandler(XueChengPlusException.class)
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
 public RestErrorResponse customException(XueChengPlusException e){

    //记录异常
    log.error("系统异常{}",e.getErrMessage(),e);
    //..

    //解析出异常信息
    String errMessage = e.getErrMessage();
    RestErrorResponse restErrorResponse = new RestErrorResponse(errMessage);
    return restErrorResponse;
   }



   @ExceptionHandler(Exception.class)
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
 public RestErrorResponse exception(Exception e){

    //记录异常
    log.error("系统异常{}",e.getMessage(),e);

    //解析出异常信息
    RestErrorResponse restErrorResponse = new RestErrorResponse(CommonError.UNKOWN_ERROR.getErrMessage());
    return restErrorResponse;
   }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RestErrorResponse methodArgumentNotValidException(MethodArgumentNotValidException e){
        BindingResult bindingResult = e.getBindingResult();
        List<String> errors = new ArrayList<>();
        bindingResult.getFieldErrors().stream().forEach(item->{
            errors.add(item.getDefaultMessage());
        });
        String errMessage = StringUtils.join(errors, ",");
        //记录异常
        log.error("系统异常{}",errMessage);

        //解析出异常信息
        RestErrorResponse restErrorResponse = new RestErrorResponse(errMessage);
        return restErrorResponse;
    }


}
