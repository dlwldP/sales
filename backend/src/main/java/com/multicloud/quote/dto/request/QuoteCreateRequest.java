package com.multicloud.quote.dto.request;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "견적 생성 요청")
public record QuoteCreateRequest(

        @Schema(example = "테스트 서버")
        @NotBlank(message = "workloadName은 필수입니다.")
        @Size(max = 100, message = "workloadName은 100자를 넘을 수 없습니다.")
        String workloadName,

        @Schema(example = "4")
        @NotNull(message = "vcpu는 필수입니다.")
        @Min(value = 1, message = "vcpu는 1 이상이어야 합니다.")
        @Max(value = 512, message = "vcpu는 512 이하여야 합니다.")
        Integer vcpu,

        @Schema(example = "16")
        @NotNull(message = "memoryGb는 필수입니다.")
        @Min(value = 1, message = "memoryGb는 1 이상이어야 합니다.")
        @Max(value = 4096, message = "memoryGb는 4096 이하여야 합니다.")
        Integer memoryGb,

        @Schema(example = "100")
        @NotNull(message = "storageGb는 필수입니다.")
        @Min(value = 0, message = "storageGb는 0 이상이어야 합니다.")
        @Max(value = 65536, message = "storageGb는 65536 이하여야 합니다.")
        Integer storageGb,

        @Schema(description = "논리 리전 키 (korea, tokyo, us-east, west-europe)", example = "korea")
        @NotBlank(message = "region은 필수입니다.")
        String region,

        @Schema(example = "LINUX")
        @NotNull(message = "os는 필수입니다.")
        OsType os,

        @Schema(description = "비교할 벤더 목록", example = "[\"AWS\", \"AZURE\"]")
        @NotEmpty(message = "vendors는 1개 이상이어야 합니다.")
        List<VendorType> vendors
) {
}
