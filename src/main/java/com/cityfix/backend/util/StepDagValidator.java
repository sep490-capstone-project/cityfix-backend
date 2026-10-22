package com.cityfix.backend.util;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;

import java.util.*;

public final class StepDagValidator {

    private StepDagValidator() {
        // Utility class
    }

    /**
     * Xác thực toàn diện cấu trúc DAG cho tập các bước xử lý:
     * 1. Kiểm tra tự phụ thuộc (self-dependency)
     * 2. Kiểm tra các bước phụ thuộc phải thuộc cùng phạm vi tập hợp các bước
     * 3. Kiểm tra mốc mở đầu (Anchor Step rule)
     * 4. Kiểm tra chu trình phụ thuộc khép kín (Cycle Detection - DFS 3-Coloring)
     */
    public static void validateDag(Collection<UUID> allStepIds, Map<UUID, Set<UUID>> adjacencyMap) {
        if (allStepIds == null || allStepIds.isEmpty()) {
            return;
        }

        Set<UUID> validStepIdSet = new HashSet<>(allStepIds);

        // 1 & 2. Kiểm tra tính hợp lệ cơ bản của các cạnh quan hệ
        for (UUID stepId : allStepIds) {
            Set<UUID> dependencies = adjacencyMap.get(stepId);
            if (dependencies != null) {
                for (UUID depId : dependencies) {
                    if (stepId.equals(depId)) {
                        throw new AppException(ErrorCode.INVALID_STEP_DEPENDENCY,
                                "Bước xử lý không thể tự phụ thuộc vào chính mình: " + stepId);
                    }
                    if (!validStepIdSet.contains(depId)) {
                        throw new AppException(ErrorCode.INVALID_STEP_DEPENDENCY,
                                "Bước phụ thuộc " + depId + " không thuộc cùng vé hoặc không nằm trong danh sách bước hợp lệ");
                    }
                }
            }
        }

        // 3. Anchor Step Check
        validateAnchorStep(allStepIds, adjacencyMap);

        // 4. Cycle Check
        validateNoCycle(allStepIds, adjacencyMap);
    }

    /**
     * Kiểm tra mốc mở đầu (Anchor Step): Phải có ít nhất 1 step không phụ thuộc vào bất kỳ step nào khác
     */
    public static void validateAnchorStep(Collection<UUID> allStepIds, Map<UUID, Set<UUID>> adjacencyMap) {
        if (allStepIds == null || allStepIds.isEmpty()) {
            return;
        }

        boolean hasAnchor = allStepIds.stream().anyMatch(stepId -> {
            Set<UUID> deps = adjacencyMap.get(stepId);
            return deps == null || deps.isEmpty();
        });

        if (!hasAnchor) {
            throw new AppException(ErrorCode.ANCHOR_STEP_REQUIRED,
                    "Quy trình xử lý vé không hợp lệ: Phải có ít nhất 1 bước mở đầu không phụ thuộc vào bất kỳ bước nào khác (Anchor Step)");
        }
    }

    /**
     * Kiểm tra chu trình (Cycle Detection) bằng thuật toán tô màu DFS 3 trạng thái:
     * WHITE (0): Chưa thăm
     * GRAY (1): Đang thăm trong ngăn xếp đệ quy hiện tại (In active recursion stack)
     * BLACK (2): Đã duyệt xong nhánh an toàn
     */
    public static void validateNoCycle(Collection<UUID> allStepIds, Map<UUID, Set<UUID>> adjacencyMap) {
        if (allStepIds == null || allStepIds.isEmpty()) {
            return;
        }

        Map<UUID, Integer> colorMap = new HashMap<>();
        for (UUID stepId : allStepIds) {
            colorMap.put(stepId, 0); // WHITE
        }

        for (UUID stepId : allStepIds) {
            if (colorMap.get(stepId) == 0) {
                if (hasCycleDfs(stepId, adjacencyMap, colorMap)) {
                    throw new AppException(ErrorCode.STEP_CYCLE_DETECTED,
                            "Phát hiện vòng lặp phụ thuộc khép kín giữa các bước xử lý (Dependency Cycle Detected)");
                }
            }
        }
    }

    private static boolean hasCycleDfs(UUID u, Map<UUID, Set<UUID>> adjacencyMap, Map<UUID, Integer> colorMap) {
        colorMap.put(u, 1); // GRAY

        Set<UUID> dependencies = adjacencyMap.getOrDefault(u, Collections.emptySet());
        for (UUID v : dependencies) {
            Integer color = colorMap.get(v);
            if (color == null) {
                continue;
            }
            if (color == 1) {
                // Gặp lại node đang GRAY trong call stack hiện tại -> tồn tại chu trình
                return true;
            }
            if (color == 0 && hasCycleDfs(v, adjacencyMap, colorMap)) {
                return true;
            }
        }

        colorMap.put(u, 2); // BLACK
        return false;
    }
}
