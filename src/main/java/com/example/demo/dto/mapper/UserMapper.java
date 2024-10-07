package com.example.demo.dto.mapper;

import com.example.demo.dto.request.UserReq;
import com.example.demo.dto.response.UserRes;
import com.example.demo.model.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;



@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(target = "email", expression = "java(userReq.email().toLowerCase())")
  User toEntity(UserReq userReq);

  UserRes toResponse (User user);
}


