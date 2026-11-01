package dev.ae2guide.validation.mixin;

import dev.ae2guide.validation.TextWindowCoexistenceProbe;
import guideme.internal.shaded.lucene.search.Query;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A second same-invocation modifier, confined to the non-distributed validation client. */
@Mixin(targets = "guideme.internal.search.GuideQueryParser", remap = false)
public abstract class GuideSearchCoexistenceMixin {
    @ModifyArg(method = "buildFieldQuery", remap = false, index = 0, at = @At(value = "INVOKE", target =
            "Lguideme/internal/shaded/lucene/search/BooleanQuery$Builder;add(Lguideme/internal/shaded/lucene/search/Query;Lguideme/internal/shaded/lucene/search/BooleanClause$Occur;)Lguideme/internal/shaded/lucene/search/BooleanQuery$Builder;"))
    private static Query ae2guidevalidation$observeSearchTerm(Query query) {
        TextWindowCoexistenceProbe.recordSearchQuery();
        return query;
    }
}
