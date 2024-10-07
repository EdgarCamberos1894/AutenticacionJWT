package com.example.demo.dto.mapper;

import com.example.demo.dto.request.UserProfileReq;
import com.example.demo.dto.response.UserProfileRes;
import com.example.demo.model.entity.User;
import com.example.demo.model.entity.UserProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserProfileMapper  {

    @Mapping(target = "name", expression = "java(CustomMapperUtil.capitalizeName(name))")
    @Mapping(target = "lastName", expression = "java(CustomMapperUtil.capitalizeName(lastName))")
    @Mapping(source = "user", target = "user")
    UserProfile toEntity(String name, String lastName, User user);

    @Mapping(source = "lastName", target = "lastname")
    UserProfileRes toResponse(UserProfile userProfile);

    @Mapping(target = "name", expression = "java(CustomMapperUtil.mapName(userProfileReq.name(), userProfile.getName()))")
    @Mapping(target = "lastName", expression = "java(CustomMapperUtil.mapName(userProfileReq.lastname(), userProfile.getLastName()))")
    @Mapping(target = "photo", expression = "java(CustomMapperUtil.mapPhoto(userProfileReq.photo(), userProfile.getPhoto(), userProfileReq.isRemovePhoto()))")
    void updateEntity(UserProfileReq userProfileReq, @MappingTarget UserProfile userProfile);

}


class CustomMapperUtil {

    public static String mapName(String newValue, String currentValue) {
        if (newValue != null && !newValue.isBlank()) {
            newValue = capitalizeName(newValue);
            return newValue;
        }
        return currentValue;
    }

    public static String capitalizeName(String name) {
        String[] words = name.trim().toLowerCase().split("\\s+");
        StringBuilder formattedName = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                formattedName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return formattedName.toString().trim();
    }

    public static String mapPhoto(String newPhoto, String currentPhoto, boolean isRemovePhoto) {
        if (isRemovePhoto) {
            return null;
        }
        if (newPhoto != null && !newPhoto.isBlank()) {
            return newPhoto;
        }
        return currentPhoto;
    }
}
