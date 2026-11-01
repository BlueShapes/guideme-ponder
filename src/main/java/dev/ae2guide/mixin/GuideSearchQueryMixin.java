package dev.ae2guide.mixin;

import guideme.internal.shaded.lucene.search.MatchNoDocsQuery;
import guideme.internal.shaded.lucene.search.Query;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep GuideME searches containing analyzer stop words from discarding the whole query. */
@Pseudo
@Mixin(targets = "guideme.internal.search.GuideQueryParser", remap = false)
public abstract class GuideSearchQueryMixin {
    @ModifyArg(method = "buildFieldQuery", remap = false, index = 0, at = @At(value = "INVOKE", target =
            "Lguideme/internal/shaded/lucene/search/BooleanQuery$Builder;add(Lguideme/internal/shaded/lucene/search/Query;Lguideme/internal/shaded/lucene/search/BooleanClause$Occur;)Lguideme/internal/shaded/lucene/search/BooleanQuery$Builder;"))
    private static Query ae2ponder$emptyAnalyzedTerm(Query query) {
        // Both supported GuideME versions add per-term SHOULD clauses. An analyzer may return null
        // for a stop word; an always-empty clause leaves the remaining terms searchable.
        // Repeated compatible modifiers are harmless because the replacement is non-null.
        return query == null ? new MatchNoDocsQuery() : query;
    }
}
