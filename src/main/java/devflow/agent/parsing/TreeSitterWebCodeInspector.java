package devflow.agent.parsing;

import java.util.ArrayList;
import java.util.List;
import org.treesitter.TSNode;

/**
 * Web 相关语言结构扫描器。
 *
 * <p>当前负责：
 * 1. JavaScript / TypeScript 符号提取；
 * 2. CSS rule 级符号提取。
 */
final class TreeSitterWebCodeInspector {

    private final TreeSitterSymbolSupport symbolSupport;

    TreeSitterWebCodeInspector(TreeSitterSymbolSupport symbolSupport) {
        this.symbolSupport = symbolSupport;
    }

    List<CodeSymbol> inspectJavascriptSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        if (root == null || root.isNull()) {
            return List.of();
        }
        for (int index = 0; index < root.getChildCount(); index++) {
            TSNode node = unwrapJavaScriptTopLevelDeclaration(root.getChild(index));
            if (node == null || node.isNull()) {
                continue;
            }
            switch (node.getType()) {
                case "class_declaration" -> {
                    symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                            source,
                            node,
                            "class",
                            TreeSitterNodeSupport.findChildByTypes(node, "identifier", "type_identifier"),
                            TreeSitterNodeSupport.findChildByTypes(node, "class_body")
                    ));
                    collectJavaScriptClassMethods(source, node, symbols);
                }
                case "function_declaration" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                        source,
                        node,
                        "function",
                        TreeSitterNodeSupport.findChildByTypes(node, "identifier"),
                        TreeSitterNodeSupport.findChildByTypes(node, "statement_block")
                ));
                case "lexical_declaration" -> symbolSupport.addIfPresent(
                        symbols,
                        symbolSupport.buildJavaScriptVariableSymbol(source, node)
                );
                default -> {
                }
            }
        }
        return symbolSupport.deduplicateSymbols(symbols);
    }

    private TSNode unwrapJavaScriptTopLevelDeclaration(TSNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!"export_statement".equals(node.getType())) {
            return node;
        }
        return TreeSitterNodeSupport.findChildByTypes(
                node,
                "class_declaration",
                "function_declaration",
                "lexical_declaration"
        );
    }

    private void collectJavaScriptClassMethods(String source, TSNode classNode, List<CodeSymbol> symbols) {
        TSNode classBody = TreeSitterNodeSupport.findChildByTypes(classNode, "class_body");
        if (classBody == null || classBody.isNull()) {
            return;
        }
        for (int index = 0; index < classBody.getChildCount(); index++) {
            TSNode member = classBody.getChild(index);
            if (!"method_definition".equals(member.getType())) {
                continue;
            }
            symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                    source,
                    member,
                    "method",
                    TreeSitterNodeSupport.findChildByTypes(member, "property_identifier", "identifier"),
                    TreeSitterNodeSupport.findChildByTypes(member, "statement_block")
            ));
        }
    }

    List<CodeSymbol> inspectCssSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        TreeSitterNodeSupport.walk(root, node -> {
            if ("rule_set".equals(node.getType())) {
                symbolSupport.addIfPresent(symbols, symbolSupport.buildCssRuleSymbol(source, node));
            }
        });
        return symbolSupport.deduplicateSymbols(symbols);
    }
}
