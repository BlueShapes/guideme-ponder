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

import java.util.Set;

/** Index translated captions in GuideME without adding them to the displayed chapter links. */
public final class SearchTermsTagCompiler implements TagCompiler {
    @Override public Set<String> getTagNames() { return Set.of("ae2ponder:SearchTerms"); }

    @Override public void compileBlockContext(PageCompiler compiler, LytBlockContainer parent,
                                              MdxJsxFlowElement element) {}

    @Override public void compileFlowContext(PageCompiler compiler, LytFlowParent parent,
                                             MdxJsxTextElement element) {}

    @Override public void index(IndexingContext context, MdxJsxElementFields element, IndexingSink sink) {
        context.indexContent(element.children(), sink);
    }
}
