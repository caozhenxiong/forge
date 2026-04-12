package devflow.agent.quality;

import java.util.List;

/**
 * 单个能力项的覆盖账本。
 */
public record CoverageLedgerEntry(
        String capabilityId,
        boolean required,
        CoverageLedgerStatus status,
        List<String> caseIds,
        String note
) {

    public CoverageLedgerEntry {
        capabilityId = CapabilityIds.normalize(capabilityId);
        caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
        note = note == null ? "" : note;
    }

    public boolean covered() {
        return status == CoverageLedgerStatus.COVERED;
    }

    public boolean missingRequired() {
        return required && status != CoverageLedgerStatus.COVERED;
    }
}
