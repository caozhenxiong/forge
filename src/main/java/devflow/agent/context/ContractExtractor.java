package devflow.agent.context;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ContractExtractor {

    private final ContractSectionResolver sectionResolver = new ContractSectionResolver();
    private final ContractListSupport listSupport = new ContractListSupport();
    private final ContractMetadataReader metadataReader = new ContractMetadataReader(sectionResolver, listSupport);

    public ProductContract extractProductContract(String prd) {
        return StructuredArtifactBlocks.readFirstJsonBlock(
                prd,
                ArtifactBlockKind.PRODUCT_CONTRACT,
                ProductContract.class
        );
    }

    /**
     * PRD 阶段把固定章节投影成 PRODUCT_CONTRACT machine block。
     *
     * <p>这里只允许依赖稳定章节号和列表结构，不允许猜正文语义；
     * 下游阶段必须只读取 block，不再回退到正文。
     */
    public ProductContract projectProductContractFromPrd(String prd) {
        return ProductContract.projectedFromPrdSections(
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(prd, 1)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(prd, 2)),
                listSupport.collectReferenceItems(sectionResolver.productCapabilitiesSection(prd)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(prd, 4)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(prd, 5)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(prd, 6))
        );
    }

    public DesignContract extractDesignContract(String design) {
        return new DesignContract(
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(design, 1)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(design, 2)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(design, 3)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(design, 4)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(design, 5)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(design, 6)),
                listSupport.collectReferenceItems(sectionResolver.sectionByNumber(design, 7))
        );
    }

    public ContractView extractContractView(String prd, String design) {
        return extractContractView("", "", "", prd, design);
    }

    public ContractView extractContractView(String goal, String constraints, String prd, String design) {
        return extractContractView(goal, constraints, "", prd, design);
    }

    public ContractView extractContractView(String goal, String constraints, String analysis, String prd, String design) {
        ConstraintSourceMetadata sourceMetadata = mergeSourceMetadata(
                metadataReader.extractSourceMetadata(analysis),
                metadataReader.extractSourceMetadata(prd),
                metadataReader.extractSourceMetadata(design)
        );
        ExecutionContract executionContract = extractExecutionContract(goal, constraints, prd, design);
        return new ContractView(
                extractProductContract(prd),
                extractDesignContract(design),
                executionContract,
                sourceMetadata
        );
    }

    public ConstraintSourceMetadata extractConstraintSourceMetadata(String markdown) {
        return metadataReader.extractSourceMetadata(markdown);
    }

    public ConstraintSourceMetadata buildAuthoritativeSourceMetadata(String goal, String constraints, String... upstreamDocs) {
        ConstraintSourceMetadata upstream = mergeSourceMetadata(metadataReader.extractFromMany(upstreamDocs));
        return new ConstraintSourceMetadata(
                listSupport.combineLists(listSupport.parseLooseList(goal), listSupport.parseLooseList(constraints)),
                upstream.hardUserRequirements().isEmpty() && upstream.hardUpstreamFacts().isEmpty()
                        ? List.of()
                        : listSupport.combineLists(upstream.hardUserRequirements(), upstream.hardUpstreamFacts()),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public ExecutionContract extractExecutionContract(String goal, String constraints, String prd, String design) {
        ExecutionContract structuredExecutionContract = StructuredArtifactBlocks.readFirstJsonBlock(
                design,
                ArtifactBlockKind.EXECUTION_CONTRACT,
                ExecutionContract.class
        );
        if (structuredExecutionContract == null) {
            structuredExecutionContract = StructuredArtifactBlocks.readFirstJsonBlock(
                    prd,
                    ArtifactBlockKind.EXECUTION_CONTRACT,
                    ExecutionContract.class
            );
        }
        if (structuredExecutionContract != null) {
            return structuredExecutionContract.normalized();
        }
        Map<String, String> metadata = metadataReader.parseContractMetadataSection(design);
        if (metadata.isEmpty()) {
            metadata = metadataReader.parseContractMetadataSection(prd);
        }
        if (!metadata.isEmpty()) {
            return new ExecutionContract(
                    listSupport.parseBoolean(metadata.get(ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED), false),
                    listSupport.blank(metadata.get(ContractMetadataKeys.RUNTIME_ENTRY_KIND)),
                    listSupport.blank(metadata.get(ContractMetadataKeys.RUNTIME_ENTRY_PACKAGING_MODE)),
                    listSupport.blank(metadata.get(ContractMetadataKeys.RUNTIME_RUNTIME_OWNERSHIP_MODE)),
                    listSupport.parseBoolean(metadata.get(ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED), false),
                    listSupport.parseBoolean(metadata.get(ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED), false),
                    listSupport.parseList(metadata.get(ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS))
            ).normalized();
        }
        return new ExecutionContract(false, "unspecified", false, false, List.of()).normalized();
    }

    /**
     * 只从 Contract Metadata 读取稳定的验证元数据。
     *
     * <p>这里不再从 PRD / DESIGN 正文里猜“性能要求”“加载时间”“响应时间”等自由文本语义。
     * 如果没有显式 metadata，就视为未声明，而不是让下游自行推断。
     */
    public ValidationMetadata extractValidationMetadata(String prd, String design) {
        return metadataReader.extractValidationMetadata(design).merge(metadataReader.extractValidationMetadata(prd));
    }

    public ValidationMetadata extractValidationMetadata(String markdown) {
        return metadataReader.extractValidationMetadata(markdown);
    }

    private ConstraintSourceMetadata mergeSourceMetadata(ConstraintSourceMetadata... values) {
        return metadataReader.mergeSourceMetadata(values);
    }
}
