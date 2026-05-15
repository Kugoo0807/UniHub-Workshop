package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Result payload for student CSV synchronization")
public class StudentSyncResponse {

    @Schema(description = "Total processed rows", example = "50000")
    private long totalRows;

    @Schema(description = "Successfully upserted rows", example = "49950")
    private long successRows;

    @Schema(description = "Failed rows", example = "50")
    private long failedRows;

    @Schema(description = "Batch size used during sync", example = "500")
    private int batchSize;

    @Schema(description = "CSV fetch strategy", example = "SUPABASE_HTTP")
    private String strategy;

    @Schema(description = "Whether sync was manually triggered", example = "true")
    private boolean manualTrigger;
}
