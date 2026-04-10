package devflow.agent.parsing;

import java.util.ArrayList;
import java.util.List;
import org.treesitter.TSNode;

/**
 * tree-sitter 符号构造与去重支持。
 *
 * <p>这里不负责语言分支判断，只提供：
 * 1. 各语言共享的符号构造；
 * 2. 统一的 replace/body range 生成；
 * 3. 符号去重与跨度优先策略。
 */
final class TreeSitterSymbolSupport {

    CodeSymbol buildNamedSymbol(String source, TSNode node, String kind, TSNode nameNode, TSNode bodyNode) {
        if (node == null || nameNode == null) {
            return null;
        }
        String name = TreeSitterNodeSupport.sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        ByteRange bodyRange = null;
        if (bodyNode != null) {
            bodyRange = TreeSitterNodeSupport.innerBraceRange(bodyNode);
            if (bodyRange == null) {
                bodyRange = TreeSitterNodeSupport.innerBodyRange(bodyNode);
            }
        }
        return new CodeSymbol(
                name,
                kind,
                new ByteRange(node.getStartByte(), node.getEndByte()),
                bodyRange
        );
    }

    CodeSymbol buildJavaScriptVariableSymbol(String source, TSNode node) {
        TSNode declarator = TreeSitterNodeSupport.findChildByTypes(node, "variable_declarator");
        if (declarator == null) {
            return null;
        }
        TSNode nameNode = TreeSitterNodeSupport.findChildByTypes(declarator, "identifier");
        if (nameNode == null) {
            return null;
        }
        String name = TreeSitterNodeSupport.sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        return new CodeSymbol(name, "variable", new ByteRange(node.getStartByte(), node.getEndByte()), null);
    }

    CodeSymbol buildCssRuleSymbol(String source, TSNode node) {
        TSNode selectorsNode = TreeSitterNodeSupport.findChildByTypes(node, "selectors");
        TSNode blockNode = TreeSitterNodeSupport.findChildByTypes(node, "block");
        if (selectorsNode == null || blockNode == null) {
            return null;
        }
        String selector = TreeSitterNodeSupport.sliceUtf8(source, selectorsNode.getStartByte(), selectorsNode.getEndByte()).trim();
        if (selector.isBlank()) {
            return null;
        }
        return new CodeSymbol(
                selector,
                "rule",
                new ByteRange(node.getStartByte(), node.getEndByte()),
                TreeSitterNodeSupport.innerBraceRange(blockNode)
        );
    }

    CodeSymbol buildGoTypeSymbol(String source, TSNode node) {
        TSNode typeSpec = TreeSitterNodeSupport.findChildByTypes(node, "type_spec");
        if (typeSpec == null) {
            return null;
        }
        TSNode nameNode = TreeSitterNodeSupport.findChildByTypes(typeSpec, "type_identifier", "identifier");
        TSNode typeBody = TreeSitterNodeSupport.findChildByTypes(typeSpec, "struct_type", "interface_type");
        ByteRange bodyRange = null;
        if (typeBody != null) {
            bodyRange = TreeSitterNodeSupport.innerBraceRange(typeBody);
            if (bodyRange == null) {
                TSNode nestedBraceContainer = TreeSitterNodeSupport.findChildByTypes(
                        typeBody,
                        "field_declaration_list",
                        "method_spec_list"
                );
                bodyRange = TreeSitterNodeSupport.innerBraceRange(nestedBraceContainer);
            }
        }
        String name = TreeSitterNodeSupport.sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        return new CodeSymbol(name, "type", new ByteRange(node.getStartByte(), node.getEndByte()), bodyRange);
    }

    CodeSymbol buildDecoratedPythonSymbol(String source, TSNode node) {
        TSNode wrapped = TreeSitterNodeSupport.findChildByTypes(node, "class_definition", "function_definition");
        if (wrapped == null) {
            return null;
        }
        String kind = "class_definition".equals(wrapped.getType()) ? "class" : "function";
        TSNode nameNode = TreeSitterNodeSupport.findChildByTypes(wrapped, "identifier");
        TSNode bodyNode = TreeSitterNodeSupport.findChildByTypes(wrapped, "block");
        if (nameNode == null || bodyNode == null) {
            return null;
        }
        String name = TreeSitterNodeSupport.sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        return new CodeSymbol(
                name,
                kind,
                new ByteRange(node.getStartByte(), node.getEndByte()),
                TreeSitterNodeSupport.innerBodyRange(bodyNode)
        );
    }

    void addIfPresent(List<CodeSymbol> symbols, CodeSymbol symbol) {
        if (symbol != null) {
            symbols.add(symbol);
        }
    }

    List<CodeSymbol> deduplicateSymbols(List<CodeSymbol> symbols) {
        List<CodeSymbol> deduplicated = new ArrayList<>();
        for (CodeSymbol candidate : symbols) {
            int existingIndex = findEquivalentSymbolIndex(deduplicated, candidate);
            if (existingIndex < 0) {
                deduplicated.add(candidate);
                continue;
            }
            CodeSymbol existing = deduplicated.get(existingIndex);
            if (symbolSpan(candidate) > symbolSpan(existing)) {
                deduplicated.set(existingIndex, candidate);
            }
        }
        return List.copyOf(deduplicated);
    }

    private int findEquivalentSymbolIndex(List<CodeSymbol> symbols, CodeSymbol candidate) {
        for (int index = 0; index < symbols.size(); index++) {
            CodeSymbol existing = symbols.get(index);
            if (!existing.kind().equals(candidate.kind()) || !existing.name().equals(candidate.name())) {
                continue;
            }
            ByteRange existingBody = existing.bodyInnerRange();
            ByteRange candidateBody = candidate.bodyInnerRange();
            if ((existingBody == null && candidateBody == null)
                    || (existingBody != null && candidateBody != null
                    && existingBody.startByte() == candidateBody.startByte()
                    && existingBody.endByte() == candidateBody.endByte())) {
                return index;
            }
        }
        return -1;
    }

    private int symbolSpan(CodeSymbol symbol) {
        return symbol.replaceRange().endByte() - symbol.replaceRange().startByte();
    }
}
