package devflow.agent.editing.precise;

import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.List;

public class CodePreciseEditor {

    private final CodePreciseSymbolSupport symbolSupport;

    public CodePreciseEditor(TreeSitterSupport treeSitterSupport) {
        this.symbolSupport = new CodePreciseSymbolSupport(treeSitterSupport);
    }

    public boolean supportsPreciseEditing(Path relativePath, String source) {
        return symbolSupport.supportsPreciseEditing(relativePath, source);
    }

    /**
     * 新建代码文件或空文件在没有现成符号时，仍允许走 append-only 骨架单元。
     * 这让“局部编辑优先”也能覆盖新文件，而不是退回整文件重写。
     */
    public boolean supportsAppendOnlyEditing(Path relativePath, String source) {
        return symbolSupport.supportsAppendOnlyEditing(relativePath, source);
    }

    public String describeSymbols(Path relativePath, String source) {
        return symbolSupport.describeSymbols(relativePath, source);
    }

    public List<String> listSymbolNames(Path relativePath, String source) {
        return symbolSupport.listSymbolNames(relativePath, source);
    }

    /**
     * 只返回支持“插入/替换 body”的稳定符号名。
     *
     * <p>这层主要给 working-set 规划使用，避免把只有声明范围、没有 body 边界的局部变量
     * 当成可持续精确编辑的锚点，导致模型反复命中 TARGET_NOT_FOUND。
     */
    public List<String> listInsertableSymbolNames(Path relativePath, String source) {
        return symbolSupport.listInsertableSymbolNames(relativePath, source);
    }

    public boolean hasInsertableSymbols(Path relativePath, String source) {
        return symbolSupport.hasInsertableSymbols(relativePath, source);
    }
}
