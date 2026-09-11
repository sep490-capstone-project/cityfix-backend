package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface ImageMapper {

    @Mapping(target = "publicId", expression = "java(getMapString(map, \"public_id\"))")
    @Mapping(target = "secureUrl", expression = "java(getMapString(map, \"secure_url\"))")
    @Mapping(target = "format", expression = "java(getMapString(map, \"format\"))")
    @Mapping(target = "width", expression = "java(getMapInteger(map, \"width\"))")
    @Mapping(target = "height", expression = "java(getMapInteger(map, \"height\"))")
    @Mapping(target = "bytes", expression = "java(getMapLong(map, \"bytes\"))")
    @Mapping(target = "createdAt", expression = "java(getMapString(map, \"created_at\"))")
    ImageUploadResponse toImageUploadResponse(Map<String, Object> map);

    default String getMapString(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }

    default Integer getMapInteger(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    default Long getMapLong(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
