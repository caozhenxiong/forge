package devflow.agent.parsing;

import java.util.ArrayList;
import java.util.List;
import org.treesitter.TSNode;

/**
 * 后端语言结构扫描器。
 *
 * <p>当前负责：
 * 1. Java 类型/构造器/方法；
 * 2. Python class/function；
 * 3. Go type/function/method。
 */
final class TreeSitterBackendCodeInspector {

    private final TreeSitterSymbolSupport symbolSupport;

    TreeSitterBackendCodeInspector(TreeSitterSymbolSupport symbolSupport) {
        this.symbolSupport = symbolSupport;
    }

    List<CodeSymbol> inspectJavaSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        TreeSitterNodeSupport.walk(root, node -> {
            switch (node.getType()) {
                case "class_declaration" -> symbolSupport.addIfPresent(symbols, buildJavaTypeSymbol(source, node, "class"));
                case "interface_declaration" -> symbolSupport.addIfPresent(symbols, buildJavaTypeSymbol(source, node, "interface"));
                case "enum_declaration" -> symbolSupport.addIfPresent(symbols, buildJavaTypeSymbol(source, node, "enum"));
                case "record_declaration" -> symbolSupport.addIfPresent(symbols, buildJavaTypeSymbol(source, node, "record"));
                case "constructor_declaration" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                        source,
                        node,
                        "constructor",
                        TreeSitterNodeSupport.findChildByTypes(node, "identifier"),
                        TreeSitterNodeSupport.findChildByTypes(node, "constructor_body")
                ));
                case "method_declaration" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                        source,
                        node,
                        "method",
                        TreeSitterNodeSupport.findChildByTypes(node, "identifier"),
                        TreeSitterNodeSupport.findChildByTypes(node, "block")
                ));
                default -> {
                }
            }
        });
        return symbolSupport.deduplicateSymbols(symbols);
    }

    List<CodeSymbol> inspectPythonSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        TreeSitterNodeSupport.walk(root, node -> {
            switch (node.getType()) {
                case "class_definition" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                        source,
                        node,
                        "class",
                        TreeSitterNodeSupport.findChildByTypes(node, "identifier"),
                        TreeSitterNodeSupport.findChildByTypes(node, "block")
                ));
                case "function_definition" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                        source,
                        node,
                        "function",
                        TreeSitterNodeSupport.findChildByTypes(node, "identifier"),
                        TreeSitterNodeSupport.findChildByTypes(node, "block")
                ));
                case "decorated_definition" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildDecoratedPythonSymbol(source, node));
                default -> {
                }
            }
        });
        return symbolSupport.deduplicateSymbols(symbols);
    }

    List<CodeSymbol> inspectGoSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        TreeSitterNodeSupport.walk(root, node -> {
            switch (node.getType()) {
                case "type_declaration" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildGoTypeSymbol(source, node));
                case "method_declaration" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                        source,
                        node,
                        "method",
                        TreeSitterNodeSupport.findChildByTypes(node, "field_identifier"),
                        TreeSitterNodeSupport.findChildByTypes(node, "block")
                ));
                case "function_declaration" -> symbolSupport.addIfPresent(symbols, symbolSupport.buildNamedSymbol(
                        source,
                        node,
                        "function",
                        TreeSitterNodeSupport.findChildByTypes(node, "identifier"),
                        TreeSitterNodeSupport.findChildByTypes(node, "block")
                ));
                default -> {
                }
            }
        });
        return symbolSupport.deduplicateSymbols(symbols);
    }

    private CodeSymbol buildJavaTypeSymbol(String source, TSNode node, String kind) {
        return symbolSupport.buildNamedSymbol(
                source,
                node,
                kind,
                TreeSitterNodeSupport.findChildByTypes(node, "identifier", "type_identifier"),
                TreeSitterNodeSupport.findChildByTypes(node, "class_body", "interface_body", "enum_body", "record_body")
        );
    }
}
