package devflow.agent.protocol;

/**
 * 跨 review / revision note / implementation directive 复用的文件级 patch 协议。
 *
 * <p>这里只表达稳定的文件级约束，不带任何执行态信息。
 * 后续执行链只消费这份结构化字段，不再从 prose 里猜“该改哪个文件、走哪条 patch 路由”。
 */
public record FileChangePayload(
        String path,
        String action,
        String reason,
        String editScope,
        String runtimeOwnership,
        Boolean hostHtmlPatchRequired
) {
}
