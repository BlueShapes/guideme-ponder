package dev.ae2guide.client;

import guideme.compiler.IndexingContext;
import guideme.compiler.IndexingSink;
import guideme.compiler.PageCompiler;
import guideme.compiler.TagCompiler;
import guideme.document.block.LytBlockContainer;
import guideme.document.flow.LytFlowParent;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import dev.ae2guide.Platform;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Conditionally compiles and indexes the children of the namespaced GuideME IfModLoaded tag. */
public final class OptionalModTagCompiler implements TagCompiler {
    private static final String TAG = "ae2ponder:IfModLoaded";

    @Override public Set<String> getTagNames() { return Set.of(TAG); }

    @Override public void compileBlockContext(PageCompiler compiler, LytBlockContainer parent,
                                              MdxJsxFlowElement element) {
        if (matches(element)) compiler.compileBlockContext(element.children(), parent);
    }

    @Override public void compileFlowContext(PageCompiler compiler, LytFlowParent parent,
                                             MdxJsxTextElement element) {
        if (matches(element)) compiler.compileFlowContext(element.children(), parent);
    }

    @Override public void index(IndexingContext context, MdxJsxElementFields element, IndexingSink sink) {
        if (matches(element)) context.indexContent(element.children(), sink);
    }

    private static boolean matches(MdxJsxElementFields element) {
        var modsAttribute = element.getAttribute("mods");
        if (modsAttribute == null || !modsAttribute.hasStringValue()) return false;
        List<String> required = Arrays.stream(modsAttribute.getStringValue().split(",", -1))
                .map(String::trim).toList();
        if (required.isEmpty() || required.stream().anyMatch(mod -> mod.isEmpty() || !mod.matches("[a-z0-9_.-]+")))
            return false;

        var modeAttribute = element.getAttribute("mode");
        String mode = modeAttribute == null ? "all"
                : modeAttribute.hasStringValue() ? modeAttribute.getStringValue() : "";
        if (!mode.equals("all") && !mode.equals("any")) return false;

        return mode.equals("all") ? required.stream().allMatch(Platform::isModLoaded)
                : required.stream().anyMatch(Platform::isModLoaded);
    }
}
