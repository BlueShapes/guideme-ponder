package dev.ae2guide.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** GuideME CommandLinks execute these locally, before any server command is sent. */
final class GuideCommands {
    private GuideCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(addGuideCommands(Commands.literal("ponder-ae2")));
        dispatcher.register(addGuideCommands(Commands.literal("ae2ponder")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addGuideCommands(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.executes(context -> openIndex())
                .then(Commands.argument("chapter", StringArgumentType.greedyString())
                        .executes(context -> openChapter(StringArgumentType.getString(context, "chapter"))))
                .then(Commands.literal("item")
                        .then(Commands.argument("item_name", StringArgumentType.greedyString())
                                .executes(context -> openItem(StringArgumentType.getString(context, "item_name")))))
                .then(Commands.literal("lesson")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        GuideBook.lessons().stream().map(GuideBook.Lesson::id), builder))
                                .executes(context -> openLesson(StringArgumentType.getString(context, "id")))));
    }

    private static int openIndex() throws CommandSyntaxException {
        requireWorld();
        GuideNavigation.openIndex();
        return 1;
    }

    private static int openChapter(String target) throws CommandSyntaxException {
        requireWorld();
        var lesson = GuideNavigation.firstLessonForChapter(target);
        if (lesson == null) throw unknownTarget(target);
        GuidePonderScreen.open(lesson);
        return 1;
    }

    private static int openItem(String target) throws CommandSyntaxException {
        requireWorld();
        var lesson = GuideNavigation.firstLessonForItem(target);
        if (lesson == null) throw unknownTarget(target);
        GuidePonderScreen.open(lesson);
        return 1;
    }

    private static int openLesson(String id) throws CommandSyntaxException {
        requireWorld();
        var lesson = GuideBook.lessons().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new DynamicCommandExceptionType(value ->
                        Component.translatable("ae2ponder.command.unknown", value)).create(id));
        GuidePonderScreen.open(lesson);
        return 1;
    }

    private static void requireWorld() throws CommandSyntaxException {
        if (Minecraft.getInstance().level == null)
            throw new SimpleCommandExceptionType(Component.translatable("ae2ponder.command.requires_world")).create();
    }

    private static CommandSyntaxException unknownTarget(String target) {
        return new DynamicCommandExceptionType(value ->
                Component.translatable("ae2ponder.command.unknown_target", value)).create(target);
    }
}
