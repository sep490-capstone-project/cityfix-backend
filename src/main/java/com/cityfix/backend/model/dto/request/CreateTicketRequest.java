package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.util.List;
import java.util.UUID;

@Data
public class CreateTicketRequest {

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    @Deprecated
    private String imageUrl;

    @Size(max = 7, message = "Tối đa 7 tệp hình ảnh/video")
    private List<@NotBlank(message = "URL tệp không được để trống") @Size(max = 500, message = "URL tệp không được vượt quá 500 ký tự") String> imageUrls;

    @NotEmpty(message = "At least one subcategory is required")
    private List<@NotNull(message = "Mã sự cố phụ không được để trống") UUID> subCategoryIds;

    @NotNull(message = "Priority is required")
    private com.cityfix.backend.model.enums.Priority priority;

    @Size(max = 1000, message = "Lý do mức độ ưu tiên không được vượt quá 1000 ký tự")
    private String priorityReasoning;

    @NotNull(message = "Location method is required")
    private com.cityfix.backend.model.enums.LocationMethod locationMethod;

    @NotBlank(message = "Địa chỉ sự cố không được để trống")
    @Size(max = 255, message = "Địa chỉ sự cố không được vượt quá 255 ký tự")
    private String address;

    @NotNull(message = "Latitude is required")
    @Range(min = -90, max = 90, message = "Vĩ độ không hợp lệ (-90 đến 90)")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Range(min = -180, max = 180, message = "Kinh độ không hợp lệ (-180 đến 180)")
    private Double longitude;

    @DecimalMin(value = "0.0", message = "Điểm tin cậy AI phải từ 0 đến 100")
    @DecimalMax(value = "100.0", message = "Điểm tin cậy AI phải từ 0 đến 100")
    private Double confidenceScore;

    private Boolean isAnonymous;
}
