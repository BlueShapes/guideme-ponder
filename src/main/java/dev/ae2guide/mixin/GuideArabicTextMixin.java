package dev.ae2guide.mixin;

import dev.ae2guide.client.RightToLeftText;
import guideme.document.block.LytDocument;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContent;
import guideme.document.flow.LytFlowText;
import guideme.libs.mdast.model.MdAstRoot;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shape only compiled display nodes, leaving the source AST used for search and commands intact. */
@Pseudo
@Mixin(targets = "guideme.compiler.PageCompiler", remap = false)
public abstract class GuideArabicTextMixin {
    @Shadow @Final private ResourceLocation pageId;

    @Inject(method = "compile(Lguideme/libs/mdast/model/MdAstRoot;)Lguideme/document/block/LytDocument;",
            at = @At("RETURN"))
    private void ae2ponder$shapeDisplayText(MdAstRoot root, CallbackInfoReturnable<LytDocument> callback) {
        if (!pageId.getNamespace().equals("ae2ponder")) return;
        callback.getReturnValue().visit(new LytVisitor() {
            @Override public Result beforeFlowContent(LytFlowContent content) {
                if (content instanceof LytFlowText text) text.setText(RightToLeftText.shapeArabic(text.getText()));
                return Result.CONTINUE;
            }
        });
    }
}
